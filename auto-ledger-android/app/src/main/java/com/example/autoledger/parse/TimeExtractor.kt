package com.example.autoledger.parse

import java.time.LocalDate

/**
 * 时间抽取并归一化为 'YYYY-MM-DD HH:MM:SS'（与核心引擎 extractTime 行为一致）。
 *
 * 支持（按优先级）：
 *  1) 完整日期含年：2026-08-19 09:30:12 / 2026/8/19 9:30 / 2026.08.19 / 2026年8月19日 14:23
 *  2) 相对日期：今天/今日 09:30、昨天/昨日 22:10、前天 08:00
 *  3) 无年日期：08-19 09:30 / 8月19日（取当前年；若结果晚于今天则视为去年，跨年兜底）
 *
 * 每类都做月/日/时分秒合法性校验，无法识别返回 null。
 */
object TimeExtractor {

    // 完整日期：4 位年 + 月 + 日（分隔符支持 - / . 年月日，月日可单位数），可选时分秒。
    // 注意：日期与时间之间的空白必须留给时间组 (?:\s+|T) 消费，不能用 \s* 提前吞掉。
    private val DT_FULL_RE = Regex(
        "(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})日?" +
            "(?:(?:\\s+|T)\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?",
    )

    // 相对日期：今天/今日/昨天/昨日/前天 + HH:MM
    private val REL_RE = Regex("(今天|今日|昨天|昨日|前天)\\s*(\\d{1,2}):(\\d{2})")

    // 无年日期：MM-DD / MM/DD / MM月DD日。
    // 前位负向后顾 (?<!\d) 防止吃进完整日期的月段（2026-08 的 08）或订单号里的数字段。
    private val DT_MD_RE = Regex(
        "(?<!\\d)(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})日?(?:(?:\\s+|T)\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?",
    )

    fun extract(text: String): String? {
        // 1) 完整日期（含年）最可靠，优先
        DT_FULL_RE.find(text)?.let { m ->
            val (y, mo, d, hh, mm, ss) = m.destructured
            safeDate(y.toInt(), mo.toInt(), d.toInt())?.let { date ->
                return withTime(date, hh, mm, ss)
            }
        }
        // 2) 相对日期（今天/昨天/前天）
        REL_RE.find(text)?.let { m ->
            val (word, hh, mm) = m.destructured
            val base = when (word) {
                "昨天", "昨日" -> LocalDate.now().minusDays(1)
                "前天" -> LocalDate.now().minusDays(2)
                else -> LocalDate.now()
            }
            return withTime(base, hh, mm, "")
        }
        // 3) 无年日期：补当前年；若结果晚于今天（未来不合理）则视为去年
        DT_MD_RE.find(text)?.let { m ->
            val (mo, d, hh, mm, ss) = m.destructured
            val thisYear = LocalDate.now().year
            var date = safeDate(thisYear, mo.toInt(), d.toInt())
            if (date != null && date.isAfter(LocalDate.now().plusDays(1))) {
                date = safeDate(thisYear - 1, mo.toInt(), d.toInt())
            }
            if (date != null) return withTime(date, hh, mm, ss)
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun withTime(date: LocalDate, hh: String, mm: String, ss: String): String {
        val h = if (hh.isNotEmpty() && hh.toIntOrNull() in 0..23) hh.padStart(2, '0') else "00"
        val mi = if (mm.isNotEmpty() && mm.toIntOrNull() in 0..59) mm.padStart(2, '0') else "00"
        val s = if (ss.isNotEmpty() && ss.toIntOrNull() in 0..59) ss.padStart(2, '0') else "00"
        return "%04d-%02d-%02d %s:%s:%s".format(date.year, date.monthValue, date.dayOfMonth, h, mi, s)
    }
}
