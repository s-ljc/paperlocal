package com.paperlocal.ai;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal OpenAI-compatible client on raw HttpURLConnection.
 * Native HTTP: no CORS, no relay, no third-party deps.
 */
public final class AiClient {

    public interface Callback {
        /** Always invoked on the worker thread. */
        void onResult(boolean ok, int status, String body);
    }

    /** POST JSON to url with optional bearer token; async, single-shot. */
    public static void postAsync(final String url, final String body,
            final String auth, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c =
                            (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(180000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    if (auth != null && auth.length() > 0) {
                        c.setRequestProperty("Authorization", "Bearer " + auth);
                    }
                    OutputStream os = c.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            bo.write(buf, 0, n);
                        }
                        in.close();
                    }
                    String txt = bo.toString("UTF-8");
                    c.disconnect();
                    if (cb != null) {
                        cb.onResult(code == 200, code, txt);
                    }
                } catch (Exception e) {
                    if (cb != null) {
                        cb.onResult(false, 0, String.valueOf(e));
                    }
                }
            }
        }).start();
    }

    /** Normalize a user-typed base url to end with /v1 (no trailing slash). */
    public static String normalizeBase(String u) {
        u = String.valueOf(u == null ? "" : u).trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/chat/completions")) {
            u = u.substring(0, u.length() - "/chat/completions".length());
        }
        if (!u.matches(".*/v\\d+$")) {
            u = u + "/v1";
        }
        return u;
    }

    private AiClient() {
    }
}
