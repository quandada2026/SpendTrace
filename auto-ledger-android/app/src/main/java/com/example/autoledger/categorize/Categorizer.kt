package com.example.autoledger.categorize

import com.example.autoledger.Categories

/**
 * 自动归类：
 * 1) 商户名优先匹配；
 * 2) 商户匹配不到时，退到整段 OCR 文本兜底（OCR 把"收款方备注"识别成"备住"等
 *    错字导致商户提取失败时，仍能靠文本里的品牌词归类）；
 * 3) 仍无法匹配归「其他」。
 */
object Categorizer {

    fun categorize(merchant: String?, fullText: String? = null): String {
        merchant?.let { m -> match(m)?.let { return it } }
        fullText?.let { t -> match(t)?.let { return it } }
        return Categories.其他
    }

    private fun match(text: String): String? {
        for ((cat, kws) in CategoryDictionary.KEYWORDS) {
            if (cat == Categories.其他) continue
            if (kws.any { text.contains(it) }) return cat
        }
        return null
    }
}
