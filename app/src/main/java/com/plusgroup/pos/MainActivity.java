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

    private static final String TAG     = "PlusGroupPOS";
    private static final String APP_URL = "https://app.plusgroupe.com";

    private WebView             webView;
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
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36"
        );

        webView.addJavascriptInterface(new PrinterBridge(), "SunmiPrinter");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page chaje: " + url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "JS: " + msg.message());
                return true;
            }
        });

        webView.loadUrl(APP_URL);
    }

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
        printer.disconnect();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
