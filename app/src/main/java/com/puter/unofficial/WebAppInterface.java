package com.puter.unofficial;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.puter.unofficial.logging.LiveLogManager;
import com.puter.unofficial.logging.LogEntry;

import org.json.JSONObject;

import java.io.OutputStream;

/**
 * JavaScript interface bridge bound to headless_live_audio.html, browser.html, and bot.html.
 * Handles Gemini Live Bidi WebSockets, Puter SDK authentication synchronization,
 * live audio streams, transcripts, vision tool events, official Puter.js
 * client-side chat streaming, model listing, Nostr relay handshakes, and deep telemetry logging.
 */
public class WebAppInterface {

    private static final String TAG = "WebAppInterface";
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler;
    private HeadlessAudioBridge audioBridge;
    private MainActivity mainActivity;
    private final LiveLogManager logger;

    public WebAppInterface(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.logger = LiveLogManager.getInstance(context);
    }

    public void setAudioBridge(HeadlessAudioBridge bridge) {
        this.audioBridge = bridge;
    }

    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
    }

    // --- PORTAL DISMISSAL & NAVIGATION BRIDGE ---

    @JavascriptInterface
    public void closeBrowserPortal() {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.dismissPortalDialog());
        }
    }

    @JavascriptInterface
    public void finishActivity() {
        closeBrowserPortal();
    }

    @JavascriptInterface
    public void loadLocalUrl(String url) {
        closeBrowserPortal();
    }

    // --- OPERATIONAL MODE SYNCHRONIZATION (MANUAL / AUTO) ---

    @JavascriptInterface
    public void setAutoMode(boolean isAuto) {
        prefs.edit()
                .putBoolean("pref_auto_mode", isAuto)
                .putString("operational_mode", isAuto ? "AUTO" : "MANUAL")
                .apply();
        logger.info("OPERATIONAL_MODE", "Auto Mode Toggled", "isAuto: " + isAuto);
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.setAutoMode(isAuto));
        }
    }

    @JavascriptInterface
    public boolean getAutoMode() {
        return prefs.getBoolean("pref_auto_mode", "AUTO".equalsIgnoreCase(prefs.getString("operational_mode", "MANUAL")));
    }

    @JavascriptInterface
    public void setOperationalMode(String mode) {
        boolean isAuto = "AUTO".equalsIgnoreCase(mode);
        setAutoMode(isAuto);
    }

    @JavascriptInterface
    public String getOperationalMode() {
        return getAutoMode() ? "AUTO" : "MANUAL";
    }

    // --- NOSTR HANDSHAKE & SCRAPER BRIDGE ---

    @JavascriptInterface
    public String getNostrSettings() {
        JSONObject obj = new JSONObject();
        try {
            String defaultNpub = prefs.getString(AppConstants.KEY_NOSTR_PUBLIC_KEY, "");
            String pubKey = prefs.getString("nostr_extension_pubkey", defaultNpub);
            String relayUrl = prefs.getString("nostr_relay_url", "wss://relay.damus.io");

            obj.put("public_key", pubKey);
            obj.put("relay_url", relayUrl);
        } catch (Exception e) {
            logger.error("NOSTR_BRIDGE", "Settings JSON Error", e.getMessage());
        }
        return obj.toString();
    }

    @JavascriptInterface
    public void saveNostrSettings(String pubKey, String relayUrl) {
        prefs.edit()
                .putString("nostr_extension_pubkey", pubKey != null ? pubKey.trim() : "")
                .putString("nostr_relay_url", (relayUrl != null && !relayUrl.trim().isEmpty()) ? relayUrl.trim() : "wss://relay.damus.io")
                .apply();
        logger.ok("NOSTR_BRIDGE", "Settings Saved", "Relay: " + relayUrl);
    }

    @JavascriptInterface
    public void addScrapedProduct(String id, String json) {
        if (id == null || json == null) return;
        prefs.edit().putString("scraped_product_" + id, json).apply();
        logger.ok("SCRAPER", "Product Saved to Catalog", "ID: " + id);
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onScrapedProductReceived(id, json));
        }
    }

    @JavascriptInterface
    public void wakeUpKiwi(String query) {
        logger.info("KIWI_WAKEUP", "Wake-up Request from Bridge", "Query: " + query);
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.wakeUpKiwi(query));
        }
    }

    // --- DIAGNOSTICS & LOGGING BRIDGES ---

    @JavascriptInterface
    public void nativeLog(String message) {
        nativeLog(message, "BROWSER_PORTAL");
    }

    @JavascriptInterface
    public void nativeLog(String message, String tag) {
        logger.info(tag != null ? tag : "BROWSER_PORTAL", "Portal Log", message);
    }

    @JavascriptInterface
    public void logHtmlGlitch(String details) {
        logHtmlGlitch("HTML_GLITCH", details);
    }

    @JavascriptInterface
    public void logHtmlGlitch(String tag, String details) {
        logger.warn(tag != null ? tag : "HTML_GLITCH", "Portal Glitch", details);
    }

    @JavascriptInterface
    public void reportGlitchedLogic(String details) {
        reportGlitchedLogic("LOGIC_GLITCH", details);
    }

    @JavascriptInterface
    public void reportGlitchedLogic(String tag, String details) {
        logger.error(tag != null ? tag : "LOGIC_GLITCH", "Logic Glitch Report", details);
    }

    @JavascriptInterface
    public void logTechnicalEvent(String message) {
        logTechnicalEvent("INFO", "BROWSER_PORTAL", "Console Log", message);
    }

    @JavascriptInterface
    public void logTechnicalEvent(String levelStr, String tag, String title, String details) {
        LogEntry.Level level = LogEntry.Level.INFO;
        try {
            if (levelStr != null) {
                level = LogEntry.Level.valueOf(levelStr.toUpperCase());
            }
        } catch (Exception ignored) {}

        logger.log(level, tag != null ? tag : "JS_BRIDGE", title, details);
    }

    @JavascriptInterface
    public void logDiagnostic(String msg) {
        logDiagnostic(msg, "DIAGNOSTIC");
    }

    @JavascriptInterface
    public void logDiagnostic(String msg, String tag) {
        logger.info(tag != null ? tag : "DIAGNOSTIC", msg, "");
    }

    // --- CONFIGURATION RETRIEVAL METHODS ---

    @JavascriptInterface
    public String getGeminiApiKey() {
        return prefs.getString(AppConstants.KEY_GEMINI_API_KEY, "");
    }

    @JavascriptInterface
    public String getModelId() {
        if (mainActivity != null) {
            String m = mainActivity.getActiveModelId();
            if (m != null && !m.trim().isEmpty()) {
                return m;
            }
        }
        return "gemini-3.1-flash-live-preview";
    }

    @JavascriptInterface
    public String getGeminiVoice() {
        String voice = prefs.getString("gemini_voice_character", "");
        if (voice == null || voice.trim().isEmpty()) {
            voice = prefs.getString(AppConstants.KEY_GEMINI_VOICE_NAME, "Puck");
        }
        return voice;
    }

    @JavascriptInterface
    public String getVoiceName() {
        return getGeminiVoice();
    }

    @JavascriptInterface
    public String getCustomInstruction() {
        return prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, "");
    }

    // --- PUTER SDK AUTH SYNCHRONIZATION ---

    @JavascriptInterface
    public void onAuthStatusChanged(boolean isSignedIn) {
        logger.info("PUTER_AUTH", "Auth Status Changed", "isSignedIn: " + isSignedIn);
        AuthManager.getInstance(context).setLoggedIn(isSignedIn);
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.reloadWebView());
        }
    }

    @JavascriptInterface
    public void saveAuthToken(String token) {
        if (token != null && !token.isEmpty()) {
            AuthManager.getInstance(context).setAuthToken(token);
        }
    }

    @JavascriptInterface
    public String getSavedAuthToken() {
        return AuthManager.getInstance(context).getAuthToken();
    }

    @JavascriptInterface
    public boolean isLoggedIn() {
        return AuthManager.getInstance(context).isLoggedIn();
    }

    @JavascriptInterface
    public void signOut() {
        logger.info("PUTER_AUTH", "Sign Out Triggered", "Clearing native session.");
        AuthManager.getInstance(context).logout();
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.reloadWebView());
        }
    }

    // --- PUTER.JS CLIENT-SIDE SDK BRIDGE RECEIVERS ---

    @JavascriptInterface
    public void onPuterStreamChunk(String chunk) {
        if (audioBridge != null) {
            audioBridge.dispatchPuterChunk(chunk);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onPuterChunkReceived(chunk));
        }
    }

    @JavascriptInterface
    public void onPuterStreamComplete(String fullResponse) {
        if (audioBridge != null) {
            audioBridge.dispatchPuterComplete(fullResponse);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onPuterGenerationComplete(fullResponse));
        }
    }

    @JavascriptInterface
    public void onPuterImageResult(String imageUrl) {
        if (audioBridge != null) {
            audioBridge.dispatchPuterImage(imageUrl);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onPuterImageReceived(imageUrl));
        }
    }

    @JavascriptInterface
    public void onPuterLiveModelsResult(String modelsJson) {
        if (audioBridge != null) {
            audioBridge.dispatchPuterModels(modelsJson);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onPuterLiveModelsReceived(modelsJson));
        }
    }

    @JavascriptInterface
    public void onPuterError(String errorMessage) {
        if (audioBridge != null) {
            audioBridge.dispatchPuterError(errorMessage);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onPuterGenerationError(errorMessage));
        }
    }

    // --- LIVE WEBSOCKET & MULTI-TURN COMMENTARY CALLBACKS ---

    @JavascriptInterface
    public void onLiveSessionConnected() {
        logger.ok("GEMINI_LIVE", "Session Connected", "WebSocket Bidi link successfully established.");
        if (audioBridge != null) {
            audioBridge.dispatchConnected();
        } else if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveSessionConnected());
        }
    }

    @JavascriptInterface
    public void onLiveAudioChunkReceived(String base64Pcm) {
        if (audioBridge != null) {
            audioBridge.dispatchAudioChunk(base64Pcm);
        }
    }

    @JavascriptInterface
    public void onLiveCommentaryText(String text) {
        if (audioBridge != null) {
            audioBridge.dispatchTranscript(text);
        } else if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveTranscriptReceived(text));
        }
    }

    @JavascriptInterface
    public void onLiveUserTranscript(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("LIVE_TRANSCRIPT", "Dropped Empty User Speech", "Received empty or null text from JS bridge.");
            return;
        }
        String clean = text.trim();
        logger.ok("LIVE_TRANSCRIPT", "User Transcript from JS", clean);

        if (audioBridge != null) {
            audioBridge.dispatchUserTranscript(clean);
        } else if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveUserTranscript(clean));
        }
    }

    @JavascriptInterface
    public void onLiveModelTranscript(String text, boolean isTurnFinished) {
        if (text != null && !text.trim().isEmpty()) {
            logger.ok("LIVE_TRANSCRIPT", "Model Transcript from JS", text.trim());
        }

        if (audioBridge != null) {
            audioBridge.dispatchModelTranscript(text, isTurnFinished);
        } else if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveModelTranscript(text, isTurnFinished));
        }
    }

    @JavascriptInterface
    public void onLiveCommentaryFinished() {
        logger.info("GEMINI_LIVE", "Vocal Turn Completed", "Model finished speaking turn. Microphone active for reply.");
        if (audioBridge != null) {
            audioBridge.dispatchFinished();
        }
    }

    @JavascriptInterface
    public void onLiveSessionClosedWithTelemetry(int code, String reason, boolean wasClean) {
        String logDesc = "Close Code: " + code + " | Reason: '" + reason + "' | WasClean: " + wasClean;
        if (code == 1000) {
            logger.info("GEMINI_LIVE", "Remote WebSocket Closed (Clean)", logDesc);
        } else {
            logger.error("GEMINI_LIVE", "Remote WebSocket Closed (Error)", logDesc);
        }

        if (audioBridge != null) {
            audioBridge.dispatchSessionClosedWithTelemetry(code, reason, wasClean);
        } else if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveSessionClosedWithTelemetry(code, reason, wasClean));
        }
    }

    @JavascriptInterface
    public void onLiveError(String err) {
        logger.error("GEMINI_LIVE", "Live Session Error Event", err != null ? err : "Unknown error");
        if (audioBridge != null) {
            audioBridge.dispatchError(err);
        } else if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveError(err));
        }
    }

    @JavascriptInterface
    public void onTargetCoordinatesReceived(String targetId, float left, float top, float right, float bottom) {
        if (audioBridge != null) {
            audioBridge.dispatchTargetCoordinates(targetId, left, top, right, bottom);
        }
    }

    @JavascriptInterface
    public void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public void saveImageToGallery(String base64Data, String filename) {
        new Thread(() -> {
            try {
                String cleanBase64 = base64Data.replaceFirst("^data:image/[a-zA-Z]+;base64,", "");
                byte[] decoded = Base64.decode(cleanBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);

                String name = (filename != null && !filename.isEmpty()) ? filename : "Puter_" + System.currentTimeMillis() + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PuterAI");
                }

                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                    }
                    mainHandler.post(() -> Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show());
                    logger.ok("IMAGE_EXPORT", "Saved Successfully", "Path: " + uri.toString());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                logger.error("IMAGE_EXPORT", "Save Failed", e.getMessage());
            }
        }).start();
    }
}