package com.puter.unofficial;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import com.puter.unofficial.models.ChatMessage;
import com.puter.unofficial.models.GenerationConfig;
import com.puter.unofficial.models.StagedFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pure Java OkHttp client for Puter AI chat completions and SSE streaming over HTTP.
 * Correctly intercepts HTTP errors and injects WebKit session cookies, valid Web Origins, 
 * and handles fallbacks across Puter Drivers, Puter OpenAI, and Puter v2 endpoints.
 */
public class PuterChatManager {
    private static final String TAG = "PuterChatManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String PUTER_DRIVERS_CALL_URL = "https://api.puter.com/drivers/call";
    private static final String PUTER_OPENAI_CHAT_URL = "https://api.puter.com/puterai/openai/v1/chat/completions";
    private static final String PUTER_V2_CHAT_URL = "https://api.puter.com/v2/ai/chat";

    private static final String PUTER_OPENAI_MODELS_URL = "https://api.puter.com/puterai/openai/v1/models";
    private static final String PUTER_V2_MODELS_URL = "https://api.puter.com/v2/ai/models";

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private static final String PUTER_ORIGIN = "https://puter.com";
    private static final String PUTER_REFERER = "https://puter.com/";

    private static PuterChatManager activeInstance;

    private final Context context;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private Call activeCall;
    private AutoModeDataListener autoModeDataListener;

    public interface PuterChatListener {
        void onChunk(String chunkText);
        void onImageReceived(String imageUrlOrBase64);
        void onComplete(String fullText);
        void onError(String errorMessage);
    }

    public interface PuterLiveModelsListener {
        void onModelsFetched(String rawJson);
        void onError(String errorMessage);
    }

    public interface AutoModeDataListener {
        void onAutoModeDataAvailable(String rawContent, String formattedContext);
    }

