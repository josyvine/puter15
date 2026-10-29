package com.puter.unofficial;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.puter.unofficial.logging.LiveLogManager;
import com.puter.unofficial.models.GroundingSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Native Network Service for Google Gemini APIs.
 * Pure Java implementation with direct Java callback listeners.
 * Handles background REST, SSE streaming execution, active call cancellation,
 * multi-turn conversational history sanitization, official url_context tool declaration,
 * urlContextMetadata extraction, and live model discovery.
 */
public class GeminiService {

    private static final String TAG = "GeminiService";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[a-zA-Z0-9./_\\-~%#?=&]+");

    public interface GeminiStreamListener {
        void onChunk(String chunkText);
        void onComplete(String fullResponse);
        void onCitationsReceived(List<GroundingSource> citations);
        void onError(String errorMessage);
    }

    public interface GeminiModelsListener {
        void onModelsFetched(String json);
        void onError(String errorMessage);
    }

    private final Context context;
    private final OkHttpClient client;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    private Call activeCall = null;
    private final Object callLock = new Object();

    public GeminiService(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();

        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public void cancelCurrentCall() {
        synchronized (callLock) {
            if (activeCall != null && !activeCall.isCanceled()) {
                Log.d(TAG, "Aborting active Gemini network call.");
                LiveLogManager.getInstance().info("GEMINI_NETWORK", "Call Canceled", "User aborted active network request.");
                activeCall.cancel();
                activeCall = null;
            }
        }
    }

    public void executeCall(final String modelId, final String payloadJson, final boolean isStream, final GeminiStreamListener listener) {
        executorService.execute(() -> {
            Call call = null;
            long requestStartTime = System.currentTimeMillis();
            try {
                if (isNonGeminiModel(modelId)) {
                    String err = "Invalid model for Google Gemini API: " + modelId + 
                            ". This model belongs to Puter/OpenRouter. Please switch active provider to Puter.";
                    LiveLogManager.getInstance().error("GEMINI_NETWORK", "Model Routing Violation", err);
                    deliverError(listener, err);
                    return;
                }

                String sanitizedModel = sanitizeModelId(modelId);

                SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE);
                String apiKey = prefs.getString(AppConstants.KEY_GEMINI_API_KEY, "");

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = "AIzaSyBbwPg-mPKe4FNtfI8j07REFFjV6C-xVMw";
                }

                String cleanPayload = processUrlContextAndCleanPayload(payloadJson);

                String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + sanitizedModel;
                String action = isStream ? ":streamGenerateContent?alt=sse&key=" : ":generateContent?key=";
                String targetUrl = baseUrl + action + apiKey;

                LiveLogManager.getInstance().process("GEMINI_NETWORK", "Dispatching Gemini Call",
                        "Model: " + sanitizedModel + " | Stream: " + isStream + " | Payload: " + cleanPayload.length() + " chars");

                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(cleanPayload, mediaType);

                Request request = new Request.Builder()
                        .url(targetUrl)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(body)
                        .build();

                call = client.newCall(request);
                synchronized (callLock) {
                    this.activeCall = call;
                }

                Response response = call.execute();
                long latency = System.currentTimeMillis() - requestStartTime;

                if (!response.isSuccessful()) {
                    String errorText = response.body() != null ? response.body().string() : "Empty response body";
                    String diag = "HTTP " + response.code() + " (" + latency + "ms): " + errorText;
                    LiveLogManager.getInstance().error("GEMINI_NETWORK", "Server Error Response", diag);
                    deliverError(listener, "HTTP " + response.code() + ": " + errorText);
                    return;
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    LiveLogManager.getInstance().error("GEMINI_NETWORK", "Empty Body", "HTTP " + response.code() + " returned null body.");
                    deliverError(listener, "Response body was empty.");
                    return;
                }

                LiveLogManager.getInstance().ok("GEMINI_NETWORK", "HTTP " + response.code() + " OK",
                        "Handshake Latency: " + latency + "ms | Processing response stream...");

                if (isStream) {
                    parseStreamResponse(responseBody, listener, requestStartTime);
                } else {
                    String responseData = responseBody.string();
                    parseSingleResponse(responseData, listener, requestStartTime);
                }

            } catch (IOException e) {
                if (call != null && call.isCanceled()) {
                    deliverError(listener, "Request canceled.");
                    return;
                }
                long duration = System.currentTimeMillis() - requestStartTime;
                LiveLogManager.getInstance().error("GEMINI_NETWORK", "Connection Failed (" + duration + "ms)", e.getMessage());
                deliverError(listener, "Connection failed: " + e.getMessage());
            } catch (Exception ex) {
                LiveLogManager.getInstance().error("GEMINI_NETWORK", "Processing Exception", ex.getMessage());
                deliverError(listener, "Processing error: " + ex.getMessage());
            } finally {
                synchronized (callLock) {
                    if (this.activeCall == call) {
                        this.activeCall = null;
                    }
                }
            }
        });
    }

