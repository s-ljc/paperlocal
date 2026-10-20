# Architecture

## Principles

1. **Pure client.** No account, no cloud, no vendor backend. The only network
   peer is the user's own OpenAI-compatible endpoint.
2. **Native HTTP only.** All AI traffic goes through `AiClient`
   (`HttpURLConnection` on a worker thread). The WebView never fetches the
   network — it has no CORS surface at all.
3. **Zero third-party runtime deps** in the skeleton (org.json ships with
   Android). Renderers (pdf.js / pdfium) are the only planned additions and
   must be registered in NOTICE.

## Data flow

```
reader page (assets/reader/*.js)
   │  window.pl.aiFetchAsync(id, url, body, auth)     @JavascriptInterface
   ▼
ReaderBridge ──> AiClient.postAsync ──worker thread──> user endpoint
   │  w.post(evaluateJavascript("window.__plDone(id, env)"))
   ▼
reader page resolves the promise; translation cache (Map + localStorage)

SettingsActivity ──> AiCfgStore (SharedPreferences: profiles[] + active)
MainActivity     ──> LibraryStore (local dir scan; no DB yet)
```

## Module map

| Module | Responsibility | Status |
|---|---|---|
| `ai/AiClient` | OpenAI-compatible POST, base-url normalization | done (ported from mod, field-tested) |
| `ai/AiCfgStore` | multi-profile endpoint store | done |
| `bridge/ReaderBridge` | `window.pl`: aiFetchAsync / profiles / active / switchTo | done |
| `MainActivity` | local PDF list + settings entry | done (list only) |
| `ReaderActivity` | WebView host, bridge attach, back handling | done |
| `SettingsActivity` | endpoint editor + connection test | skeleton (single profile UI) |
| `library/LibraryStore` | scan Download/Documents | skeleton (no SAF, no favorites) |
| `assets/reader` | page shell + selection translation + cache | done; PDF render needs pdf.js drop-in |

## Roadmap

1. vendor pdf.js into `assets/reader/pdfjs/` (Apache-2.0, NOTICE entry)
2. text layer overlay for real selection on PDF (pdf.js text layer)
3. multi-profile settings list + model picker chip (port from the mod's panel)
4. SAF picker + persisted library (Room)
5. optional: document chat (history in local DB), research Q&A
   (the mod's fake-WebSocket protocol in `docs/PROTOCOL.md` becomes the
   reference for a self-hosted agent server, if ever wanted)
