package com.puter.unofficial;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.puter.unofficial.logging.LiveLogManager;

import org.json.JSONObject;

/**
 * Native controller managing the background 1x1 pixel WebView running headless_live_audio.html.
 * Bridges native Java calls to the browser AudioContext/WebSocket pipeline for Gemini Live,
 * and controls client-side Puter SDK chat completions and live model discovery.
 */
public class HeadlessAudioBridge {
    private static final String TAG = "HeadlessAudioBridge";
    private static final String HEADLESS_ASSET_URL = "https://appassets.androidplatform.net/assets/headless_live_audio.html";

    public interface LiveAudioEventListener {
        void onSessionConnected();
        void onTranscriptReceived(String text);
        default void onUserTranscriptReceived(String text) {}
        default void onModelTranscriptReceived(String text, boolean isFinished) {}
        void onAudioChunkReceived(String base64Pcm);
        void onCommentaryFinished();
        default void onSessionDisconnected() {}
        default void onSessionClosedWithTelemetry(int code, String reason, boolean clean) {}
        void onTargetCoordinatesReceived(String targetId, float left, float top, float right, float bottom);
        void onError(String errorMessage);
    }

    public interface PuterBridgeEventListener {
        void onPuterChunkReceived(String chunk);
        void onPuterStreamComplete(String fullResponse);
        void onPuterImageReceived(String imageUrl);
        void onPuterModelsLoaded(String rawJson);
        void onPuterError(String errorMessage);
    }

    private final Context context;
    private final WebView webView;
    private final Handler mainHandler;
    private LiveAudioEventListener listener;
    private PuterBridgeEventListener puterListener;
    private boolean isSessionActive = false;

    @SuppressLint("SetJavaScriptEnabled")
    public HeadlessAudioBridge(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());

