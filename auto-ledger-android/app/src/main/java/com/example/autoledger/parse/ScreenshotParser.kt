package com.example.autoledger.parse

import com.example.autoledger.AmountCandidate
import com.example.autoledger.OcrResult
import com.example.autoledger.ReviewDraft
import com.example.autoledger.TradeType

/** 把 OCR 结果解析为结构化复核草稿。核心纯函数，可独立单元测试。 */
object ScreenshotParser {

    fun parse(result: OcrResult): ReviewDraft {
        val text = result.text ?: ""
        val amountCandidates = AmountExtractor.extractCandidates(text)
        val suggestMoney = amountCandidates.maxByOrNull { it.score }?.value
        val merchants = MerchantExtractor.extractCandidates(text)
        val time = TimeExtractor.extract(text)
        val platform = PlatformExtractor.detect(text)
        val tradeType = inferTradeType(text)
        val conf = computeConfidence(amountCandidates)
        val warning = buildWarning(amountCandidates, tradeType, conf)
        return ReviewDraft(
            success = true,
            rawText = text,
            confidence = conf,
            candidateMoneyList = amountCandidates,
            suggestMoney = suggestMoney,
            merchantCandidates = merchants,
            suggestMerchant = merchants.firstOrNull(),
            tradeTime = time,
            tradeType = tradeType,
            platform = platform,
            warningMsg = warning,
        )
    }

    /** 收支方向推断：命中收入词 → 收入；命中支出词 → 支出；否则未知（强制人工）。 */
    private fun inferTradeType(text: String): TradeType {
        if (text.contains(Regex("收到|已到账|退款成功|转账收入|收款成功|入账|退还|收款方"))) return TradeType.INCOME
        if (text.contains(Regex("实付|支付成功|消费|付款|支出|扣款|已付|支付金额"))) return TradeType.EXPENSE
        return TradeType.UNKNOWN
    }

    /** 置信度：无候选=0；单候选=0.85；多候选用 top1-top2 分差估计（分差小=待核对）。 */
    private fun computeConfidence(cands: List<AmountCandidate>): Float {
        if (cands.isEmpty()) return 0f
        if (cands.size == 1) return 0.85f
        val sorted = cands.sortedByDescending { it.score }
        val gap = (sorted[0].score - sorted[1].score).coerceAtMost(60)
        return (0.5f + gap / 60f * 0.49f).coerceIn(0f, 0.99f)
    }

    private fun buildWarning(cands: List<AmountCandidate>, type: TradeType, conf: Float): String? {
        if (cands.isEmpty()) return "未识别到金额，请核对或手动记账"
        if (type == TradeType.UNKNOWN) return "收支方向无法判断，请选择支出 / 收入"
        if (conf < 0.6f) return "识别置信度较低，请核对金额与商户"
        return null
    }
}
