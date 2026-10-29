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

import java.io.OutputStream;

/**
 * JavaScript interface bridge bound to headless_live_audio.html.
 * Handles Gemini Live Bidi WebSockets, Puter SDK authentication synchronization,
 * live audio streams, transcripts, vision tool events, official Puter.js
 * client-side chat streaming, model listing, and deep telemetry logging.
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

    // --- SYSTEM LOG CONSOLE TELEMETRY BRIDGE ---

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

    /**
     * Bridges transcribed user speech to display on the right as a blue chat bubble.
     * Single-dispatch via audioBridge ensures exactly one invocation.
     */
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

    /**
     * Bridges clean model spoken words to display on the left as an assistant chat bubble.
     * Single-dispatch via audioBridge ensures exactly one invocation.
     */
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
    public void logDiagnostic(String msg, String tag) {
        logger.info(tag != null ? tag : "DIAGNOSTIC", msg, "");
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