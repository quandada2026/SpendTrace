package com.example.autoledger.parse

/**
 * 金额抽取（与核心引擎行为一致）。
 *
 * 策略：
 *  1) 优先「金额关键词 + 紧随其后的金额」强绑定正则，避免"余额 ¥1000"蹭"支付金额"字样误归因；
 *  2) 无强信号时，扫描全文所有数字候选：
 *     - 带小数的（XX.XX）最像金额，优先取最大；
 *     - 整数作为补充，过滤 1900-2099 年份与"余额/剩余/额度"等非支付上下文；
 *     - 值域限定 0.01~999999。
 *
 * 容错真实 OCR 失真：全角符号归一、¥ 错为 4、数字周围夹字母、千分位逗号、中文"x元"。
 */
object AmountExtractor {

    private val KEYWORD_RE = Regex(
        "(支付金额|付款金额|交易金额|实付金额|应付金额|需付款|合计金额|合计|总额|收款金额|消费金额|扣款金额|金额)\\s*[：:]?\\s*¥?\\s*-?([\\d,]+(?:\\.\\d{1,2})?)"
    )

    private val NUM_CANDIDATE = Regex("(\\d{1,7}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+\\.\\d{1,2})")

    private val NON_PAYMENT_PREFIX = Regex("余额|剩余|额度|卡余|原额")

    fun extract(text: String): Double? {
        val t = text
            .replace('￥', '¥')
            .replace('－', '-')
            .replace('　', ' ')

        // 1) 关键词强绑定
        var best: Double? = null
        for (m in KEYWORD_RE.findAll(t)) {
            val v = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            if (best == null || v > best) best = v
        }
        if (best != null) return best

        // 2) 兜底：扫所有数字候选
        var decimalBest: Double? = null
        var intBest: Double? = null
        for (m in NUM_CANDIDATE.findAll(t)) {
            val raw = m.groupValues[1]
            val idx = m.range.first
            val before = if (idx >= 6) t.substring(idx - 6, idx) else t.substring(0, idx)
            if (NON_PAYMENT_PREFIX.containsMatchIn(before)) continue
            val v = raw.replace(",", "").toDoubleOrNull() ?: continue
            if (v < 0.01 || v > 999999.0) continue
            if (raw.contains('.')) {
                if (decimalBest == null || v > decimalBest) decimalBest = v
            } else {
                if (raw.length == 4 && v >= 1900 && v <= 2099) continue
                if (intBest == null || v > intBest) intBest = v
            }
        }
        return decimalBest ?: intBest
    }
}