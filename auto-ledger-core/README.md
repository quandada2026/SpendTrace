# auto-ledger-core

平台无关的「截图自动记账」核心引擎，外加一个**桌面零操作外壳**：
监听截图目录 → 新截图落盘自动 OCR → 解析 → 分类 → 入库，并配本地查看/补录页面。
核心逻辑与平台无关，安卓 `MediaStore` 观察者可直接复用同一套编排。

## 架构

```
src/
  ocr/          OCR 抽象层（接口 + Mock/Local(Tesseract)/Cloud 三实现，可切换）
  parse/        结构化解析（金额/时间/商户/平台，纯函数可单测）
  categorize/   商户关键词 → 分类 字典
  store/        LedgerStore 接口 + MemoryStore / JsonStore
  index.ts      processScreenshot() 编排：OCR→解析→分类→入库
  shell/        桌面外壳
    config.ts     env 配置（WATCH_DIR / DATA_FILE / PORT / OCR_MODE ...）
    watcher.ts    chokidar 监听截图目录 → 落盘即调 processScreenshot（零操作闭环）
    server.ts     本地 HTTP 服务：账本 API + 静态 UI
    cli.ts        启动入口（同时起 watcher + server）
  ui/           账本查看页面（列表/月统计/待核对修正/手动上传）
```

## 运行

```bash
npm install
npm test       # 单元测试 + 外壳集成测试（解析/分类/端到端/watcher/server）
npm run demo   # 用 samples/ 跑一遍端到端，打印账本

# 启动桌面外壳（看守 + 查看页面）
npm run shell
# 打开 http://localhost:5173
```

### 让它"零操作"记账
把 `WATCH_DIR` 指向你的系统截图目录，截图一保存就自动记账：

```bash
# Windows 示例：监听系统的截图文件夹
WATCH_DIR="$USERPROFILE/Pictures/Screenshots" npm run shell

# 或监听本仓库自带的 inbox/ 目录，把截图丢进去即可
npm run shell
```

### OCR 切换
- 默认端侧 `tesseract.js`（中文、免费、离线；首次使用会按需下载语言包）。
- 切云端（腾讯/百度/阿里等）：设置环境变量
  `OCR_MODE=cloud CLOUD_ENDPOINT=... CLOUD_API_KEY=... CLOUD_NAME=...`
  （云端响应映射在 `src/shell/cli.ts` 的 `buildEngine` 里按需调整）。

## 设计要点

- **解析器是纯函数**，不依赖 OCR 实现，可独立单测；这是整个项目的核心价值。
- **容错**：金额识别失败时 `needsReview=true`，仍入库并保留 `rawText`，
  供用户在 UI 里「修正」或「删除」，不丢数据。
- 没有双击截图功能的手机：用页面上的「手动上传截图」作为兜底路径。

## 下一步

- **Android**：用 `MediaStore` 内容观察者替换 `watcher.ts` 的 chokidar，触发逻辑完全一致。
- **UI 增强**：月报图表、分类统计、导出 CSV、iOS 端（受系统限制只能"分享到 App"）。
