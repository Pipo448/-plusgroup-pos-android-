package com.plusgroup.pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.util.Base64;
import android.util.Log;

public class MainActivity extends Activity {

    private static final String TAG = "PlusGroupPOS";
    private static final String APP_URL = "file:///android_asset/index.html";

    private WebView webView;
    private SunmiPrinterManager printer;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        printer = new SunmiPrinterManager(this);
        printer.connect();

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();

        // ───────── Fonksyonalite debaz
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // ───────── Pèmèt WebView li fichye lokal React yo
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // ───────── Optimizasyon pou POS (Sunmi V2)
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36"
        );

        // ───────── JavaScript Bridge pou printer
        webView.addJavascriptInterface(new PrinterBridge(), "SunmiPrinter");

        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page chaje: " + url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView Erè: " + description + " URL: " + failingUrl);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "JS Console: " + msg.message());
                return true;
            }
        });

        // ───────── Chaje aplikasyon React la
        webView.loadUrl(APP_URL);
    }

    // ───────── Bridge pou Sunmi printer
    public class PrinterBridge {

        @JavascriptInterface
        public void print(String base64Data) {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                printer.printRaw(bytes);
                Log.d(TAG, "Print reyisi: " + bytes.length + " bytes");
            } catch (Exception e) {
                Log.e(TAG, "Print erè: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public String isConnected() {
            return String.valueOf(printer.isConnected());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (webView != null) {
            webView.clearCache(true);
            webView.destroy();
        }

        printer.disconnect();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
