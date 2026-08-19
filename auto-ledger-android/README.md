# 自动记账 · 安卓版（AutoLedger）

把「核心引擎」（`../auto-ledger-core`）套上安卓外壳，实现**真·零操作**自动记账：
付款后双击/电源键截图 → 系统截图一落盘 → 自动 OCR → 解析 → 分类 → 入库 → 通知。
这是手机上唯一能做到「付完款→截图→全程不碰 App」的路径（iOS 第三方 App 基本不可行）。

> ⚠️ 本项目在交付环境（无 Android SDK）**未做编译验证**，需你在 Android Studio 中同步构建。
> 代码已尽量贴近可编译形态，并标注了所有需按本机环境确认的点（见「版本与构建」）。

---

## 技术栈
- 语言：Kotlin 1.9.24
- UI：Jetpack Compose（Material 3）
- 存储：Room（本机 SQLite，无任何云端依赖）
- OCR：`com.google.mlkit:text-recognition-chinese`（端侧、简体中文、免费、离线、无需 Key）
- 截图监听：`MediaStore` 内容观察者 + 前台常驻服务
- 协程：kotlinx-coroutines

## 工程结构
```
auto-ledger-android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/src/main/
    ├── AndroidManifest.xml
    ├── java/com/example/autoledger/
    │   ├── AutoLedgerApplication.kt        # 初始化 Room
    │   ├── Types.kt                        # OcrResult / ParsedReceipt / 平台·分类常量
    │   ├── data/      LedgerEntry.kt · LedgerDao.kt · AppDatabase.kt   # Room 账本
    │   ├── ocr/       OcrEngine.kt · MlKitOcrEngine.kt · CloudOcrEngine.kt · OcrEngineProvider.kt
    │   ├── parse/     Amount/Time/Merchant/Platform/ ScreenshotParser   # 结构化解析（纯函数）
    │   ├── categorize/ CategoryDictionary.kt · Categorizer.kt
    │   ├── watcher/   ScreenshotObserver.kt · ScreenshotWatcher.kt · ScreenshotService.kt  # 零操作核心
    │   ├── pipeline/   LedgerPipeline.kt    # processUri → OCR→解析→分类→入库
    │   └── ui/        MainActivity.kt · LedgerViewModel.kt   # 账本/待核对/修正/设置/上传
    └── res/values/strings.xml
```

## 与核心引擎（桌面版）的一致性
桌面版TS规则已**逐条移植**为 Kotlin，行为一致。映射：

| 桌面版(TS) | 安卓版(Kotlin) | 说明 |
|---|---|---|
| `src/parse/amount.ts` | `parse/AmountExtractor.kt` | 关键词强绑定正则 + 兜底最大值（已修复跨金额误归因 bug） |
| `src/parse/time.ts` | `parse/TimeExtractor.kt` | 年月日 / 今天·昨日 归一化 |
| `src/parse/merchant.ts` | `parse/MerchantExtractor.kt` | 标签匹配 + 「给XX转账」句式 |
| `src/parse/platform.ts` | `parse/PlatformExtractor.kt` | 微信/支付宝/银行关键词 |
| `src/parse/index.ts` | `parse/ScreenshotParser.kt` | 组装 ParsedReceipt |
| `src/categorize/*` | `categorize/*` | 商户关键词字典完全一致 |
| `src/ocr/*` | `ocr/*` | 抽象层；MlKit 对应 local(Tesseract) |
| `src/shell/watcher.ts`(chokidar) | `watcher/ScreenshotObserver+Service` | 监听逻辑同构 |
| `src/index.ts processScreenshot` | `pipeline/LedgerPipeline.kt` | 编排入口一致 |
| `src/store/json.ts` | `data/Room` | 持久化等价 |

## 运行步骤
1. 用 **Android Studio（Hedgehog / Iguana 或更新）** 打开本目录。
2. 首次打开若提示「Gradle wrapper 未找到」，选择让 IDE 生成 / 使用默认 wrapper（已附带 `gradle-wrapper.properties`）。
3. 同步依赖（会自动下载 Compose BOM、Room、ML Kit、Coroutines）。需要联网。
4. 连接安卓手机（USB 调试）或启动模拟器（建议 API 30+ 以验证截图监听）。
5. `Run 'app'`。
6.  App 内点「开启监听」→ 系统授予「读取图片 / 通知」权限 → 到微信/支付宝付一笔款 → 截图 → 通知栏弹出「已自动记账」，App 列表出现该笔。
7.  无双击截图功能的手机：用 App 右上角「上传」按钮手动选图兜底。

## 权限说明
- `READ_MEDIA_IMAGES`（API33+）/ `READ_EXTERNAL_STORAGE`（≤32）：监听并读取截图，**必须**。
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`（API34+）：常驻监听服务，必带。
- `POST_NOTIFICATIONS`：提示记账结果 / 常驻通知。
- 应用首次进入会弹权限请求；被拒则监听不工作（这是系统限制，非代码问题）。

## OCR 切换（满足「可切换」要求）
- 默认 `local`：ML Kit 端侧，免费离线。
- 设置页切 `cloud` 并填 endpoint / apiKey：走 `CloudOcrEngine`，HTTP 上传 Base64 图片。
  `CloudOcrEngine.parseResponse()` 是按厂商适配的扩展点（默认原样返回响应体），
  接入腾讯/百度/阿里时重写该方法提取 text 字段即可，无需改动监听与解析链路。

## 版本与构建（需按本机确认的点）
以下为可编译的稳定组合，如你的 Android Studio / AGP 较新，按需升级：
- AGP `8.5.2`、Kotlin `1.9.24`、Compose 编译器扩展 `1.5.14`、Compose BOM `2024.06.00`
- Room `2.6.1`、Lifecycle `2.8.3`、Activity Compose `1.9.1`
- ML Kit 中文识别 `16.0.0`
- compileSdk/targetSdk `34`，minSdk `26`，JDK `17`

若同步后报版本不兼容，统一把上述版本升到 Android Studio 推荐值即可（逻辑不受影响）。

## 已知边界 / 后续可优化
- 解析规则覆盖主流支付截图；冷门商户/新平台布局可在 `parse/*` 与 `categorize/CategoryDictionary` 增补。
- 清单金额识别失败会标记 `needsReview` 并保留 `rawText`，App 内可点开修正（不丢数据）。
- 前台服务常驻会显示通知（安卓强制要求），可在系统设置将其设为「最小化/无声」。
- 暂未做「跨设备同步 / CSV 导出 / 月报图表」，可在 UI 层追加（DAO 已支持按月汇总）。
