package com.puter.unofficial;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.adapters.ChatAdapter;
import com.puter.unofficial.adapters.ModelAccordionAdapter;
import com.puter.unofficial.adapters.StagedAttachmentsAdapter;
import com.puter.unofficial.logging.LiveLogManager;
import com.puter.unofficial.logging.LogEntry;
import com.puter.unofficial.models.AIModelDefinition;
import com.puter.unofficial.models.ChatMessage;
import com.puter.unofficial.models.ChatSession;
import com.puter.unofficial.models.GroundingSource;
import com.puter.unofficial.models.StagedFile;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Native Android Activity for Puter Unofficial.
 * Coordinates UI timelines, live audio bridges, and dispatches to
 * AppDialogManager, UrlContextScraper, and ModelCatalogManager.
 */
public class MainActivity extends AppCompatActivity implements LiveLogManager.LogEventListener {

    private static final String TAG = "MainActivity";

    // Native Views from activity_chat_native.xml
    private CoordinatorLayout chatRootLayout;
    private TextView tvAppHeaderTitle;
    private ImageView btnHeaderMenu;
    private LinearLayout settingsBar;
    private LinearLayout modelSelectTrigger;
    private TextView selectedModelText;
    private ImageView ivTriggerChevron;
    private LinearLayout aspectRatioBar;
    private Spinner spnAspectRatio;
    private RecyclerView rvChatTimeline;
    private LinearLayout ecgLoaderContainer;
    private ImageView ivEcgIcon;
    private TextView tvEcgStatus;
    private ImageButton btnStopGeneration;
    private LinearLayout chatControlsContainer;
    private RecyclerView rvStagedAttachments;
    private ImageView btnAttach;
    private EditText etMessageInput;
    private ImageView btnVoiceInput;
    private FrameLayout btnVoiceAgent;
    private ImageView voiceAgentIcon;
    private TextView orbitStar1, orbitStar2, orbitStar3;
    private ImageButton btnSendMessage;

    // Floating Accordion and Dropdown Cards
    private CardView accordionMenuCard;
    private RecyclerView rvAccordionModels;
    private ModelAccordionAdapter modelAccordionAdapter;

    private CardView puterDropdownMenuCard;
    private TextView menuAuthOption;
    private TextView menuModelSourceToggle;
    private TextView menuViewHistory;
    private TextView menuNewSession;
    private TextView menuToggleSettings;
    private TextView menuHelp;
    private TextView menuSettingsHub;
    private TextView menuBrowseWebsite;
    private TextView menuTradersZone;
    private EditText etMenuReplyLanguage;
    private CheckBox cbMenuUrlContext;
    private TextView tvMenuLastTokens;
    private TextView tvMenuLastCost;
    private TextView tvMenuGlobalTokens;
    private TextView tvMenuGlobalCost;
    private LinearLayout llDynamicSessionsContainer;
    private LinearLayout llDynamicScrapedContainer;

    // Context Menu Card
    private CardView customContextMenuCard;
    private TextView ctxCancelGen;
    private TextView ctxCopyChat;
    private TextView ctxReadAloud;
    private TextView ctxDeleteChat;
    private TextView ctxRefreshRelay;
    private TextView ctxToggleMode;
    private TextView ctxToggleProvider;
    private TextView ctxEndSession;
    private ChatMessage contextTargetMessage;

    // Floating Diagnostic Log Console Badge
    private View floatingLogView;
    private TextView tvFloatingLogBadge;
    private float dX, dY;
    private float initialTouchX, initialTouchY;

    // Headless WebView & Live Voice Overlay HUD
    private WebView headlessAudioWebView;
    private TextView tvLiveTranscriptTicker;
    private TextView tvLiveVoiceStatus;

    // Sub-System Managers (Modular Architecture)
    private AppDialogManager dialogManager;
    private UrlContextScraper urlContextScraper;
    private ModelCatalogManager modelCatalogManager;

    // Adapters & Core Services
    private ChatAdapter chatAdapter;
    private StagedAttachmentsAdapter stagedAttachmentsAdapter;
    private ChatSessionManager sessionManager;
    private GeminiService geminiService;
    private HeadlessAudioBridge headlessAudioBridge;
    private WebAppInterface webAppInterface;
    private SharedPreferences prefs;

    // State Variables
    private ChatSession currentSession;
    private ChatMessage currentActiveAssistantMsg;
    private ChatMessage currentLiveAssistantMsg;
    private String activeModelId = AppConstants.DEFAULT_MODEL;
    private String activeProvider = "Puter";
    private String previousProviderBeforeLive = null;
    private boolean isLiveMode = false;
    private boolean isAutoMode = false;
    private boolean isGenerating = false;
    private boolean isLiveVoiceConnected = false;
    private String lastInjectedPuterToken = null;

    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;

    // Live Turn Deduplication Guards
    private String lastRenderedUserText = "";
    private long lastUserTextTimestamp = 0;

