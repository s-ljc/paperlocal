package com.paperlocal.bridge;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.paperlocal.ai.AiCfgStore;
import com.paperlocal.ai.AiClient;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Exposed to the reader page as window.pl.
 *
 * - aiFetchAsync: one-shot native POST -> window.__plDone(id, env)
 * - aiStreamAsync: SSE streaming -> window.__plChunk / window.__plDone
 * - readFile: local PDF bytes as base64 -> window.__plFile(id, env)
 * - profiles/active/switchTo/chatCompletionsUrl/modelsUrl: endpoint config
 * Native HTTP everywhere: no CORS, no relay.
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
                eval("window.__plDone&&window.__plDone(" + id + "," + env(ok, status, body) + ");");
            }
        });
    }

    @JavascriptInterface
    public void aiStreamAsync(final int id, final String url, final String body,
            final String auth) {
        AiClient.streamAsync(url, body, auth, new AiClient.StreamCallback() {
            public void onChunk(String delta) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("d", delta);
                    eval("window.__plChunk&&window.__plChunk(" + id + "," + o + ");");
                } catch (Exception e) {
                }
            }
            public void onResult(boolean ok, int status, String body) {
                eval("window.__plDone&&window.__plDone(" + id + "," + env(ok, status, body) + ");");
            }
        });
    }

    @JavascriptInterface
    public void readFile(final int id, final String path) {
        try {
            File f = new File(path);
            if (!f.isFile() || !f.canRead()) {
                eval("window.__plFile&&window.__plFile(" + id + ",{ok:false,status:404,body:'file not found'});");
                return;
            }
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            in.close();
            final String b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
            eval("window.__plFile&&window.__plFile(" + id + ",{ok:true,status:200,body:\"" + b64 + "\"});");
        } catch (Exception e) {
            eval("window.__plFile&&window.__plFile(" + id + ",{ok:false,status:0,body:'" + e + "'});");
        }
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

    @JavascriptInterface
    public String modelsUrl() {
        return AiClient.normalizeBase(AiCfgStore.active(ctx).optString("baseUrl", ""))
                + "/models";
    }

    @JavascriptInterface
    public String apiKey() {
        return AiCfgStore.active(ctx).optString("apiKey", "");
    }

    private static String env(boolean ok, int status, String body) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            o.put("status", status);
            o.put("body", body == null ? "" : body);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"status\":0,\"body\":\"env error\"}";
        }
    }

    private void eval(final String script) {
        try {
            w.post(new Runnable() {
                public void run() {
                    w.evaluateJavascript(script, null);
                }
            });
        } catch (Exception e) {
        }
    }
}