    public PuterChatManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        activeInstance = this;
    }

    public static PuterChatManager getActiveInstance() {
        return activeInstance;
    }

    public void setAutoModeDataListener(AutoModeDataListener listener) {
        this.autoModeDataListener = listener;
    }

    public void stopCurrentGeneration() {
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
            activeCall = null;
        }
    }

    // ==========================================
    // AUTO MODE CONTEXT BUILDER & INJECTION
    // ==========================================

    /**
     * Parses raw scraped JSON from Nostr/Kiwi into an AI-ready context block.
     */
    public String buildContextFromScrapedData(String rawScrapedJson) {
        if (rawScrapedJson == null || rawScrapedJson.trim().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- [LIVE WEB SCRAPED CONTEXT VIA AUTO-MODE] ---\n");
        try {
            JSONArray arr = new JSONArray(rawScrapedJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                JSONObject specs = item.optJSONObject("specifications");
                String title = specs != null ? specs.optString("title", "Article " + (i + 1)) : "Web Document";
                String url = specs != null ? specs.optString("url", "") : "";
                String content = specs != null ? specs.optString("raw_content", specs.optString("content", "")) : "";

                sb.append("[").append(i + 1).append("] ").append(title).append("\n");
                if (!url.isEmpty()) sb.append("Source: ").append(url).append("\n");
                if (!content.isEmpty()) {
                    if (content.length() > 4000) {
                        content = content.substring(0, 4000) + "... [truncated]";
                    }
                    sb.append("Extracted Text: ").append(content).append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            sb.append("Scraped Data Payload: ").append(rawScrapedJson).append("\n");
        }
        sb.append("--- [END OF WEB CONTEXT] ---\n");
        return sb.toString();
    }

    /**
     * Dispatches incoming auto-mode data from WebAppInterface to the active listener.
     */
    public void dispatchAutoModeData(String rawContent) {
        String formatted = buildContextFromScrapedData(rawContent);
        if (autoModeDataListener != null) {
            mainHandler.post(() -> autoModeDataListener.onAutoModeDataAvailable(rawContent, formatted));
        }
    }

    /**
     * Injects the scraped context into the prompt and streams the AI completion.
     */
    public void sendChatCompletionWithScrapedContext(String rawScrapedJson, List<ChatMessage> history,
                                                    String userPrompt, List<StagedFile> attachments,
                                                    String modelId, GenerationConfig config,
                                                    PuterChatListener listener) {
        String contextBlock = buildContextFromScrapedData(rawScrapedJson);
        String augmentedPrompt = userPrompt + "\n" + contextBlock +
                "\n\nInstruction: Use the live web context above to provide an accurate, up-to-date answer to the user query.";

        sendChatCompletion(history, augmentedPrompt, attachments, modelId, config, listener);
    }

    /**
     * Resolves the token from explicit input, AuthManager, or extracts it directly from CookieManager.
     */
    private String resolvePuterAuthToken(String explicitToken) {
        if (explicitToken != null && !explicitToken.trim().isEmpty()) {
            return explicitToken.trim();
        }

        try {
            AuthManager auth = AuthManager.getInstance(context);
            String token = auth.getAuthToken();
            if (token != null && !token.trim().isEmpty()) {
                return token.trim();
            }
        } catch (Exception ignored) {}

        // Fallback: Extract directly from CookieManager
        try {
            CookieManager cm = CookieManager.getInstance();
            String cookies = cm.getCookie("https://puter.com");
            if (cookies == null || cookies.isEmpty()) {
                cookies = cm.getCookie("https://api.puter.com");
            }
            if (cookies != null && !cookies.isEmpty()) {
                for (String c : cookies.split(";")) {
                    String[] kv = c.trim().split("=");
                    if (kv.length >= 2) {
                        String k = kv[0].trim();
                        String v = kv[1].trim();
                        if (k.equalsIgnoreCase("token") ||
                            k.equalsIgnoreCase("puter_auth_token") ||
                            k.equalsIgnoreCase("puter_token") ||
                            k.equalsIgnoreCase("auth_token") ||
                            k.equalsIgnoreCase("puter-auth")) {
                            if (!v.isEmpty()) {
                                try {
                                    AuthManager.getInstance(context).saveAuthToken(v);
                                } catch (Exception ignored) {}
                                return v;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return "";
    }

    public void fetchLiveModels(String authToken, PuterLiveModelsListener listener) {
        new Thread(() -> {
            try {
                final String resolvedToken = resolvePuterAuthToken(authToken);

                // Tier 1: Fetch via Puter's OpenAI-compatible models endpoint
                Request.Builder openAiReq = new Request.Builder()
                        .url(PUTER_OPENAI_MODELS_URL)
                        .get()
                        .addHeader("Accept", "application/json");
                applyAuthAndOriginHeaders(openAiReq, resolvedToken);

                Response openAiResp = client.newCall(openAiReq.build()).execute();
                if (openAiResp.isSuccessful() && openAiResp.body() != null) {
                    String bodyStr = openAiResp.body().string();
                    mainHandler.post(() -> listener.onModelsFetched(bodyStr));
                    return;
                }

                // Tier 2: Fallback to Puter v2 models endpoint
                Request.Builder v2Req = new Request.Builder()
                        .url(PUTER_V2_MODELS_URL)
                        .get()
                        .addHeader("Accept", "application/json");
                applyAuthAndOriginHeaders(v2Req, resolvedToken);

                Response v2Resp = client.newCall(v2Req.build()).execute();
                if (v2Resp.isSuccessful() && v2Resp.body() != null) {
                    String v2Body = v2Resp.body().string();
                    mainHandler.post(() -> listener.onModelsFetched(v2Body));
                    return;
                }

                // Tier 3: Fallback to Driver list-models call
                JSONObject rootPayload = new JSONObject();
                rootPayload.put("interface", "puter-chat-completion");
                rootPayload.put("driver", "ai");
                rootPayload.put("test_mode", false);
                rootPayload.put("method", "list-models");
                rootPayload.put("args", new JSONObject());

                Request.Builder driverReq = new Request.Builder()
                        .url(PUTER_DRIVERS_CALL_URL)
                        .post(RequestBody.create(rootPayload.toString(), JSON))
                        .addHeader("Accept", "application/json");
                applyAuthAndOriginHeaders(driverReq, resolvedToken);

                Response driverResp = client.newCall(driverReq.build()).execute();
                if (driverResp.isSuccessful() && driverResp.body() != null) {
                    String driverBody = driverResp.body().string();
                    mainHandler.post(() -> listener.onModelsFetched(driverBody));
                } else {
                    String err = (openAiResp.body() != null) ? openAiResp.body().string() : "HTTP " + openAiResp.code();
                    mainHandler.post(() -> listener.onError(err));
                }
            } catch (Exception e) {
                mainHandler.post(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    public void sendChatCompletion(List<ChatMessage> history, String userPrompt, List<StagedFile> attachments,
                                   String modelId, GenerationConfig config, PuterChatListener listener) {
        stopCurrentGeneration();

        new Thread(() -> {
            try {
                String authToken = resolvePuterAuthToken(null);
                boolean isImageGen = isImageModel(modelId);
                String normalizedModel = normalizeModelId(modelId);

                JSONArray messagesArray = new JSONArray();

                // Add conversation history
                if (history != null) {
                    for (ChatMessage msg : history) {
                        JSONObject m = new JSONObject();
                        m.put("role", msg.getRole().equalsIgnoreCase(ChatMessage.ROLE_USER) ? "user" : "assistant");
                        m.put("content", msg.getText());
                        messagesArray.put(m);
                    }
                }

                // Add current user message with staged files if any
                JSONObject currentMsg = new JSONObject();
                currentMsg.put("role", "user");

                if (attachments != null && !attachments.isEmpty()) {
                    JSONArray contentBlocks = new JSONArray();
                    JSONObject textBlock = new JSONObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", userPrompt);
                    contentBlocks.put(textBlock);

                    for (StagedFile file : attachments) {
                        JSONObject imgBlock = new JSONObject();
                        imgBlock.put("type", "image_url");
                        JSONObject urlObj = new JSONObject();
                        urlObj.put("url", file.getBase64DataUri());
                        imgBlock.put("image_url", urlObj);
                        contentBlocks.put(imgBlock);
                    }
                    currentMsg.put("content", contentBlocks);
                } else {
                    currentMsg.put("content", userPrompt);
                }
                messagesArray.put(currentMsg);

                // Build Puter Driver Call Payload
                JSONObject rootPayload = new JSONObject();
                rootPayload.put("interface", "puter-chat-completion");
                rootPayload.put("driver", isImageGen ? "txt2img" : "ai-chat");
                rootPayload.put("test_mode", false);
                rootPayload.put("method", "complete");

                JSONObject argsObj = new JSONObject();
                argsObj.put("messages", messagesArray);
                argsObj.put("model", normalizedModel);
                argsObj.put("stream", !isImageGen);

                if (config != null) {
                    if (config.getSystemInstructions() != null && !config.getSystemInstructions().isEmpty()) {
                        argsObj.put("system_message", config.getSystemInstructions());
                    }
                    if (config.getTemperature() > 0) {
                        argsObj.put("temperature", (double) config.getTemperature());
                    }
                    if (isImageGen && config.getAspectRatio() != null) {
                        argsObj.put("aspect_ratio", config.getAspectRatio());
                    }
                }
                rootPayload.put("args", argsObj);

                Request.Builder reqBuilder = new Request.Builder()
                        .url(PUTER_DRIVERS_CALL_URL)
                        .post(RequestBody.create(rootPayload.toString(), JSON))
                        .addHeader("Accept", !isImageGen ? "text/event-stream, application/json" : "application/json");

                applyAuthAndOriginHeaders(reqBuilder, authToken);

                Request request = reqBuilder.build();
                activeCall = client.newCall(request);

                activeCall.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, java.io.IOException e) {
                        if (call.isCanceled()) return;
                        executeOpenAIFallback(messagesArray, normalizedModel, config, authToken, isImageGen, listener);
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        if (!response.isSuccessful()) {
                            executeOpenAIFallback(messagesArray, normalizedModel, config, authToken, isImageGen, listener);
                            return;
                        }

                        handleStreamOrJsonResponse(call, response, isImageGen, listener);
                    }
                });

            } catch (Exception e) {
                postError(listener, "Chat preparation failed: " + e.getMessage());
            }
        }).start();
    }

    private void executeOpenAIFallback(JSONArray messages, String modelId, GenerationConfig config,
                                       String authToken, boolean isImageGen, PuterChatListener listener) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("messages", messages);
            payload.put("model", modelId);
            payload.put("stream", !isImageGen);

            if (config != null) {
                if (config.getSystemInstructions() != null && !config.getSystemInstructions().isEmpty()) {
                    JSONObject sysTurn = new JSONObject();
                    sysTurn.put("role", "system");
                    sysTurn.put("content", config.getSystemInstructions());
                    JSONArray newMessages = new JSONArray();
                    newMessages.put(sysTurn);
                    for (int i = 0; i < messages.length(); i++) {
                        newMessages.put(messages.get(i));
                    }
                    payload.put("messages", newMessages);
                }
                if (config.getTemperature() > 0) {
                    payload.put("temperature", (double) config.getTemperature());
                }
            }

            Request.Builder b = new Request.Builder()
                    .url(PUTER_OPENAI_CHAT_URL)
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("Accept", !isImageGen ? "text/event-stream, application/json" : "application/json");

            applyAuthAndOriginHeaders(b, authToken);

            activeCall = client.newCall(b.build());
            activeCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    if (call.isCanceled()) return;
                    executeV2Fallback(messages, modelId, config, authToken, isImageGen, listener);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        executeV2Fallback(messages, modelId, config, authToken, isImageGen, listener);
                        return;
                    }
                    handleStreamOrJsonResponse(call, response, isImageGen, listener);
                }
            });
        } catch (Exception ex) {
            executeV2Fallback(messages, modelId, config, authToken, isImageGen, listener);
        }
    }

    private void executeV2Fallback(JSONArray messages, String modelId, GenerationConfig config,
                                   String authToken, boolean isImageGen, PuterChatListener listener) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("messages", messages);
            payload.put("model", modelId);
            payload.put("stream", !isImageGen);
            if (config != null && config.getSystemInstructions() != null) {
                payload.put("system_message", config.getSystemInstructions());
            }

            Request.Builder b = new Request.Builder()
                    .url(PUTER_V2_CHAT_URL)
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("Accept", !isImageGen ? "text/event-stream, application/json" : "application/json");

            applyAuthAndOriginHeaders(b, authToken);

            activeCall = client.newCall(b.build());
            activeCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    if (!call.isCanceled()) postError(listener, e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        try {
                            String errBody = response.body() != null ? response.body().string() : "";
                            postError(listener, "Puter API Error: HTTP " + response.code() + " " + errBody);
                        } catch (Exception ignored) {
                            postError(listener, "Puter API Error: HTTP " + response.code());
                        }
                        return;
                    }
                    handleStreamOrJsonResponse(call, response, isImageGen, listener);
                }
            });
        } catch (Exception ex) {
            postError(listener, ex.getMessage());
        }
    }

    private void handleStreamOrJsonResponse(Call call, Response response, boolean isImageGen, PuterChatListener listener) {
        ResponseBody body = response.body();
        if (body == null) {
            postError(listener, "Empty server response");
            return;
        }

        if (isImageGen) {
            try {
                String respStr = body.string();
                JSONObject json = new JSONObject(respStr);
                String imgResult = "";
                if (json.has("result")) {
                    Object res = json.get("result");
                    if (res instanceof JSONObject) {
                        JSONObject rObj = (JSONObject) res;
                        imgResult = rObj.optString("image_url", rObj.optString("image", ""));
                    } else {
                        imgResult = res.toString();
                    }
                } else if (json.has("image_url")) {
                    imgResult = json.getString("image_url");
                } else if (json.has("image")) {
                    imgResult = json.getString("image");
                }

                if (!imgResult.isEmpty()) {
                    final String finalImg = imgResult;
                    mainHandler.post(() -> listener.onImageReceived(finalImg));
                } else {
                    postError(listener, "Could not extract image from response: " + respStr);
                }
            } catch (Exception e) {
                postError(listener, "Failed to parse image response: " + e.getMessage());
            }
        } else {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
                String line;
                StringBuilder fullAccumulator = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (data.equals("[DONE]")) break;

                        String deltaText = extractDeltaText(data);
                        if (deltaText != null && !deltaText.isEmpty()) {
                            fullAccumulator.append(deltaText);
                            mainHandler.post(() -> listener.onChunk(deltaText));
                        }
                    }
                }

                mainHandler.post(() -> listener.onComplete(fullAccumulator.toString()));
            } catch (Exception e) {
                if (!call.isCanceled()) {
                    postError(listener, "Stream reading interrupted: " + e.getMessage());
                }
            }
        }
    }

    private void applyAuthAndOriginHeaders(Request.Builder builder, String authToken) {
        builder.addHeader("Origin", PUTER_ORIGIN);
        builder.addHeader("Referer", PUTER_REFERER);
        builder.addHeader("User-Agent", DEFAULT_USER_AGENT);

        if (authToken != null && !authToken.trim().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + authToken.trim());
        }

        String cookies = CookieManager.getInstance().getCookie("https://puter.com");
        if (cookies != null && !cookies.isEmpty()) {
            builder.addHeader("Cookie", cookies);
        }
    }

    public static String normalizeModelId(String rawModelId) {
        if (rawModelId == null || rawModelId.trim().isEmpty()) {
            return "gpt-4o-mini";
        }
        String model = rawModelId.trim();
        if (model.contains(":")) {
            model = model.replaceFirst(":", "/");
        }
        return model;
    }

    private String extractDeltaText(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            if (obj.has("text")) return obj.getString("text");
            if (obj.has("delta")) {
                JSONObject delta = obj.getJSONObject("delta");
                if (delta.has("content")) return delta.getString("content");
                if (delta.has("text")) return delta.getString("text");
            }
            if (obj.has("choices")) {
                JSONArray choices = obj.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject c = choices.getJSONObject(0);
                    if (c.has("delta") && c.getJSONObject("delta").has("content")) {
                        return c.getJSONObject("delta").getString("content");
                    }
                    if (c.has("text")) return c.getString("text");
                }
            }
            if (obj.has("message") && obj.getJSONObject("message").has("content")) {
                return obj.getJSONObject("message").getString("content");
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isImageModel(String modelId) {
        if (modelId == null) return false;
        String m = modelId.toLowerCase(Locale.US);
        return m.contains("dall-e") || m.contains("flux") || m.contains("imagen") || 
               m.contains("midjourney") || m.contains("txt2img") || m.contains("banana") || 
               m.contains("image");
    }

    private void postError(PuterChatListener listener, String err) {
        mainHandler.post(() -> listener.onError(err));
    }
}