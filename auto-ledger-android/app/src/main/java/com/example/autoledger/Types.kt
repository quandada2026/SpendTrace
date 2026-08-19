package com.example.autoledger

import android.graphics.Rect

/** OCR 单块文字（与核心引擎 OcrBlock 对应）。 */
data class OcrBlock(
    val text: String,
    val confidence: Float,
    val bbox: Rect? = null,
)

/** OCR 整体结果（与核心引擎 OcrResult 对应）。 */
data class OcrResult(
    val text: String,
    val blocks: List<OcrBlock>,
    val engine: String,
)

/** 结构化解析结果（与核心引擎 ParsedReceipt 对应）。 */
data class ParsedReceipt(
    val platform: String,
    val amount: Double?,
    val currency: String,
    val time: String?,
    val merchant: String?,
    val rawText: String,
    val needsReview: Boolean,
)

/** 平台常量（与核心引擎保持一致）。 */
object Platforms {
    const val WECHAT = "wechat"
    const val ALIPAY = "alipay"
    const val BANK = "bank"
    const val UNKNOWN = "unknown"
}

/** 分类常量。 */
object Categories {
    // 支出
    const val 餐饮 = "餐饮"
    const val 购物 = "购物"
    const val 交通 = "交通"
    const val 休闲 = "休闲"
    const val 医疗 = "医疗"
    const val 学习 = "学习"
    const val 人情支出 = "人情支出"
    const val 住房 = "住房"
    const val 其他 = "其他"
    // 收入
    const val 工资 = "工资"
    const val 转账 = "转账"
    const val 其他收入 = "其他收入"
}
