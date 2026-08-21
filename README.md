# SpendTrace（钱迹 Love）

> Simple expense‑tracking app: one‑click screenshot, auto‑recognize and log entries. Ultra‑minimalist, 100% local‑only.

付款 → 双击/电源键截图 → 系统截图一落盘 → 自动 OCR → 解析金额/商户/时间/分类 → 写入账本 → 通知栏提示确认。
**全程不碰 App**，是手机端唯一能做到「付完款→截图→真·零操作」的记账路径（iOS 第三方 App 几乎不可行）。

## 核心理念

- **零操作自动记账**：付款后只管截图，App 在后台监听截图目录 + 端侧 OCR，跑完弹通知确认。
- **100% 本地、无云端**：OCR 走 ML Kit 端侧（离线、免费、隐私），账本存 Room/SQLite，**不联网、不上传、不依赖任何远程服务**。
- **复核算账闸门**：识别不直接入库，必须经人确认（多候选金额点选/方向/日期/分类可改）才能写库，规避 OCR 误判。
- **农历友好**：支持中文商户词字典（已扩充 200+ 商户关键词，含元初食品/蜜雪冰城/海底捞/学而思/链家/叮咚买菜等）。

## 仓库结构

```
SpendTrace/
├── auto-ledger-android/        # Android 应用（Kotlin/Compose/Material3/Room/ML Kit）
│   ├── README.md               #   构建说明（AS 打开 / ./gradlew assembleDebug）
│   ├── INSTALL.md              #   端用户装包与使用
│   ├── OVERVIEW.md             #   产品/架构总览
│   ├── app/                    #   源码（OCR 解析/复核/截图监听/账本/统计）
│   ├── gradlew + wrapper/      #   完整 Gradle Wrapper（distribution 走腾讯镜像）
│   └── build_fixed.ps1         #   本机构建脚本（可选）
└── auto-ledger-core/           # 平台无关核心引擎（TypeScript）
    └── src/categorize/dictionary.ts   # 分类关键词字典（与 Android 侧完全一致）
```

两套代码独立、无自动同步：TS 引擎当"试验田"快速迭代；Android 侧定稿后手动对齐关键路径（已对齐金额/时间/商户/平台/分类/截图监听/入库）。

## 快速开始

- **装包使用** → [`auto-ledger-android/INSTALL.md`](auto-ledger-android/INSTALL.md)（手机装 APK + 首次授权 + 截图即记账）
- **开发者构建** → [`auto-ledger-android/README.md`](auto-ledger-android/README.md)（AS 打开 / `./gradlew assembleDebug` 均可，已实测通过）
- **架构与设计** → [`auto-ledger-android/OVERVIEW.md`](auto-ledger-android/OVERVIEW.md)

## 当前版本

- **当前版号：0.9.0**——核心记账闭环完整、可日常使用；P1 草稿持久化 + P2 付款通知自动识别 + 单元测试补齐后，正式升 **1.0.0**。
- 历史：`v1.0` tag 为早期内部里程碑（OCR 容错重构、多图上传、日历补记/翻月热力、上传日期归属、统计按月联动、分类关键词扩充、日期识别增强、复核页日期选择器、杂志靛蓝瓷主题等已全部并入 0.9.0）。
- 每版均通过本机 `./gradlew assembleDebug` 实测产出 APK。

## 后续规划（按需排期）

- P1：复核草稿持久化（进程被杀不丢单）。
- P2：通知监听自动识别（微信/支付宝/云闪付付款成功通知直入复核队列，绕开截图 OCR 链路）。
- P2：各 APP 规则库 / 置信度机制 / 单元测试。

## 许可

个人自用项目，未开源。如需使用/参考请联系作者。
