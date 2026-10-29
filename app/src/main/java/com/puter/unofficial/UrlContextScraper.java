package com.puter.unofficial;

import android.util.Base64;

import com.puter.unofficial.logging.LiveLogManager;
import com.puter.unofficial.models.StagedFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConnectionPool;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Dedicated High-Speed Parallel 20-URL Batch Context Scraper & Sanitizer.
 * Extracts web documents, clamps payloads for token safety, and builds
 * unified context documents for LLM injection.
 */
public class UrlContextScraper {

    public interface ScraperProgressListener {
        void onProgressUpdate(String statusMessage);
    }

    private static final int URL_BATCH_SIZE = 20;
    private static final int MAX_CHARS_PER_URL = 40000;

    private final OkHttpClient fastScraperClient;

    public UrlContextScraper() {
        this.fastScraperClient = new OkHttpClient.Builder()
                .connectionPool(new okhttp3.ConnectionPool(30, 5, TimeUnit.MINUTES))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Extracts and sanitizes URLs: strips bullets, numbers, trailing punctuation, and encodes spaces.
     */
    public static List<String> extractAndSanitizeUrls(String text) {
        List<String> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;

        Pattern pattern = Pattern.compile("https?://[^\\s]+");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String rawUrl = matcher.group(0);
            if (rawUrl != null) {
                String clean = rawUrl.replaceAll("^[0-9]+[.)]\\s*", "")
                                     .replaceAll("^[-*•]\\s*", "");
                clean = clean.replaceAll("[.,;:)>\\]]+$", "");
                clean = clean.replace(" ", "%20");
                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                    list.add(clean);
                }
            }
        }
        return list;
    }

    /**
     * Inspects staged text attachments (.txt, .json, .csv, .md) and extracts embedded URLs.
     */
    public static List<String> extractUrlsFromAttachments(List<StagedFile> attachments) {
        List<String> list = new ArrayList<>();
        if (attachments == null || attachments.isEmpty()) return list;

        for (StagedFile file : attachments) {
            String name = (file.getFilename() != null) ? file.getFilename().toLowerCase(Locale.US) : "";
            String mime = (file.getMimeType() != null) ? file.getMimeType().toLowerCase(Locale.US) : "";

            boolean isTextDoc = name.endsWith(".txt") || name.endsWith(".json") ||
                                name.endsWith(".csv") || name.endsWith(".md") ||
                                mime.startsWith("text/") || mime.contains("json");

            if (isTextDoc && file.getBase64DataUri() != null) {
                try {
                    String cleanB64 = file.getBase64DataUri().replaceFirst("^data:[^;]+;base64,", "");
                    byte[] bytes = Base64.decode(cleanB64, Base64.DEFAULT);
                    String fileContent = new String(bytes, StandardCharsets.UTF_8);
                    list.addAll(extractAndSanitizeUrls(fileContent));
                } catch (Exception e) {
                    LiveLogManager.getInstance().warn("URL_CONTEXT", "Attachment URL Parse Error", e.getMessage());
                }
            }
        }
        return list;
    }

    /**
     * Executes parallel 20-URL batch downloading, progress reporting, and compilation.
     * Note: Must be invoked on a background thread.
     */
    public String executeHighSpeedUrlBatchProcessing(String basePrompt,
                                                    List<String> urls,
                                                    ScraperProgressListener progressListener) {
        long startTime = System.currentTimeMillis();
        int totalUrls = urls.size();
        int totalBatches = (totalUrls + URL_BATCH_SIZE - 1) / URL_BATCH_SIZE;

        LiveLogManager.getInstance().process("URL_CONTEXT", "Initiating Multi-Batch URL Processing",
                "Total URLs: " + totalUrls + " | Total Batches: " + totalBatches + " (Batch Size: " + URL_BATCH_SIZE + ")");

        Map<Integer, String> fetchedResultsMap = new HashMap<>();

        for (int batchIdx = 0; batchIdx < totalBatches; batchIdx++) {
            final int currentBatchNum = batchIdx + 1;
            int start = batchIdx * URL_BATCH_SIZE;
            int end = Math.min(start + URL_BATCH_SIZE, totalUrls);
            List<String> batchUrls = urls.subList(start, end);

            if (progressListener != null) {
                progressListener.onProgressUpdate("Analyzing Batch " + currentBatchNum + " of " + totalBatches +
                        " (URLs " + (start + 1) + "–" + end + ")...");
            }

            LiveLogManager.getInstance().process("URL_CONTEXT", "Batch " + currentBatchNum + " of " + totalBatches,
                    "Dispatching parallel downloads for URLs " + (start + 1) + " to " + end);

            ExecutorService batchPool = Executors.newFixedThreadPool(Math.min(batchUrls.size(), 20));
            CountDownLatch latch = new CountDownLatch(batchUrls.size());

            for (int i = 0; i < batchUrls.size(); i++) {
                final int globalIndex = start + i;
                final String targetUrl = batchUrls.get(i);

                batchPool.execute(() -> {
                    long fetchStart = System.currentTimeMillis();
                    try {
                        Request req = new Request.Builder()
                                .url(targetUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PuterUnofficial/1.4")
                                .header("Accept", "text/plain, text/html, application/json, */*")
                                .build();

                        try (Response resp = fastScraperClient.newCall(req).execute()) {
                            long latency = System.currentTimeMillis() - fetchStart;
                            if (resp.isSuccessful() && resp.body() != null) {
                                ResponseBody body = resp.body();

                                char[] buffer = new char[4096];
                                StringBuilder sb = new StringBuilder();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                                    int read;
                                    while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
                                        sb.append(buffer, 0, read);
                                        if (sb.length() >= MAX_CHARS_PER_URL) {
                                            sb.setLength(MAX_CHARS_PER_URL);
                                            sb.append("\n...[Content clamped at 40KB for token safety]...");
                                            break;
                                        }
                                    }
                                }

                                String content = sb.toString();
                                synchronized (fetchedResultsMap) {
                                    fetchedResultsMap.put(globalIndex, "--- START OF FILE CONTEXT [URL: " + targetUrl + "] ---\n" +
                                            content + "\n--- END OF FILE CONTEXT ---\n");
                                }

                                LiveLogManager.getInstance().ok("URL_CONTEXT", "URL [" + (globalIndex + 1) + "/" + totalUrls + "] HTTP " + resp.code(),
                                        "Latency: " + latency + "ms | Size: " + content.length() + " chars | " + targetUrl);
                            } else {
                                LiveLogManager.getInstance().error("URL_CONTEXT", "URL [" + (globalIndex + 1) + "/" + totalUrls + "] HTTP " + resp.code(),
                                        "Failed: " + resp.message() + " | Latency: " + latency + "ms | " + targetUrl);
                            }
                        }
                    } catch (Exception e) {
                        LiveLogManager.getInstance().error("URL_CONTEXT", "URL [" + (globalIndex + 1) + "/" + totalUrls + "] Fetch Exception",
                                e.getMessage() + " | " + targetUrl);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            batchPool.shutdown();

            LiveLogManager.getInstance().ok("URL_CONTEXT", "Batch " + currentBatchNum + " Completed",
                    "Processed " + batchUrls.size() + " URLs in parallel.");
        }

        if (progressListener != null) {
            progressListener.onProgressUpdate("Compiling Complete A-to-Z Repository Report...");
        }

        StringBuilder compiledContext = new StringBuilder();
        compiledContext.append("\n\n=== REPOSITORY & URL CONTEXT COMPILATION (Total Sources Verified: ")
                .append(fetchedResultsMap.size()).append("/").append(totalUrls).append(") ===\n");

        for (int i = 0; i < totalUrls; i++) {
            if (fetchedResultsMap.containsKey(i)) {
                compiledContext.append(fetchedResultsMap.get(i)).append("\n");
            }
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        LiveLogManager.getInstance().ok("URL_CONTEXT", "Compilation Finished in " + (totalElapsed / 1000.0) + "s",
                "Successfully compiled " + fetchedResultsMap.size() + " files (" + compiledContext.length() + " chars) across " + totalBatches + " batches.");

        return basePrompt + "\n" + compiledContext.toString();
    }
}