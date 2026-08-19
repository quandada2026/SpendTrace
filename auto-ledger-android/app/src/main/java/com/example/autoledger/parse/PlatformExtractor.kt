package com.example.autoledger.parse

import com.example.autoledger.Platforms

/** 根据截图文本识别支付平台（与核心引擎 detectPlatform 行为一致）。 */
object PlatformExtractor {

    fun detect(text: String): String {
        if (Regex("微信|WeChat|wechat", RegexOption.IGNORE_CASE).containsMatchIn(text)) return Platforms.WECHAT
        if (Regex("支付宝|Alipay|alipay", RegexOption.IGNORE_CASE).containsMatchIn(text)) return Platforms.ALIPAY
        if (text.contains("银行")) return Platforms.BANK
        return Platforms.UNKNOWN
    }
}
