package com.ltzhushou.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final String TAG = "LTZhuShou";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            webView = new WebView(this);
            setContentView(webView);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            settings.setUserAgentString(settings.getUserAgentString() + " LtZhuShou/1.0");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    Log.e(TAG, "Page load error: " + error.getErrorCode() + " - " + error.getDescription());
                }
            });

            // Load the PWA from assets
            webView.loadUrl("file:///android_asset/index.html");
            Log.d(TAG, "Loading 率土助手 from assets");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing WebView", e);
        }
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