    private boolean isNonGeminiModel(String modelId) {
        if (modelId == null) return true;
        String m = modelId.toLowerCase(Locale.US);
        return m.contains("openrouter") || m.contains("togetherai") || m.contains("puter") || 
               m.contains("claude") || m.contains("deepseek") || m.contains("gpt-") || 
               m.contains("mistral") || m.contains("qwen") || m.contains(":") || m.contains("/");
    }

    private String sanitizeModelId(String rawModelId) {
        if (rawModelId == null || rawModelId.trim().isEmpty()) {
            return "gemini-1.5-flash";
        }
        String cleaned = rawModelId.trim();
        if (cleaned.startsWith("models/")) {
            cleaned = cleaned.substring(7);
        }
        return cleaned;
    }

    private void parseSingleResponse(String jsonStr, GeminiStreamListener listener, long requestStartTime) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            String text = extractTextFromGeminiJson(root);
            List<GroundingSource> citations = extractCitations(root);
            long totalDuration = System.currentTimeMillis() - requestStartTime;

            LiveLogManager.getInstance().ok("GEMINI_NETWORK", "Single-Turn Complete (" + totalDuration + "ms)",
                    "Response: " + text.length() + " chars | Citations: " + (citations != null ? citations.size() : 0));

            mainHandler.post(() -> {
                if (citations != null && !citations.isEmpty()) {
                    listener.onCitationsReceived(citations);
                }
                listener.onComplete(text);
            });
        } catch (Exception e) {
            LiveLogManager.getInstance().error("GEMINI_NETWORK", "JSON Parse Error", e.getMessage());
            deliverError(listener, "Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private void parseStreamResponse(ResponseBody responseBody, GeminiStreamListener listener, long requestStartTime) {
        InputStream inputStream = responseBody.byteStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        StringBuilder fullAccumulator = new StringBuilder();
        List<GroundingSource> allCitations = new ArrayList<>();
        boolean firstChunkReceived = false;

        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                    try {
                        JSONObject chunkObj = new JSONObject(data);
                        String text = extractTextFromGeminiJson(chunkObj);
                        List<GroundingSource> citations = extractCitations(chunkObj);
                        
                        if (citations != null && !citations.isEmpty()) {
                            for (GroundingSource src : citations) {
                                boolean exists = false;
                                for (GroundingSource existing : allCitations) {
                                    if (existing.getUrl().equalsIgnoreCase(src.getUrl())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) allCitations.add(src);
                            }
                        }

                        if (text != null && !text.isEmpty()) {
                            if (!firstChunkReceived) {
                                firstChunkReceived = true;
                                long ttft = System.currentTimeMillis() - requestStartTime;
                                LiveLogManager.getInstance().ok("GEMINI_NETWORK", "First SSE Chunk (TTFT)", "Latency: " + ttft + "ms");
                            }
                            fullAccumulator.append(text);
                            mainHandler.post(() -> listener.onChunk(text));
                        }
                    } catch (Exception ignored) {}
                }
            }

            long totalDuration = System.currentTimeMillis() - requestStartTime;
            LiveLogManager.getInstance().ok("GEMINI_NETWORK", "Streaming Complete (" + totalDuration + "ms)",
                    "Total response: " + fullAccumulator.length() + " chars | Unique Citations: " + allCitations.size());

            mainHandler.post(() -> {
                if (!allCitations.isEmpty()) {
                    listener.onCitationsReceived(allCitations);
                }
                listener.onComplete(fullAccumulator.toString());
            });

        } catch (IOException e) {
            synchronized (callLock) {
                if (activeCall != null && activeCall.isCanceled()) {
                    return;
                }
            }
            LiveLogManager.getInstance().error("GEMINI_NETWORK", "Streaming Stream Dropped", e.getMessage());
            deliverError(listener, "Streaming interrupted: " + e.getMessage());
        } finally {
            try { reader.close(); } catch (IOException ignored) {}
            try { inputStream.close(); } catch (IOException ignored) {}
        }
    }

    private String extractTextFromGeminiJson(JSONObject root) {
        try {
            if (root.has("candidates")) {
                JSONArray candidates = root.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject cand = candidates.getJSONObject(0);
                    if (cand.has("content") && cand.getJSONObject("content").has("parts")) {
                        JSONArray parts = cand.getJSONObject("content").getJSONArray("parts");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject p = parts.getJSONObject(i);
                            if (p.has("text")) sb.append(p.getString("text"));
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Extracts grounding citations from both Google Search and official Gemini urlContextMetadata.
     */
    private List<GroundingSource> extractCitations(JSONObject root) {
        List<GroundingSource> list = new ArrayList<>();
        try {
            if (!root.has("candidates")) return list;
            JSONArray candidates = root.getJSONArray("candidates");
            if (candidates.length() == 0) return list;

            JSONObject cand = candidates.getJSONObject(0);

            // 1. Official Google Gemini URL Context Metadata (urlContextMetadata)
            if (cand.has("urlContextMetadata")) {
                JSONObject ucm = cand.getJSONObject("urlContextMetadata");
                if (ucm.has("urlMetadata")) {
                    JSONArray urlArr = ucm.getJSONArray("urlMetadata");
                    for (int i = 0; i < urlArr.length(); i++) {
                        JSONObject uObj = urlArr.getJSONObject(i);
                        String rawUrl = uObj.optString("retrievedUrl", uObj.optString("url", ""));
                        if (!rawUrl.isEmpty()) {
                            String domain = extractHostDomain(rawUrl);
                            list.add(new GroundingSource(rawUrl, domain, "", ""));
                            LiveLogManager.getInstance().ok("URL_CONTEXT", "Citation Extracted (urlContextMetadata)", rawUrl);
                        }
                    }
                }
            }

            // 2. Google Search Grounding Metadata (groundingMetadata)
            if (cand.has("groundingMetadata")) {
                JSONObject gm = cand.getJSONObject("groundingMetadata");
                if (gm.has("groundingChunks")) {
                    JSONArray chunks = gm.getJSONArray("groundingChunks");
                    for (int i = 0; i < chunks.length(); i++) {
                        JSONObject c = chunks.getJSONObject(i);
                        if (c.has("web")) {
                            JSONObject w = c.getJSONObject("web");
                            String uri = w.optString("uri", "");
                            String title = w.optString("title", extractHostDomain(uri));
                            if (!uri.isEmpty()) {
                                list.add(new GroundingSource(uri, title, "", ""));
                                LiveLogManager.getInstance().ok("URL_CONTEXT", "Citation Extracted (Search Grounding)", uri);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LiveLogManager.getInstance().warn("URL_CONTEXT", "Citation Parse Notice", e.getMessage());
        }
        return list;
    }

    private String extractHostDomain(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            return (host != null && !host.isEmpty()) ? host : rawUrl;
        } catch (Exception e) {
            return rawUrl;
        }
    }

    /**
     * Sanitizes multi-turn payload sequences, isolates URL checks to the active prompt,
     * strips disallowed flags, and prevents Google API schema rejections.
     */
    private String processUrlContextAndCleanPayload(String rawPayloadJson) {
        try {
            JSONObject root = new JSONObject(rawPayloadJson);

            // Strip internal non-schema boolean flags to avoid Google API HTTP 400 Bad Request
            if (root.has("urlContext")) {
                root.remove("urlContext");
            }

            // Clean empty systemInstruction if present
            if (root.has("systemInstruction")) {
                JSONObject sysObj = root.optJSONObject("systemInstruction");
                if (sysObj == null || !sysObj.has("parts") || sysObj.getJSONArray("parts").length() == 0) {
                    root.remove("systemInstruction");
                }
            }

            // Validate and sanitize multi-turn contents
            if (!root.has("contents")) {
                return root.toString();
            }

            JSONArray rawContents = root.getJSONArray("contents");
            JSONArray sanitizedContents = new JSONArray();

            for (int i = 0; i < rawContents.length(); i++) {
                JSONObject turn = rawContents.getJSONObject(i);
                String role = turn.optString("role", "user").toLowerCase(Locale.US);
                if (!turn.has("parts")) continue;

                JSONArray rawParts = turn.getJSONArray("parts");
                JSONArray validParts = new JSONArray();

                for (int p = 0; p < rawParts.length(); p++) {
                    JSONObject part = rawParts.getJSONObject(p);
                    if (part.has("text")) {
                        String t = part.getString("text");
                        if (!t.trim().isEmpty()) {
                            validParts.put(part);
                        }
                    } else if (part.has("inlineData")) {
                        validParts.put(part);
                    }
                }

                if (validParts.length() == 0) continue;

                // Ensure the first turn in contents is always "user"
                if (sanitizedContents.length() == 0 && !"user".equals(role)) {
                    continue;
                }

                // If consecutive turns have the same role, merge parts to prevent Gemini HTTP 400
                if (sanitizedContents.length() > 0) {
                    JSONObject prevTurn = sanitizedContents.getJSONObject(sanitizedContents.length() - 1);
                    if (role.equals(prevTurn.optString("role"))) {
                        JSONArray prevParts = prevTurn.getJSONArray("parts");
                        for (int p = 0; p < validParts.length(); p++) {
                            prevParts.put(validParts.get(p));
                        }
                        continue;
                    }
                }

                JSONObject cleanTurn = new JSONObject();
                cleanTurn.put("role", role);
                cleanTurn.put("parts", validParts);
                sanitizedContents.put(cleanTurn);
            }

            root.put("contents", sanitizedContents);

            // Check if official Gemini url_context tool is declared in tools array
            boolean hasOfficialUrlContextTool = false;
            if (root.has("tools")) {
                JSONArray tools = root.getJSONArray("tools");
                for (int i = 0; i < tools.length(); i++) {
                    JSONObject tool = tools.getJSONObject(i);
                    if (tool.has("url_context") || tool.has("urlContext")) {
                        hasOfficialUrlContextTool = true;
                        break;
                    }
                }
            }

            // If prompt is already pre-compiled by MainActivity or official tool is active, pass cleanly
            if (hasOfficialUrlContextTool || rawPayloadJson.contains("=== REPOSITORY & URL CONTEXT COMPILATION")) {
                LiveLogManager.getInstance().ok("URL_CONTEXT", "Schema Validated", "Multi-turn payload validated.");
                return root.toString();
            }

            // Fallback scraping: Only scan the LATEST user message in the session to avoid re-scraping past turns
            if (sanitizedContents.length() > 0) {
                JSONObject lastTurn = sanitizedContents.getJSONObject(sanitizedContents.length() - 1);
                if ("user".equals(lastTurn.optString("role")) && lastTurn.has("parts")) {
                    JSONArray parts = lastTurn.getJSONArray("parts");
                    List<String> detectedUrls = new ArrayList<>();

                    for (int j = 0; j < parts.length(); j++) {
                        JSONObject part = parts.getJSONObject(j);
                        if (part.has("text")) {
                            String text = part.getString("text");
                            Matcher matcher = URL_PATTERN.matcher(text);
                            while (matcher.find()) {
                                String url = matcher.group();
                                if (!detectedUrls.contains(url)) {
                                    detectedUrls.add(url);
                                }
                            }
                        }
                    }

                    if (!detectedUrls.isEmpty()) {
                        StringBuilder fetchedContextBuilder = new StringBuilder();
                        int fetchedCount = 0;

                        for (String url : detectedUrls) {
                            String fetchedData = fetchUrlContentDirectly(url);
                            if (fetchedData != null && !fetchedData.trim().isEmpty()) {
                                fetchedContextBuilder.append("\n\n--- START OF FILE CONTEXT [URL: ").append(url).append("] ---\n");
                                fetchedContextBuilder.append(fetchedData);
                                fetchedContextBuilder.append("\n--- END OF FILE CONTEXT ---\n");
                                fetchedCount++;
                            }
                        }

                        if (fetchedCount > 0) {
                            JSONObject contextPart = new JSONObject();
                            contextPart.put("text", "\n[System URL Context Retrieval: The following " + fetchedCount +
                                    " source file(s) were successfully fetched and verified]\n" +
                                    fetchedContextBuilder.toString());
                            parts.put(contextPart);
                            LiveLogManager.getInstance().ok("URL_CONTEXT", "Fallback Scraping Completed", "Injected " + fetchedCount + " source files.");
                        }
                    }
                }
            }

            return root.toString();
        } catch (Exception e) {
            LiveLogManager.getInstance().error("URL_CONTEXT", "Payload Cleaning Exception", e.getMessage());
            return rawPayloadJson;
        }
    }

    private String fetchUrlContentDirectly(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PuterUnofficial/1.4")
                    .get()
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String bodyStr = resp.body().string();
                    if (bodyStr.length() > 40000) {
                        return bodyStr.substring(0, 40000) + "\n...[Content clamped for token safety]...";
                    }
                    return bodyStr;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void fetchAvailableModels(final String apiKey, final GeminiModelsListener listener) {
        executorService.execute(() -> {
            try {
                String keyToUse = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : "";
                if (keyToUse.isEmpty()) {
                    SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE);
                    keyToUse = prefs.getString(AppConstants.KEY_GEMINI_API_KEY, "");
                }
                if (keyToUse.isEmpty()) {
                    keyToUse = "AIzaSyBbwPg-mPKe4FNtfI8j07REFFjV6C-xVMw";
                }

                String targetUrl = AppConstants.GEMINI_MODELS_LIST_URL + "?key=" + keyToUse;
                Request request = new Request.Builder()
                        .url(targetUrl)
                        .addHeader("x-goog-api-key", keyToUse)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "Empty error";
                    LiveLogManager.getInstance().error("GEMINI_MODELS", "Model Fetch Failed", "HTTP " + response.code() + ": " + err);
                    mainHandler.post(() -> listener.onError("HTTP " + response.code() + ": " + err));
                    return;
                }

                ResponseBody body = response.body();
                String json = body != null ? body.string() : "{}";
                LiveLogManager.getInstance().ok("GEMINI_MODELS", "Models Fetched Successfully", "Length: " + json.length() + " chars");
                mainHandler.post(() -> listener.onModelsFetched(json));

            } catch (Exception e) {
                LiveLogManager.getInstance().error("GEMINI_MODELS", "Fetch Exception", e.getMessage());
                mainHandler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }

    private void deliverError(GeminiStreamListener listener, String err) {
        mainHandler.post(() -> listener.onError(err));
    }

    public void shutdown() {
        cancelCurrentCall();
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}