        setupWebView();
    }

    public void setEventListener(LiveAudioEventListener listener) {
        this.listener = listener;
    }

    public void setPuterEventListener(PuterBridgeEventListener listener) {
        this.puterListener = listener;
    }

    public boolean isSessionActive() {
        return isSessionActive;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        String userAgent = settings.getUserAgentString().replace("; wv", "");
        settings.setUserAgentString(userAgent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Grant WebRTC microphone capture permission automatically to avoid silent getUserMedia drop
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                mainHandler.post(() -> {
                    if (request != null) {
                        request.grant(request.getResources());
                        LiveLogManager.getInstance().ok("GEMINI_LIVE_BRIDGE", "WebRTC Permission Granted", "Microphone access allowed for headless engine.");
                    }
                });
            }
        });

        webView.loadUrl(HEADLESS_ASSET_URL);
    }

    // --- PUTER TOKEN AND AUTH SYNCHRONIZATION ---

    /**
     * Triggers the headless JavaScript SDK to inspect its session and push tokens to Java AuthManager.
     */
    public void syncPuterAuth() {
        mainHandler.post(() -> {
            Log.d(TAG, "Triggering syncPuterAuth in headless worker");
            webView.evaluateJavascript("if (typeof window.checkAndBroadcastAuth === 'function') { window.checkAndBroadcastAuth(); }", null);
        });
    }

    /**
     * Injects a token from native storage into the headless worker's localStorage and SDK runtime.
     */
    public void injectPuterToken(String token) {
        if (token == null || token.trim().isEmpty()) return;
        mainHandler.post(() -> {
            String tokenEscaped = JSONObject.quote(token.trim());
            String js = String.format(
                "(function() {" +
                "  try {" +
                "    localStorage.setItem('puter.auth.token', %s);" +
                "    if (typeof puter !== 'undefined') {" +
                "      puter.authToken = %s;" +
                "      if (puter.auth) { puter.auth.token = %s; }" +
                "    }" +
                "  } catch(e) {}" +
                "})();", tokenEscaped, tokenEscaped, tokenEscaped);
            webView.evaluateJavascript(js, null);
        });
    }

    // --- GEMINI LIVE COMMENTARY CONTROLS ---

    public void startCommentarySession(String scriptText, String personaPrompt) {
        mainHandler.post(() -> {
            isSessionActive = true;
            String scriptEscaped = scriptText != null ? JSONObject.quote(scriptText) : "\"\"";
            String personaEscaped = personaPrompt != null ? JSONObject.quote(personaPrompt) : "\"\"";
            String js = String.format(
                    "try {" +
                    "  if (typeof window.startCommentarySession === 'function') {" +
                    "    window.startCommentarySession(%s, %s);" +
                    "    'LAUNCHED';" +
                    "  } else {" +
                    "    'MISSING_FUNCTION';" +
                    "  }" +
                    "} catch(e) {" +
                    "  'ERROR:' + e.message;" +
                    "}", scriptEscaped, personaEscaped);

            webView.evaluateJavascript(js, value -> {
                if (value == null || "\"MISSING_FUNCTION\"".equals(value)) {
                    LiveLogManager.getInstance().error("GEMINI_LIVE_BRIDGE", "Bridge JS Function Missing", "window.startCommentarySession not defined.");
                    dispatchError("Headless audio engine is not ready. Please try again.");
                } else if (value.contains("ERROR:")) {
                    LiveLogManager.getInstance().error("GEMINI_LIVE_BRIDGE", "JS Evaluation Exception", value);
                    dispatchError("Live session start error: " + value);
                } else {
                    LiveLogManager.getInstance().ok("GEMINI_LIVE_BRIDGE", "Live Session Command Dispatched", "Handshake initiated with model: " + personaPrompt);
                }
            });
        });
    }

    public void stopCommentarySession() {
        mainHandler.post(() -> {
            isSessionActive = false;
            webView.evaluateJavascript("if (typeof window.stopCommentarySession === 'function') { window.stopCommentarySession(); }", null);
            dispatchDisconnected();
        });
    }

    public void groundTargetWithVision(String base64Jpg, String targetId, String desc) {
        mainHandler.post(() -> {
            String b64 = JSONObject.quote(base64Jpg != null ? base64Jpg : "");
            String tId = JSONObject.quote(targetId != null ? targetId : "");
            String d = JSONObject.quote(desc != null ? desc : "");
            String js = String.format("window.groundTargetWithVision(%s, %s, %s);", b64, tId, d);
            webView.evaluateJavascript(js, null);
        });
    }

    // --- PUTER.JS CLIENT-SIDE SDK CONTROLS ---

    public void fetchPuterLiveModels() {
        mainHandler.post(() -> {
            Log.d(TAG, "Requesting Puter live models from headless worker");
            LiveLogManager.getInstance().process("PUTER_BRIDGE", "Fetching Live Models", "Invoking window.fetchPuterLiveModels()");
            webView.evaluateJavascript("if (typeof window.fetchPuterLiveModels === 'function') { window.fetchPuterLiveModels(); }", null);
        });
    }

    public void sendPuterChat(String messagesJson, String modelId, boolean isStream, String systemMessage, String aspectRatio) {
        mainHandler.post(() -> {
            String safeMessagesJson = messagesJson != null ? messagesJson : "[]";
            String quotedMessagesJson = JSONObject.quote(safeMessagesJson);
            String modelEscaped = JSONObject.quote(modelId != null ? modelId : "gpt-4o-mini");
            String sysEscaped = systemMessage != null ? JSONObject.quote(systemMessage) : "\"\"";
            String aspectEscaped = aspectRatio != null ? JSONObject.quote(aspectRatio) : "\"\"";

            LiveLogManager.getInstance().process("PUTER_BRIDGE", "Dispatched to JS Engine",
                    "Model: " + modelId + " | Payload chars: " + safeMessagesJson.length());

            String js = String.format(
                    "try {" +
                    "  if (typeof window.sendPuterChat === 'function') {" +
                    "    window.sendPuterChat(JSON.parse(%s), %s, %b, %s, %s);" +
                    "    'SCHEDULED';" +
                    "  } else {" +
                    "    'MISSING_FUNCTION';" +
                    "  }" +
                    "} catch(e) {" +
                    "  'ERROR:' + e.message;" +
                    "}",
                    quotedMessagesJson, modelEscaped, isStream, sysEscaped, aspectEscaped);

            webView.evaluateJavascript(js, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (value == null || value.equals("null") || "\"MISSING_FUNCTION\"".equals(value)) {
                        LiveLogManager.getInstance().error("PUTER_BRIDGE", "JS Function Missing", "window.sendPuterChat is not ready yet in headless WebView.");
                        dispatchPuterError("Headless chat worker is still loading. Please try again in a moment.");
                    } else if (value.contains("ERROR:")) {
                        LiveLogManager.getInstance().error("PUTER_BRIDGE", "JS Evaluation Crash", value);
                        dispatchPuterError("Chat dispatch error: " + value);
                    } else {
                        LiveLogManager.getInstance().ok("PUTER_BRIDGE", "JS Confirmed", "Chat execution confirmed by browser engine");
                    }
                }
            });
        });
    }

    public void stopPuterChat() {
        mainHandler.post(() -> {
            LiveLogManager.getInstance().info("PUTER_BRIDGE", "Stop Requested", "Invoking window.stopPuterChat()");
            webView.evaluateJavascript("if (typeof window.stopPuterChat === 'function') { window.stopPuterChat(); }", null);
        });
    }

    // --- PUTER DISPATCHERS (CALLED BY WebAppInterface) ---

    public void dispatchPuterChunk(String chunk) {
        mainHandler.post(() -> {
            if (puterListener != null) puterListener.onPuterChunkReceived(chunk);
        });
    }

    public void dispatchPuterComplete(String fullResponse) {
        mainHandler.post(() -> {
            LiveLogManager.getInstance().ok("PUTER_BRIDGE", "Stream Completed",
                    "Total response: " + (fullResponse != null ? fullResponse.length() : 0) + " chars");
            if (puterListener != null) puterListener.onPuterStreamComplete(fullResponse);
        });
    }

    public void dispatchPuterImage(String imageUrl) {
        mainHandler.post(() -> {
            LiveLogManager.getInstance().ok("PUTER_BRIDGE", "Image Received", "URL: " + imageUrl);
            if (puterListener != null) puterListener.onPuterImageReceived(imageUrl);
        });
    }

    public void dispatchPuterModels(String rawJson) {
        mainHandler.post(() -> {
            LiveLogManager.getInstance().ok("PUTER_BRIDGE", "Models Fetched", "JSON payload received");
            if (puterListener != null) puterListener.onPuterModelsLoaded(rawJson);
        });
    }

    public void dispatchPuterError(String errorMessage) {
        mainHandler.post(() -> {
            LiveLogManager.getInstance().error("PUTER_BRIDGE", "Error from Engine", errorMessage != null ? errorMessage : "Unknown error");
            if (puterListener != null) puterListener.onPuterError(errorMessage);
        });
    }

    // --- LIVE AUDIO DISPATCHERS (CALLED BY WebAppInterface) ---

    public void dispatchConnected() {
        mainHandler.post(() -> {
            isSessionActive = true;
            if (listener != null) listener.onSessionConnected();
        });
    }

    public void dispatchTranscript(String text) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTranscriptReceived(text);
        });
    }

    public void dispatchUserTranscript(String text) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onUserTranscriptReceived(text);
                LiveLogManager.getInstance().ok("LIVE_TRANSCRIPT", "Bridge Dispatched User Transcript", text);
            } else {
                LiveLogManager.getInstance().warn("LIVE_TRANSCRIPT", "Bridge Dispatch Warning", 
                        "LiveAudioEventListener is null when dispatching user transcript: " + text);
            }
        });
    }

    public void dispatchModelTranscript(String text, boolean isFinished) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onModelTranscriptReceived(text, isFinished);
                if (text != null && !text.isEmpty()) {
                    LiveLogManager.getInstance().ok("LIVE_TRANSCRIPT", "Bridge Dispatched Model Transcript", text);
                }
            } else {
                LiveLogManager.getInstance().warn("LIVE_TRANSCRIPT", "Bridge Dispatch Warning", 
                        "LiveAudioEventListener is null when dispatching model transcript.");
            }
        });
    }

    public void dispatchAudioChunk(String base64Pcm) {
        mainHandler.post(() -> {
            if (listener != null) listener.onAudioChunkReceived(base64Pcm);
        });
    }

    public void dispatchFinished() {
        mainHandler.post(() -> {
            if (listener != null) listener.onCommentaryFinished();
        });
    }

    public void dispatchDisconnected() {
        mainHandler.post(() -> {
            isSessionActive = false;
            if (listener != null) listener.onSessionDisconnected();
        });
    }

    public void dispatchSessionClosedWithTelemetry(int code, String reason, boolean wasClean) {
        mainHandler.post(() -> {
            isSessionActive = false;
            LiveLogManager.getInstance().info("GEMINI_LIVE_BRIDGE", "Session Closed", 
                    "Code: " + code + " | Reason: " + reason + " | Clean: " + wasClean);
            if (listener != null) {
                listener.onSessionClosedWithTelemetry(code, reason, wasClean);
            }
        });
    }

    public void dispatchTargetCoordinates(String targetId, float left, float top, float right, float bottom) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTargetCoordinatesReceived(targetId, left, top, right, bottom);
        });
    }

    public void dispatchError(String error) {
        mainHandler.post(() -> {
            isSessionActive = false;
            if (listener != null) listener.onError(error);
        });
    }
}