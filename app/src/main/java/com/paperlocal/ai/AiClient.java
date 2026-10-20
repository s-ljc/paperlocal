package com.paperlocal.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    public interface StreamCallback {
        /** Incremental assistant text; worker thread. */
        void onChunk(String delta);
        /** Once. body = full assistant text on success, raw error text otherwise. */
        void onResult(boolean ok, int status, String body);
    }

    /** POST JSON to url with optional bearer token; async, single-shot. */
    public static void postAsync(final String url, final String body,
            final String auth, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                String txt = null;
                int code = 0;
                try {
                    HttpURLConnection c = open(url, "POST", auth);
                    c.setReadTimeout(180000);
                    OutputStream os = c.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    code = c.getResponseCode();
                    txt = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    c.disconnect();
                } catch (Exception e) {
                    txt = String.valueOf(e);
                }
                if (cb != null) {
                    cb.onResult(code == 200, code, txt);
                }
            }
        }).start();
    }

    /** GET with optional bearer token (e.g. /models). */
    public static void getAsync(final String url, final String auth, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                String txt = null;
                int code = 0;
                try {
                    HttpURLConnection c = open(url, "GET", auth);
                    c.setReadTimeout(30000);
                    code = c.getResponseCode();
                    txt = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    c.disconnect();
                } catch (Exception e) {
                    txt = String.valueOf(e);
                }
                if (cb != null) {
                    cb.onResult(code == 200, code, txt);
                }
            }
        }).start();
    }

    /**
     * POST with stream=true semantics: parses SSE ("data: {...}") line by line
     * and forwards deltas; falls back to a single chunk when the server
     * answers plain JSON.
     */
    public static void streamAsync(final String url, final String body,
            final String auth, final StreamCallback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = open(url, "POST", auth);
                    c.setRequestProperty("Accept", "text/event-stream");
                    c.setReadTimeout(300000);
                    OutputStream os = c.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    int code = c.getResponseCode();
                    if (code != 200) {
                        String err = readAll(c.getErrorStream());
                        c.disconnect();
                        cb.onResult(false, code, err);
                        return;
                    }
                    String ctype = String.valueOf(c.getContentType());
                    InputStream in = c.getInputStream();
                    StringBuilder full = new StringBuilder();
                    if (ctype.contains("event-stream")) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String d = line.substring(5).trim();
                            if (d.equals("[DONE]")) {
                                break;
                            }
                            try {
                                JSONObject j = new JSONObject(d);
                                JSONArray ch = j.optJSONArray("choices");
                                if (ch != null && ch.length() > 0) {
                                    JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                                    String piece = delta == null ? "" : delta.optString("content", "");
                                    if (piece.length() > 0) {
                                        full.append(piece);
                                        cb.onChunk(piece);
                                    }
                                }
                            } catch (Exception ignore) {
                            }
                        }
                        r.close();
                        cb.onResult(true, 200, full.toString());
                    } else {
                        String raw = readAll(in);
                        c.disconnect();
                        String content = extractContent(raw);
                        if (content != null) {
                            cb.onChunk(content);
                            cb.onResult(true, 200, content);
                        } else {
                            cb.onResult(false, 200, raw);
                        }
                    }
                } catch (Exception e) {
                    cb.onResult(false, 0, String.valueOf(e));
                }
            }
        }).start();
    }

    /** choices[0].message.content from a chat-completions JSON body, or null. */
    public static String extractContent(String raw) {
        try {
            JSONObject j = new JSONObject(raw);
            JSONArray ch = j.optJSONArray("choices");
            if (ch != null && ch.length() > 0) {
                return ch.getJSONObject(0).getJSONObject("message").getString("content");
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static HttpURLConnection open(String url, String method, String auth)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setRequestMethod(method);
        if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
        }
        if (auth != null && auth.length() > 0) {
            c.setRequestProperty("Authorization", "Bearer " + auth);
        }
        return c;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            in.close();
        }
        return bo.toString("UTF-8");
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
