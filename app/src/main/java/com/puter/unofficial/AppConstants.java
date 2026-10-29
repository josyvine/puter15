package com.puter.unofficial;

/**
 * Centralized constants for the Puter Unofficial application.
 * Manages preferences, official Google Gemini URL Context schemas,
 * high-speed batch quotas, telemetry tags, and network timeouts.
 */
public class AppConstants {

    // --- SHARED PREFERENCES ---
    public static final String PREF_NAME = "PuterPrefs";
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_AUTH_TOKEN = "puter_auth_token";
    public static final String KEY_CUSTOM_INSTRUCTION = "puter_custom_instruction";
    public static final String KEY_CHAT_HISTORY = "puter_chat_history";
    public static final String KEY_SESSIONS_LIST = "puter_sessions_list";
    public static final String KEY_ACTIVE_SESSION_ID = "puter_active_session_id";
    public static final String KEY_USE_LIVE_MODELS = "use_live_models";
    public static final String KEY_PUTER_CACHED_LIVE_MODELS = "puter_live_models";
    public static final String KEY_SCRAPED_PRODUCTS_INDEX = "puter_scraped_index";
    public static final String KEY_SCRAPER_QUEUE = "puter_scraper_queue";
    public static final String KEY_AUTO_MODE = "puter_auto_mode";
    public static final String KEY_MODE_AUTO = "puter_auto_mode";
    public static final String KEY_TARGET_LANGUAGE = "puter_target_language";
    public static final String KEY_URL_CONTEXT_ENABLED = "gemini_url_context_enabled";

    // --- NOSTR IDENTITY & RELAY PREFERENCES ---
    public static final String KEY_NOSTR_PRIVATE_KEY = "nostr_private_key_hex";
    public static final String KEY_NOSTR_PUBLIC_KEY = "nostr_public_key_hex";
    public static final String KEY_NOSTR_RELAY_URL = "puter_nostr_relay_url";
    public static final String KEY_EXTENSION_PUBLIC_ID = "puter_extension_public_id";

    // --- GEMINI INTEGRATION & SETTINGS ---
    public static final String KEY_ACTIVE_PROVIDER = "puter_active_provider";
    public static final String KEY_GEMINI_API_KEY = "gemini_user_api_key";
    public static final String KEY_GEMINI_STREAMING = "gemini_streaming_enabled";
    public static final String KEY_GEMINI_GROUNDING = "gemini_grounding_type";
    public static final String KEY_GEMINI_GROUNDING_MODE = "gemini_grounding_mode";
    public static final String KEY_GEMINI_VOICE_NAME = "gemini_voice_name";
    public static final String KEY_GEMINI_VOICE_CHARACTER = "gemini_voice_character";
    public static final String KEY_GEMINI_CACHED_LIVE_MODELS = "gemini_cached_live_models";

    // --- OFFICIAL GOOGLE GEMINI URL CONTEXT & BATCHING CONFIGURATION ---
    public static final String TOOL_URL_CONTEXT = "url_context";
    public static final int URL_CONTEXT_BATCH_SIZE = 20; // Official Google request limit
    public static final int URL_CONTEXT_MAX_CHARS_PER_URL = 40000; // 40KB stream-clamping threshold
    public static final int URL_FETCH_CONNECT_TIMEOUT_SEC = 8;
    public static final int URL_FETCH_READ_TIMEOUT_SEC = 10;
    public static final int URL_PARALLEL_POOL_SIZE = 20;

    // --- USAGE TRACKING ---
    public static final String KEY_USAGE_TOTAL_TOKENS = "puter_usage_total_tokens";
    public static final String KEY_USAGE_TOTAL_COST = "puter_usage_total_cost";

    // --- ASSETS & URLS ---
    public static final String HEADLESS_AUDIO_URL = "file:///android_asset/headless_live_audio.html";
    public static final String HEADLESS_ASSET_URL = "https://appassets.androidplatform.net/assets/headless_live_audio.html";
    public static final String LOCAL_INDEX_URL = "https://appassets.androidplatform.net/assets/index.html";
    public static final String LOCAL_BROWSER_URL = "file:///android_asset/browser.html";
    public static final String LOCAL_BOT_URL = "file:///android_asset/bot.html";
    
    // Dedicated Puter OAuth Authorization URL with embedded directive
    public static final String PUTER_LOGIN_URL = "https://puter.com/login?embedded=true";
    public static final String GEMINI_MODELS_LIST_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    // Markers for detecting successful SDK authentication redirects
    public static final String AUTH_SUCCESS_MARKER = "signed_in=true";
    public static final String AUTH_TOKEN_PARAM = "token=";

    public static final String MODELS_JSON_FILE = "models.json";
    public static final String GEMINI_MODELS_JSON_FILE = "gemini_models.json";

    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-2.0-flash-exp";

    // --- PERMISSION REQUEST CODES ---
    public static final int REQUEST_CODE_PERMISSIONS = 100;
    public static final int FILE_CHOOSER_RESULT_CODE = 1;

    // --- BRIDGE NAMES & TTS ---
    public static final String JS_BRIDGE_NAME = "AndroidInterface";
    public static final String TTS_UTTERANCE_ID = "PuterTTS_ID";

    // --- LOG TAGS FOR SYSTEM LOG CONSOLE ---
    public static final String TAG_AUTH = "PuterAuth";
    public static final String TAG_VOICE = "PuterVoice";
    public static final String TAG_WEBVIEW = "PuterWebView";
    public static final String TAG_URL_CONTEXT = "URL_CONTEXT";
    public static final String TAG_LIVE_TRANSCRIPT = "LIVE_TRANSCRIPT";
    public static final String TAG_GEMINI_NETWORK = "GEMINI_NETWORK";

    // --- WATCHER SERVICE CONFIGURATION ---
    public static final int WATCHER_NOTIFICATION_ID = 1002;
    public static final String WATCHER_CHANNEL_ID = "puter_watcher_channel";
    public static final String WATCHER_CHANNEL_NAME = "Puter Query Watcher";

    private AppConstants() {}
}