    // Activity Result Launchers
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> documentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_native);

        prefs = getSharedPreferences(AppConstants.PREF_NAME, MODE_PRIVATE);
        sessionManager = ChatSessionManager.getInstance(this);
        geminiService = new GeminiService(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        activeProvider = prefs.getString(AppConstants.KEY_ACTIVE_PROVIDER, "Puter");
        isLiveMode = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        isAutoMode = prefs.getBoolean(AppConstants.KEY_AUTO_MODE, false);

        // Initialize Managers
        urlContextScraper = new UrlContextScraper();
        modelCatalogManager = new ModelCatalogManager(this);
        dialogManager = new AppDialogManager(this, prefs, sessionManager, geminiService, this::onDialogManagerAuthSuccess);

        NativeMarkdownParser.init(this);

        initViews();
        setupHeadlessAudioWebView();
        setupTTS();
        setupResultLaunchers();
        loadModels();
        setupChatTimeline();
        setupStagedAttachments();
        setupInputDock();
        setupHeaderAndDropdownActions();
        setupOutsideTouchDismissal();
        loadInitialSession();

        attachFloatingLogBadge();
        LiveLogManager.getInstance(this).addListener(this);

        checkAndRequestPermissions();
    }

    private void initViews() {
        chatRootLayout = findViewById(R.id.chatRootLayout);
        tvAppHeaderTitle = findViewById(R.id.tvAppHeaderTitle);
        btnHeaderMenu = findViewById(R.id.btnHeaderMenu);
        settingsBar = findViewById(R.id.settingsBar);
        modelSelectTrigger = findViewById(R.id.modelSelectTrigger);
        selectedModelText = findViewById(R.id.selectedModelText);
        ivTriggerChevron = findViewById(R.id.ivTriggerChevron);
        aspectRatioBar = findViewById(R.id.aspectRatioContainer);
        spnAspectRatio = findViewById(R.id.spnAspectRatio);
        rvChatTimeline = findViewById(R.id.rvChatTimeline);
        ecgLoaderContainer = findViewById(R.id.ecgLoader);
        ivEcgIcon = findViewById(R.id.ivEcgIcon);
        tvEcgStatus = findViewById(R.id.loaderText);
        btnStopGeneration = findViewById(R.id.btnStopGeneration);
        chatControlsContainer = findViewById(R.id.chatControlsContainer);
        rvStagedAttachments = findViewById(R.id.thumbnailArea);
        btnAttach = findViewById(R.id.btnUpload);
        etMessageInput = findViewById(R.id.puterChatInput);
        btnVoiceInput = findViewById(R.id.btnMic);
        btnVoiceAgent = findViewById(R.id.flVoiceAgentBtn);
        voiceAgentIcon = findViewById(R.id.voiceAgentIcon);
        orbitStar1 = findViewById(R.id.orbitStar1);
        orbitStar2 = findViewById(R.id.orbitStar2);
        orbitStar3 = findViewById(R.id.orbitStar3);
        btnSendMessage = findViewById(R.id.btnSend);

        accordionMenuCard = findViewById(R.id.accordionMenuCard);
        rvAccordionModels = findViewById(R.id.rvAccordionModels);

        puterDropdownMenuCard = findViewById(R.id.puterDropdownMenuCard);
        menuAuthOption = findViewById(R.id.menuAuthOption);
        menuModelSourceToggle = findViewById(R.id.menuModelSourceToggle);
        menuViewHistory = findViewById(R.id.menuViewHistory);
        menuNewSession = findViewById(R.id.menuNewSession);
        menuToggleSettings = findViewById(R.id.menuToggleSettings);
        menuHelp = findViewById(R.id.menuHelp);
        menuSettingsHub = findViewById(R.id.menuSettingsHub);
        menuBrowseWebsite = findViewById(R.id.menuBrowseWebsite);
        menuTradersZone = findViewById(R.id.menuTradersZone);
        etMenuReplyLanguage = findViewById(R.id.etMenuReplyLanguage);
        cbMenuUrlContext = findViewById(R.id.cbMenuUrlContext);
        tvMenuLastTokens = findViewById(R.id.tvMenuLastTokens);
        tvMenuLastCost = findViewById(R.id.tvMenuLastCost);
        tvMenuGlobalTokens = findViewById(R.id.tvMenuGlobalTokens);
        tvMenuGlobalCost = findViewById(R.id.tvMenuGlobalCost);
        llDynamicSessionsContainer = findViewById(R.id.llDynamicSessionsContainer);
        llDynamicScrapedContainer = findViewById(R.id.llDynamicScrapedContainer);

        customContextMenuCard = findViewById(R.id.customContextMenuCard);
        ctxCancelGen = findViewById(R.id.ctxCancelGen);
        ctxCopyChat = findViewById(R.id.ctxCopyChat);
        ctxReadAloud = findViewById(R.id.ctxReadAloud);
        ctxDeleteChat = findViewById(R.id.ctxDeleteChat);
        ctxRefreshRelay = findViewById(R.id.ctxRefreshRelay);
        ctxToggleMode = findViewById(R.id.ctxToggleMode);
        ctxToggleProvider = findViewById(R.id.ctxToggleProvider);
        ctxEndSession = findViewById(R.id.ctxEndSession);

        headlessAudioWebView = findViewById(R.id.headlessAudioWebView);

        setupAspectRatioSpinner();
    }

    private void setupAspectRatioSpinner() {
        if (spnAspectRatio == null) return;
        String[] ratios = new String[]{
                "1:1 Square (Profile/Logo)",
                "16:9 Landscape (YouTube/Wallpapers)",
                "9:16 Portrait (Reels/Shorts)",
                "4:3 Classic",
                "3:4 Portrait",
                "21:9 Cinematic"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ratios);
        spnAspectRatio.setAdapter(adapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachFloatingLogBadge() {
        if (chatRootLayout == null) return;

        floatingLogView = LayoutInflater.from(this).inflate(R.layout.view_floating_log_badge, chatRootLayout, false);
        tvFloatingLogBadge = floatingLogView.findViewById(R.id.tvFloatingLogBadge);

        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int marginX = (int) (16 * dm.density);
        int marginY = (int) (80 * dm.density);

        lp.leftMargin = dm.widthPixels - marginX - (int)(68 * dm.density);
        lp.topMargin = dm.heightPixels - marginY - (int)(68 * dm.density);
        chatRootLayout.addView(floatingLogView, lp);

        CardView cvBtn = floatingLogView.findViewById(R.id.cvFloatingLogBtn);
        cvBtn.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX = floatingLogView.getX() - event.getRawX();
                    dY = floatingLogView.getY() - event.getRawY();
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;
                    floatingLogView.setX(newX);
                    floatingLogView.setY(newY);
                    return true;

                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    float deltaX = Math.abs(event.getRawX() - initialTouchX);
                    float deltaY = Math.abs(event.getRawY() - initialTouchY);
                    if (deltaX < 10 && deltaY < 10) {
                        dialogManager.openSystemLogConsole();
                    }
                    return true;
            }
            return false;
        });

        LiveLogManager logger = LiveLogManager.getInstance(this);
        onNewLogEntry(null, logger.getTotalCount(), logger.getErrorCount());
    }

    @Override
    public void onNewLogEntry(LogEntry entry, int totalCount, int errorCount) {
        if (tvFloatingLogBadge != null) {
            tvFloatingLogBadge.setVisibility(totalCount > 0 ? View.VISIBLE : View.GONE);
            tvFloatingLogBadge.setText(String.valueOf(totalCount));
            if (errorCount > 0) {
                tvFloatingLogBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D50000")));
            } else {
                tvFloatingLogBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7C4DFF")));
            }
        }
    }

    @Override
    public void onLogsCleared() {
        if (tvFloatingLogBadge != null) {
            tvFloatingLogBadge.setVisibility(View.GONE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupHeadlessAudioWebView() {
        if (headlessAudioWebView == null) return;
        webAppInterface = new WebAppInterface(this);
        webAppInterface.setMainActivity(this);
        headlessAudioBridge = new HeadlessAudioBridge(this, headlessAudioWebView);
        webAppInterface.setAudioBridge(headlessAudioBridge);

        headlessAudioWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        headlessAudioWebView.setKeepScreenOn(true);

        WebSettings ws = headlessAudioWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        String userAgent = ws.getUserAgentString().replace("; wv", "");
        ws.setUserAgentString(userAgent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(headlessAudioWebView, true);

        headlessAudioWebView.setWebViewClient(new PuterWebViewClient(this) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                headlessAudioWebView.resumeTimers();
                syncPuterTokenToHeadless(false);
            }
        });
        headlessAudioWebView.setWebChromeClient(new MyWebChromeClient(this));
        headlessAudioWebView.addJavascriptInterface(webAppInterface, AppConstants.JS_BRIDGE_NAME);

        headlessAudioBridge.setEventListener(new HeadlessAudioBridge.LiveAudioEventListener() {
            @Override
            public void onSessionConnected() {
                MainActivity.this.onLiveSessionConnected();
            }

            @Override
            public void onTranscriptReceived(String text) {
                MainActivity.this.onLiveTranscriptReceived(text);
            }

            @Override
            public void onUserTranscriptReceived(String text) {
                MainActivity.this.onLiveUserTranscript(text);
            }

            @Override
            public void onModelTranscriptReceived(String text, boolean isFinished) {
                MainActivity.this.onLiveModelTranscript(text, isFinished);
            }

            @Override
            public void onAudioChunkReceived(String base64Pcm) {}

            @Override
            public void onCommentaryFinished() {
                LiveLogManager.getInstance().info("GEMINI_LIVE", "Vocal Turn Completed", "Microphone remains active for multi-turn input.");
            }

            @Override
            public void onSessionClosedWithTelemetry(int code, String reason, boolean clean) {
                MainActivity.this.onLiveSessionClosedWithTelemetry(code, reason, clean);
            }

            @Override
            public void onTargetCoordinatesReceived(String targetId, float left, float top, float right, float bottom) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Target: " + targetId, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String errorMessage) {
                MainActivity.this.onLiveError(errorMessage);
            }
        });

        headlessAudioWebView.loadUrl("https://appassets.androidplatform.net/assets/headless_live_audio.html");
    }

    public void syncPuterTokenToHeadless() {
        syncPuterTokenToHeadless(false);
    }

    public void syncPuterTokenToHeadless(boolean force) {
        if (headlessAudioWebView == null) return;
        String token = AuthManager.getInstance(this).getAuthToken();
        if (token != null && !token.trim().isEmpty()) {
            String clean = token.trim();
            if (!force && clean.equals(lastInjectedPuterToken)) return;
            lastInjectedPuterToken = clean;

            String safeToken = clean.replace("'", "\\'");
            String js = "try { " +
                        "  localStorage.setItem('puter.auth.token', '" + safeToken + "'); " +
                        "  if (typeof puter !== 'undefined') { " +
                        "    puter.authToken = '" + safeToken + "'; " +
                        "    if (puter.auth) { puter.auth.token = '" + safeToken + "'; } " +
                        "  } " +
                        "} catch(e) {}";
            headlessAudioWebView.evaluateJavascript(js, null);
            LiveLogManager.getInstance().ok("PUTER_AUTH", "Token Injected to Headless", "Length: " + clean.length() + " chars (forced: " + force + ")");
        } else {
            if (lastInjectedPuterToken != null) {
                lastInjectedPuterToken = null;
                LiveLogManager.getInstance().warn("PUTER_AUTH", "Token Cleared", "AuthManager token is now empty.");
            }
        }
    }

    public String getActiveModelId() {
        return activeModelId;
    }

    public void onPuterChunkReceived(String chunk) {
        runOnUiThread(() -> {
            if (currentActiveAssistantMsg != null) {
                currentActiveAssistantMsg.setText(currentActiveAssistantMsg.getText() + chunk);
                chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
    }

    public void onPuterGenerationComplete(String fullResponse) {
        runOnUiThread(() -> {
            if (currentActiveAssistantMsg != null) {
                if (fullResponse != null && !fullResponse.isEmpty()) {
                    currentActiveAssistantMsg.setText(fullResponse);
                }
                currentActiveAssistantMsg.setStreaming(false);
                chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
            }
            finishGeneration();
        });
    }

    public void onPuterImageReceived(String imageUrl) {
        runOnUiThread(() -> {
            if (currentActiveAssistantMsg != null) {
                currentActiveAssistantMsg.setGeneratedImageUrl(imageUrl);
                currentActiveAssistantMsg.setStreaming(false);
                chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
            }
            finishGeneration();
        });
    }

    public void onPuterLiveModelsReceived(String rawJson) {
        runOnUiThread(() -> {
            prefs.edit().putString(AppConstants.KEY_PUTER_CACHED_LIVE_MODELS, rawJson).apply();
            modelCatalogManager.parsePuterModelsJson(rawJson);
            setupAccordionAdapter();
            updateSelectedModelDisplayText();
            Toast.makeText(MainActivity.this, "Live Puter models updated!", Toast.LENGTH_SHORT).show();
        });
    }

    public void onPuterGenerationError(String errorMessage) {
        runOnUiThread(() -> {
            if (currentActiveAssistantMsg != null) {
                currentActiveAssistantMsg.setText("Puter Error: " + errorMessage);
                currentActiveAssistantMsg.setStreaming(false);
                chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
            }
            finishGeneration();
        });
    }

    public void onLiveSessionConnected() {
        runOnUiThread(() -> {
            stopTts();
            isLiveVoiceConnected = true;
            lastRenderedUserText = "";
            lastUserTextTimestamp = 0;
            currentLiveAssistantMsg = null;

            if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(R.string.voice_status_listening);
            toggleStarAnimation(true);
            addSystemMessage("🟢 Connected to Gemini Live Model");
            LiveLogManager.getInstance().ok("GEMINI_LIVE", "Session Active (Multi-Turn)", "Connected with model: " + activeModelId);
            Toast.makeText(MainActivity.this, "Connected to Gemini Live Model", Toast.LENGTH_SHORT).show();
        });
    }

    public void onLiveTranscriptReceived(String text) {
        runOnUiThread(() -> {
            if (tvLiveTranscriptTicker != null) tvLiveTranscriptTicker.setText(text);
        });
    }

    public void onLiveUserTranscript(String text) {
        runOnUiThread(() -> {
            if (text == null || text.trim().isEmpty()) return;
            String cleanText = text.trim();
            long now = System.currentTimeMillis();

            if (cleanText.equalsIgnoreCase(lastRenderedUserText) && (now - lastUserTextTimestamp) < 2500) {
                return;
            }

            lastRenderedUserText = cleanText;
            lastUserTextTimestamp = now;

            if (chatAdapter == null || currentSession == null) return;

            try {
                ChatMessage userMsg = new ChatMessage(
                        "live_user_" + System.currentTimeMillis(),
                        ChatMessage.ROLE_USER,
                        cleanText,
                        System.currentTimeMillis()
                );
                chatAdapter.addMessage(userMsg);
                currentSession.addMessage(userMsg);
                sessionManager.saveSession(currentSession);
                rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                LiveLogManager.getInstance().ok("LIVE_TRANSCRIPT", "User Bubble Rendered", "Spoken: \"" + cleanText + "\"");
            } catch (Exception e) {
                LiveLogManager.getInstance().error("LIVE_TRANSCRIPT", "User Bubble Render Exception", e.getMessage());
            }
        });
    }

    public void onLiveModelTranscript(String chunk, boolean isTurnFinished) {
        runOnUiThread(() -> {
            try {
                if (chatAdapter == null || currentSession == null) return;

                if (chunk != null && !chunk.isEmpty()) {
                    String clean = chunk.replaceAll("(?i)<thought[\\s\\S]*?</thought>", "")
                                        .replaceAll("(?i)<thinking[\\s\\S]*?</thinking>", "")
                                        .trim();

                    if (!clean.isEmpty()) {
                        if (currentLiveAssistantMsg == null) {
                            currentLiveAssistantMsg = new ChatMessage(
                                    "live_bot_" + System.currentTimeMillis(),
                                    ChatMessage.ROLE_BOT,
                                    clean,
                                    System.currentTimeMillis()
                            );
                            currentLiveAssistantMsg.setModelUsed(activeModelId);
                            currentLiveAssistantMsg.setStreaming(true);
                            chatAdapter.addMessage(currentLiveAssistantMsg);
                            currentSession.addMessage(currentLiveAssistantMsg);
                            LiveLogManager.getInstance().ok("LIVE_TRANSCRIPT", "Model Bubble Created", clean);
                        } else {
                            String existing = currentLiveAssistantMsg.getText();
                            if (existing != null && clean.startsWith(existing)) {
                                currentLiveAssistantMsg.setText(clean);
                            } else {
                                String updated = (existing != null && !existing.isEmpty()) ? existing + " " + clean : clean;
                                currentLiveAssistantMsg.setText(updated);
                            }
                            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        }
                        rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                }

                if (isTurnFinished) {
                    if (currentLiveAssistantMsg != null) {
                        currentLiveAssistantMsg.setStreaming(false);
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        sessionManager.saveSession(currentSession);
                        currentLiveAssistantMsg = null;
                        LiveLogManager.getInstance().info("LIVE_TRANSCRIPT", "Model Bubble Finalized", "Turn committed.");
                    }
                }
            } catch (Exception e) {
                LiveLogManager.getInstance().error("LIVE_TRANSCRIPT", "Model Bubble Render Exception", e.getMessage());
            }
        });
    }

    public void onLiveSessionClosedWithTelemetry(int code, String reason, boolean clean) {
        runOnUiThread(() -> {
            if (currentLiveAssistantMsg != null) {
                currentLiveAssistantMsg.setStreaming(false);
                currentLiveAssistantMsg = null;
            }
            lastRenderedUserText = "";
            lastUserTextTimestamp = 0;

            if (isLiveVoiceConnected) {
                isLiveVoiceConnected = false;
                if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(R.string.voice_status_speaking);
                toggleStarAnimation(false);

                String statusMsg = "🔴 Disconnected from Live Model";
                if (code != 1000 && reason != null && !reason.isEmpty()) {
                    statusMsg += " (" + code + ": " + reason + ")";
                }
                addSystemMessage(statusMsg);
                Toast.makeText(MainActivity.this, statusMsg, Toast.LENGTH_SHORT).show();

                if (previousProviderBeforeLive != null) {
                    activeProvider = previousProviderBeforeLive;
                    prefs.edit().putString(AppConstants.KEY_ACTIVE_PROVIDER, activeProvider).apply();
                    previousProviderBeforeLive = null;
                }
            }
        });
    }

    public void onLiveError(String err) {
        runOnUiThread(() -> {
            if (currentLiveAssistantMsg != null) {
                currentLiveAssistantMsg.setStreaming(false);
                currentLiveAssistantMsg = null;
            }
            lastRenderedUserText = "";
            lastUserTextTimestamp = 0;

            LiveLogManager.getInstance().error("GEMINI_LIVE", "Live Session Error Event", err);
            if (isLiveVoiceConnected) {
                isLiveVoiceConnected = false;
                if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(err);
                toggleStarAnimation(false);
                addSystemMessage("🔴 Disconnected from Live Model (" + err + ")");
                Toast.makeText(MainActivity.this, "Live Error: " + err, Toast.LENGTH_SHORT).show();

                if (previousProviderBeforeLive != null) {
                    activeProvider = previousProviderBeforeLive;
                    prefs.edit().putString(AppConstants.KEY_ACTIVE_PROVIDER, activeProvider).apply();
                    previousProviderBeforeLive = null;
                }
            }
        });
    }

    private void addSystemMessage(String text) {
        ChatMessage sysMsg = new ChatMessage(
                "msg_sys_" + System.currentTimeMillis(),
                ChatMessage.ROLE_SYSTEM,
                text,
                System.currentTimeMillis()
        );
        chatAdapter.addMessage(sysMsg);
        if (currentSession != null) {
            currentSession.addMessage(sysMsg);
            sessionManager.saveSession(currentSession);
        }
        rvChatTimeline.smoothScrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));
    }

    private void setupTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                String targetLang = prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, "");
                if (!targetLang.isEmpty()) {
                    setTtsLanguage(targetLang);
                } else {
                    textToSpeech.setLanguage(Locale.US);
                }
            }
        });
    }

    private void setTtsLanguage(String languageName) {
        if (textToSpeech == null || languageName == null) return;
        Locale found = null;
        for (Locale loc : Locale.getAvailableLocales()) {
            if (loc.getDisplayLanguage().equalsIgnoreCase(languageName) ||
                    loc.getLanguage().equalsIgnoreCase(languageName)) {
                found = loc;
                break;
            }
        }
        textToSpeech.setLanguage(found != null ? found : Locale.US);
    }

    public void stopTts() {
        if (textToSpeech != null) {
            try { textToSpeech.stop(); } catch (Exception ignored) {}
        }
    }

    private void setupResultLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) stageFileFromUri(uri);
        });

        documentLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) stageFileFromUri(uri);
        });
    }

    private void stageFileFromUri(Uri uri) {
        long fileSize = 0;
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex);
                cursor.close();
            }
        } catch (Exception ignored) {}

        if (fileSize > 25 * 1024 * 1024) {
            Toast.makeText(this, "File too large (max 20MB for direct inline analysis)", Toast.LENGTH_LONG).show();
            LiveLogManager.getInstance().warn("ATTACHMENT", "File Skipped", "File exceeds 25MB ceiling: " + fileSize + " bytes");
            return;
        }

        final long finalFileSize = fileSize;
        final String name = FileUtils.getFileName(this, uri);
        final String mime = FileUtils.getMimeType(this, uri);

        Bitmap thumb = null;
        try {
            if (mime != null) {
                if (mime.startsWith("image/")) {
                    thumb = ImageUtils.getThumbnail(this, uri, 120);
                } else if (mime.startsWith("video/")) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(this, uri);
                    thumb = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    mmr.release();
                }
            }
        } catch (Exception ignored) {}

        final Bitmap finalThumb = thumb;

        new Thread(() -> {
            try {
                String dataUri = FileUtils.fileToDataUri(MainActivity.this, uri);
                if (dataUri == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not process media (memory limit)", Toast.LENGTH_SHORT).show());
                    return;
                }

                runOnUiThread(() -> {
                    StagedFile file = new StagedFile(uri, name, mime, finalFileSize, dataUri, finalThumb);
                    stagedAttachmentsAdapter.addAttachment(file);
                    rvStagedAttachments.setVisibility(View.VISIBLE);
                    updateSendButtonState();
                    LiveLogManager.getInstance().ok("ATTACHMENT", "File Staged Successfully", "Name: " + name + " | Mime: " + mime);
                });
            } catch (OutOfMemoryError oom) {
                LiveLogManager.getInstance().error("ATTACHMENT", "OOM in staging thread", "File too large for heap allocation");
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Media file is too large for device memory", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                LiveLogManager.getInstance().error("ATTACHMENT", "Staging Thread Error", e.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to attach: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupChatTimeline() {
        chatAdapter = new ChatAdapter(new ChatAdapter.OnChatMessageActionListener() {
            @Override
            public void onCopyMessage(ChatMessage message) {
                copyText(message.getText());
            }

            @Override
            public void onSpeakMessage(ChatMessage message) {
                speakText(message.getText());
            }

            @Override
            public void onSpeakAssistantClicked(ChatMessage message, View speakContainer, TextView s1, TextView s2, TextView s3) {
                toggleChatStarAnimation(true, s1, s2, s3);
                speakAssistantWithProgress(message.getText(), () -> toggleChatStarAnimation(false, s1, s2, s3));
            }

            @Override
            public void onRegenerateMessage(ChatMessage message) {
                regenerateTurn(message);
            }

            @Override
            public void onDeleteMessage(ChatMessage message) {
                stopTts();
                chatAdapter.removeMessage(message);
                if (currentSession != null) {
                    currentSession.getMessages().remove(message);
                    sessionManager.saveSession(currentSession);
                }
            }

            @Override
            public void onEditMessage(ChatMessage message) {
                etMessageInput.setText(message.getText());
                etMessageInput.setSelection(etMessageInput.getText().length());
            }

            @Override
            public void onQuoteAndReattach(ChatMessage message) {
                quoteMessageIntoInput(message);
            }

            @Override
            public void onPreviewCode(String language, String code) {
                dialogManager.showCodePreviewFullscreen(code);
            }

            @Override
            public void onPreviewImage(String imageUrlOrBase64) {
                if (imageUrlOrBase64 != null && (imageUrlOrBase64.startsWith("data:video/") || imageUrlOrBase64.endsWith(".mp4"))) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(imageUrlOrBase64), "video/*");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "No external video player found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    dialogManager.showImagePreviewViewer(imageUrlOrBase64, webAppInterface);
                }
            }

            @Override
            public void onLongPressMessage(ChatMessage message, View view) {
                int[] loc = new int[2];
                view.getLocationOnScreen(loc);
                showUniversalContextMenu(message, loc[0] + 50, loc[1] + (view.getHeight() / 2));
            }
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvChatTimeline.setLayoutManager(lm);
        rvChatTimeline.setAdapter(chatAdapter);

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) { return false; }

            @Override
            public void onLongPress(MotionEvent e) {
                showEmptyCanvasContextMenu(e.getRawX(), e.getRawY());
            }
        });

        rvChatTimeline.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });
    }

    private void speakAssistantWithProgress(String text, Runnable onFinished) {
        if (textToSpeech != null && text != null) {
            String utteranceId = "tts_" + System.currentTimeMillis();
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) { runOnUiThread(onFinished); }
                @Override public void onError(String utteranceId) { runOnUiThread(onFinished); }
            });
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            onFinished.run();
        }
    }

    private void setupStagedAttachments() {
        stagedAttachmentsAdapter = new StagedAttachmentsAdapter((position, file) -> {
            stagedAttachmentsAdapter.removeAttachment(position);
            if (stagedAttachmentsAdapter.getItemCount() == 0) {
                rvStagedAttachments.setVisibility(View.GONE);
            }
            updateSendButtonState();
        });
        rvStagedAttachments.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvStagedAttachments.setAdapter(stagedAttachmentsAdapter);
    }

    private void setupInputDock() {
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSendButtonState(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAttach.setOnClickListener(v -> dialogManager.showAttachmentPickerDialog(galleryLauncher, documentLauncher));

        btnSendMessage.setOnClickListener(v -> {
            if (isGenerating) {
                stopCurrentGeneration();
            } else {
                dispatchSendMessage();
            }
        });

        btnStopGeneration.setOnClickListener(v -> stopCurrentGeneration());
        btnVoiceInput.setOnClickListener(v -> startSpeechToText());
        btnVoiceAgent.setOnClickListener(v -> launchLiveVoiceAgent());
    }

    private void setupHeaderAndDropdownActions() {
        btnHeaderMenu.setOnClickListener(v -> {
            if (puterDropdownMenuCard.getVisibility() == View.VISIBLE) {
                puterDropdownMenuCard.setVisibility(View.GONE);
            } else {
                refreshDropdownUI();
                puterDropdownMenuCard.setVisibility(View.VISIBLE);
                if (accordionMenuCard != null) accordionMenuCard.setVisibility(View.GONE);
            }
        });

        modelSelectTrigger.setOnClickListener(v -> {
            if (accordionMenuCard.getVisibility() == View.VISIBLE) {
                accordionMenuCard.setVisibility(View.GONE);
                ivTriggerChevron.setRotation(0);
            } else {
                accordionMenuCard.setVisibility(View.VISIBLE);
                ivTriggerChevron.setRotation(180);
                if (puterDropdownMenuCard != null) puterDropdownMenuCard.setVisibility(View.GONE);
            }
        });

        menuAuthOption.setOnClickListener(v -> {
            toggleAuthMirror();
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuModelSourceToggle.setOnClickListener(v -> {
            toggleModelSource();
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuViewHistory.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            dialogManager.showHistoryDialog(currentSession, this::loadSessionIntoUI, this::loadInitialSession);
        });

        menuNewSession.setOnClickListener(v -> {
            createNewSession();
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuToggleSettings.setOnClickListener(v -> {
            if (settingsBar.getVisibility() == View.VISIBLE) {
                settingsBar.setVisibility(View.GONE);
                menuToggleSettings.setText("Show Settings");
            } else {
                settingsBar.setVisibility(View.VISIBLE);
                menuToggleSettings.setText("Hide Settings");
            }
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuHelp.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            dialogManager.showHelpDialog();
        });

        menuSettingsHub.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            dialogManager.showSettingsHubDialog(currentSession, () -> {
                loadModels();
            });
        });

        menuBrowseWebsite.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            dialogManager.showBrowserDialog(webAppInterface);
        });

        menuTradersZone.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            dialogManager.showCryptoBotDialog(webAppInterface);
        });

        etMenuReplyLanguage.setText(prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, ""));
        etMenuReplyLanguage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(AppConstants.KEY_TARGET_LANGUAGE, s.toString().trim()).apply();
                setTtsLanguage(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        cbMenuUrlContext.setChecked(prefs.getBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, false));
        cbMenuUrlContext.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, isChecked).apply();
            Toast.makeText(this, isChecked ? "URL Context Enabled" : "URL Context Disabled", Toast.LENGTH_SHORT).show();
        });

        setupContextMenuActions();
    }

    private void setupContextMenuActions() {
        if (customContextMenuCard == null) return;
        ctxCancelGen.setOnClickListener(v -> {
            stopTts();
            stopCurrentGeneration();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxCopyChat.setOnClickListener(v -> {
            if (contextTargetMessage != null) copyText(contextTargetMessage.getText());
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxReadAloud.setOnClickListener(v -> {
            if (contextTargetMessage != null) speakText(contextTargetMessage.getText());
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxDeleteChat.setOnClickListener(v -> {
            stopTts();
            if (contextTargetMessage != null) {
                chatAdapter.removeMessage(contextTargetMessage);
                if (currentSession != null) {
                    currentSession.getMessages().remove(contextTargetMessage);
                    sessionManager.saveSession(currentSession);
                }
            }
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxRefreshRelay.setOnClickListener(v -> {
            Toast.makeText(this, "Nostr Relay Refreshed", Toast.LENGTH_SHORT).show();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxToggleMode.setOnClickListener(v -> {
            isAutoMode = !isAutoMode;
            prefs.edit().putBoolean(AppConstants.KEY_AUTO_MODE, isAutoMode).apply();
            if (webAppInterface != null) webAppInterface.setAutoMode(isAutoMode);
            Toast.makeText(this, isAutoMode ? "Auto Mode Enabled" : "Normal Mode Enabled", Toast.LENGTH_SHORT).show();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxToggleProvider.setOnClickListener(v -> {
            activeProvider = "Gemini".equalsIgnoreCase(activeProvider) ? "Puter" : "Gemini";
            prefs.edit().putString(AppConstants.KEY_ACTIVE_PROVIDER, activeProvider).apply();
            Toast.makeText(this, "Switched active engine provider to: " + activeProvider, Toast.LENGTH_SHORT).show();
            loadModels();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxEndSession.setOnClickListener(v -> {
            stopTts();
            createNewSession();
            customContextMenuCard.setVisibility(View.GONE);
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (customContextMenuCard != null && customContextMenuCard.getVisibility() == View.VISIBLE) {
                Rect rect = new Rect();
                customContextMenuCard.getGlobalVisibleRect(rect);
                if (!rect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    customContextMenuCard.setVisibility(View.GONE);
                }
            }
            if (puterDropdownMenuCard != null && puterDropdownMenuCard.getVisibility() == View.VISIBLE) {
                Rect rect = new Rect();
                puterDropdownMenuCard.getGlobalVisibleRect(rect);
                Rect btnRect = new Rect();
                btnHeaderMenu.getGlobalVisibleRect(btnRect);
                if (!rect.contains((int) ev.getRawX(), (int) ev.getRawY()) && !btnRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    puterDropdownMenuCard.setVisibility(View.GONE);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void setupOutsideTouchDismissal() {
        chatRootLayout.setOnClickListener(v -> {
            if (customContextMenuCard != null) customContextMenuCard.setVisibility(View.GONE);
            if (puterDropdownMenuCard != null) puterDropdownMenuCard.setVisibility(View.GONE);
        });
    }

    private void refreshDropdownUI() {
        boolean loggedIn = AuthManager.getInstance(this).isLoggedIn();
        menuAuthOption.setText(loggedIn ? "Sign Out" : "Sign In");

        boolean isLive = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        menuModelSourceToggle.setText(isLive ? "Use Local Models" : "Enable Live Models");

        long totalTokens = prefs.getLong(AppConstants.KEY_USAGE_TOTAL_TOKENS, 0);
        float totalCost = prefs.getFloat(AppConstants.KEY_USAGE_TOTAL_COST, 0.0f);
        tvMenuGlobalTokens.setText("Global Total: " + totalTokens + " tokens");
        tvMenuGlobalCost.setText(String.format(Locale.US, "Global Cost: $%.4f", totalCost));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int calculatedHeight = (int) (dm.heightPixels * 0.72f);
        int minH = (int) (380 * dm.density);
        int maxH = (int) (580 * dm.density);
        int finalH = Math.max(minH, Math.min(calculatedHeight, maxH));

        ViewGroup.LayoutParams lp = puterDropdownMenuCard.getLayoutParams();
        lp.height = finalH;
        puterDropdownMenuCard.setLayoutParams(lp);

        refreshDynamicSessionsInMenu();
    }

    private void refreshDynamicSessionsInMenu() {
        if (llDynamicSessionsContainer == null) return;
        llDynamicSessionsContainer.removeAllViews();
        List<ChatSession> sessions = sessionManager.getAllSessions();
        if (sessions == null || sessions.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No saved sessions");
            tv.setTextColor(Color.parseColor("#888888"));
            tv.setTextSize(12);
            tv.setPadding(32, 16, 32, 16);
            llDynamicSessionsContainer.addView(tv);
            return;
        }

        for (ChatSession s : sessions) {
            TextView tv = new TextView(this);
            tv.setText(s.getTitle());
            boolean isActive = currentSession != null && s.getId().equals(currentSession.getId());
            tv.setTextColor(isActive ? Color.parseColor("#1a73e8") : Color.parseColor("#333333"));
            tv.setTextSize(13);
            tv.setPadding(32, 20, 32, 20);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> {
                loadSessionIntoUI(s);
                puterDropdownMenuCard.setVisibility(View.GONE);
            });
            tv.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Session")
                        .setMessage("Delete session \"" + s.getTitle() + "\"?")
                        .setPositiveButton("Delete", (d, w) -> {
                            sessionManager.deleteSession(s.getId());
                            if (currentSession != null && currentSession.getId().equals(s.getId())) {
                                loadInitialSession();
                            } else {
                                refreshDynamicSessionsInMenu();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
            llDynamicSessionsContainer.addView(tv);
        }
    }

    private void updateSendButtonState() {
        boolean hasText = etMessageInput.getText().toString().trim().length() > 0;
        boolean hasAttachments = stagedAttachmentsAdapter.getItemCount() > 0;

        if (isGenerating) {
            btnSendMessage.setVisibility(View.VISIBLE);
            btnSendMessage.setImageResource(R.drawable.ic_stop);
            btnSendMessage.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.stop_red));
            btnVoiceInput.setVisibility(View.GONE);
            btnVoiceAgent.setVisibility(View.GONE);
        } else if (hasText || hasAttachments) {
            btnSendMessage.setVisibility(View.VISIBLE);
            btnSendMessage.setImageResource(R.drawable.ic_send);
            btnSendMessage.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.puter_blue));
            btnVoiceInput.setVisibility(View.GONE);
            btnVoiceAgent.setVisibility(View.GONE);
        } else {
            btnSendMessage.setVisibility(View.GONE);
            btnVoiceInput.setVisibility(View.VISIBLE);
            btnVoiceAgent.setVisibility(View.VISIBLE);
        }
    }

    private void dispatchSendMessage() {
        String prompt = etMessageInput.getText().toString().trim();
        List<StagedFile> attachments = new ArrayList<>(stagedAttachmentsAdapter.getItems());

        if (prompt.isEmpty() && attachments.isEmpty()) return;

        etMessageInput.setText("");
        stagedAttachmentsAdapter.clear();
        rvStagedAttachments.setVisibility(View.GONE);
        updateSendButtonState();

        ChatMessage userMsg = new ChatMessage(
                "msg_user_" + System.currentTimeMillis(),
                ChatMessage.ROLE_USER,
                prompt,
                System.currentTimeMillis()
        );
        userMsg.setAttachments(attachments);
        chatAdapter.addMessage(userMsg);
        currentSession.addMessage(userMsg);
        sessionManager.autoUpdateTitleIfNeeded(currentSession, prompt);
        rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        ChatMessage assistantMsg = new ChatMessage(
                "msg_bot_" + System.currentTimeMillis(),
                ChatMessage.ROLE_BOT,
                "",
                System.currentTimeMillis()
        );
        assistantMsg.setModelUsed(activeModelId);
        assistantMsg.setStreaming(true);
        chatAdapter.addMessage(assistantMsg);
        currentSession.addMessage(assistantMsg);
        currentActiveAssistantMsg = assistantMsg;

        // Auto Mode Scraper Crawl Execution Workflow
        if (isAutoMode) {
            executeAutoModeCrawlSequence(prompt, assistantMsg);
            return;
        }

        String targetLang = prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, "");
        String activeSystemMessage = prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, "");
        if (!targetLang.isEmpty()) {
            String systemDirective = "IMPORTANT:\nALWAYS respond ONLY in " + targetLang + ".\n\nNEVER use English.\n\nEven if user speaks English, translate internally and answer only in " + targetLang + ". Do not transliterate. Write strictly in the native script characters of the language.";
            activeSystemMessage = activeSystemMessage.isEmpty() ? systemDirective : activeSystemMessage + "\n\n" + systemDirective;
        }
        currentSession.getConfig().setSystemInstructions(activeSystemMessage);

        boolean isImageGen = modelCatalogManager.isImageGenerationModel(activeModelId);
        String ratio = "1:1";
        if (spnAspectRatio != null && spnAspectRatio.getSelectedItem() != null) {
            String selected = spnAspectRatio.getSelectedItem().toString();
            if (selected.contains("16:9")) ratio = "16:9";
            else if (selected.contains("9:16")) ratio = "9:16";
            else if (selected.contains("4:3")) ratio = "4:3";
            else if (selected.contains("3:4")) ratio = "3:4";
            else if (selected.contains("21:9")) ratio = "21:9";
        }
        currentSession.getConfig().setAspectRatio(ratio);

        String finalPrompt = prompt;
        if (isImageGen) {
            finalPrompt = prompt + ". Aspect ratio: " + ratio + " cinematic landscape.";
        }

        isGenerating = true;
        updateSendButtonState();
        tvEcgStatus.setText(isImageGen ? "AI is generating image..." : "AI is answering...");
        ecgLoaderContainer.setVisibility(View.VISIBLE);
        try {
            Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.ecg_trace_animation);
            if (ivEcgIcon != null) ivEcgIcon.startAnimation(pulseAnim);
        } catch (Exception ignored) {}

        boolean urlContextEnabled = prefs.getBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, false);

        if (urlContextEnabled) {
            List<String> promptUrls = UrlContextScraper.extractAndSanitizeUrls(finalPrompt);
            List<String> attachmentUrls = UrlContextScraper.extractUrlsFromAttachments(attachments);

            Set<String> allUniqueUrls = new LinkedHashSet<>();
            allUniqueUrls.addAll(promptUrls);
            allUniqueUrls.addAll(attachmentUrls);

            List<String> urlList = new ArrayList<>(allUniqueUrls);

            if (!urlList.isEmpty()) {
                final String basePrompt = finalPrompt;
                new Thread(() -> {
                    String enrichedPrompt = urlContextScraper.executeHighSpeedUrlBatchProcessing(basePrompt, urlList,
                            status -> runOnUiThread(() -> {
                                if (tvEcgStatus != null) tvEcgStatus.setText(status);
                            }));
                    runOnUiThread(() -> {
                        tvEcgStatus.setText("AI is answering...");
                        routeGeneration(enrichedPrompt, attachments, assistantMsg);
                    });
                }).start();
                return;
            }
        }

        routeGeneration(finalPrompt, attachments, assistantMsg);
    }

    private void executeAutoModeCrawlSequence(String prompt, ChatMessage assistantMsg) {
        isGenerating = true;
        updateSendButtonState();
        ecgLoaderContainer.setVisibility(View.VISIBLE);
        tvEcgStatus.setText("Auto Mode: Connecting to relay...");

        // Ensure background QueryWatcherService is active
        try {
            Intent serviceIntent = new Intent(this, QueryWatcherService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            LiveLogManager.getInstance().warn("QUERY_SERVICE", "Service start skipped", e.getMessage());
        }

        Handler handler = new Handler(Looper.getMainLooper());

        // Step 1: Establishing socket connection
        assistantMsg.setText("Establishing socket connection to relay...");
        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);

        // Step 2: Waiting for crawl response
        handler.postDelayed(() -> {
            if (!isGenerating) return;
            assistantMsg.setText("Waiting for extension crawl response...");
            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
        }, 900);

        // Step 3: Waking up Kiwi Browser & launching intent
        handler.postDelayed(() -> {
            if (!isGenerating) return;
            assistantMsg.setText("Extension sleeping. Waking up Kiwi Browser...");
            assistantMsg.setStreaming(false);
            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
            finishGeneration();

            wakeUpKiwi(prompt);
        }, 1800);
    }

    public void wakeUpKiwi(String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query)));
            intent.setPackage("com.kiwibrowser.browser");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            LiveLogManager.getInstance().ok("KIWI_WAKEUP", "Launched Kiwi Browser Successfully", "Query: " + query);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query)));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                LiveLogManager.getInstance().warn("KIWI_WAKEUP", "Fallback Browser Launched", "Kiwi not installed; opened default browser.");
            } catch (Exception ex) {
                LiveLogManager.getInstance().error("KIWI_WAKEUP", "Launch Failed", ex.getMessage());
                Toast.makeText(this, "Could not open browser for query", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void setAutoMode(boolean auto) {
        this.isAutoMode = auto;
        prefs.edit().putBoolean(AppConstants.KEY_AUTO_MODE, auto).apply();
        if (ctxToggleMode != null) {
            ctxToggleMode.setText(isAutoMode ? "⚡ Auto Mode" : "✋ Normal Mode");
        }
    }

    public void dismissPortalDialog() {
        if (dialogManager != null) {
            dialogManager.dismissPortalDialog();
        }
    }

    public void onScrapedProductReceived(String id, String json) {
        Toast.makeText(this, "Scraped packet cataloged: " + id, Toast.LENGTH_SHORT).show();
    }

    private boolean isGeminiNativeModel(String modelId) {
        if (modelId == null) return false;
        String lower = modelId.toLowerCase(Locale.US);
        if (lower.contains("openrouter") || lower.contains("togetherai") || lower.contains("puter") || lower.contains(":")) {
            return false;
        }
        return "Gemini".equalsIgnoreCase(activeProvider);
    }

    private boolean isGeminiSupportedInlineMedia(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase(Locale.US);
        return lower.startsWith("image/") || lower.startsWith("video/") || lower.startsWith("audio/") || lower.equals("application/pdf");
    }

    private void routeGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        currentActiveAssistantMsg = assistantMsg;
        if (isGeminiNativeModel(activeModelId)) {
            dispatchGeminiGeneration(prompt, attachments, assistantMsg);
        } else {
            dispatchPuterGeneration(prompt, attachments, assistantMsg);
        }
    }

    private void dispatchPuterGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        currentActiveAssistantMsg = assistantMsg;
        syncPuterTokenToHeadless(true);

        boolean isConnected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                    isConnected = (caps != null && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
                } else {
                    android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                    isConnected = (ni != null && ni.isConnected());
                }
            }
        } catch (Exception ignored) {}

        LiveLogManager.getInstance().process("PUTER_DISPATCH", "Packaging Payload in Java",
                "Model: " + activeModelId + " | Network Validated: " + isConnected + " | Prompt Len: " + prompt.length());

        JSONArray messagesArray = new JSONArray();
        try {
            List<ChatMessage> history = new ArrayList<>(currentSession.getMessages());
            if (history.size() >= 2) {
                history = history.subList(0, history.size() - 2);
            }

            for (ChatMessage msg : history) {
                JSONObject m = new JSONObject();
                m.put("role", msg.getRole().equalsIgnoreCase(ChatMessage.ROLE_USER) ? "user" : "assistant");
                m.put("content", msg.getText());
                messagesArray.put(m);
            }

            JSONObject current = new JSONObject();
            current.put("role", "user");

            if (attachments != null && !attachments.isEmpty()) {
                JSONArray contentBlocks = new JSONArray();
                JSONObject textBlock = new JSONObject();
                textBlock.put("type", "text");
                textBlock.put("text", prompt);
                contentBlocks.put(textBlock);

                for (StagedFile file : attachments) {
                    if (file.getMimeType() != null && file.getMimeType().startsWith("image/")) {
                        JSONObject imgBlock = new JSONObject();
                        imgBlock.put("type", "image_url");
                        JSONObject urlObj = new JSONObject();
                        urlObj.put("url", file.getBase64DataUri());
                        imgBlock.put("image_url", urlObj);
                        contentBlocks.put(imgBlock);
                    }
                }
                current.put("content", contentBlocks);
            } else {
                current.put("content", prompt);
            }
            messagesArray.put(current);

        } catch (Exception ignored) {}

        String ratio = currentSession.getConfig().getAspectRatio();
        String sysInst = currentSession.getConfig().getSystemInstructions();
        boolean isImageGen = modelCatalogManager.isImageGenerationModel(activeModelId);

        if (headlessAudioBridge != null) {
            LiveLogManager.getInstance().info("PUTER_DISPATCH", "Handing to Headless Bridge",
                    "Dispatching to window.sendPuterChat (Stream: " + !isImageGen + ")");
            headlessAudioBridge.sendPuterChat(messagesArray.toString(), activeModelId, !isImageGen, sysInst, ratio);
        } else {
            LiveLogManager.getInstance().error("PUTER_DISPATCH", "Bridge Missing", "HeadlessAudioBridge instance is null");
            onPuterGenerationError("Headless worker bridge not available");
        }
    }

    private void dispatchGeminiGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        currentActiveAssistantMsg = assistantMsg;
        JSONObject payload = new JSONObject();
        try {
            JSONArray contents = new JSONArray();

            if (currentSession != null && currentSession.getMessages() != null) {
                List<ChatMessage> history = new ArrayList<>(currentSession.getMessages());
                if (history.size() >= 2) {
                    history = history.subList(0, history.size() - 2);
                }

                for (ChatMessage msg : history) {
                    if (msg == null || ChatMessage.ROLE_SYSTEM.equalsIgnoreCase(msg.getRole())) continue;
                    String role = msg.getRole().equalsIgnoreCase(ChatMessage.ROLE_USER) ? "user" : "model";
                    String text = msg.getText();
                    if (text == null) text = "";

                    JSONArray priorParts = new JSONArray();
                    if (!text.trim().isEmpty()) {
                        JSONObject tPart = new JSONObject();
                        tPart.put("text", text);
                        priorParts.put(tPart);
                    }

                    if (msg.getAttachments() != null && "user".equals(role)) {
                        for (StagedFile file : msg.getAttachments()) {
                            if (file != null && file.getBase64DataUri() != null && isGeminiSupportedInlineMedia(file.getMimeType())) {
                                String cleanB64 = file.getBase64DataUri().replaceFirst("^data:[^;]+;base64,", "");
                                JSONObject inlineData = new JSONObject();
                                inlineData.put("mimeType", file.getMimeType());
                                inlineData.put("data", cleanB64);
                                JSONObject partObj = new JSONObject();
                                partObj.put("inlineData", inlineData);
                                priorParts.put(partObj);
                            }
                        }
                    }

                    if (priorParts.length() == 0) continue;

                    if (contents.length() == 0 && !"user".equals(role)) {
                        continue;
                    }

                    if (contents.length() > 0) {
                        JSONObject lastTurn = contents.getJSONObject(contents.length() - 1);
                        if (role.equals(lastTurn.optString("role"))) {
                            JSONArray lastParts = lastTurn.getJSONArray("parts");
                            for (int p = 0; p < priorParts.length(); p++) {
                                lastParts.put(priorParts.get(p));
                            }
                            continue;
                        }
                    }

                    JSONObject turnObj = new JSONObject();
                    turnObj.put("role", role);
                    turnObj.put("parts", priorParts);
                    contents.put(turnObj);
                }
            }

            JSONArray currentParts = new JSONArray();
            if (prompt != null && !prompt.trim().isEmpty()) {
                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);
                currentParts.put(textPart);
            }

            if (attachments != null) {
                for (StagedFile file : attachments) {
                    if (file != null && file.getBase64DataUri() != null && isGeminiSupportedInlineMedia(file.getMimeType())) {
                        String cleanB64 = file.getBase64DataUri().replaceFirst("^data:[^;]+;base64,", "");
                        JSONObject inlineData = new JSONObject();
                        inlineData.put("mimeType", file.getMimeType());
                        inlineData.put("data", cleanB64);
                        JSONObject partObj = new JSONObject();
                        partObj.put("inlineData", inlineData);
                        currentParts.put(partObj);
                        LiveLogManager.getInstance().ok("GEMINI_MEDIA", "Attached Media Part", "Mime: " + file.getMimeType() + " | Length: " + cleanB64.length());
                    }
                }
            }

            if (currentParts.length() > 0) {
                if (contents.length() > 0) {
                    JSONObject lastTurn = contents.getJSONObject(contents.length() - 1);
                    if ("user".equals(lastTurn.optString("role"))) {
                        JSONArray lastParts = lastTurn.getJSONArray("parts");
                        for (int p = 0; p < currentParts.length(); p++) {
                            lastParts.put(currentParts.get(p));
                        }
                    } else {
                        JSONObject userTurn = new JSONObject();
                        userTurn.put("role", "user");
                        userTurn.put("parts", currentParts);
                        contents.put(userTurn);
                    }
                } else {
                    JSONObject userTurn = new JSONObject();
                    userTurn.put("role", "user");
                    userTurn.put("parts", currentParts);
                    contents.put(userTurn);
                }
            }

            payload.put("contents", contents);

            String sysInst = (currentSession != null && currentSession.getConfig() != null) ?
                    currentSession.getConfig().getSystemInstructions() : null;
            if (sysInst == null || sysInst.trim().isEmpty()) {
                sysInst = prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, "");
            }

            if (sysInst != null && !sysInst.trim().isEmpty()) {
                JSONObject sysObj = new JSONObject();
                JSONArray sysParts = new JSONArray();
                JSONObject sysTextPart = new JSONObject();
                sysTextPart.put("text", sysInst);
                sysParts.put(sysTextPart);
                sysObj.put("parts", sysParts);
                payload.put("systemInstruction", sysObj);
            }

            JSONArray toolsArray = new JSONArray();
            if (currentSession != null && currentSession.getConfig() != null && currentSession.getConfig().isWebGroundingEnabled()) {
                JSONObject googleSearch = new JSONObject();
                googleSearch.put("googleSearch", new JSONObject());
                toolsArray.put(googleSearch);
            }

            if (prefs.getBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, false)) {
                JSONObject urlContextTool = new JSONObject();
                urlContextTool.put("url_context", new JSONObject());
                toolsArray.put(urlContextTool);
            }

            if (toolsArray.length() > 0) {
                payload.put("tools", toolsArray);
            }

        } catch (Exception ignored) {}

        geminiService.executeCall(activeModelId, payload.toString(), true, new GeminiService.GeminiStreamListener() {
            @Override
            public void onChunk(String chunkText) {
                runOnUiThread(() -> {
                    assistantMsg.setText(assistantMsg.getText() + chunkText);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onComplete(String fullResponse) {
                runOnUiThread(() -> {
                    assistantMsg.setText(fullResponse);
                    assistantMsg.setStreaming(false);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    finishGeneration();
                });
            }

            @Override
            public void onCitationsReceived(List<GroundingSource> citations) {
                runOnUiThread(() -> {
                    assistantMsg.setGroundingSources(citations);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    assistantMsg.setText("Gemini Error: " + errorMessage);
                    assistantMsg.setStreaming(false);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    finishGeneration();
                });
            }
        });
    }

    private void stopCurrentGeneration() {
        stopTts();
        if (headlessAudioBridge != null) {
            headlessAudioBridge.stopPuterChat();
        }
        geminiService.cancelCurrentCall();
        finishGeneration();
    }

    private void finishGeneration() {
        isGenerating = false;
        if (ecgLoaderContainer != null) ecgLoaderContainer.setVisibility(View.GONE);
        if (ivEcgIcon != null) ivEcgIcon.clearAnimation();
        updateSendButtonState();
        if (currentSession != null) sessionManager.saveSession(currentSession);
        updateUsageStats();
    }

    private void regenerateTurn(ChatMessage botMessage) {
        int idx = chatAdapter.getMessages().indexOf(botMessage);
        if (idx > 0) {
            ChatMessage prevUserMsg = chatAdapter.getMessages().get(idx - 1);
            if (prevUserMsg.getRole().equalsIgnoreCase(ChatMessage.ROLE_USER)) {
                chatAdapter.removeMessage(botMessage);
                currentSession.getMessages().remove(botMessage);

                ChatMessage newBotMsg = new ChatMessage(
                        "msg_bot_" + System.currentTimeMillis(),
                        ChatMessage.ROLE_BOT,
                        "",
                        System.currentTimeMillis()
                );
                newBotMsg.setModelUsed(activeModelId);
                newBotMsg.setStreaming(true);
                chatAdapter.addMessage(newBotMsg);
                currentSession.addMessage(newBotMsg);
                currentActiveAssistantMsg = newBotMsg;

                isGenerating = true;
                updateSendButtonState();
                ecgLoaderContainer.setVisibility(View.VISIBLE);

                if (isGeminiNativeModel(activeModelId)) {
                    dispatchGeminiGeneration(prevUserMsg.getText(), prevUserMsg.getAttachments(), newBotMsg);
                } else {
                    dispatchPuterGeneration(prevUserMsg.getText(), prevUserMsg.getAttachments(), newBotMsg);
                }
            }
        }
    }

    private void quoteMessageIntoInput(ChatMessage message) {
        String quoted = "> " + message.getText().replace("\n", "\n> ") + "\n\n";
        etMessageInput.setText(quoted);
        etMessageInput.setSelection(etMessageInput.getText().length());
    }

    private void loadInitialSession() {
        String currentId = sessionManager.getCurrentSessionId();
        if (currentId != null) {
            currentSession = sessionManager.loadSession(currentId);
        }
        if (currentSession == null) {
            currentSession = sessionManager.createNewSession(activeModelId);
        }
        activeModelId = currentSession.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(currentSession.getMessages());
        updateAspectRatioBarVisibility();
    }

    private void createNewSession() {
        stopTts();
        currentSession = sessionManager.createNewSession(activeModelId);
        activeModelId = currentSession.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(currentSession.getMessages());
        updateAspectRatioBarVisibility();
        Toast.makeText(this, "New Session Started", Toast.LENGTH_SHORT).show();
    }

    private void loadSessionIntoUI(ChatSession session) {
        stopTts();
        sessionManager.setCurrentSessionId(session.getId());
        currentSession = session;
        activeModelId = session.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(session.getMessages());
        updateAspectRatioBarVisibility();
        rvChatTimeline.scrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));
    }

    private void loadModels() {
        isLiveMode = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        activeProvider = prefs.getString(AppConstants.KEY_ACTIVE_PROVIDER, "Puter");
        if (menuModelSourceToggle != null) {
            menuModelSourceToggle.setText(isLiveMode ? "Use Local Models" : "Enable Live Models");
        }

        modelCatalogManager.loadModels(isLiveMode, activeProvider, new ModelCatalogManager.ModelLoadCallback() {
            @Override
            public void onModelsReady() {
                setupAccordionAdapter();
                updateSelectedModelDisplayText();
                updateAspectRatioBarVisibility();
            }

            @Override
            public void onRequestLivePuterSync() {
                Toast.makeText(MainActivity.this, "Syncing live models...", Toast.LENGTH_SHORT).show();
                syncPuterTokenToHeadless(false);
                if (headlessAudioBridge != null) {
                    headlessAudioBridge.fetchPuterLiveModels();
                }
            }
        });
    }

    private void updateSelectedModelDisplayText() {
        AIModelDefinition m = modelCatalogManager.findModelById(activeModelId);
        if (m != null) {
            selectedModelText.setText(m.getDisplayName());
        } else {
            List<AIModelDefinition> list = modelCatalogManager.getAllModelsList();
            if (!list.isEmpty()) {
                activeModelId = list.get(0).getId();
                selectedModelText.setText(list.get(0).getDisplayName());
                if (currentSession != null) {
                    currentSession.setSelectedModelId(activeModelId);
                    sessionManager.saveSession(currentSession);
                }
            }
        }
    }

    private void setupAccordionAdapter() {
        modelAccordionAdapter = new ModelAccordionAdapter(model -> {
            activeModelId = model.getId();
            selectedModelText.setText(model.getDisplayName());
            if (accordionMenuCard != null) accordionMenuCard.setVisibility(View.GONE);
            if (ivTriggerChevron != null) ivTriggerChevron.setRotation(0);
            updateAspectRatioBarVisibility();
            if (currentSession != null) {
                currentSession.setSelectedModelId(activeModelId);
                sessionManager.saveSession(currentSession);
            }
            LiveLogManager.getInstance().info("PUTER_MODEL", "Model Switched by User",
                    "Name: " + model.getDisplayName() + " | ID: " + model.getId());
            Toast.makeText(MainActivity.this, "Selected: " + model.getDisplayName(), Toast.LENGTH_SHORT).show();
        });

        modelAccordionAdapter.setData(modelCatalogManager.buildAccordionItems(activeModelId), activeModelId);
        rvAccordionModels.setLayoutManager(new LinearLayoutManager(this));
        rvAccordionModels.setAdapter(modelAccordionAdapter);
    }

    private void updateAspectRatioBarVisibility() {
        if (aspectRatioBar == null) return;
        boolean isImage = modelCatalogManager.isImageGenerationModel(activeModelId);
        aspectRatioBar.setVisibility(isImage ? View.VISIBLE : View.GONE);
    }

    private void launchLiveVoiceAgent() {
        stopTts();
        if (isLiveVoiceConnected) {
            LiveLogManager.getInstance().info("GEMINI_LIVE", "User Terminated Live Session", "Closing active WebSocket session.");
            if (headlessAudioBridge != null) {
                headlessAudioBridge.stopCommentarySession();
            }
            if (currentLiveAssistantMsg != null) {
                currentLiveAssistantMsg.setStreaming(false);
                currentLiveAssistantMsg = null;
            }
            lastRenderedUserText = "";
            lastUserTextTimestamp = 0;
            isLiveVoiceConnected = false;
            toggleStarAnimation(false);
            addSystemMessage("🔴 Disconnected from Live Model");
            Toast.makeText(this, "Disconnected from Live Model", Toast.LENGTH_SHORT).show();
            if (previousProviderBeforeLive != null) {
                activeProvider = previousProviderBeforeLive;
                prefs.edit().putString(AppConstants.KEY_ACTIVE_PROVIDER, activeProvider).apply();
                previousProviderBeforeLive = null;
            }
            return;
        }

        String apiKey = prefs.getString(AppConstants.KEY_GEMINI_API_KEY, "");
        if (apiKey.trim().isEmpty()) {
            LiveLogManager.getInstance().warn("GEMINI_LIVE", "Live Agent Blocked", "Gemini API key is not configured.");
            new AlertDialog.Builder(this)
                    .setTitle("Gemini Live")
                    .setMessage("Please configure a valid Gemini Access API key first in Settings.")
                    .setPositiveButton("Open Settings", (d, w) -> dialogManager.showGeminiSettingsDialog(this::loadModels))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            LiveLogManager.getInstance().warn("GEMINI_LIVE", "Live Agent Blocked", "Microphone RECORD_AUDIO permission missing.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 202);
            return;
        }

        previousProviderBeforeLive = activeProvider;
        activeProvider = "Gemini";
        prefs.edit().putString(AppConstants.KEY_ACTIVE_PROVIDER, activeProvider).apply();

        String targetLiveModel = activeModelId;
        boolean alreadyLiveModel = false;

        String idLower = (activeModelId != null) ? activeModelId.toLowerCase(Locale.US) : "";
        if (idLower.contains("live") || idLower.contains("native-audio") || idLower.contains("native audio")) {
            alreadyLiveModel = true;
        }

        if (!alreadyLiveModel) {
            for (AIModelDefinition m : modelCatalogManager.getAllModelsList()) {
                String dName = (m.getDisplayName() != null) ? m.getDisplayName().toLowerCase(Locale.US) : "";
                String mId = (m.getId() != null) ? m.getId().toLowerCase(Locale.US) : "";
                if (dName.contains("3.1 flash live") || mId.contains("3.1-flash-live")) {
                    targetLiveModel = m.getId();
                    break;
                }
            }
            if (targetLiveModel == null || targetLiveModel.isEmpty()) {
                targetLiveModel = "gemini-3.1-flash-live-preview";
            }
        }

        activeModelId = targetLiveModel;
        updateSelectedModelDisplayText();
        if (currentSession != null) {
            currentSession.setSelectedModelId(activeModelId);
            sessionManager.saveSession(currentSession);
        }

        LiveLogManager.getInstance().process("GEMINI_LIVE", "Launching Live Session via Bridge",
                "Model: " + activeModelId + " | Key Length: " + apiKey.trim().length());
        Toast.makeText(this, "Connecting to Live Model: " + selectedModelText.getText(), Toast.LENGTH_SHORT).show();

        if (headlessAudioBridge != null) {
            headlessAudioBridge.startCommentarySession("auto", activeModelId);
        } else {
            LiveLogManager.getInstance().error("GEMINI_LIVE", "Bridge Inactive", "HeadlessAudioBridge instance is null.");
            Toast.makeText(this, "Live Audio Bridge not ready yet.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleStarAnimation(boolean start) {
        if (start) {
            orbitStar1.setVisibility(View.VISIBLE);
            orbitStar2.setVisibility(View.VISIBLE);
            orbitStar3.setVisibility(View.VISIBLE);
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_stars);
            orbitStar1.startAnimation(rotate);
            orbitStar2.startAnimation(rotate);
            orbitStar3.startAnimation(rotate);
        } else {
            orbitStar1.setVisibility(View.INVISIBLE);
            orbitStar2.setVisibility(View.INVISIBLE);
            orbitStar3.setVisibility(View.INVISIBLE);
            orbitStar1.clearAnimation();
            orbitStar2.clearAnimation();
            orbitStar3.clearAnimation();
        }
    }

    private void toggleChatStarAnimation(boolean start, TextView s1, TextView s2, TextView s3) {
        if (s1 == null || s2 == null || s3 == null) return;
        if (start) {
            s1.setVisibility(View.VISIBLE);
            s2.setVisibility(View.VISIBLE);
            s3.setVisibility(View.VISIBLE);
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_stars);
            s1.startAnimation(rotate);
            s2.startAnimation(rotate);
            s3.startAnimation(rotate);
        } else {
            s1.setVisibility(View.INVISIBLE);
            s2.setVisibility(View.INVISIBLE);
            s3.setVisibility(View.INVISIBLE);
            s1.clearAnimation();
            s2.clearAnimation();
            s3.clearAnimation();
        }
    }

    public void showUniversalContextMenu(ChatMessage message, float rawX, float rawY) {
        contextTargetMessage = message;
        if (customContextMenuCard == null) return;

        if (ctxCancelGen != null) ctxCancelGen.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
        if (ctxCopyChat != null) ctxCopyChat.setVisibility(View.VISIBLE);
        if (ctxReadAloud != null) ctxReadAloud.setVisibility(View.VISIBLE);
        if (ctxDeleteChat != null) ctxDeleteChat.setVisibility(View.VISIBLE);

        configureBaseContextMenuActions();
        positionAndDisplayContextMenu(rawX, rawY);
    }

    public void showEmptyCanvasContextMenu(float rawX, float rawY) {
        contextTargetMessage = null;
        if (customContextMenuCard == null) return;

        if (ctxCancelGen != null) ctxCancelGen.setVisibility(View.GONE);
        if (ctxCopyChat != null) ctxCopyChat.setVisibility(View.GONE);
        if (ctxReadAloud != null) ctxReadAloud.setVisibility(View.GONE);
        if (ctxDeleteChat != null) ctxDeleteChat.setVisibility(View.GONE);

        configureBaseContextMenuActions();
        positionAndDisplayContextMenu(rawX, rawY);
    }

    private void configureBaseContextMenuActions() {
        if (ctxToggleProvider != null) {
            ctxToggleProvider.setText("Gemini".equalsIgnoreCase(activeProvider) ? "☁ Puter Mode" : "🤖 Gemini Mode");
        }
        if (ctxToggleMode != null) {
            ctxToggleMode.setText(isAutoMode ? "⚡ Auto Mode" : "✋ Normal Mode");
        }
    }

    private void positionAndDisplayContextMenu(float rawX, float rawY) {
        customContextMenuCard.setVisibility(View.INVISIBLE);
        customContextMenuCard.post(() -> {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;

            int menuWidth = customContextMenuCard.getWidth();
            int menuHeight = customContextMenuCard.getHeight();
            if (menuWidth == 0) menuWidth = 440;
            if (menuHeight == 0) menuHeight = 560;

            float targetX = rawX;
            float targetY = rawY;

            if (targetX + menuWidth > screenWidth - 32) targetX = targetX - menuWidth;
            if (targetX < 32) targetX = 32;

            if (targetY + menuHeight > screenHeight - 120) targetY = targetY - menuHeight;
            if (targetY < 120) targetY = 120;

            customContextMenuCard.setX(targetX);
            customContextMenuCard.setY(targetY);
            customContextMenuCard.setVisibility(View.VISIBLE);
        });
    }

    private void toggleAuthMirror() {
        boolean loggedIn = AuthManager.getInstance(this).isLoggedIn();
        if (loggedIn) {
            LiveLogManager.getInstance().process("PUTER_AUTH", "User Triggered Sign Out", "Purging credentials, cookies, and session cache.");
            AuthManager.getInstance(this).logout();
            lastInjectedPuterToken = null;

            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setCookie("https://puter.com", "token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                cm.setCookie("https://api.puter.com", "token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                cm.setCookie("https://puter.com", "puter_auth=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                cm.flush();
            } catch (Exception ignored) {}

            refreshDropdownUI();
            Toast.makeText(this, "Signed Out", Toast.LENGTH_SHORT).show();
            if (headlessAudioWebView != null) {
                headlessAudioWebView.evaluateJavascript("try { localStorage.removeItem('puter.auth.token'); } catch(e){}; if (typeof puter !== 'undefined') { puter.authToken = null; if (puter.auth) puter.auth.signOut(); }", null);
            }
            LiveLogManager.getInstance().ok("PUTER_AUTH", "Sign Out Complete", "Puter token and cookies cleared.");
        } else {
            LiveLogManager.getInstance().process("PUTER_AUTH", "Sign In Selected", "Launching OAuth Popup window.");
            dialogManager.openPuterAuthDialog();
        }
    }

    private void onDialogManagerAuthSuccess() {
        refreshDropdownUI();
        syncPuterTokenToHeadless(true);
        if (isLiveMode) loadModels();
    }

    private void toggleModelSource() {
        boolean curr = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        boolean next = !curr;
        prefs.edit().putBoolean(AppConstants.KEY_USE_LIVE_MODELS, next).apply();
        loadModels();
        Toast.makeText(this, next ? "Live Models Enabled" : "Using Local Models", Toast.LENGTH_SHORT).show();
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Puter Chat", text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null && text != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, AppConstants.TTS_UTTERANCE_ID);
        }
    }

    private void startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 202);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Puter...");

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show(); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {}
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoke = matches.get(0);
                    etMessageInput.setText(spoke);
                    etMessageInput.setSelection(spoke.length());
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void updateUsageStats() {
        long currentTotal = prefs.getLong(AppConstants.KEY_USAGE_TOTAL_TOKENS, 0);
        prefs.edit().putLong(AppConstants.KEY_USAGE_TOTAL_TOKENS, currentTotal + 150).apply();
    }

    private void checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), AppConstants.REQUEST_CODE_PERMISSIONS);
        }
    }

    public void handleUrlChange(String url) {
        Log.d(TAG, "handleUrlChange: " + url);
        runOnUiThread(this::refreshDropdownUI);
    }

    public void reloadWebView() {
        runOnUiThread(this::refreshDropdownUI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTts();
        if (textToSpeech != null) textToSpeech.shutdown();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (geminiService != null) geminiService.shutdown();
        if (headlessAudioBridge != null) {
            headlessAudioBridge.stopCommentarySession();
            headlessAudioBridge.stopPuterChat();
        }
        if (dialogManager != null) dialogManager.dismissAll();
    }
}
