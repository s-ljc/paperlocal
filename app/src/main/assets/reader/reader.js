/* PaperLocal reader — own code (MIT). Talks to the AI only via window.pl
 * (native bridge): pl.aiFetchAsync / pl.aiStreamAsync / pl.readFile.
 * PDF rendering: bundled pdf.js (MIT, assets/reader/pdfjs/). */
(function () {
    'use strict';

    var qs = new URLSearchParams(location.search);
    var path = qs.get('path') || '';
    document.getElementById('title').textContent =
        decodeURIComponent(path.split('/').pop() || 'PaperLocal');

    /* ---------- native bridge plumbing ---------- */
    var seq = 0, proms = {}, chunks = {};
    window.__plDone = function (id, env) {
        var f = proms[id];
        if (f) { delete proms[id]; f(env); }
    };
    window.__plChunk = function (id, o) {
        var f = chunks[id];
        if (f) f(o.d || '');
    };
    window.__plFile = function (id, env) {
        var f = proms[id];
        if (f) { delete proms[id]; f(env); }
    };
    function bridgeCall(name, args, onChunk) {
        return new Promise(function (res, rej) {
            if (!window.pl || typeof pl[name] !== 'function') { rej(new Error('no bridge')); return; }
            var id = ++seq;
            proms[id] = function (env) {
                if (env && env.ok) res(env.body);
                else rej(new Error('HTTP ' + (env ? env.status : '?') + ' ' +
                        String(env && env.body || '').slice(0, 120)));
            };
            if (onChunk) chunks[id] = onChunk;
            pl[name].apply(pl, [id].concat(args));
            setTimeout(function () {
                if (proms[id]) { delete proms[id]; rej(new Error('timeout')); }
            }, 300000);
        });
    }
    function aiPost(body) {
        return bridgeCall('aiFetchAsync',
            [pl.chatCompletionsUrl(), body, pl.apiKey()]);
    }
    function aiStream(body, onDelta) {
        return bridgeCall('aiStreamAsync',
            [pl.chatCompletionsUrl(), body, pl.apiKey()], onDelta);
    }

    /* ---------- config / model switching ---------- */
    var cfgCache = null;
    function cfg() {
        if (!cfgCache) {
            try { cfgCache = JSON.parse(pl.active()); } catch (e) { cfgCache = {}; }
        }
        return cfgCache || {};
    }
    var mchip = document.getElementById('model');
    function refreshChip() {
        cfgCache = null;
        mchip.textContent = '🤖 ' + (cfg().model || '未配置');
        document.getElementById('chatModel').textContent =
            '🤖 ' + (cfg().model || '未配置');
    }
    refreshChip();
    var sheet = document.getElementById('sheet');
    function openSheet() {
        var list = document.getElementById('profList');
        list.innerHTML = '';
        var ps = [];
        try { ps = JSON.parse(pl.profiles()); } catch (e) {}
        if (!ps.length) {
            list.innerHTML = '<div class="prof">还没有接口，先去首页"AI 接口设置"添加</div>';
        }
        ps.forEach(function (p) {
            var d = document.createElement('div');
            d.className = 'prof' + (p.name === cfg().name ? ' on' : '');
            d.innerHTML = '🤖 ' + esc(p.name) + '<small>' +
                esc(p.baseUrl || '') + '</small>';
            d.onclick = function () {
                try { pl.switchTo(p.name); } catch (e) {}
                refreshChip();
                sheet.style.display = 'none';
            };
            list.appendChild(d);
        });
        sheet.style.display = 'block';
    }
    mchip.onclick = openSheet;
    document.getElementById('chatModel').onclick = openSheet;
    sheet.onclick = function (e) { if (e.target === sheet) sheet.style.display = 'none'; };

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }
    function status(s) { document.getElementById('status').textContent = s; }

    /* ---------- selection translation (cached) ---------- */
    var mem = new Map();
    function cacheKey(text) {
        var h = 0x811c9dc5;
        for (var i = 0; i < text.length; i++) {
            h ^= text.charCodeAt(i); h = Math.imul(h, 0x01000193);
        }
        return (h >>> 0).toString(36) + '_' + text.length;
    }
    var trBtn = document.getElementById('trBtn');
    document.addEventListener('selectionchange', function () {
        var sel = window.getSelection();
        var text = String(sel || '').trim();
        if (text.length > 2 && sel.rangeCount) {
            var r = sel.getRangeAt(0).getBoundingClientRect();
            if (r.width || r.height) {
                trBtn.style.display = 'block';
                trBtn.style.left = Math.max(8, Math.min(
                    innerWidth - 90, r.left + r.width / 2 - 40)) + 'px';
                trBtn.style.top = Math.max(56, r.top - 44) + 'px';
                return;
            }
        }
        trBtn.style.display = 'none';
    });
    trBtn.onclick = function () {
        var text = String(window.getSelection() || '').trim();
        trBtn.style.display = 'none';
        if (!text) return;
        translate(text);
    };
    function translate(text) {
        var key = cacheKey(text);
        if (mem.has(key)) { showTrans(mem.get(key)); return; }
        if (!cfg().model) { status('先配置 AI 接口（首页 → AI 接口设置）'); return; }
        status('翻译中…');
        var body = JSON.stringify({
            model: cfg().model, temperature: 0.3, max_tokens: 4096,
            messages: [
                { role: 'system', content: '你是学术翻译。把用户文本翻译成简体中文，保留专有名词与公式，只输出译文。' },
                { role: 'user', content: text }
            ]
        });
        aiPost(body).then(function (txt) {
            var out = '';
            try { out = JSON.parse(txt).choices[0].message.content.trim(); }
            catch (e) { throw new Error('响应格式异常'); }
            mem.set(key, out);
            showTrans(out);
            status('');
        }).catch(function (e) { status('翻译失败: ' + e); });
    }
    function showTrans(out) {
        var d = document.createElement('div');
        d.className = 'tr';
        d.innerHTML = '<b>译文</b><span class="x">×</span>';
        var t = document.createElement('span');
        t.textContent = out;
        d.appendChild(t);
        d.querySelector('.x').onclick = function () { d.remove(); };
        document.getElementById('pages').prepend(d);
        scrollTo(0, 0);
    }

    /* ---------- PDF rendering ---------- */
    var pdfDoc = null, pageTexts = {};
    var s = document.createElement('script');
    s.src = 'pdfjs/pdf.min.js';
    s.onload = loadPdf;
    s.onerror = function () { status('pdf.js 加载失败'); };
    document.head.appendChild(s);

    function loadPdf() {
        if (!window.pdfjsLib) { status('pdf.js 缺失'); return; }
        if (!path) { status('未指定文件'); return; }
        status('读取文件…');
        bridgeCall('readFile', [path]).then(function (b64) {
            var bin = atob(b64), n = bin.length, u8 = new Uint8Array(n);
            for (var i = 0; i < n; i++) u8[i] = bin.charCodeAt(i);
            pdfjsLib.GlobalWorkerOptions.workerSrc = 'pdfjs/pdf.worker.min.js';
            return pdfjsLib.getDocument({ data: u8 }).promise;
        }).then(function (pdf) {
            pdfDoc = pdf;
            status('共 ' + pdf.numPages + ' 页，加载中…');
            var box = document.getElementById('pages');
            var pending = [];
            for (var p = 1; p <= pdf.numPages; p++) {
                var wrap = document.createElement('div');
                wrap.className = 'page';
                wrap.dataset.page = p;
                box.appendChild(wrap);
                pending.push(p);
            }
            var io = new IntersectionObserver(function (es) {
                es.forEach(function (en) {
                    if (!en.isIntersecting) return;
                    io.unobserve(en.target);
                    renderPage(+en.target.dataset.page, en.target);
                });
            }, { rootMargin: '600px 0px' });
            Array.prototype.forEach.call(box.children, function (el) {
                if (el.dataset.page) io.observe(el);
            });
        }).catch(function (e) { status('PDF 加载失败: ' + e); });
    }

    function renderPage(n, wrap) {
        pdfDoc.getPage(n).then(function (p) {
            var scale = Math.min(2, (innerWidth - 16) / p.getViewport({ scale: 1 }).width);
            var vp = p.getViewport({ scale: scale });
            var c = document.createElement('canvas');
            c.width = vp.width; c.height = vp.height;
            wrap.style.height = vp.height + 'px';
            wrap.appendChild(c);
            return p.render({ canvasContext: c.getContext('2d'), viewport: vp })
                .promise.then(function () { return p.getTextContent(); })
                .then(function (tc) {
                    var layer = document.createElement('div');
                    layer.className = 'textLayer';
                    tc.items.forEach(function (it) {
                        if (!it.str) return;
                        var tx = pdfjsLib.Util.transform(vp.transform, it.transform);
                        var sp = document.createElement('span');
                        sp.textContent = it.str;
                        sp.style.left = tx[4] + 'px';
                        sp.style.top = (tx[5] - it.height * vp.scale) + 'px';
                        sp.style.fontSize = (it.height * vp.scale) + 'px';
                        layer.appendChild(sp);
                        var w0 = sp.offsetWidth || 1;
                        sp.style.transform = 'scaleX(' +
                            (it.width * vp.scale / w0) + ')';
                    });
                    wrap.appendChild(layer);
                    pageTexts[n - 1] = tc.items.map(function (x) { return x.str; }).join(' ');
                    var done = box_done();
                    if (done) status('共 ' + pdfDoc.numPages + ' 页');
                });
        }).catch(function (e) { status('第 ' + n + ' 页渲染失败: ' + e); });
    }
    function box_done() {
        var kids = document.getElementById('pages').children;
        for (var i = 0; i < kids.length; i++) {
            if (kids[i].dataset.page && !kids[i].querySelector('canvas')) return false;
        }
        return true;
    }

    /* ---------- document chat (streaming) ---------- */
    var chat = document.getElementById('chat');
    var msgs = document.getElementById('msgs');
    var history = [];
    document.getElementById('chatBtn').onclick = function () {
        chat.classList.add('open');
        if (!history.length) {
            addMsg('a', '我会基于当前打开的论文回答。常见问题：\n· 总结这篇论文的贡献\n· 方法部分讲了什么\n· 实验结果如何');
        }
        document.getElementById('inp').focus();
    };
    document.getElementById('chatClose').onclick = function () {
        chat.classList.remove('open');
    };
    function addMsg(role, text, cls) {
        var d = document.createElement('div');
        d.className = 'msg ' + (cls || role);
        d.textContent = text;
        msgs.appendChild(d);
        msgs.scrollTop = msgs.scrollHeight;
        return d;
    }
    var sending = false;
    function send() {
        var inp = document.getElementById('inp');
        var q = inp.value.trim();
        if (!q || sending) return;
        if (!cfg().model) { addMsg('a', '先配置 AI 接口（首页 → AI 接口设置）', 'err'); return; }
        inp.value = '';
        sending = true;
        document.getElementById('send').disabled = true;
        addMsg('u', q);
        var bubble = addMsg('a', '…');
        var acc = '';
        var ctx = docContext();
        var sys = '你是论文阅读助手。基于下面提供的论文文本回答用户问题；' +
            '论文文本中没有的信息明确说明不知道。回答用简体中文，简洁准确。\n\n' +
            '【论文文本】\n' + ctx;
        var body = JSON.stringify({
            model: cfg().model, stream: true, temperature: 0.4, max_tokens: 2048,
            messages: [{ role: 'system', content: sys }]
                .concat(history).concat([{ role: 'user', content: q }])
        });
        aiStream(body, function (d) {
            acc += d;
            bubble.textContent = acc;
            msgs.scrollTop = msgs.scrollHeight;
        }).then(function (full) {
            bubble.textContent = acc || String(full).trim() || '(空回答)';
            history.push({ role: 'user', content: q });
            history.push({ role: 'assistant', content: bubble.textContent });
            if (history.length > 12) history = history.slice(-12);
        }).catch(function (e) {
            bubble.textContent = '失败: ' + e;
            bubble.className = 'msg err';
        }).then(function () {
            sending = false;
            document.getElementById('send').disabled = false;
        });
    }
    document.getElementById('send').onclick = send;
    document.getElementById('inp').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') send();
    });
    function docContext() {
        if (!pdfDoc) return '(论文文本尚未加载完成)';
        var parts = [];
        for (var i = 0; i < pdfDoc.numPages; i++) {
            parts.push(pageTexts[i] || '');
        }
        var all = parts.join('\n');
        if (!all.trim()) return '(论文文本尚未加载完成)';
        if (all.length > 24000) {
            all = all.slice(0, 16000) + '\n……[中间省略]……\n' + all.slice(-6000);
        }
        return all;
    }
})();
