package com.puter.unofficial.logging;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data model for deep diagnostic telemetry in the System Log Console.
 * Stores complete, untruncated request/response payloads, auth tokens,
 * WebKit cookies, and JavaScript console events with millisecond precision.
 */
public class LogEntry implements Serializable {

    public enum Level {
        OK,
        INFO,
        WARN,
        ERROR,
        CRASH,
        PROCESS
    }

    private final String id;
    private final long timestamp;
    private final Level level;
    private final String tag;
    private final String title;
    private final String details;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat SHORT_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public LogEntry(Level level, String tag, String title, String details) {
        this.id = "log_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 10000);
        this.timestamp = System.currentTimeMillis();
        this.level = level != null ? level : Level.INFO;
        this.tag = tag != null ? tag.trim() : "GENERAL";
        this.title = title != null ? title.trim() : "";
        this.details = details != null ? details.trim() : "";
    }

    public LogEntry(long timestamp, Level level, String tag, String title, String details) {
        this.id = "log_" + timestamp + "_" + Math.round(Math.random() * 10000);
        this.timestamp = timestamp;
        this.level = level != null ? level : Level.INFO;
        this.tag = tag != null ? tag.trim() : "GENERAL";
        this.title = title != null ? title.trim() : "";
        this.details = details != null ? details.trim() : "";
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Level getLevel() {
        return level;
    }

    public String getTag() {
        return tag;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Returns the full, completely untruncated technical details (raw JSON, tokens, stack traces).
     */
    public String getDetails() {
        return details;
    }

    public String getFormattedTime() {
        return TIME_FORMAT.format(new Date(timestamp));
    }

    public String getShortFormattedTime() {
        return SHORT_TIME_FORMAT.format(new Date(timestamp));
    }

    public String getLevelBadgeText() {
        switch (level) {
            case OK: return "[OK]";
            case WARN: return "[WARN]";
            case ERROR: return "[ERROR]";
            case CRASH: return "[CRASH]";
            case PROCESS: return "[PROC]";
            case INFO:
            default:
                return "[INFO]";
        }
    }

    /**
     * Checks if this log entry matches a search query and an active category filter.
     */
    public boolean matches(String query, String filterCategory) {
        // 1. Check category chip filter
        if (filterCategory != null && !filterCategory.equalsIgnoreCase("ALL")) {
            if (filterCategory.equalsIgnoreCase("ERRORS")) {
                if (level != Level.ERROR && level != Level.CRASH) {
                    return false;
                }
            } else if (filterCategory.equalsIgnoreCase("CRASHES")) {
                if (level != Level.CRASH) {
                    return false;
                }
            } else if (filterCategory.equalsIgnoreCase("PROCESS")) {
                if (level != Level.PROCESS && !tag.equalsIgnoreCase("PROCESS")) {
                    return false;
                }
            } else {
                if (!tag.equalsIgnoreCase(filterCategory) && !level.name().equalsIgnoreCase(filterCategory)) {
                    return false;
                }
            }
        }

        // 2. Check search text query
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String q = query.trim().toLowerCase(Locale.US);
        return tag.toLowerCase(Locale.US).contains(q) ||
               title.toLowerCase(Locale.US).contains(q) ||
               details.toLowerCase(Locale.US).contains(q) ||
               level.name().toLowerCase(Locale.US).contains(q);
    }

    /**
     * Formats this single entry into a clean diagnostic string for clipboard copying.
     */
    public String toClipboardFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append(getLevelBadgeText())
          .append(" ")
          .append(tag)
          .append(" (")
          .append(getFormattedTime())
          .append(")\n")
          .append(title);

        if (!details.isEmpty()) {
            sb.append("\nDetails:\n").append(details);
        }
        sb.append("\n--------------------------------------------------\n");
        return sb.toString();
    }
}