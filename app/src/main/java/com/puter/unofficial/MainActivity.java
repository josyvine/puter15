package com.puter.unofficial;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.puter.unofficial.adapters.ChatAdapter;
import com.puter.unofficial.adapters.LogConsoleAdapter;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 100% Pure Native Android Java Activity for Puter Unofficial.
 * Includes floating draggable system log console for deep telemetry interception,
 * high-speed parallel 20-URL batch context extraction, and live speech bubbles.
 */
public class MainActivity extends AppCompatActivity implements LiveLogManager.LogEventListener {

    private static final String TAG = "MainActivity";
    private static final int URL_BATCH_SIZE = 20;
    private static final int MAX_CHARS_PER_URL = 40000;

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

    // Floating Diagnostic Log Console
    private View floatingLogView;
    private TextView tvFloatingLogBadge;
    private float dX, dY;
    private float initialTouchX, initialTouchY;

    // Headless WebView & Live Voice Overlay HUD
    private WebView headlessAudioWebView;
    private TextView tvLiveTranscriptTicker;
    private TextView tvLiveVoiceStatus;

    // Dialog reference for Puter Authentication
    private Dialog puterAuthDialog;
    private boolean isPuterAuthProcessing = false;
    private String lastInjectedPuterToken = null;

    // Adapters & Managers
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
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;
    private final List<AIModelDefinition> allModelsList = new ArrayList<>();
    private final Map<String, List<AIModelDefinition>> modelsByProvider = new HashMap<>();

    // Live Turn Deduplication Guards
    private String lastRenderedUserText = "";
    private long lastUserTextTimestamp = 0;

    // High-speed parallel connection pool for URL scraping
    private OkHttpClient fastScraperClient;

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

        initFastScraperClient();
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

    private void initFastScraperClient() {
        fastScraperClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(30, 5, TimeUnit.MINUTES))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();
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

    // --- FLOATING DIAGNOSTIC LOG CONSOLE SYSTEM ---

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
                        openSystemLogConsole();
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

