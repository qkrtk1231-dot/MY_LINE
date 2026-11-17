package com.myline.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Offline WebView shell: the entire MY LINE application is bundled in assets/index.html. */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String APP_URL = "https://" + HOST + "/assets/index.html";
    private static final int REQUEST_IMPORT = 1001;
    private static final int REQUEST_SAVE = 1002;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingBytes;
    private boolean pendingBackup;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(248, 245, 241));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(true); // Used only by the user-selected JSON restore file.
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new AppBridge(), "MyLineAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()) ||
                    uri.getPath() == null || !uri.getPath().startsWith("/assets/")) return null;
                String path = uri.getPath().substring("/assets/".length());
                if (!path.equals("index.html")) return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                try {
                    InputStream input = getAssets().open(path);
                    return new WebResourceResponse("text/html", "UTF-8", input);
                } catch (IOException ex) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) return false;
                openExternalUrl(uri.toString());
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("application/json");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQUEST_IMPORT);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "파일 선택 앱을 사용할 수 없어", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        webView.loadUrl(APP_URL);
    }

    // Only bundled first-party HTML runs in this WebView; outside sites open in the browser.
    public final class AppBridge {
        @JavascriptInterface public void saveFile(String name, String content, String mime) {
            if (content == null || content.length() > 10000000) return;
            String safeName = (name == null ? "MY_LINE_backup.json" : name).replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeMime = ("text/csv".equals(mime) || mime != null && mime.startsWith("text/csv")) ? "text/csv" : "application/json";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            runOnUiThread(() -> {
                pendingBytes = bytes;
                pendingBackup = safeMime.equals("application/json");
                Intent saveIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
                saveIntent.setType(safeMime);
                saveIntent.putExtra(Intent.EXTRA_TITLE, safeName);
                try { startActivityForResult(saveIntent, REQUEST_SAVE); }
                catch (ActivityNotFoundException ex) {
                    pendingBytes = null;
                    Toast.makeText(MainActivity.this, "파일 저장 앱을 사용할 수 없어", Toast.LENGTH_SHORT).show();
                }
            });
        }
        @JavascriptInterface public void searchProducts(String query) {
            if (query == null || query.trim().length() < 2 || query.length() > 120) return;
            new Thread(() -> {
                HttpURLConnection connection = null;
                try {
                    String base = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=";
                    String path = base + URLEncoder.encode(query.trim(), "UTF-8")
                        + "&search_simple=1&action=process&json=1&page_size=15"
                        + "&fields=code,product_name,product_name_ko,brands,nutriments,quantity";
                    connection = (HttpURLConnection) new URL(path).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(12000);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "MYLINE-NutritionTracker/19.0 (personal-use; Android)");
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null && sb.length() < 900000) sb.append(line);
                    }
                    final String raw = JSONObject.quote(sb.toString());
                    runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript("window.myLineOnlineResult(" + raw + ")", null); });
                } catch (Exception ex) {
                    final String error = JSONObject.quote(ex.getMessage() == null ? "네트워크 연결 실패" : ex.getMessage());
                    runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript("window.myLineOnlineError(" + error + ")", null); });
                } finally { if (connection != null) connection.disconnect(); }
            }).start();
        }
        @JavascriptInterface public void openExternal(String url) {
            if (!"https://chatgpt.com/".equals(url)) return;
            runOnUiThread(() -> openExternalUrl(url));
        }
    }

    private void openExternalUrl(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "링크를 열 브라우저가 없어", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_IMPORT) {
            if (fileCallback != null) {
                Uri picked = resultCode == RESULT_OK && intent != null ? intent.getData() : null;
                fileCallback.onReceiveValue(picked == null ? null : new Uri[]{picked});
                fileCallback = null;
            }
        } else if (requestCode == REQUEST_SAVE) {
            if (resultCode == RESULT_OK && intent != null && intent.getData() != null && pendingBytes != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(intent.getData())) {
                    if (stream == null) throw new IOException("No output stream");
                    stream.write(pendingBytes);
                    stream.flush();
                    if (pendingBackup) {
                        if (webView != null) webView.evaluateJavascript("window.myLineBackupSaved && window.myLineBackupSaved()", null);
                    }
                    Toast.makeText(this, "백업 파일을 저장했어", Toast.LENGTH_SHORT).show();
                } catch (IOException ex) {
                    Toast.makeText(this, "저장하지 못했어. 다시 시도해 줘", Toast.LENGTH_LONG).show();
                }
            }
            pendingBytes = null;
        }
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setMessage("MY LINE 앱을 종료할까? 기록은 자동 저장돼.")
            .setNegativeButton("계속 사용", (dialog, which) -> dialog.dismiss())
            .setPositiveButton("종료", (dialog, which) -> finish())
            .show();
    }
    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("MyLineAndroid");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
