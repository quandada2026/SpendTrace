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

/** 单条候选金额（OCR 容错：保留多候选，供复核页下拉切换）。 */
data class AmountCandidate(
    val value: Double,
    val score: Int,
    val sourceLine: String,
)

/** 收支方向推断结果；UNKNOWN 强制人工选择，绝不瞎猜。 */
enum class TradeType { EXPENSE, INCOME, UNKNOWN }

/**
 * OCR 解析后的复核草稿。analyze() 产出，commit() 消费。
 * 绝不自动入库——UI 必须让用户确认后才 commit。
 */
data class ReviewDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val success: Boolean = true,
    val rawText: String = "",
    val confidence: Float = 0f,
    /** 临时原图绝对路径（供复核页缩略图），commit/discard 后删除。 */
    val imagePath: String? = null,
    val candidateMoneyList: List<AmountCandidate> = emptyList(),
    val suggestMoney: Double? = null,
    val merchantCandidates: List<String> = emptyList(),
    val suggestMerchant: String? = null,
    val tradeTime: String? = null,
    val tradeType: TradeType = TradeType.UNKNOWN,
    val platform: String = "unknown",
    /** 用户复核时可手动指定分类；为 null 时由自动归类决定。 */
    val category: String? = null,
    val warningMsg: String? = null,
    val source: String = "manual",
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
