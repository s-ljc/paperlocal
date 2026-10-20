package com.paperlocal;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.paperlocal.bridge.ReaderBridge;

/**
 * WebView reader host. The page (assets/reader/index.html) renders the PDF
 * locally (pdf.js, drop-in) and talks to the AI only through window.pl.
 */
public class ReaderActivity extends Activity {

    public static final String EXTRA_PATH = "path";

    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // page-side translation cache
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        web.addJavascriptInterface(new ReaderBridge(this, web), "pl");

        String path = getIntent().getStringExtra(EXTRA_PATH);
        String url = "file:///android_asset/reader/index.html?path="
                + Uri.encode(path == null ? "" : path);
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
