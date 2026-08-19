package com.example.autoledger.parse

import java.time.LocalDate

/**
 * 时间抽取并归一化为 'YYYY-MM-DD HH:MM:SS'（与核心引擎 extractTime 行为一致）。
 * 支持：
 *  - 2026-08-18 09:30:12 / 2026/08/18 09:30 / 2026.08.18
 *  - 今天 09:05 / 昨日 22:10（按本地日期推导）
 * 无法识别返回 null。
 */
object TimeExtractor {

    private val DT_RE = Regex(
        "(\\d{4})[-/.](\\d{2})[-/.](\\d{2})(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?)?",
    )

    private val REL_RE = Regex("(今天|昨日)\\s*(\\d{2}):(\\d{2})")

    fun extract(text: String): String? {
        val dt = DT_RE.find(text)
        if (dt != null) {
            val (y, mo, d, hh, mm, ss) = dt.destructured
            val h = hh.ifEmpty { "00" }
            val m = mm.ifEmpty { "00" }
            val s = ss.ifEmpty { "00" }
            return "%s-%s-%s %s:%s:%s".format(y, mo, d, h, m, s)
        }

        val rel = REL_RE.find(text)
        if (rel != null) {
            val (word, hh, mm) = rel.destructured
            val base = if (word == "昨日") LocalDate.now().minusDays(1) else LocalDate.now()
            return "%04d-%02d-%02d %s:%s:00".format(
                base.year,
                base.monthValue,
                base.dayOfMonth,
                hh,
                mm,
            )
        }
        return null
    }
}