    private void openSystemLogConsole() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_system_log_console, null);

        ImageView btnMinimize = sheet.findViewById(R.id.btnMinimizeConsole);
        EditText etSearch = sheet.findViewById(R.id.etLogSearch);
        ImageView btnClearSearch = sheet.findViewById(R.id.btnClearSearch);
        RecyclerView rvConsole = sheet.findViewById(R.id.rvLogConsole);
        TextView tvSummary = sheet.findViewById(R.id.tvLogCountSummary);
        TextView btnClearAll = sheet.findViewById(R.id.btnClearAllLogs);
        LinearLayout llEmpty = sheet.findViewById(R.id.llEmptyLogsState);

        TextView chipAll = sheet.findViewById(R.id.chipFilterAll);
        TextView chipErrors = sheet.findViewById(R.id.chipFilterErrors);
        TextView chipCrashes = sheet.findViewById(R.id.chipFilterCrashes);
        TextView chipProcess = sheet.findViewById(R.id.chipFilterProcess);
        TextView chipPuter = sheet.findViewById(R.id.chipFilterPuter);
        TextView chipGemini = sheet.findViewById(R.id.chipFilterGemini);
        TextView chipAuth = sheet.findViewById(R.id.chipFilterAuth);

        TextView[] allChips = {chipAll, chipErrors, chipCrashes, chipProcess, chipPuter, chipGemini, chipAuth};

        LogConsoleAdapter consoleAdapter = new LogConsoleAdapter();
        rvConsole.setLayoutManager(new LinearLayoutManager(this));
        rvConsole.setAdapter(consoleAdapter);

        Runnable refreshUI = () -> {
            int displayed = consoleAdapter.getItemCount();
            int total = consoleAdapter.getMasterCount();
            tvSummary.setText("Showing " + displayed + " of " + total + " total logs");
            llEmpty.setVisibility(displayed == 0 ? View.VISIBLE : View.GONE);
        };

        LiveLogManager logger = LiveLogManager.getInstance(this);
        consoleAdapter.setLogs(logger.getAllLogs());
        refreshUI.run();

        LiveLogManager.LogEventListener dialogListener = new LiveLogManager.LogEventListener() {
            @Override
            public void onNewLogEntry(LogEntry entry, int totalCount, int errorCount) {
                runOnUiThread(() -> {
                    consoleAdapter.addEntry(entry);
                    refreshUI.run();
                });
            }
            @Override
            public void onLogsCleared() {
                runOnUiThread(() -> {
                    consoleAdapter.clearLogs();
                    refreshUI.run();
                });
            }
        };
        logger.addListener(dialogListener);
        dialog.setOnDismissListener(d -> logger.removeListener(dialogListener));

        btnMinimize.setOnClickListener(v -> dialog.dismiss());
        btnClearAll.setOnClickListener(v -> logger.clearLogs());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                consoleAdapter.setFilterQuery(s.toString());
                refreshUI.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        View.OnClickListener chipClickListener = v -> {
            for (TextView chip : allChips) {
                chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#181C2B")));
                chip.setTextColor(Color.parseColor("#8C95AB"));
            }
            v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7C4DFF")));
            ((TextView)v).setTextColor(Color.WHITE);

            String cat = ((TextView)v).getText().toString();
            consoleAdapter.setCategoryFilter(cat);
            refreshUI.run();
        };

        for (TextView chip : allChips) {
            chip.setOnClickListener(chipClickListener);
        }

        dialog.setContentView(sheet);
        dialog.show();
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
                onLiveSessionConnected();
            }

            @Override
            public void onTranscriptReceived(String text) {
                onLiveTranscriptReceived(text);
            }

            @Override
            public void onUserTranscriptReceived(String text) {
                onLiveUserTranscript(text);
            }

            @Override
            public void onModelTranscriptReceived(String text, boolean isFinished) {
                onLiveModelTranscript(text, isFinished);
            }

            @Override
            public void onAudioChunkReceived(String base64Pcm) {
            }

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
                onLiveError(errorMessage);
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
            if (!force && clean.equals(lastInjectedPuterToken)) {
                return;
            }
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

    // --- PUBLIC METHODS CALLED BY WebAppInterface FOR PUTER.JS CHAT & MODELS ---

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
            parsePuterModelsJson(rawJson);
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

    // --- GEMINI LIVE CHAT BUBBLE STREAMING & CONNECTION ---

    public void onLiveSessionConnected() {
        runOnUiThread(() -> {
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
            if (text == null || text.trim().isEmpty()) {
                LiveLogManager.getInstance().warn("LIVE_TRANSCRIPT", "Dropped Empty User Speech", "Received empty or null text from JS bridge.");
                return;
            }
            String cleanText = text.trim();
            long now = System.currentTimeMillis();

            if (cleanText.equalsIgnoreCase(lastRenderedUserText) && (now - lastUserTextTimestamp) < 2500) {
                LiveLogManager.getInstance().info("LIVE_TRANSCRIPT", "Duplicate User Speech Suppressed",
                        "Suppressed duplicate speech turn: \"" + cleanText + "\"");
                return;
            }

            lastRenderedUserText = cleanText;
            lastUserTextTimestamp = now;

            if (chatAdapter == null || currentSession == null) {
                LiveLogManager.getInstance().error("LIVE_TRANSCRIPT", "UI State Error", "Cannot post user bubble: chatAdapter or currentSession is null.");
                return;
            }

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
                if (chatAdapter == null || currentSession == null) {
                    LiveLogManager.getInstance().error("LIVE_TRANSCRIPT", "UI State Error", "Cannot post model bubble: chatAdapter or currentSession is null.");
                    return;
                }

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
                            LiveLogManager.getInstance().ok("LIVE_TRANSCRIPT", "Model Bubble Updated", clean);
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
                        LiveLogManager.getInstance().info("LIVE_TRANSCRIPT", "Model Bubble Finalized", "Turn completed committed to session.");
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

    public void onLiveSessionFinished() {
        onLiveSessionClosedWithTelemetry(1000, "Clean termination", true);
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
        if (found != null) {
            textToSpeech.setLanguage(found);
        } else {
            textToSpeech.setLanguage(Locale.US);
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
        try {
            String name = FileUtils.getFileName(this, uri);
            String mime = FileUtils.getMimeType(this, uri);
            String dataUri = FileUtils.fileToDataUri(this, uri);
            Bitmap thumb = null;
            if (mime != null && mime.startsWith("image/")) {
                thumb = ImageUtils.getThumbnail(this, uri, 120);
            }
            StagedFile file = new StagedFile(uri, name, mime, 0, dataUri, thumb);
            stagedAttachmentsAdapter.addAttachment(file);
            rvStagedAttachments.setVisibility(View.VISIBLE);
            updateSendButtonState();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to attach: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
                showCodePreviewFullscreen(code);
            }

            @Override
            public void onPreviewImage(String imageUrlOrBase64) {
                showImagePreviewViewer(imageUrlOrBase64);
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
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

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
                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(onFinished);
                }
                @Override
                public void onError(String utteranceId) {
                    runOnUiThread(onFinished);
                }
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
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAttach.setOnClickListener(v -> showAttachmentPickerDialog());

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
            showHistoryDialog();
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
            showHelpDialog();
        });

        menuSettingsHub.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showSettingsHubDialog();
        });

        menuBrowseWebsite.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showBrowserDialog();
        });

        menuTradersZone.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showCryptoBotDialog();
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

        String targetLang = prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, "");
        String activeSystemMessage = prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, "");
        if (!targetLang.isEmpty()) {
            String systemDirective = "IMPORTANT:\nALWAYS respond ONLY in " + targetLang + ".\n\nNEVER use English.\n\nEven if user speaks English, translate internally and answer only in " + targetLang + ". Do not transliterate. Write strictly in the native script characters of the language.";
            activeSystemMessage = activeSystemMessage.isEmpty() ? systemDirective : activeSystemMessage + "\n\n" + systemDirective;
        }
        currentSession.getConfig().setSystemInstructions(activeSystemMessage);

        boolean isImageGen = isImageGenerationModel(activeModelId);
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

        // --- HIGH-SPEED MULTI-BATCH CONTEXT URL PROCESSING ---
        if (urlContextEnabled) {
            List<String> promptUrls = extractAndSanitizeUrls(finalPrompt);
            List<String> attachmentUrls = extractUrlsFromAttachments(attachments);

            Set<String> allUniqueUrls = new LinkedHashSet<>();
            allUniqueUrls.addAll(promptUrls);
            allUniqueUrls.addAll(attachmentUrls);

            List<String> urlList = new ArrayList<>(allUniqueUrls);

            if (!urlList.isEmpty()) {
                final String basePrompt = finalPrompt;
                new Thread(() -> {
                    String enrichedPrompt = executeHighSpeedUrlBatchProcessing(basePrompt, urlList);
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

    /**
     * Extracts and sanitizes URLs: strips bullets, whitespace, trailing punctuation.
     */
    private List<String> extractAndSanitizeUrls(String text) {
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
     * Inspects staged text attachments (.txt, .json, .csv, .md) and extracts all embedded URLs.
     */
    private List<String> extractUrlsFromAttachments(List<StagedFile> attachments) {
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
     * Executes parallel 20-URL batch scraping, live ECG loader progress, and compilation.
     */
    private String executeHighSpeedUrlBatchProcessing(String basePrompt, List<String> urls) {
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

            runOnUiThread(() -> {
                if (tvEcgStatus != null) {
                    tvEcgStatus.setText("Analyzing Batch " + currentBatchNum + " of " + totalBatches +
                                        " (URLs " + (start + 1) + "–" + end + ")...");
                }
            });

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

        runOnUiThread(() -> {
            if (tvEcgStatus != null) {
                tvEcgStatus.setText("Compiling Complete A-to-Z Repository Report...");
            }
        });

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

    private boolean isGeminiNativeModel(String modelId) {
        if (modelId == null) return false;
        String lower = modelId.toLowerCase(Locale.US);
        if (lower.contains("openrouter") || lower.contains("togetherai") || lower.contains("puter") || lower.contains(":")) {
            return false;
        }
        return "Gemini".equalsIgnoreCase(activeProvider);
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
                    JSONObject imgBlock = new JSONObject();
                    imgBlock.put("type", "image_url");
                    JSONObject urlObj = new JSONObject();
                    urlObj.put("url", file.getBase64DataUri());
                    imgBlock.put("image_url", urlObj);
                    contentBlocks.put(imgBlock);
                }
                current.put("content", contentBlocks);
            } else {
                current.put("content", prompt);
            }
            messagesArray.put(current);

        } catch (Exception ignored) {}

        String ratio = currentSession.getConfig().getAspectRatio();
        String sysInst = currentSession.getConfig().getSystemInstructions();
        boolean isImageGen = isImageGenerationModel(activeModelId);

        if (headlessAudioBridge != null) {
            LiveLogManager.getInstance().info("PUTER_DISPATCH", "Handing to Headless Bridge",
                    "Dispatching to window.sendPuterChat (Stream: " + !isImageGen + ")");
            headlessAudioBridge.sendPuterChat(messagesArray.toString(), activeModelId, !isImageGen, sysInst, ratio);
        } else {
            LiveLogManager.getInstance().error("PUTER_DISPATCH", "Bridge Missing", "HeadlessAudioBridge instance is null");
            onPuterGenerationError("Headless worker bridge not available");
        }
    }

    /**
     * Dispatches multi-turn conversations to Gemini API with full historical chat memory serialization,
     * system instructions, and inline media context.
     */
    private void dispatchGeminiGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        currentActiveAssistantMsg = assistantMsg;
        JSONObject payload = new JSONObject();
        try {
            JSONArray contents = new JSONArray();

            // 1. Serialize entire prior conversation history of this session (excluding current pending turn)
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
                            if (file != null && file.getBase64DataUri() != null && file.getMimeType() != null && file.getMimeType().startsWith("image/")) {
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

                    // Gemini strictly requires the first turn to have role: "user"
                    if (contents.length() == 0 && !"user".equals(role)) {
                        continue;
                    }

                    // Merge consecutive turns with the same role to prevent Gemini HTTP 400 Bad Request
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

            // 2. Append the current user prompt & staged attachments
            JSONArray currentParts = new JSONArray();
            if (prompt != null && !prompt.trim().isEmpty()) {
                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);
                currentParts.put(textPart);
            }

            if (attachments != null) {
                for (StagedFile file : attachments) {
                    if (file != null && file.getBase64DataUri() != null && file.getMimeType() != null && file.getMimeType().startsWith("image/")) {
                        String cleanB64 = file.getBase64DataUri().replaceFirst("^data:[^;]+;base64,", "");
                        JSONObject inlineData = new JSONObject();
                        inlineData.put("mimeType", file.getMimeType());
                        inlineData.put("data", cleanB64);
                        JSONObject partObj = new JSONObject();
                        partObj.put("inlineData", inlineData);
                        currentParts.put(partObj);
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

            // 3. System Instructions (Custom Instructions & reply languages)
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

            // 4. Web Grounding & Official URL Context Tool Declaration
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
        currentSession = sessionManager.createNewSession(activeModelId);
        activeModelId = currentSession.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(currentSession.getMessages());
        updateAspectRatioBarVisibility();
        Toast.makeText(this, "New Session Started", Toast.LENGTH_SHORT).show();
    }

    private void loadSessionIntoUI(ChatSession session) {
        sessionManager.setCurrentSessionId(session.getId());
        currentSession = session;
        activeModelId = session.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(session.getMessages());
        updateAspectRatioBarVisibility();
        rvChatTimeline.scrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));
    }

    // --- MODEL ACCORDION & TAXONOMY ---

    private void loadModels() {
        allModelsList.clear();
        modelsByProvider.clear();

        isLiveMode = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        activeProvider = prefs.getString(AppConstants.KEY_ACTIVE_PROVIDER, "Puter");
        if (menuModelSourceToggle != null) {
            menuModelSourceToggle.setText(isLiveMode ? "Use Local Models" : "Enable Live Models");
        }

        if ("Gemini".equalsIgnoreCase(activeProvider)) {
            if (isLiveMode) {
                String cached = prefs.getString(AppConstants.KEY_GEMINI_CACHED_LIVE_MODELS, null);
                if (cached != null && !cached.trim().isEmpty() && !cached.equals("{}") && !cached.equals("[]")) {
                    parseGeminiModelsJson(cached);
                    setupAccordionAdapter();
                } else {
                    loadLocalGeminiModels();
                    setupAccordionAdapter();
                }
            } else {
                loadLocalGeminiModels();
                setupAccordionAdapter();
            }
        } else {
            if (isLiveMode) {
                String cached = prefs.getString(AppConstants.KEY_PUTER_CACHED_LIVE_MODELS, null);
                if (cached != null && !cached.trim().isEmpty() && !cached.equals("{}") && !cached.equals("[]")) {
                    parsePuterModelsJson(cached);
                    setupAccordionAdapter();
                }

                if (AuthManager.getInstance(this).isLoggedIn()) {
                    Toast.makeText(this, "Syncing live models...", Toast.LENGTH_SHORT).show();
                    syncPuterTokenToHeadless(false);
                    if (headlessAudioBridge != null) {
                        headlessAudioBridge.fetchPuterLiveModels();
                    }
                } else {
                    if (allModelsList.isEmpty()) {
                        loadLocalPuterModels();
                        setupAccordionAdapter();
                    }
                }
            } else {
                loadLocalPuterModels();
                setupAccordionAdapter();
            }
        }

        updateSelectedModelDisplayText();
        updateAspectRatioBarVisibility();
    }

    private void updateSelectedModelDisplayText() {
        boolean found = false;
        for (AIModelDefinition m : allModelsList) {
            if (m.getId().equalsIgnoreCase(activeModelId)) {
                found = true;
                selectedModelText.setText(m.getDisplayName());
                break;
            }
        }
        if (!found && !allModelsList.isEmpty()) {
            activeModelId = allModelsList.get(0).getId();
            selectedModelText.setText(allModelsList.get(0).getDisplayName());
            if (currentSession != null) {
                currentSession.setSelectedModelId(activeModelId);
                sessionManager.saveSession(currentSession);
            }
        }
    }

    private void loadLocalGeminiModels() {
        try {
            InputStream is = getAssets().open(AppConstants.GEMINI_MODELS_JSON_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            parseGeminiModelsJson(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load gemini_models.json: " + e.getMessage());
        }
    }

    private void loadLocalPuterModels() {
        try {
            InputStream is = getAssets().open(AppConstants.MODELS_JSON_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            parsePuterModelsJson(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load models.json: " + e.getMessage());
        }
    }

    private void parseGeminiModelsJson(String jsonStr) {
        try {
            allModelsList.clear();
            modelsByProvider.clear();

            if (jsonStr.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    addGeminiModelNode(arr.getJSONObject(i), "Google Gemini");
                }
                return;
            }

            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String sectionKey = keys.next();
                JSONArray arr = root.optJSONArray(sectionKey);
                if (arr != null) {
                    String parentName = formatParentName(sectionKey);
                    if (!modelsByProvider.containsKey(parentName)) {
                        modelsByProvider.put(parentName, new ArrayList<>());
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        addGeminiModelNode(obj, parentName);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Gemini models: " + e.getMessage());
        }
    }

    private void addGeminiModelNode(JSONObject obj, String parentGroup) {
        String rawId = obj.optString("id", obj.optString("name", "")).replace("models/", "");
        if (rawId.isEmpty()) return;
        String name = obj.optString("name", obj.optString("displayName", rawId));
        int ctxTokens = obj.optInt("context", 1000000);
        String ctxStr = (ctxTokens >= 1000) ? (ctxTokens / 1000) + "k" : String.valueOf(ctxTokens);

        List<String> cats = new ArrayList<>();
        JSONArray catArr = obj.optJSONArray("categories");
        if (catArr != null) {
            for (int c = 0; c < catArr.length(); c++) {
                cats.add(catArr.optString(c).toLowerCase(Locale.US));
            }
        } else {
            String desc = obj.optString("description", "").toLowerCase(Locale.US);
            String idLower = rawId.toLowerCase(Locale.US);
            String nameLower = name.toLowerCase(Locale.US);
            if (nameLower.contains("live preview") || idLower.contains("live-preview") || nameLower.contains("native audio") || idLower.contains("native-audio")) {
                cats.add("live");
                cats.add("audio");
            } else if (idLower.contains("audio") || idLower.contains("tts") || idLower.contains("speech")) {
                cats.add("audio");
            }
            if (idLower.contains("image") || idLower.contains("banana") || idLower.contains("imagen")) {
                cats.add("image_generation");
            }
            if (idLower.contains("flash") || idLower.contains("pro") || desc.contains("vision")) {
                cats.add("vision");
                cats.add("multimodal");
            }
            if (cats.isEmpty()) cats.add("text_chat");
        }

        boolean isImg = cats.contains("image_generation");
        boolean isVis = cats.contains("vision") || cats.contains("multimodal");
        boolean isLiv = cats.contains("live");

        AIModelDefinition model = new AIModelDefinition(rawId, name, parentGroup, ctxStr, isVis, isImg, isLiv);
        model.setCategories(cats);
        allModelsList.add(model);
        if (!modelsByProvider.containsKey(parentGroup)) {
            modelsByProvider.put(parentGroup, new ArrayList<>());
        }
        modelsByProvider.get(parentGroup).add(model);
    }

    private void parsePuterModelsJson(String jsonStr) {
        try {
            allModelsList.clear();
            modelsByProvider.clear();

            if (jsonStr.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            JSONObject root = new JSONObject(jsonStr);
            if (root.has("result") && root.optJSONArray("result") != null) {
                JSONArray arr = root.getJSONArray("result");
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            if (root.has("models") && root.optJSONArray("models") != null) {
                JSONArray arr = root.getJSONArray("models");
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String providerKey = keys.next();
                JSONArray arr = root.optJSONArray(providerKey);
                if (arr != null) {
                    String providerDisplay = capitalize(providerKey);
                    if (!modelsByProvider.containsKey(providerDisplay)) {
                        modelsByProvider.put(providerDisplay, new ArrayList<>());
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        addPuterModel(arr.getJSONObject(i), providerDisplay);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Puter models: " + e.getMessage());
        }
    }

    private void addPuterModel(JSONObject obj, String defaultProvider) {
        String id = obj.optString("id", obj.optString("name", ""));
        if (id.isEmpty()) return;
        String name = obj.optString("name", id);
        String provider = capitalize(obj.optString("provider", obj.optString("vendor", defaultProvider)));
        if (!modelsByProvider.containsKey(provider)) {
            modelsByProvider.put(provider, new ArrayList<>());
        }
        int ctx = obj.optInt("context", 128000);
        String ctxStr = (ctx >= 1000) ? (ctx / 1000) + "k" : String.valueOf(ctx);

        List<String> cats = new ArrayList<>();
        JSONArray catArr = obj.optJSONArray("categories");
        if (catArr != null && catArr.length() > 0) {
            for (int c = 0; c < catArr.length(); c++) {
                cats.add(catArr.optString(c).toLowerCase(Locale.US));
            }
        } else {
            cats = inferPuterCategories(id, name, obj.optString("description", ""));
        }

        boolean imgGen = cats.contains("image_generation");
        boolean vision = cats.contains("vision") || cats.contains("multimodal");

        AIModelDefinition m = new AIModelDefinition(id, name, provider, ctxStr, vision, imgGen, false);
        m.setCategories(cats);
        allModelsList.add(m);
        modelsByProvider.get(provider).add(m);
    }

    private List<String> inferPuterCategories(String id, String name, String desc) {
        String combined = (id + " " + name + " " + desc).toLowerCase(Locale.US);
        List<String> cats = new ArrayList<>();
        if (combined.contains("image") || combined.contains("dall-e") || combined.contains("flux") ||
                combined.contains("stable-diffusion") || combined.contains("imagen") || combined.contains("midjourney") ||
                combined.contains("txt2img") || combined.contains("banana")) {
            cats.add("image_generation");
        }
        if (combined.contains("vision") || combined.contains("vl") || combined.contains("4o") ||
                combined.contains("gemini") || combined.contains("multimodal") || combined.contains("omni")) {
            cats.add("vision");
            cats.add("multimodal");
        }
        if (combined.contains("coder") || combined.contains("code") || combined.contains("deepseek") ||
                combined.contains("starcoder") || combined.contains("qwen") || combined.contains("wizardcoder")) {
            cats.add("code");
        }
        if (combined.contains("reasoning") || combined.contains("thinking") || combined.contains("r1") ||
                combined.contains("o1") || combined.contains("o3")) {
            cats.add("reasoning");
        }
        if (combined.contains("live") || combined.contains("audio") || combined.contains("voice") || combined.contains("tts")) {
            cats.add("audio");
            cats.add("live");
        }
        if (cats.isEmpty() || !cats.contains("image_generation")) {
            cats.add("text_chat");
        }
        return cats;
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

        List<ModelAccordionAdapter.AccordionItem> items = new ArrayList<>();

        List<AIModelDefinition> sortedAll = new ArrayList<>(allModelsList);
        Collections.sort(sortedAll, (a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        items.add(ModelAccordionAdapter.AccordionItem.createHeader("★ All Models", sortedAll.size()));
        for (AIModelDefinition m : sortedAll) {
            items.add(ModelAccordionAdapter.AccordionItem.createChild(m, "★ All Models"));
        }

        for (Map.Entry<String, List<AIModelDefinition>> entry : modelsByProvider.entrySet()) {
            String pName = "Parent: " + entry.getKey();
            items.add(ModelAccordionAdapter.AccordionItem.createHeader(pName, entry.getValue().size()));
            for (AIModelDefinition m : entry.getValue()) {
                items.add(ModelAccordionAdapter.AccordionItem.createChild(m, pName));
            }
        }

        Map<String, List<AIModelDefinition>> byCategory = new HashMap<>();
        for (AIModelDefinition m : allModelsList) {
            List<String> cats = m.getCategories();
            if (cats == null || cats.isEmpty()) cats = m.getCapabilityTags();
            for (String cat : cats) {
                String formatted = "Category: " + formatCategoryName(cat);
                if (!byCategory.containsKey(formatted)) {
                    byCategory.put(formatted, new ArrayList<>());
                }
                byCategory.get(formatted).add(m);
            }
        }
        for (Map.Entry<String, List<AIModelDefinition>> entry : byCategory.entrySet()) {
            items.add(ModelAccordionAdapter.AccordionItem.createHeader(entry.getKey(), entry.getValue().size()));
            for (AIModelDefinition m : entry.getValue()) {
                items.add(ModelAccordionAdapter.AccordionItem.createChild(m, entry.getKey()));
            }
        }

        modelAccordionAdapter.setData(items, activeModelId);
        rvAccordionModels.setLayoutManager(new LinearLayoutManager(this));
        rvAccordionModels.setAdapter(modelAccordionAdapter);
    }

    private String formatCategoryName(String cat) {
        if (cat == null) return "General";
        return cat.replace("_", " ").toUpperCase(Locale.US);
    }

    private String formatParentName(String raw) {
        if (raw == null || raw.isEmpty()) return "Other";
        return raw.replace("_", " ").toUpperCase(Locale.US);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase(Locale.US) + str.substring(1);
    }

    private boolean isImageGenerationModel(String modelId) {
        if (modelId == null) return false;
        String mLower = modelId.toLowerCase(Locale.US);
        if (mLower.contains("banana") || mLower.contains("dall-e") || mLower.contains("flux") ||
            mLower.contains("imagen") || mLower.contains("midjourney") || mLower.contains("txt2img")) {
            return true;
        }
        for (AIModelDefinition def : allModelsList) {
            if (def.getId().equalsIgnoreCase(modelId)) {
                List<String> cats = def.getCategories();
                if (cats != null && (cats.contains("image_generation") || cats.contains("image"))) {
                    return true;
                }
            }
        }
        return PuterChatManager.isImageModel(modelId);
    }

    private void updateAspectRatioBarVisibility() {
        if (aspectRatioBar == null) return;
        boolean isImage = isImageGenerationModel(activeModelId);
        aspectRatioBar.setVisibility(isImage ? View.VISIBLE : View.GONE);
    }

    // --- LIVE VOICE AGENT ---

    private void launchLiveVoiceAgent() {
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
                    .setPositiveButton("Open Settings", (d, w) -> showGeminiSettingsDialog())
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
            for (AIModelDefinition m : allModelsList) {
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

    // --- DIALOGS & OVERFLOWS ---

    private void showHistoryDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Chat History");
        title.setTextSize(18);
        title.setTextColor(Color.parseColor("#333333"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        List<ChatSession> sessions = sessionManager.getAllSessions();

        SessionHistoryAdapter adapter = new SessionHistoryAdapter(sessions, session -> {
            loadSessionIntoUI(session);
            dialog.dismiss();
        }, session -> {
            sessionManager.deleteSession(session.getId());
            if (currentSession != null && currentSession.getId().equals(session.getId())) {
                loadInitialSession();
            }
            dialog.dismiss();
        });
        rv.setAdapter(adapter);
        root.addView(rv);

        dialog.setContentView(root);
        dialog.show();
    }

    private void showSettingsHubDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_settings_hub, null);

        sheet.findViewById(R.id.btnHubCustomInstruction).setOnClickListener(v -> {
            dialog.dismiss();
            showCustomInstructionDialog();
        });

        sheet.findViewById(R.id.btnHubGeminiSettings).setOnClickListener(v -> {
            dialog.dismiss();
            showGeminiSettingsDialog();
        });

        sheet.findViewById(R.id.btnHubNostrIdentity).setOnClickListener(v -> {
            dialog.dismiss();
            showNostrIdentityDialog();
        });

        sheet.findViewById(R.id.btnHubClose).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showCustomInstructionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_custom_instruction, null);
        EditText et = sheet.findViewById(R.id.etCustomInstructionInput);
        et.setText(prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, ""));

        sheet.findViewById(R.id.btnSaveCustom).setOnClickListener(v -> {
            String val = et.getText().toString().trim();
            prefs.edit().putString(AppConstants.KEY_CUSTOM_INSTRUCTION, val).apply();
            if (currentSession != null) {
                currentSession.getConfig().setSystemInstructions(val);
                sessionManager.saveSession(currentSession);
            }
            Toast.makeText(this, "Custom Instruction saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        sheet.findViewById(R.id.btnCancelCustom).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showGeminiSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_gemini_settings, null);

        EditText etKey = sheet.findViewById(R.id.etGeminiApiKey);
        TextView tvStatus = sheet.findViewById(R.id.tvGeminiFetchStatus);
        TextView tvGrounding = sheet.findViewById(R.id.tvGroundingMode);
        TextView tvVoice = sheet.findViewById(R.id.tvVoiceCharacter);

        etKey.setText(prefs.getString(AppConstants.KEY_GEMINI_API_KEY, ""));

        String[] groundingModes = new String[]{"None (Standard)", "Google Search", "Google Maps"};
        String currentGrounding = prefs.getString("gemini_grounding_mode", "None (Standard)");
        if (tvGrounding != null) {
            tvGrounding.setText(currentGrounding);
            tvGrounding.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Select Grounding Mode")
                        .setItems(groundingModes, (d, which) -> {
                            String selected = groundingModes[which];
                            prefs.edit().putString("gemini_grounding_mode", selected).apply();
                            tvGrounding.setText(selected);
                        })
                        .show();
            });
        }

        String[] voiceCharacters = new String[]{
                "Aoede (Breezy)", "Charon (Informative)", "Kore (Firm)",
                "Puck (Upbeat)", "Fenrir (Excitable)", "Leda (Youthful)", "Zephyr (Bright)"
        };
        String currentVoice = prefs.getString("gemini_voice_character", "Leda (Youthful)");
        if (tvVoice != null) {
            tvVoice.setText(currentVoice);
            tvVoice.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Select Voice Character")
                        .setItems(voiceCharacters, (d, which) -> {
                            String selected = voiceCharacters[which];
                            prefs.edit().putString("gemini_voice_character", selected).apply();
                            tvVoice.setText(selected);
                        })
                        .show();
            });
        }

        sheet.findViewById(R.id.btnFetchGeminiModels).setOnClickListener(v -> {
            String key = etKey.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter API Key first", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(AppConstants.KEY_GEMINI_API_KEY, key).apply();
            tvStatus.setText("Fetching models from Google...");
            geminiService.fetchAvailableModels(key, new GeminiService.GeminiModelsListener() {
                @Override
                public void onModelsFetched(String json) {
                    runOnUiThread(() -> {
                        prefs.edit().putString(AppConstants.KEY_GEMINI_CACHED_LIVE_MODELS, json).apply();
                        loadModels();
                        tvStatus.setText("Models fetched successfully!");
                        Toast.makeText(MainActivity.this, "Live Gemini models updated!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Fetch error: " + errorMessage);
                        Toast.makeText(MainActivity.this, "Fetch error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        sheet.findViewById(R.id.btnGeminiSettingsDone).setOnClickListener(v -> {
            prefs.edit().putString(AppConstants.KEY_GEMINI_API_KEY, etKey.getText().toString().trim()).apply();
            Toast.makeText(this, "Gemini settings saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        sheet.findViewById(R.id.btnGeminiSettingsBack).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showNostrIdentityDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_nostr_identity, null);

        TextView tvNpub = sheet.findViewById(R.id.tvNostrNpub);
        TextView tvNsec = sheet.findViewById(R.id.tvNostrNsec);

        String npub = prefs.getString(AppConstants.KEY_NOSTR_PUBLIC_KEY, null);
        String nsec = prefs.getString(AppConstants.KEY_NOSTR_PRIVATE_KEY, null);

        if (npub == null || nsec == null) {
            String[] pair = NostrKeyManager.generateKeyPair();
            nsec = pair[0];
            npub = pair[1];
            prefs.edit().putString(AppConstants.KEY_NOSTR_PRIVATE_KEY, nsec)
                    .putString(AppConstants.KEY_NOSTR_PUBLIC_KEY, npub).apply();
        }

        final String finalNpub = npub;
        final String finalNsec = nsec;
        tvNpub.setText(finalNpub);
        tvNsec.setText(finalNsec);

        sheet.findViewById(R.id.btnCopyNpub).setOnClickListener(v -> copyText(finalNpub));
        sheet.findViewById(R.id.btnCopyNsec).setOnClickListener(v -> copyText(finalNsec));

        sheet.findViewById(R.id.btnNostrDone).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnNostrBack).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showBrowserDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_code_preview_fullscreen, null);
        WebView wv = sheet.findViewById(R.id.wvCodePreview);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadUrl(AppConstants.LOCAL_BROWSER_URL);
        sheet.findViewById(R.id.btnCloseCodePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnReloadCodePreview).setOnClickListener(v -> wv.reload());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showCryptoBotDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_code_preview_fullscreen, null);
        WebView wv = sheet.findViewById(R.id.wvCodePreview);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadUrl(AppConstants.LOCAL_BOT_URL);
        sheet.findViewById(R.id.btnCloseCodePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnReloadCodePreview).setOnClickListener(v -> wv.reload());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Puter Unofficial Help")
                .setMessage("Pure Native Android client for Puter AI, Gemini Live Audio commentary, and multi-model completions.\n\nDeveloped by Josyvine.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showCodePreviewFullscreen(String code) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_code_preview_fullscreen, null);
        WebView wv = sheet.findViewById(R.id.wvCodePreview);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadDataWithBaseURL(null, code, "text/html", "UTF-8", null);

        sheet.findViewById(R.id.btnCloseCodePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnReloadCodePreview).setOnClickListener(v -> wv.loadDataWithBaseURL(null, code, "text/html", "UTF-8", null));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showImagePreviewViewer(String base64OrUrl) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview_viewer, null);
        ImageView iv = sheet.findViewById(R.id.ivFullPreviewImage);

        if (base64OrUrl.startsWith("data:image")) {
            String clean = base64OrUrl.replaceFirst("^data:image/[a-zA-Z]+;base64,", "");
            byte[] decoded = Base64.decode(clean, Base64.DEFAULT);
            Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            iv.setImageBitmap(bmp);
        }

        sheet.findViewById(R.id.btnCloseImagePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnSaveImageToGallery).setOnClickListener(v -> {
            if (webAppInterface != null) webAppInterface.saveImageToGallery(base64OrUrl, "Puter_" + System.currentTimeMillis() + ".jpg");
        });
        sheet.findViewById(R.id.btnShareImage).setOnClickListener(v -> Toast.makeText(this, "Sharing image...", Toast.LENGTH_SHORT).show());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showAttachmentPickerDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_attachment_picker, null);

        sheet.findViewById(R.id.btnOptionGallery).setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch("image/*");
        });

        sheet.findViewById(R.id.btnOptionDocument).setOnClickListener(v -> {
            dialog.dismiss();
            documentLauncher.launch("*/*");
        });

        sheet.findViewById(R.id.btnOptionCamera).setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch("image/*");
        });

        dialog.setContentView(sheet);
        dialog.show();
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

            if (targetX + menuWidth > screenWidth - 32) {
                targetX = targetX - menuWidth;
            }
            if (targetX < 32) targetX = 32;

            if (targetY + menuHeight > screenHeight - 120) {
                targetY = targetY - menuHeight;
            }
            if (targetY < 120) targetY = 120;

            customContextMenuCard.setX(targetX);
            customContextMenuCard.setY(targetY);
            customContextMenuCard.setVisibility(View.VISIBLE);
        });
    }

    // --- PUTER AUTHENTICATION POPUP ---

    private void toggleAuthMirror() {
        boolean loggedIn = AuthManager.getInstance(this).isLoggedIn();
        if (loggedIn) {
            LiveLogManager.getInstance().process("PUTER_AUTH", "User Triggered Sign Out", "Purging credentials and session cache.");
            AuthManager.getInstance(this).logout();
            lastInjectedPuterToken = null;
            refreshDropdownUI();
            Toast.makeText(this, "Signed Out", Toast.LENGTH_SHORT).show();
            if (headlessAudioWebView != null) {
                headlessAudioWebView.evaluateJavascript("try { localStorage.removeItem('puter.auth.token'); } catch(e){}; if (typeof puter !== 'undefined') { puter.authToken = null; if (puter.auth) puter.auth.signOut(); }", null);
            }
            LiveLogManager.getInstance().ok("PUTER_AUTH", "Sign Out Complete", "Puter token cleared from AuthManager and headless storage.");
        } else {
            LiveLogManager.getInstance().process("PUTER_AUTH", "Sign In Selected", "Launching OAuth Popup window.");
            openPuterAuthDialog();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openPuterAuthDialog() {
        if (puterAuthDialog != null && puterAuthDialog.isShowing()) {
            puterAuthDialog.dismiss();
        }
        isPuterAuthProcessing = false;

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setBackgroundColor(Color.WHITE);

        final WebView popupWebView = new WebView(this);

        Button closeButton = new Button(this);
        closeButton.setText("Close Window (Tap when Signed In)");
        closeButton.setBackgroundColor(Color.parseColor("#1a73e8"));
        closeButton.setTextColor(Color.WHITE);
        closeButton.setOnClickListener(v -> {
            LiveLogManager.getInstance().warn("PUTER_AUTH", "Manual Window Close", "User tapped Close Button. Inspecting storage for token...");
            CookieManager.getInstance().flush();
            extractTokensFromWebView(popupWebView, true);
        });

        dialogLayout.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        dialogLayout.addView(popupWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = popupWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);

        String userAgent = ws.getUserAgentString().replace("; wv", "");
        ws.setUserAgentString(userAgent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true);

        popupWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onAuthMessageReceived(String rawData) {
                LiveLogManager.getInstance().ok("PUTER_AUTH", "PostMessage Captured", "Raw: " + rawData);
                runOnUiThread(() -> extractTokensFromWebView(popupWebView, true));
            }

            @JavascriptInterface
            public void onTokenExtracted(String token) {
                if (token != null && !token.trim().isEmpty()) {
                    String clean = token.replace("\"", "").replace("'", "").trim();
                    LiveLogManager.getInstance().ok("PUTER_AUTH", "Token via JS Bridge", "Token len: " + clean.length());
                    AuthManager.getInstance(MainActivity.this).saveAuthToken(clean);
                    syncPuterTokenToHeadless(true);
                    runOnUiThread(() -> completePuterAuth());
                }
            }
        }, "AndroidPuterAuthBridge");

        popupWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                LiveLogManager.getInstance().ok("PUTER_AUTH", "SDK Issued window.close()", "Finalizing auth extraction.");
                runOnUiThread(() -> extractTokensFromWebView(popupWebView, true));
            }
        });

        final String openerPolyfillScript =
                "javascript:(function() {" +
                "  if (!window.opener) {" +
                "    window.opener = {" +
                "      postMessage: function(data, origin) {" +
                "        if (window.AndroidPuterAuthBridge) {" +
                "          window.AndroidPuterAuthBridge.onAuthMessageReceived(typeof data === 'object' ? JSON.stringify(data) : String(data));" +
                "        }" +
                "      }" +
                "    };" +
                "  }" +
                "  try {" +
                "    var token = localStorage.getItem('puter.auth.token') || (typeof puter !== 'undefined' && puter.authToken);" +
                "    if (token && window.AndroidPuterAuthBridge) {" +
                "      window.AndroidPuterAuthBridge.onTokenExtracted(token);" +
                "    }" +
                "  } catch(e) {}" +
                "})()";

        popupWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                LiveLogManager.getInstance().process("PUTER_AUTH", "OAuth Page Loading", "URL: " + url);
                view.evaluateJavascript(openerPolyfillScript, null);
                checkAuthRedirect(url, view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return checkAuthRedirect(request.getUrl().toString(), view);
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return checkAuthRedirect(url, view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                LiveLogManager.getInstance().ok("PUTER_AUTH", "OAuth Page Finished", "URL: " + url);
                view.evaluateJavascript(openerPolyfillScript, null);
                CookieManager.getInstance().flush();

                extractTokensFromWebView(view, false);
                checkAuthRedirect(url, view);
            }

            private boolean checkAuthRedirect(String url, WebView view) {
                if (url == null || isPuterAuthProcessing) return false;
                AuthManager auth = AuthManager.getInstance(MainActivity.this);
                if (auth.isAuthCallback(url)) {
                    isPuterAuthProcessing = true;
                    LiveLogManager.getInstance().ok("PUTER_AUTH", "Callback Detected", "Callback URL matched: " + url);
                    extractTokensFromWebView(view, true);
                    return true;
                }
                return false;
            }
        });

        puterAuthDialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        puterAuthDialog.setContentView(dialogLayout);
        puterAuthDialog.show();

        String sdkAuthUrl = "https://puter.com/?embedded_in_popup=true&request_auth=true";
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Origin", "https://appassets.androidplatform.net");
        headers.put("Referer", "https://appassets.androidplatform.net/");
        popupWebView.loadUrl(sdkAuthUrl, headers);
    }

    private void extractTokensFromWebView(WebView webView, boolean finalizeOnFound) {
        if (webView == null) return;

        try {
            CookieManager cm = CookieManager.getInstance();
            String cookies = cm.getCookie("https://puter.com");
            if (cookies == null || cookies.isEmpty()) {
                cookies = cm.getCookie("https://api.puter.com");
            }
            if (cookies != null && !cookies.isEmpty()) {
                String[] cookieList = cookies.split(";");
                List<String> foundKeys = new ArrayList<>();
                for (String c : cookieList) {
                    String[] kv = c.trim().split("=");
                    if (kv.length >= 2) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        foundKeys.add(key);

                        if (key.equalsIgnoreCase("token") ||
                            key.equalsIgnoreCase("puter_auth_token") ||
                            key.equalsIgnoreCase("puter_token") ||
                            key.equalsIgnoreCase("auth_token") ||
                            key.equalsIgnoreCase("puter-auth")) {

                            if (!val.isEmpty()) {
                                LiveLogManager.getInstance().ok("PUTER_AUTH", "Token Discovered in Cookies", "Key [" + key + "] len: " + val.length());
                                AuthManager.getInstance(MainActivity.this).saveAuthToken(val);
                                syncPuterTokenToHeadless(true);
                                if (finalizeOnFound) {
                                    completePuterAuth();
                                    return;
                                }
                            }
                        }
                    }
                }
                LiveLogManager.getInstance().info("PUTER_AUTH", "Cookies Scanned", "Keys: " + foundKeys);
            } else {
                LiveLogManager.getInstance().warn("PUTER_AUTH", "Cookie Jar Empty", "CookieManager returned null/empty for puter.com");
            }
        } catch (Exception e) {
            LiveLogManager.getInstance().error("PUTER_AUTH", "Cookie Scan Error", e.getMessage());
        }

        String jsQuery = "(function(){" +
                "  var t = '';" +
                "  try { t = localStorage.getItem('puter.auth.token') || localStorage.getItem('token') || ''; } catch(e){}" +
                "  if (!t && typeof puter !== 'undefined') {" +
                "    t = puter.authToken || (puter.auth && puter.auth.token) || '';" +
                "  }" +
                "  return t;" +
                "})()";

        webView.evaluateJavascript(jsQuery, val -> {
            if (val != null && !val.equals("null") && !val.trim().isEmpty()) {
                String clean = val.replace("\"", "").replace("'", "").trim();
                if (!clean.isEmpty()) {
                    LiveLogManager.getInstance().ok("PUTER_AUTH", "Token Extracted from LocalStorage", "Len: " + clean.length());
                    AuthManager.getInstance(MainActivity.this).saveAuthToken(clean);
                    syncPuterTokenToHeadless(true);
                    if (finalizeOnFound) {
                        completePuterAuth();
                    }
                    return;
                }
            }
            if (finalizeOnFound) {
                completePuterAuth();
            }
        });
    }

    private void completePuterAuth() {
        if (isPuterAuthProcessing && puterAuthDialog == null) return;
        isPuterAuthProcessing = true;

        CookieManager.getInstance().flush();
        AuthManager auth = AuthManager.getInstance(MainActivity.this);
        auth.handleAuthCallback("https://puter.com/app");
        auth.setLoggedIn(true);

        String currentToken = auth.getAuthToken();
        if (currentToken != null && !currentToken.trim().isEmpty()) {
            LiveLogManager.getInstance().ok("PUTER_AUTH", "Authentication Verified", "Token active in AuthManager (length: " + currentToken.length() + ")");
        } else {
            LiveLogManager.getInstance().warn("PUTER_AUTH", "Login Finished Without Token", "Session marked logged-in, but token remains empty.");
        }

        runOnUiThread(() -> {
            if (puterAuthDialog != null && puterAuthDialog.isShowing()) {
                try {
                    puterAuthDialog.dismiss();
                } catch (Exception ignored) {}
                puterAuthDialog = null;
            }
            refreshDropdownUI();
            syncPuterTokenToHeadless(true);
            Toast.makeText(MainActivity.this, "Puter Sign-In Successful!", Toast.LENGTH_SHORT).show();
            if (isLiveMode) {
                loadModels();
            }
        });
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
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (geminiService != null) {
            geminiService.shutdown();
        }
        if (headlessAudioBridge != null) {
            headlessAudioBridge.stopCommentarySession();
            headlessAudioBridge.stopPuterChat();
        }
        if (puterAuthDialog != null && puterAuthDialog.isShowing()) {
            puterAuthDialog.dismiss();
        }
    }

    private static class SessionHistoryAdapter extends RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder> {
        private final List<ChatSession> sessions;
        private final OnSessionClickListener clickListener;
        private final OnSessionDeleteListener deleteListener;

        interface OnSessionClickListener { void onClick(ChatSession session); }
        interface OnSessionDeleteListener { void onDelete(ChatSession session); }

        SessionHistoryAdapter(List<ChatSession> sessions, OnSessionClickListener c, OnSessionDeleteListener d) {
            this.sessions = sessions;
            this.clickListener = c;
            this.deleteListener = d;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_drawer_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatSession s = sessions.get(position);
            holder.tvTitle.setText(s.getTitle());
            holder.itemView.setOnClickListener(v -> clickListener.onClick(s));
            holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(s));
        }

        @Override
        public int getItemCount() {
            return sessions != null ? sessions.size() : 0;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            ImageView btnDelete;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvSessionTitle);
                btnDelete = v.findViewById(R.id.btnDeleteSession);
            }
        }
    }
}

