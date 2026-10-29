package com.puter.unofficial.logging;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe singleton managing the in-memory log buffer, disk persistence,
 * badge counters, and live UI dispatches for the System Log Console.
 * Features deep telemetry interception for WebSockets, audio pipelines, and background RPC.
 */
public class LiveLogManager {

    private static final String TAG = "LiveLogManager";
    private static final String LOG_FILE_NAME = "system_diagnostic_logs.json";
    private static final int MAX_MEMORY_LOGS = 1000;

    private static volatile LiveLogManager instance;
    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService ioExecutor;

    private final List<LogEntry> logsList = new CopyOnWriteArrayList<>();
    private final List<LogEventListener> listeners = new CopyOnWriteArrayList<>();

    public interface LogEventListener {
        void onNewLogEntry(LogEntry entry, int totalCount, int errorCount);
        void onLogsCleared();
    }

    private LiveLogManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.ioExecutor = Executors.newSingleThreadExecutor();

        loadLogsFromDisk();
        setupCrashHandler();
    }

    public static synchronized LiveLogManager getInstance(Context context) {
        if (instance == null) {
            instance = new LiveLogManager(context);
        }
        return instance;
    }

    /**
     * Parameterless singleton accessor for classes where context is already initialized.
     */
    public static synchronized LiveLogManager getInstance() {
        return instance;
    }

    public void addListener(LogEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LogEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    // --- LOGGING DISPATCH METHODS ---

    public void ok(String tag, String title, String details) {
        log(LogEntry.Level.OK, tag, title, details);
    }

    public void info(String tag, String title, String details) {
        log(LogEntry.Level.INFO, tag, title, details);
    }

    public void warn(String tag, String title, String details) {
        log(LogEntry.Level.WARN, tag, title, details);
    }

    public void warn(String tag, String title, Throwable throwable) {
        String details = extractStackTrace(throwable);
        log(LogEntry.Level.WARN, tag, title, details);
    }

    public void error(String tag, String title, String details) {
        log(LogEntry.Level.ERROR, tag, title, details);
    }

    public void error(String tag, String title, Throwable throwable) {
        String details = extractStackTrace(throwable);
        log(LogEntry.Level.ERROR, tag, title, details);
    }

    public void error(String tag, String title, String details, Throwable throwable) {
        String fullDetails = (details != null ? details : "") + "\n" + extractStackTrace(throwable);
        log(LogEntry.Level.ERROR, tag, title, fullDetails.trim());
    }

    public void process(String tag, String title, String details) {
        log(LogEntry.Level.PROCESS, tag, title, details);
    }

    public void crash(String tag, String title, Throwable throwable) {
        String details = extractStackTrace(throwable);
        log(LogEntry.Level.CRASH, tag, title, details);
    }

    private String extractStackTrace(Throwable throwable) {
        if (throwable == null) return "";
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (Exception ignored) {
            return String.valueOf(throwable.getMessage());
        }
    }

    public void log(LogEntry.Level level, String tag, String title, String details) {
        LogEntry entry = new LogEntry(level, tag, title, details);

        logsList.add(0, entry); // Insert newest at the top
        while (logsList.size() > MAX_MEMORY_LOGS) {
            logsList.remove(logsList.size() - 1);
        }

        // Mirror to Android native logcat for USB debugging
        int priority = Log.INFO;
        if (level == LogEntry.Level.WARN) priority = Log.WARN;
        else if (level == LogEntry.Level.ERROR || level == LogEntry.Level.CRASH) priority = Log.ERROR;
        else if (level == LogEntry.Level.OK) priority = Log.DEBUG;
        Log.println(priority, "LiveLog_" + tag, title + (!details.isEmpty() ? " -> " + details : ""));

        // Notify UI listeners on Main Looper
        final int total = getTotalCount();
        final int errors = getErrorCount();
        mainHandler.post(() -> {
            for (LogEventListener l : listeners) {
                l.onNewLogEntry(entry, total, errors);
            }
        });

        // Asynchronously save to persistent storage
        scheduleSaveToDisk();
    }

    // --- QUERY & METRICS ---

    public List<LogEntry> getAllLogs() {
        return new ArrayList<>(logsList);
    }

    public List<LogEntry> getFilteredLogs(String query, String category) {
        List<LogEntry> filtered = new ArrayList<>();
        for (LogEntry entry : logsList) {
            if (entry.matches(query, category)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public int getTotalCount() {
        return logsList.size();
    }

    public int getErrorCount() {
        int count = 0;
        for (LogEntry e : logsList) {
            if (e.getLevel() == LogEntry.Level.ERROR || e.getLevel() == LogEntry.Level.CRASH) {
                count++;
            }
        }
        return count;
    }

    public void clearLogs() {
        logsList.clear();
        ioExecutor.execute(() -> {
            try {
                File file = new File(context.getFilesDir(), LOG_FILE_NAME);
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception ignored) {}
        });

        mainHandler.post(() -> {
            for (LogEventListener l : listeners) {
                l.onLogsCleared();
            }
        });
    }

    public String getAllLogsAsClipboardString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SYSTEM LOG CONSOLE EXPORT ===\n");
        sb.append("Total Logs: ").append(getTotalCount()).append(" | Errors: ").append(getErrorCount()).append("\n\n");

        List<LogEntry> chronological = new ArrayList<>(logsList);
        Collections.reverse(chronological); // Oldest to newest for export

        for (LogEntry entry : chronological) {
            sb.append(entry.toClipboardFormat());
        }
        return sb.toString();
    }

    // --- DISK PERSISTENCE ---

    private void scheduleSaveToDisk() {
        ioExecutor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                // Save up to the latest 500 logs on disk
                int limit = Math.min(logsList.size(), 500);
                for (int i = 0; i < limit; i++) {
                    LogEntry e = logsList.get(i);
                    JSONObject obj = new JSONObject();
                    obj.put("timestamp", e.getTimestamp());
                    obj.put("level", e.getLevel().name());
                    obj.put("tag", e.getTag());
                    obj.put("title", e.getTitle());
                    obj.put("details", e.getDetails());
                    arr.put(obj);
                }

                File file = new File(context.getFilesDir(), LOG_FILE_NAME);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist logs to disk: " + e.getMessage());
            }
        });
    }

    private void loadLogsFromDisk() {
        ioExecutor.execute(() -> {
            try {
                File file = new File(context.getFilesDir(), LOG_FILE_NAME);
                if (!file.exists()) return;

                StringBuilder sb = new StringBuilder();
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }

                String jsonStr = sb.toString();
                if (jsonStr.trim().startsWith("[")) {
                    JSONArray arr = new JSONArray(jsonStr);
                    List<LogEntry> loaded = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        long ts = obj.optLong("timestamp", System.currentTimeMillis());
                        String lvlStr = obj.optString("level", "INFO");
                        LogEntry.Level lvl = LogEntry.Level.INFO;
                        try {
                            lvl = LogEntry.Level.valueOf(lvlStr);
                        } catch (Exception ignored) {}

                        String tag = obj.optString("tag", "GENERAL");
                        String title = obj.optString("title", "");
                        String details = obj.optString("details", "");

                        loaded.add(new LogEntry(ts, lvl, tag, title, details));
                    }

                    logsList.clear();
                    logsList.addAll(loaded);

                    mainHandler.post(() -> {
                        int total = getTotalCount();
                        int errors = getErrorCount();
                        for (LogEventListener l : listeners) {
                            if (!loaded.isEmpty()) {
                                l.onNewLogEntry(loaded.get(0), total, errors);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load logs from disk: " + e.getMessage());
            }
        });
    }

    private void setupCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                crash("JVM_CRASH", "Uncaught Exception on Thread: " + thread.getName(), throwable);
                // Give IO executor a brief moment to write the crash to disk
                Thread.sleep(150);
            } catch (Exception ignored) {}

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}