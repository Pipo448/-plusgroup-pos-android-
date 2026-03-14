// app/src/main/java/com/plusgroup/pos/MainActivity.java
package com.plusgroup.pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.util.Base64;
import android.util.Log;

public class MainActivity extends Activity {

    private static final String TAG     = "PlusGroupPOS";
    private static final String APP_URL = "file:///android_asset/index.html";

    private WebView            webView;
    private SunmiPrinterManager printer;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── Enstansye printer manager
        printer = new SunmiPrinterManager(this);
        printer.connect();

        // ── Konfigire WebView
        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // ── Enjekte JavaScript bridge "SunmiPrinter"
        // Nan app ou: window.SunmiPrinter.print(base64bytes)
        webView.addJavascriptInterface(new PrinterBridge(), "SunmiPrinter");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Rete nan menm WebView pou tout URL app la
                view.loadUrl(url);
                return true;
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

    // ── JavaScript Interface — rele depi React app la
    public class PrinterBridge {

        // ✅ Resevwa bytes ESC/POS an base64 depi JavaScript
        // Itilizasyon nan React: window.SunmiPrinter.print(base64string)
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

        // ✅ Verifye si printer konekte — retounen "true"/"false"
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
        // Bouton retou Android → navige tounen nan WebView
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
