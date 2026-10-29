package com.puter.unofficial;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * Custom WebViewClient for Puter Unofficial.
 * Intercepts URL loads to detect successful authentication redirects,
 * manages virtual HTTPS asset routing via WebViewAssetLoader,
 * strips frame-blocking headers for scraping, and persists session cookies.
 */
public class PuterWebViewClient extends WebViewClient {

    private static final String TAG = "PuterWebViewClient";
    private final Context context;
    private final AuthManager authManager;
    private final WebViewAssetLoader assetLoader;
    
    // Guard variable to prevent recursive redirect loops
    private String lastHandledAuthUrl = "";

    public PuterWebViewClient(Context context) {
        this.context = context;
        this.authManager = AuthManager.getInstance(context);

        this.assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .build();
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // 1. Virtual domain routing: https://appassets.androidplatform.net/assets/...
        WebResourceResponse localResponse = assetLoader.shouldInterceptRequest(request.getUrl());
        if (localResponse != null) {
            return localResponse;
        }

        // 2. Intercept external GET requests to bypass iframe framing blockages
        Uri uri = request.getUrl();
        if (uri != null) {
            String urlStr = uri.toString();

            // SUBRESOURCE SHIELD: Immediately ignore static subresources to prevent compression corruption
            String path = uri.getPath();
            if (path != null) {
                String lowercasePath = path.toLowerCase();
                if (lowercasePath.endsWith(".js") || lowercasePath.endsWith(".css") || 
                    lowercasePath.endsWith(".png") || lowercasePath.endsWith(".jpg") || 
                    lowercasePath.endsWith(".jpeg") || lowercasePath.endsWith(".gif") || 
                    lowercasePath.endsWith(".woff") || lowercasePath.endsWith(".woff2") || 
                    lowercasePath.endsWith(".ttf") || lowercasePath.endsWith(".svg") || 
                    lowercasePath.endsWith(".json") || lowercasePath.endsWith(".ico") || 
                    lowercasePath.endsWith(".webp") || lowercasePath.endsWith(".mp4")) {
                    return null;
                }
            }

            // Exclude API requests or non-HTML queries by evaluating Accept headers
            java.util.Map<String, String> reqHeaders = request.getRequestHeaders();
            boolean expectsHtml = false;
            if (reqHeaders != null) {
                for (java.util.Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("accept")) {
                        String value = entry.getValue();
                        if (value != null && value.toLowerCase().contains("text/html")) {
                            expectsHtml = true;
                        }
                        break;
                    }
                }
            }

            if (!expectsHtml && reqHeaders != null) {
                if (reqHeaders.containsKey("X-Requested-With") || reqHeaders.containsKey("x-requested-with") ||
                    reqHeaders.containsKey("Sec-Fetch-Dest") || "xmlhttprequest".equalsIgnoreCase(reqHeaders.get("X-Requested-With"))) {
                    return null;
                }
            }

            if ((urlStr.startsWith("http://") || urlStr.startsWith("https://")) 
                    && !urlStr.startsWith("https://appassets.androidplatform.net/")
                    && "GET".equalsIgnoreCase(request.getMethod())) {
                try {
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    if (request.getRequestHeaders() != null) {
                        for (java.util.Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                            conn.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }

                    // Forward active cookies
                    String cookieVal = CookieManager.getInstance().getCookie(urlStr);
                    if (cookieVal != null && !cookieVal.isEmpty()) {
                        conn.setRequestProperty("Cookie", cookieVal);
                    }

                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);

                    int statusCode = conn.getResponseCode();
                    String responseMessage = conn.getResponseCode() == 200 ? "OK" : conn.getResponseMessage();
                    String mimeType = conn.getContentType();
                    String encoding = conn.getContentEncoding();

                    if (mimeType != null && mimeType.contains(";")) {
                        String[] parts = mimeType.split(";");
                        mimeType = parts[0].trim();
                        for (int i = 1; i < parts.length; i++) {
                            if (parts[i].trim().toLowerCase().startsWith("charset=")) {
                                encoding = parts[i].trim().substring(8).trim();
                                break;
                            }
                        }
                    }

                    if (mimeType == null || mimeType.isEmpty()) {
                        mimeType = "text/html";
                    }
                    if (encoding == null || encoding.isEmpty()) {
                        encoding = "UTF-8";
                    }

                    if (!mimeType.toLowerCase().contains("text/html")) {
                        conn.disconnect();
                        return null; 
                    }

                    // Aggressively strip restrictive framing headers
                    java.util.Map<String, String> responseHeaders = new java.util.HashMap<>();
                    for (java.util.Map.Entry<String, java.util.List<String>> header : conn.getHeaderFields().entrySet()) {
                        if (header.getKey() != null) {
                            String key = header.getKey().toLowerCase();
                            if (key.equals("x-frame-options") || key.equals("frame-options") || key.equals("content-security-policy")) {
                                Log.d(TAG, "Bypass: Stripped restrictive framing header: " + header.getKey() + " from " + urlStr);
                                continue;
                            }
                            java.util.List<String> values = header.getValue();
                            if (values != null && !values.isEmpty()) {
                                responseHeaders.put(header.getKey(), values.get(0));
                            }
                        }
                    }

                    responseHeaders.put("Access-Control-Allow-Origin", "*");
                    responseHeaders.put("Access-Control-Allow-Methods", "GET, OPTIONS");

                    java.io.InputStream stream = conn.getInputStream();
                    String contentEncoding = conn.getContentEncoding();
                    boolean isGzipped = contentEncoding != null && contentEncoding.toLowerCase().contains("gzip");

                    if (mimeType.toLowerCase().contains("text/html")) {
                        try {
                            java.io.InputStream localStream = stream;
                            if (isGzipped) {
                                localStream = new java.util.zip.GZIPInputStream(stream);
                            }

                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(localStream, encoding));
                            StringBuilder htmlBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                htmlBuilder.append(line).append("\n");
                            }
                            reader.close();
                            String html = htmlBuilder.toString();

                            String scriptContent = "";
                            if (urlStr.contains("amazon.in") || urlStr.contains("amazon.com")) {
                                scriptContent = AssetUtils.readFile(context, "content.js");
                                Log.d(TAG, "Stream Interceptor: Injecting content.js into Amazon document.");
                            } else {
                                scriptContent = AssetUtils.readFile(context, "universal_scraper.js");
                                Log.d(TAG, "Stream Interceptor: Injecting universal_scraper.js into target document.");
                            }

                            String scriptWrapper = "\n<script type=\"text/javascript\">\n" + scriptContent + "\n</script>\n";

                            if (html.contains("</head>")) {
                                html = html.replace("</head>", scriptWrapper + "</head>");
                            } else if (html.contains("</body>")) {
                                html = html.replace("</body>", scriptWrapper + "</body>");
                            } else {
                                html = scriptWrapper + html;
                            }

                            stream = new java.io.ByteArrayInputStream(html.getBytes(encoding));

                            java.util.List<String> keysToRemove = new java.util.ArrayList<>();
                            for (String headerKey : responseHeaders.keySet()) {
                                if (headerKey != null && headerKey.equalsIgnoreCase("content-encoding")) {
                                    keysToRemove.add(headerKey);
                                }
                            }
                            for (String headerKey : keysToRemove) {
                                responseHeaders.remove(headerKey);
                            }
                        } catch (Exception injectionError) {
                            Log.e(TAG, "Stream Interceptor Error: Parsing failed. Restoring original stream context.", injectionError);
                            conn.disconnect();
                            conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            if (request.getRequestHeaders() != null) {
                                for (java.util.Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                                }
                            }
                            stream = conn.getInputStream();
                        }
                    }

                    return new WebResourceResponse(
                        mimeType,
                        encoding,
                        statusCode,
                        responseMessage != null ? responseMessage : "OK",
                        responseHeaders,
                        stream
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Bypass Error: Failed to strip framing restrictions for -> " + urlStr, e);
                }
            }
        }

        return null;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request != null && request.getUrl() != null) {
            return handleUrlOverride(view, request.getUrl().toString());
        }
        return false;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrlOverride(view, url);
    }

    private boolean handleUrlOverride(WebView view, String url) {
        if (url == null) return false;
        Log.d(TAG, "Loading URL: " + url);

        // Puter OAuth callback detection and token extraction (including /app dashboard redirect)
        if (authManager.isAuthCallback(url) && !url.equals(lastHandledAuthUrl)) {
            Log.i(TAG, "Authentication successful redirect detected: " + url);
            lastHandledAuthUrl = url;

            authManager.handleAuthCallback(url);
            CookieManager.getInstance().flush();

            if (context instanceof MainActivity) {
                ((MainActivity) context).runOnUiThread(() -> {
                    ((MainActivity) context).handleUrlChange(url);
                    ((MainActivity) context).reloadWebView();
                });
            }
            return false;
        }

        return false;
    }

    private void extractAndSaveTokenIfPresent(String url) {
        try {
            if (url != null && url.contains("token=")) {
                Uri uri = Uri.parse(url);
                String token = uri.getQueryParameter("token");
                if (token != null && !token.isEmpty()) {
                    authManager.saveAuthToken(token);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Log.d(TAG, "Page load started: " + url);
        if (context instanceof MainActivity) {
            ((MainActivity) context).handleUrlChange(url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Log.d(TAG, "Page load finished: " + url);

        CookieManager.getInstance().flush();

        // Check if page finished on an auth confirmation URL or desktop dashboard (/app)
        if (url != null && authManager.isAuthCallback(url)) {
            authManager.handleAuthCallback(url);
            CookieManager.getInstance().flush();
            if (context instanceof MainActivity) {
                ((MainActivity) context).runOnUiThread(() -> {
                    ((MainActivity) context).handleUrlChange(url);
                    ((MainActivity) context).reloadWebView();
                });
            }
        }

        if (url != null && url.startsWith(AppConstants.LOCAL_INDEX_URL)) {
            view.post(() -> view.evaluateJavascript("if(window.updateAuthUI) { window.updateAuthUI(); }", null));
            lastHandledAuthUrl = "";
        } 
        else if (url != null && !url.startsWith("https://appassets.androidplatform.net/")) {
            if (url.contains("amazon.in") || url.contains("amazon.com")) {
                Log.i(TAG, "Kiwi-Style System: Detecting E-Commerce Domain (Amazon). Script injected during stream intercept.");
                view.post(() -> Log.d(TAG, "Pre-injected Amazon content.js verified inside iframe page context."));
            } else {
                Log.i(TAG, "Kiwi-Style System: Detecting External Article Domain. Script injected during stream intercept.");
                view.post(() -> Log.d(TAG, "Pre-injected universal_scraper.js verified inside iframe page context."));
            }
        }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        Log.e(TAG, "WebView Error (" + errorCode + "): " + description + " at " + failingUrl);
    }
}