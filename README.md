# PaperLocal

**开源本地 AI 文献阅读器 —— 纯客户端。** 一个 PDF 阅读器 + 你自己的 OpenAI 兼容模型 API，就拥有对话、翻译、研究问答的全部 AI 能力。**不登录、不联网到任何厂商后台、不需要账号**；AI 请求只发往你自己配置的 `baseUrl`。

> **免责声明**：PaperLocal 是独立项目，与任何阅读器厂商无隶属、赞助或背书关系。本项目不使用任何厂商的代码、商标或美术资源（协议分析文档除外——接口形状不受版权保护）。本项目 MIT 许可。

## 状态

✅ **0.2.0 可用版**（[Releases 下载 APK](https://github.com/s-ljc/paperlocal/releases/download/v0.2.0/PaperLocal-0.2.0.apk)，Android 7.0+）

- 导入本地 PDF（系统文件选择器，Android 13+ 分区存储兼容）
- pdf.js 渲染 + 可选文字层（懒加载，长文档不卡）
- 选段翻译（浮动"翻译"按钮，结果缓存）
- 文档对话（流式输出，全文上下文，多轮历史）
- 多接口管理：增删改、切换、GET /models 自动填模型名（兼连接测试）
- 待办：本地端侧模型（免 API）、标注导出、阅读位置记忆

## 设计

```
┌───────────────────────────── 手机 ─────────────────────────────┐
│ PaperLocal APK（全部自研，MIT）                                  │
│  MainActivity ── 本地文献库（扫描本地 PDF，无云同步）              │
│  ReaderActivity ─ WebView 阅读器（pdf.js / pdfium，本地渲染）     │
│       │  window.pl JS 桥（@JavascriptInterface）                 │
│       │   ├─ aiFetchAsync ──> AiClient ──────────┐ 原生 HTTP，   │
│  SettingsActivity ─ AI 接口管理（增删改/测连接/切换）│ 无 CORS     │
│  AiCfgStore ─ SharedPreferences 多接口档案        │             │
└──────────────────────────────────────────────────┼─────────────┘
                                                   ▼
                            你的 OpenAI 兼容服务（vLLM / Ollama / LM Studio / …）
                            局域网 http://192.168.x.x:8000/v1 或公网 https
```

- **纯客户端**：PDF 转 HTML/渲染在手机本地完成；没有登录、没有云、没有额度。
- **AI 三件套**：选段翻译（带 IndexedDB 缓存）、文档对话、研究问答——全部经原生桥直发你的 API。
- **协议兼容**（可选）：`docs/PROTOCOL.md` 记录了一套可自建的 Agent WebSocket 事件协议，想接自建服务端时照抄即可。

## 目录

```
app/src/main/java/com/paperlocal/
  MainActivity.java        入口：本地文献列表
  ReaderActivity.java      WebView 宿主 + 桥挂载
  SettingsActivity.java    AI 接口管理界面
  ai/AiClient.java         OpenAI 兼容 POST（原生 HTTP，异步）
  ai/AiCfgStore.java       多接口档案（SharedPreferences）
  bridge/ReaderBridge.java window.pl：aiFetchAsync / profiles / switchTo
  library/LibraryStore.java 本地 PDF 扫描
app/src/main/assets/reader/ 阅读器前端（HTML/JS，自研）
docs/                      架构 + Agent 协议分析
```

## 构建

```sh
# Android SDK 34 + JDK 17
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 许可与来源标注

- 本项目代码 MIT（见 [LICENSE](LICENSE)）。
- 引入第三方渲染件时（pdf.js Apache-2.0 / pdfium BSD / pdf2htmlEX MIT）在 [NOTICE](NOTICE) 登记。
- `docs/PROTOCOL.md` 为对公开接口行为的观察记录，非厂商代码。
