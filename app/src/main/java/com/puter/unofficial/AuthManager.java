package com.puter.unofficial;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.CookieManager;

/**
 * Manages the persistent authentication state for Puter Unofficial.
 * This class ensures that once a user signs in via the browser/popup, the app 
 * remembers that state across restarts using SharedPreferences and CookieManager.
 * 
 * CORE ANALYSIS UPDATE: 
 * Synchronizes with Puter's redirect endpoints, multi-domain cookies (puter.com and api.puter.com),
 * and localStorage extractions to ensure native Android components always have a valid Bearer token.
 */
public class AuthManager {

    private static final String PREF_NAME = "PuterPrefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_AUTH_TOKEN = "puter_auth_token"; // Key for extracted SDK token
    private static AuthManager instance;
    private final SharedPreferences prefs;

    /**
     * Private constructor for Singleton pattern.
     * Accesses the shared preference file dedicated to Puter settings.
     */
    private AuthManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the global instance of the AuthManager.
     * Uses synchronized block to ensure thread safety.
     * 
     * @param context The application context.
     * @return The singleton instance of AuthManager.
     */
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    /**
     * Saves the current authentication status.
     * 
     * @param status true if the user is authenticated, false otherwise.
     */
    public void setLoggedIn(boolean status) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, status).apply();
    }

    /**
     * Checks if the user is currently considered logged in.
     * Returns true if status is saved as logged in or a valid auth token is present.
     * 
     * @return true if authenticated.
     */
    public boolean isLoggedIn() {
        boolean flag = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        if (flag) return true;
        String token = getAuthToken();
        return token != null && !token.trim().isEmpty();
    }

    /**
     * Persists the authentication token string.
     * Automatically strips quotes, removes redundant "Bearer " prefixes, and trims whitespace.
     * 
     * @param token The raw or wrapped session token string.
     */
    public void setAuthToken(String token) {
        if (token == null) {
            prefs.edit().remove(KEY_AUTH_TOKEN).apply();
            return;
        }

        String cleaned = token.trim();
        // Remove surrounding quotes if extracted directly from JSON or JS stringify
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.startsWith("'") && cleaned.endsWith("'") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        // Remove redundant "Bearer " prefix if already included
        if (cleaned.toLowerCase().startsWith("bearer ")) {
            cleaned = cleaned.substring(7).trim();
        }

        if (!cleaned.isEmpty()) {
            prefs.edit().putString(KEY_AUTH_TOKEN, cleaned).apply();
            setLoggedIn(true);
        }
    }

    /**
     * Alias method for setAuthToken to ensure compatibility across callers.
     * 
     * @param token The session token string.
     */
    public void saveAuthToken(String token) {
        setAuthToken(token);
    }

    /**
     * Synchronizes authentication token by inspecting cookies across Puter domains.
     * 
     * @return true if a token was successfully recovered from cookies.
     */
    public boolean syncTokenFromCookies() {
        try {
            CookieManager cm = CookieManager.getInstance();
            String[] domains = new String[]{"https://puter.com", "https://api.puter.com", "https://appassets.androidplatform.net"};
            for (String domain : domains) {
                String cookies = cm.getCookie(domain);
                if (cookies != null && !cookies.isEmpty()) {
                    for (String cookie : cookies.split(";")) {
                        String trimmed = cookie.trim();
                        if (trimmed.startsWith("puter_auth=") || trimmed.startsWith("token=") || 
                            trimmed.startsWith("auth_token=") || trimmed.startsWith("session=")) {
                            String[] parts = trimmed.split("=", 2);
                            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                                setAuthToken(parts[1]);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Handles redirect callback URLs, extracting and saving any token parameters
     * before persisting the logged-in flag. Also inspects CookieManager for session tokens.
     * 
     * @param url The redirect URL intercepted from OAuth.
     */
    public void handleAuthCallback(String url) {
        boolean tokenFound = false;
        if (url != null && url.contains("token=")) {
            try {
                Uri uri = Uri.parse(url);
                String token = uri.getQueryParameter("token");
                if (token != null && !token.isEmpty()) {
                    setAuthToken(token);
                    tokenFound = true;
                }
            } catch (Exception ignored) {}
        }

        // Secondary extraction from session cookies across Puter domains if URL query was tokenless
        if (!tokenFound) {
            syncTokenFromCookies();
        }

        setLoggedIn(true);
    }

    /**
     * Retrieves the saved authentication token.
     * If null, attempts a last-resort sync from CookieManager.
     * 
     * @return The saved token string or null if not available.
     */
    public String getAuthToken() {
        String token = prefs.getString(KEY_AUTH_TOKEN, null);
        if (token == null || token.trim().isEmpty()) {
            syncTokenFromCookies();
            token = prefs.getString(KEY_AUTH_TOKEN, null);
        }
        return (token != null && !token.trim().isEmpty()) ? token.trim() : null;
    }

    /**
     * Clears the authentication state.
     * Triggered when the user selects "Sign Out".
     * Ensures the login flag, backup token, and WebView cookies are wiped clean.
     */
    public void logout() {
        prefs.edit()
             .putBoolean(KEY_IS_LOGGED_IN, false)
             .remove(KEY_AUTH_TOKEN)
             .apply();

        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Exception ignored) {}
    }

    /**
     * Helper to verify if a specific URL is a Puter authentication success callback.
     * Intercepts standard token redirects and Web OS desktop routing (/app) upon login.
     * 
     * @param url The URL being intercepted in the WebView.
     * @return true if the URL indicates a successful authentication.
     */
    public boolean isAuthCallback(String url) {
        if (url == null) return false;

        /* 
         * Puter redirects back to the main domain, a callback parameter, or the 
         * Web OS desktop dashboard (/app) once login completes successfully.
         */
        return (url.contains(AppConstants.AUTH_TOKEN_PARAM) || 
                url.contains(AppConstants.AUTH_SUCCESS_MARKER) ||
                url.contains("auth_success") ||
                url.contains("signed_in=true") ||
                url.contains("puter.com/app") ||
                url.contains("/app/") ||
                url.endsWith("/app") ||
                url.contains("/app#"));
    }
}