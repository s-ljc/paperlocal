package com.paperlocal.bridge;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.paperlocal.ai.AiCfgStore;
import com.paperlocal.ai.AiClient;

import org.json.JSONObject;

/**
 * Exposed to the reader page as window.pl.
 *
 * aiFetchAsync: the page's only network path — native POST, result delivered
 * back as window.__plDone(id, {ok,status,body}). No CORS anywhere.
 */
public class ReaderBridge {

    private final WebView w;
    private final Context ctx;

    public ReaderBridge(Context ctx, WebView w) {
        this.ctx = ctx;
        this.w = w;
    }

    @JavascriptInterface
    public void aiFetchAsync(final int id, final String url, final String body,
            final String auth) {
        AiClient.postAsync(url, body, auth, new AiClient.Callback() {
            public void onResult(boolean ok, int status, String body) {
                JSONObject env = new JSONObject();
                try {
                    env.put("ok", ok);
                    env.put("status", status);
                    env.put("body", body);
                } catch (Exception e) {
                }
                String js = "window.__plDone&&window.__plDone(" + id + ","
                        + env.toString() + ");";
                js = js.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
                final String script = js;
                try {
                    w.post(new Runnable() {
                        public void run() {
                            w.evaluateJavascript(script, null);
                        }
                    });
                } catch (Exception e) {
                }
            }
        });
    }

    @JavascriptInterface
    public String profiles() {
        return AiCfgStore.profiles(ctx).toString();
    }

    @JavascriptInterface
    public String active() {
        return AiCfgStore.active(ctx).toString();
    }

    @JavascriptInterface
    public String switchTo(String name) {
        AiCfgStore.activate(ctx, name);
        return AiCfgStore.active(ctx).toString();
    }

    @JavascriptInterface
    public String chatCompletionsUrl() {
        return AiClient.normalizeBase(AiCfgStore.active(ctx).optString("baseUrl", ""))
                + "/chat/completions";
    }
}
