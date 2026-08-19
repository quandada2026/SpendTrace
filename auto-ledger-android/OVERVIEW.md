# 自动记账 · 安卓版（AutoLedger）交付概览

## 成果
把平台无关核心引擎（`auto-ledger-core`）套上安卓原生外壳，实现**真·零操作**自动记账：
付款后截图 → 系统截图落盘 → 自动 OCR → 解析 → 分类 → 入库 → 通知。
这是手机上唯一能「付完款→截图→全程不碰 App」的路径（iOS 第三方不可行）。

## 工程结构
```
auto-ledger-android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   # Gradle 8.9
├── app/build.gradle.kts                       # AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00
└── app/src/main/
    ├── AndroidManifest.xml                     # 权限 + 前台服务(dataSync) + 入口
    ├── java/com/example/autoledger/
    │   ├── AutoLedgerApplication.kt            # 初始化 Room
    │   ├── Types.kt                            # OcrResult / ParsedReceipt / 平台·分类常量
    │   ├── data/      LedgerEntry·LedgerDao·AppDatabase          # Room 账本
    │   ├── ocr/       OcrEngine·MlKitOcrEngine·CloudOcrEngine·OcrEngineProvider
    │   ├── parse/     Amount·Time·Merchant·Platform·ScreenshotParser   # 纯函数解析
    │   ├── categorize/ CategoryDictionary·Categorizer
    │   ├── watcher/   ScreenshotObserver·ScreenshotWatcher·ScreenshotService  # 零操作核心
    │   ├── pipeline/   LedgerPipeline          # processUri → OCR→解析→分类→入库
    │   └── ui/        MainActivity·LedgerViewModel   # 列表/待核对/修正/上传/设置
    └── res/values/strings.xml
```

## 关键实现点
- **零操作监听**：`ScreenshotObserver`（ContentObserver）+ 后台 `HandlerThread` 监听 `MediaStore.Images`，过滤 `screenshot` 路径、按 id 去重；`ScreenshotService` 前台常驻，新截图即触发流水线。与桌面版 `watcher.ts`(chokidar) 逻辑同构。
- **OCR**：默认 `MlKitOcrEngine`（端侧中文、免费离线、无 Key）；`CloudOcrEngine` 预留可切（设置页填 endpoint/key），`parseResponse()` 为厂商适配扩展点。
- **解析/分类**：逐条从核心引擎 TS 移植为 Kotlin 纯函数（金额强绑定正则、时间归一、商户/平台识别、分类字典），行为一致。
- **存储**：Room（本机 SQLite，无云端依赖）；`needsReview` 标记金额未识别项并保留 `rawText`，App 内可修正，不丢数据。
- **UI**：Compose —— 当月统计、账本/待核对 Tab、行内修正弹窗、手动上传截图兜底（无双击截图手机）、OCR 引擎切换设置。

## 构建与运行
- 用 Android Studio（较新版本）打开本目录 → 同步依赖（需联网）→ 连手机/模拟器(API30+) → Run。
- App 内「开启监听」并授权「读取图片/通知」→ 付款截图即自动记账；无双击功能用「上传」按钮手动选图。
- ⚠️ 本项目在交付环境（无 Android SDK）**未做编译验证**；已尽量贴近可编译形态并补齐关键 import。版本组合见 README「版本与构建」，如同步报错按 Android Studio 推荐值统一升级即可（逻辑不受影响）。

## 与核心引擎一致性
解析/分类规则、OCR 抽象、编排入口已建立一一映射（详见 README 表格），后续两边同步演进即可。
