package com.example.autoledger.parse

/**
 * 商户/收款方抽取（与核心引擎 extractMerchant 行为一致）。
 * 返回候选列表（标签匹配优先，其次「给 X 转账」句式），供复核页切换。
 */
object MerchantExtractor {

    private val LABELLED = Regex("(?:收款方备注|收款方|商家|商户|对方户名|商品|付款给)\\s*[：:]\\s*([^\\n；;]+)")
    private val GIVE = Regex("给\\s*([^\\n：:]+?)\\s*(?:的)?(?:转账|付款|支付)")

    /** 旧版单值接口（保留兼容）。 */
    fun extract(text: String): String? = extractCandidates(text).firstOrNull()

    /** 返回候选商户列表（可能为空）。 */
    fun extractCandidates(text: String): List<String> {
        val out = mutableListOf<String>()
        LABELLED.find(text)?.let {
            out.add(it.groupValues[1].trim().replace(Regex("[，。\\s]+$"), ""))
        }
        GIVE.find(text)?.let { g ->
            val name = g.groupValues[1].trim()
            if (name.isNotBlank() && !out.contains(name)) out.add(name)
        }
        return out
    }
}
