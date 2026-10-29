package com.puter.unofficial;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;

import com.puter.unofficial.logging.LiveLogManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Handles Puter SDK OAuth popup windows via onCreateWindow, prevents marketing page redirects,
 * intercepts SPA route changes (/app) to auto-dismiss, and handles native file upload functionality.
 */
public class MyWebChromeClient extends WebChromeClient {

    private static final String TAG = "MyWebChromeClient";
    private ValueCallback<Uri[]> uploadMessage;
    private final Activity activity;
    private String currentPhotoPath;
    private Dialog authDialog;
    private boolean isAuthProcessing = false;
    private String lastAuthUrl = null;

    public MyWebChromeClient(Activity activity) {
        this.activity = activity;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        String logMsg = String.format(Locale.getDefault(), 
            "[JS CONSOLE] %s -- From line %d of %s",
            consoleMessage.message(),
            consoleMessage.lineNumber(),
            consoleMessage.sourceId());

        switch (consoleMessage.messageLevel()) {
            case ERROR: 
                Log.e("PuterJS", logMsg); 
                LiveLogManager.getInstance().error("JS_CONSOLE", "JS Error", logMsg);
                ActionReportLogger.logHtmlGlitch("JS_CONSOLE_ERROR", logMsg);
                break;
            case WARNING: 
                Log.w("PuterJS", logMsg); 
                LiveLogManager.getInstance().warn("JS_CONSOLE", "JS Warning", logMsg);
                ActionReportLogger.logHtmlGlitch("JS_CONSOLE_WARNING", logMsg);
                break;
            default: 
                Log.d("PuterJS", logMsg); 
                ActionReportLogger.logAction("JS_CONSOLE_LOG", logMsg);
                break;
        }
        return true;
    }

    // --- SDK AUTH POPUP HANDLER WITH AUTO-CLOSE & FALLBACK BUTTON ---

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        if (activity == null) return false;
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        isAuthProcessing = false;

        triggerNativeLog("Auth Popup Requested by Puter SDK", "native");
        LiveLogManager.getInstance().process("PUTER_AUTH", "Auth Popup Opened", "Puter SDK requested OAuth login window");

        // 1. Layout container with fail-safe Close Button + Popup WebView
        LinearLayout dialogLayout = new LinearLayout(activity);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setBackgroundColor(Color.WHITE);

        Button closeButton = new Button(activity);
        closeButton.setText("Close Window (Tap when Signed In)");
        closeButton.setBackgroundColor(Color.parseColor("#1a73e8"));
        closeButton.setTextColor(Color.WHITE);
        closeButton.setOnClickListener(v -> {
            triggerNativeLog("Manual Close Triggered by User", "warn");
            LiveLogManager.getInstance().warn("PUTER_AUTH", "Manual Window Close", "User tapped Close Button. Inspecting credentials...");
            closeAuthAndRefresh();
        });

        dialogLayout.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. Configure the popup WebView
        final WebView popupWebView = new WebView(activity);
        dialogLayout.addView(popupWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings webSettings = popupWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        String userAgent = popupWebView.getSettings().getUserAgentString().replace("; wv", "");
        webSettings.setUserAgentString(userAgent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true);

        // 3. Monitor URL changes and bypass marketing landing page
        popupWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handlePopupUrl(view, request.getUrl().toString());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handlePopupUrl(view, url);
            }

