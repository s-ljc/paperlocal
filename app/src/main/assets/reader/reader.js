/* PaperLocal reader — own code. Talks to the AI only via window.pl
 * (native bridge): pl.aiFetchAsync(id, url, body, auth) -> window.__plDone.
 * PDF rendering: drop pdf.js into ./pdfjs/ (pdf.min.js + pdf.worker.min.js);
 * until then the page shows the file path and selection translation works
 * on any text you type/select. */
(function () {
    'use strict';

    var qs = new URLSearchParams(location.search);
    var path = qs.get('path') || '';
    document.getElementById('title').textContent =
        path.split('/').pop() || 'PaperLocal';

    /* ---------- native bridge plumbing ---------- */
    var seq = 0, proms = {};
    window.__plDone = function (id, env) {
        var f = proms[id];
        if (f) { delete proms[id]; f(env); }
    };
    function aiPost(body) {
        return new Promise(function (res, rej) {
            if (!window.pl || !pl.aiFetchAsync) { rej(new Error('no bridge')); return; }
            var id = ++seq;
            proms[id] = function (env) {
                if (env && env.ok) res(env.body);
                else rej(new Error('HTTP ' + (env ? env.status : '?')));
            };
            pl.aiFetchAsync(id, pl.chatCompletionsUrl(), body, cfgKey());
            setTimeout(function () {
                if (proms[id]) { delete proms[id]; rej(new Error('timeout')); }
            }, 200000);
        });
    }
    var cfgCache = null;
    function cfg() {
        if (!cfgCache && window.pl && pl.active) {
            try { cfgCache = JSON.parse(pl.active()); } catch (e) { cfgCache = {}; }
        }
        return cfgCache || {};
    }
    function cfgKey() { return cfg().apiKey || ''; }
    var mchip = document.getElementById('model');
    mchip.textContent = '🤖 ' + (cfg().model || '未配置');
    mchip.onclick = function () {
        cfgCache = null;
        mchip.textContent = '🤖 ' + (cfg().model || '未配置');
    };

    /* ---------- selection translation (cached) ---------- */
    var mem = new Map();
    function cacheKey(text) {
        var h = 0x811c9dc5;
        for (var i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 0x01000193); }
        return (h >>> 0).toString(36) + '_' + text.length;
    }
    document.getElementById('doTr').onclick = function () {
        var text = String(window.getSelection() || '').trim();
        if (!text) { status('先选中一段文字（或长按输入）'); return; }
        var key = cacheKey(text);
        if (mem.has(key)) { showTrans(mem.get(key)); return; }
        status('翻译中…');
        var body = JSON.stringify({
            model: cfg().model, temperature: 0.3, max_tokens: 4096,
            messages: [
                { role: 'system', content: '你是学术翻译。把用户文本翻译成中文，保留段落结构与 LaTeX/HTML 标记，只输出译文。' },
                { role: 'user', content: text }
            ]
        });
        aiPost(body).then(function (txt) {
            var j = JSON.parse(txt);
            var out = j.choices[0].message.content.trim();
            mem.set(key, out);
            showTrans(out);
            status('');
        }).catch(function (e) { status('翻译失败: ' + e); });
    };
    function showTrans(out) {
        var d = document.createElement('div');
        d.className = 'tr';
        d.textContent = out;
        document.getElementById('pages').prepend(d);
    }
    function status(s) { document.getElementById('status').textContent = s; }

    /* ---------- PDF rendering (pdf.js drop-in) ---------- */
    var pdfjsReady = document.createElement('script');
    pdfjsReady.src = 'pdfjs/pdf.min.js';
    pdfjsReady.onload = render;
    pdfjsReady.onerror = function () {
        status('把 pdf.js 放入 assets/reader/pdfjs/ 后即可渲染 PDF；翻译选段现在就能用。');
    };
    document.head.appendChild(pdfjsReady);

    function render() {
        if (!window.pdfjsLib || !path) return;
        pdfjsLib.GlobalWorkerOptions.workerSrc = 'pdfjs/pdf.worker.min.js';
        // file:// pages can't fetch() arbitrary paths; the native side exposes
        // the bytes lazily — skeleton uses XHR on a file:// URL (works with
        // setAllowFileAccess).
        pdfjsLib.getDocument(path).promise.then(function (pdf) {
            status('共 ' + pdf.numPages + ' 页');
            var box = document.getElementById('pages');
            for (var i = 1; i <= pdf.numPages; i++) {
                (function (n) {
                    pdf.getPage(n).then(function (p) {
                        var c = document.createElement('canvas');
                        p.render({ canvasContext: c.getContext('2d'),
                                   viewport: p.getViewport({ scale: 1.5 }) });
                        box.appendChild(c);
                    });
                })(i);
            }
        }).catch(function (e) { status('PDF 加载失败: ' + e); });
    }
})();
