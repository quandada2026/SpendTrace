package com.example.autoledger.parse

/**
 * 商户/收款方抽取（与核心引擎 extractMerchant 行为一致）。
 * 优先匹配「收款方备注：/商家：/商户：/对方户名：/商品：/付款给：」等标签；
 * 兜底匹配「给 XXX 转账/付款/支付」句式。匹配不到返回 null。
 */
object MerchantExtractor {

    private val LABELLED = Regex("(?:收款方备注|收款方|商家|商户|对方户名|商品|付款给)\\s*[：:]\\s*([^\\n；;]+)")

    private val GIVE = Regex("给\\s*([^\\n：:]+?)\\s*(?:的)?(?:转账|付款|支付)")

    fun extract(text: String): String? {
        val labelled = LABELLED.find(text)
        if (labelled != null) {
            return labelled.groupValues[1].trim().replace(Regex("[，。\\s]+$"), "")
        }
        val give = GIVE.find(text)
        if (give != null) return give.groupValues[1].trim()
        return null
    }
}
