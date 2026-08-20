package com.example.autoledger.parse

import com.example.autoledger.AmountCandidate

/**
 * 金额抽取（与核心引擎行为一致），并扩展「多候选 + 打分」以支持人工复核。
 *
 * 容错真实 OCR 失真：
 *  - 全角符号归一（￥→¥、－→-、．→.）
 *  - 字母 O 当数字（OCR 常把 0 识别成大写 O）
 *  - 逗号歧义：千分位（1,234.56）去逗号；否则逗号当小数点（12,50 → 12.50 / 99,99 → 99.99）
 *  - 干扰词（优惠/原价/券…）重罚，实付/已支付 关键词高分
 *
 * 候选硬约束（修复「候选十几个 + 识别成功率低」）：
 *  - 无货币单位的数字必须是**两位小数**（98.00 / 1,234.56 / 12,50），
 *    日期、时间、手机号、订单号、长数字（12345.67 被拆碎）不再混入候选；
 *  - 纯整数仅当带 ¥ / 元 货币单位（¥100 / 100元）才视为金额；
 *  - 日期时间先整段剔除，余额/剩余/额度行整行跳过；
 *  - 打分按候选金额「前后 6 字符窗口」判定：紧邻 实付→+30，紧邻 优惠/原价→-50，
 *    同行混合（原价128 优惠30 实付98）不会误伤实付；
 *  - 同值去重保留最高分；负数金额保留（退款），区间校验用绝对值。
 */
object AmountExtractor {

    // ===== 旧版单值接口（保留兼容，新流程用 extractCandidates） =====
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
        var best: Double? = null
        for (m in KEYWORD_RE.findAll(t)) {
            val v = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            if (best == null || v > best) best = v
        }
        if (best != null) return best
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

    // ===== 新版：多候选 + 打分 =====

    /** 日期/时间整段剔除（否则 2026-08-19、17:38、2026.08.19 会被当金额候选）。 */
    private val DATE_TIME_RE = Regex(
        "\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?|\\d{1,2}:\\d{2}(?::\\d{2})?"
    )

    /** 无货币单位金额：两位小数（含千分位、逗号小数）。长数字整体匹配，不拆碎。 */
    private val DECIMAL_RE = Regex(
        "[-+]?\\d{1,3}(?:,\\d{3})*\\.\\d{2}|[-+]?\\d+\\.\\d{2}|[-+]?\\d+,\\d{1,2}"
    )

    /** 带货币单位金额：¥100 / ¥98.00 / 100元 / 98.00元。 */
    private val UNIT_RE = Regex("[¥￥]\\s*[-+]?\\d+(?:\\.\\d{1,2})?|[-+]?\\d+(?:\\.\\d{1,2})?\\s*[元块]")

    /** 宽松层（仅降级启用）：一位小数（98.0 / 12.5，OCR 丢尾零）。 */
    private val DECIMAL_LOOSE_RE = Regex("[-+]?\\d+\\.\\d")

    /** 宽松层：独立短整数行（98 / 100），1-7 位。 */
    private val INT_LINE_RE = Regex("^[-+]?\\d{1,7}$")

    /** 非交易金额上下文（余额/剩余/额度），整行跳过。 */
    private val NON_PAYMENT_LINE = Regex("余额|剩余|额度|卡余|原额")

    private val CTX_HIGH = Regex("实付|已支付|实际付款|需付款|应付")
    private val CTX_PENALTY = Regex("优惠|原价|券|红包|积分|抵扣|立减|满减|返现")
    private val SCORE_MID = Regex("支付|消费|扣款|交易金额|合计|总额|金额|收款")

