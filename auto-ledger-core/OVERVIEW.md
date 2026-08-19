# 自动记账 · 交付概览（核心引擎 + 桌面零操作外壳）

## 已完成
- **核心引擎（平台无关）**：OCR 抽象层（Mock / Local Tesseract / Cloud 三实现，可切换）、
  结构化解析（金额/时间/商户/平台，纯函数可单测）、商户→分类字典、存储（Memory/Json）、
  编排 `processScreenshot()`（OCR→解析→分类→入库）。
- **桌面零操作外壳**：
  - `src/shell/watcher.ts`：chokidar 监听截图目录，新图落盘自动调 `processScreenshot` → `JsonStore`。
    触发逻辑与安卓 `MediaStore` 内容观察者同构（仅文件来源不同）。
  - `src/shell/server.ts`：本地 HTTP 服务，账本 API（列表 / 手动上传OCR / 修正 PATCH / 删除 DELETE）+ 静态 UI。
  - `src/ui/`：账本查看页（当月统计、待核对高亮、行内修正、手动上传截图兜底）。
  - `src/shell/cli.ts` + `config.ts`：env 配置（`WATCH_DIR`/`DATA_FILE`/`PORT`/`OCR_MODE`）一键启动。
- **测试**：`npm test` 16/16 通过（解析/分类/端到端/`watcher` 触发/`server` API）。
- **冒烟验证**：起服务 → 页面与 API 返回 200；向监听目录丢入 PNG → 自动 OCR 记账成功（`STORE_LEN=1`）。

## 关键决策与坑
- 金额抽取：初版"关键词上下文 + 权重"因 `'金额'` 是 `'支付金额'` 子串，导致后一笔金额蹭到前一笔
  标签误判（¥1000 赢 ¥88）；改为「关键词 + 紧随金额」强绑定正则修复，并补单测。
- `server.ts` 曾因导出函数与 `http.createServer` 同名，调用时递归指向自身 → 栈溢出；将 http 导入改名修复。
- OCR 不阻塞引擎：`tesseract.js` 动态 import，未安装也不影响核心与测试；真实中文识别在首次使用时按需下载语言包。

## 运行
```bash
cd auto-ledger-core
npm install
npm test
npm run shell                                   # 打开 http://localhost:5173
WATCH_DIR="$USERPROFILE/Pictures/Screenshots" npm run shell   # 零操作：监听系统截图目录
```
OCR 切换：默认端侧 Tesseract；设 `OCR_MODE=cloud CLOUD_ENDPOINT=… CLOUD_API_KEY=…` 切云端。

## 下一步
- **Android**：用 `MediaStore` 内容观察者替换 `watcher.ts` 的 chokidar，触发逻辑一致，结果写 Room。
- **增强**：月报图表、分类统计、CSV 导出；iOS 端受系统限制只能"分享到 App"。