            private boolean handlePopupUrl(WebView view, String url) {
                triggerNativeLog("Popup Navigating: " + url, "info");
                LiveLogManager.getInstance().info("PUTER_AUTH", "Popup Navigating", "URL: " + url);

                // If redirected to Puter marketing homepage, force route to embedded SDK login prompt
                if (isPuterMarketingLanding(url)) {
                    triggerNativeLog("Marketing page intercepted. Redirecting to Puter SDK Auth...", "warn");
                    LiveLogManager.getInstance().warn("PUTER_AUTH", "Marketing Page Intercepted", "Redirecting back to SDK auth prompt");
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Origin", "https://appassets.androidplatform.net");
                    headers.put("Referer", "https://appassets.androidplatform.net/");
                    view.loadUrl("https://puter.com/?embedded_in_popup=true&request_auth=true", headers);
                    return true;
                }

                checkAuthCompletion(view, url);
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                triggerNativeLog("Popup Page Loaded: " + url, "info");
                LiveLogManager.getInstance().ok("PUTER_AUTH", "Popup Page Loaded", "URL: " + url);

                if (isPuterMarketingLanding(url)) {
                    triggerNativeLog("Marketing page loaded. Redirecting to Puter SDK Auth...", "warn");
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Origin", "https://appassets.androidplatform.net");
                    headers.put("Referer", "https://appassets.androidplatform.net/");
                    view.loadUrl("https://puter.com/?embedded_in_popup=true&request_auth=true", headers);
                    return;
                }

                extractAndStorePuterAuthTokens();
                checkAuthCompletion(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (isPuterMarketingLanding(url)) {
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Origin", "https://appassets.androidplatform.net");
                    headers.put("Referer", "https://appassets.androidplatform.net/");
                    view.loadUrl("https://puter.com/?embedded_in_popup=true&request_auth=true", headers);
                    return;
                }
                checkAuthCompletion(view, url);
            }
        });

        // 4. Handle official window.close() call from Puter SDK
        popupWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                if (!isAuthProcessing) {
                    isAuthProcessing = true;
                    triggerNativeLog("SDK issued window.close() command", "native");
                    LiveLogManager.getInstance().ok("PUTER_AUTH", "SDK window.close()", "Puter SDK signaled login complete");
                    closeAuthAndRefresh();
                }
            }
        });

        // 5. Show Dialog and transfer WebView transport
        authDialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        authDialog.setContentView(dialogLayout);
        authDialog.show();

        transport.setWebView(popupWebView);
        resultMsg.sendToTarget();

        return true;
    }

    private boolean isPuterMarketingLanding(String url) {
        if (url == null) return false;
        if (url.contains("embedded_in_popup=true") || url.contains("request_auth=true") || url.contains("login")) {
            return false;
        }
        String clean = url.trim().toLowerCase(Locale.US);
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.equals("https://puter.com") || 
               clean.equals("http://puter.com") || 
               clean.equals("https://www.puter.com") || 
               clean.equals("http://www.puter.com");
    }

    private void checkAuthCompletion(WebView view, String url) {
        if (url == null || isAuthProcessing || activity == null) return;
        lastAuthUrl = url;

        extractTokenFromUrl(url);

        AuthManager auth = AuthManager.getInstance(activity);
        if (auth.isAuthCallback(url)) {
            isAuthProcessing = true;
            triggerNativeLog("Puter auth completion detected: " + url + ". Closing dialog...", "native");
            LiveLogManager.getInstance().ok("PUTER_AUTH", "Auth Callback Matched", "URL: " + url);
            extractAndStorePuterAuthTokens();
            CookieManager.getInstance().flush();
            view.postDelayed(this::closeAuthAndRefresh, 400);
        }
    }

    private void triggerNativeLog(String msg, String type) {
        Log.d("PuterPopupTrace", msg);
        ActionReportLogger.logAction("AUTH_POPUP", "[" + type + "] " + msg);
    }

    /**
     * Extracts token parameters directly if present in redirect URLs.
     */
    private void extractTokenFromUrl(String url) {
        if (url == null || activity == null) return;
        try {
            Uri uri = Uri.parse(url);
            String token = uri.getQueryParameter("token");
            if (token == null) {
                token = uri.getQueryParameter("puter_auth_token");
            }
            if (token == null && uri.getFragment() != null) {
                for (String part : uri.getFragment().split("&")) {
                    String[] kv = part.split("=");
                    if (kv.length == 2 && (kv[0].equalsIgnoreCase("token") || kv[0].equalsIgnoreCase("access_token"))) {
                        token = kv[1];
                        break;
                    }
                }
            }
            if (token != null && !token.trim().isEmpty()) {
                String clean = token.trim();
                AuthManager.getInstance(activity).saveAuthToken(clean);
                triggerNativeLog("Extracted Puter token directly from URL parameter", "native");
                LiveLogManager.getInstance().ok("PUTER_AUTH", "Token Extracted from URL", "Length: " + clean.length() + " chars");
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).syncPuterTokenToHeadless();
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Inspects cookies from puter.com and synchronizes them to AuthManager with deep telemetry.
     */
    private void extractAndStorePuterAuthTokens() {
        if (activity == null) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.flush();
            String[] domains = new String[]{"https://puter.com", "https://api.puter.com"};
            List<String> detectedKeys = new ArrayList<>();

            for (String domain : domains) {
                String cookieStr = cm.getCookie(domain);
                if (cookieStr != null && !cookieStr.isEmpty()) {
                    String[] cookies = cookieStr.split(";");
                    for (String c : cookies) {
                        String[] pair = c.trim().split("=");
                        if (pair.length >= 2) {
                            String key = pair[0].trim();
                            String val = pair[1].trim();
                            detectedKeys.add(key);

                            if (key.equalsIgnoreCase("token") ||
                                key.equalsIgnoreCase("puter_auth_token") ||
                                key.equalsIgnoreCase("puter_token") ||
                                key.equalsIgnoreCase("auth_token") ||
                                key.equalsIgnoreCase("puter-auth")) {
                                
                                if (!val.isEmpty()) {
                                    AuthManager.getInstance(activity).saveAuthToken(val);
                                    triggerNativeLog("Saved Puter auth token from cookie [" + key + "]", "native");
                                    LiveLogManager.getInstance().ok("PUTER_AUTH", "Token Saved from Cookie", "Key: " + key + " | Len: " + val.length());
                                    if (activity instanceof MainActivity) {
                                        ((MainActivity) activity).syncPuterTokenToHeadless();
                                    }
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            if (!detectedKeys.isEmpty()) {
                LiveLogManager.getInstance().info("PUTER_AUTH", "Cookies Scanned in Popup", "Keys found: " + detectedKeys);
            } else {
                LiveLogManager.getInstance().warn("PUTER_AUTH", "No Cookies in Popup", "CookieManager returned empty for puter.com");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed extracting cookies: " + e.getMessage());
            LiveLogManager.getInstance().error("PUTER_AUTH", "Cookie Extraction Error", e.getMessage());
        }
    }

    /**
     * Finalizes authentication, marks session in AuthManager, dismisses popup, and refreshes UI.
     */
    private void closeAuthAndRefresh() {
        CookieManager.getInstance().flush(); 

        extractAndStorePuterAuthTokens();

        if (activity != null) {
            AuthManager auth = AuthManager.getInstance(activity);
            if (lastAuthUrl != null) {
                auth.handleAuthCallback(lastAuthUrl);
            }
            auth.setLoggedIn(true);

            String activeToken = auth.getAuthToken();
            if (activeToken != null && !activeToken.trim().isEmpty()) {
                LiveLogManager.getInstance().ok("PUTER_AUTH", "Popup Auth Success", "Active Token confirmed in AuthManager (length: " + activeToken.length() + ")");
            } else {
                LiveLogManager.getInstance().warn("PUTER_AUTH", "Popup Closed Without Token", "No credentials acquired from cookies or URL parameters.");
            }
        }

        if (authDialog != null && authDialog.isShowing()) {
            try {
                authDialog.dismiss();
            } catch (Exception ignored) {}
            authDialog = null;
        }

        if (activity instanceof MainActivity) {
            activity.runOnUiThread(() -> {
                ((MainActivity) activity).handleUrlChange(lastAuthUrl != null ? lastAuthUrl : "auth_success");
                ((MainActivity) activity).syncPuterTokenToHeadless();
                ((MainActivity) activity).reloadWebView();
            });
        }
    }

    @Override
    public void onCloseWindow(WebView window) {
        if (authDialog != null && authDialog.isShowing()) {
            try {
                authDialog.dismiss();
            } catch (Exception ignored) {}
            authDialog = null;
        }
        super.onCloseWindow(window);
    }

    // --- HARDWARE PERMISSIONS GATE OVERRIDE ---

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        Log.d(TAG, "onPermissionRequest: Processing hardware capture access permission synchronously.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    try {
                        String[] requestedResources = request.getResources();
                        request.grant(requestedResources);
                        Log.d(TAG, "onPermissionRequest: Successfully granted resources: " + java.util.Arrays.toString(requestedResources));
                    } catch (Exception e) {
                        Log.e(TAG, "onPermissionRequest: Failed to grant permission. Error: " + e.getMessage());
                        request.deny();
                    }
                });
            } else {
                try {
                    request.grant(request.getResources());
                } catch (Exception ignored) {}
            }
        }
    }

    // --- FILE UPLOAD LOGIC ---

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        if (activity == null) return false;
        if (uploadMessage != null) {
            uploadMessage.onReceiveValue(null);
        }
        uploadMessage = filePathCallback;

        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("*/*");

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
                takePictureIntent.putExtra("PhotoPath", currentPhotoPath);
            } catch (IOException ex) {
                Log.e("MyWebChromeClient", "Error creating file", ex);
            }
            if (photoFile != null) {
                currentPhotoPath = "file:" + photoFile.getAbsolutePath();
                Uri photoURI = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            } else {
                takePictureIntent = null;
            }
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Upload File");
        if (takePictureIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takePictureIntent });
        }

        activity.startActivityForResult(chooserIntent, 1);
        return true;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (uploadMessage == null) return;
        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                if (currentPhotoPath != null) {
                    results = new Uri[]{Uri.parse(currentPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }
        uploadMessage.onReceiveValue(results);
        uploadMessage = null;
    }
}