    /** 提取全部疑似金额候选，按打分降序。供复核页下拉切换 + 推荐 top1。 */
    fun extractCandidates(text: String): List<AmountCandidate> {
        val cleaned = normalizeText(text)
        val out = mutableListOf<AmountCandidate>()
        cleaned.lines().forEach { line ->
            val ln = line.trim()
            if (ln.length < 2) return@forEach
            if (NON_PAYMENT_LINE.containsMatchIn(ln)) return@forEach
            fun push(raw: String, start: Int) {
                val v = normalizeMoneyToken(raw) ?: return
                val a = kotlin.math.abs(v)
                if (a < 0.01 || a > 99999.0) return
                out += AmountCandidate(value = v, score = scoreFor(ln, start), sourceLine = ln.take(40))
            }
            DECIMAL_RE.findAll(ln).forEach { push(it.value, it.range.first) }
            UNIT_RE.findAll(ln).forEach { push(it.value, it.range.first) }
        }
        if (out.isNotEmpty()) return dedup(out)

        // 降级兜底：OCR 丢小数/尾零（98.00 → 98 / 98.0）时精确层无候选，
        // 用「一位小数 + 独立短整数行」捞回。分数压低，仅保证有候选可选、不按钮卡死。
        val loose = mutableListOf<AmountCandidate>()
        cleaned.lines().forEach { line ->
            val ln = line.trim()
            if (ln.length < 2) return@forEach
            if (NON_PAYMENT_LINE.containsMatchIn(ln)) return@forEach
            if (INT_LINE_RE.matches(ln)) {
                val v = normalizeMoneyToken(ln) ?: return@forEach
                val a = kotlin.math.abs(v)
                if (a in 0.5..99999.0) loose += AmountCandidate(value = v, score = -20, sourceLine = ln.take(40))
                return@forEach
            }
            DECIMAL_LOOSE_RE.findAll(ln).forEach { m ->
                val v = normalizeMoneyToken(m.value) ?: return@forEach
                val a = kotlin.math.abs(v)
                if (a in 0.01..99999.0) loose += AmountCandidate(value = v, score = scoreFor(ln, m.range.first) - 10, sourceLine = ln.take(40))
            }
        }
        return dedup(loose)
    }

    /** 归一化 + 剔除日期时间。处理 OCR 常见字符失真：Unicode 减号/全角符号/零宽字符。
     *  这些失真若不归一，会让 "-5.00"（U+2212 减号）/ "5，00"（中文逗号小数点）整个被丢，
     *  候选 0 个 → suggestMoney=null → 复核页金额空 → 确认按钮灰。 */
    private fun normalizeText(text: String): String {
        var s = text
        // 各种横线/减号 → ASCII '-'（支付 APP 常用 U+2212 数学减号渲染负号）
        s = s.replace('\u2212', '-')  // MINUS SIGN −
        s = s.replace('\u2010', '-')  // HYPHEN ‐
        s = s.replace('\u2013', '-')  // EN DASH –
        s = s.replace('\u2014', '-')  // EM DASH —
        s = s.replace('－', '-')       // FULLWIDTH HYPHEN-MINUS －
        // 各种点/逗号 → '.'（OCR 常把小数点识别成中文标点；"5，00" → "5.00"）
        s = s.replace('\uff0c', '.')   // FULLWIDTH COMMA ，
        s = s.replace('\u3002', '.')   // IDEOGRAPHIC FULL STOP 。
        s = s.replace('\u3001', '.')   // IDEOGRAPHIC COMMA 、
        s = s.replace('．', '.')       // FULLWIDTH FULL STOP ．
        // 零宽字符 → 空格（防 "U+200B−5.00" 前导零宽导致 startsWith("-") 失败）
        s = s.replace('\u200b', ' ')
        s = s.replace('\u200c', ' ')
        s = s.replace('\u200d', ' ')
        s = s.replace('\ufeff', ' ')
        s = s.replace('￥', '¥')
        s = s.replace('　', ' ')
        s = s.replace(DATE_TIME_RE, " ")
        return s
    }

    /** 同值去重保留最高分；按分降序（同分保持原顺序）。 */
    private fun dedup(list: List<AmountCandidate>): List<AmountCandidate> =
        list.groupBy { "%.2f".format(it.value) }
            .map { (_, l) -> l.maxBy { it.score } }
            .sortedByDescending { it.score }

    /** 按候选金额「前后 6 字符窗口」打分：紧邻实付+30 / 紧邻优惠-50 / 行级支付词+10。 */
    private fun scoreFor(line: String, start: Int): Int {
        val from = (start - 6).coerceAtLeast(0)
        val to = (start + 4).coerceAtMost(line.length)
        val ctx = line.substring(from, to)
        return when {
            CTX_HIGH.containsMatchIn(ctx) -> 30
            CTX_PENALTY.containsMatchIn(ctx) -> -50
            SCORE_MID.containsMatchIn(line) -> 10
            else -> 0
        }
    }

    /** OCR 金额 token → 数值（去 ¥/元、O→0、逗号千分位/小数歧义、负号）。 */
    private fun normalizeMoneyToken(raw: String): Double? {
        val cleaned = raw.trim()
            .removePrefix("¥").removePrefix("￥").trim()
            .removeSuffix("元").removeSuffix("块").trim()
            .replace('O', '0')
        val neg = cleaned.startsWith("-")
        val body = if (neg) cleaned.removePrefix("-") else cleaned
        val isThousands = Regex("^\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?$").matches(body)
        val normalized = if (isThousands) body.replace(",", "") else body.replace(',', '.')
        val v = normalized.toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }
}
