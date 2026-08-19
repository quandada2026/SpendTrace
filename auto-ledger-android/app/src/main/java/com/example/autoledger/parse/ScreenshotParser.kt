package com.example.autoledger.parse

import com.example.autoledger.OcrResult
import com.example.autoledger.ParsedReceipt

/** 把 OCR 结果解析为结构化收据。核心纯函数，可独立单元测试（与 TS parseScreenshot 一致）。 */
object ScreenshotParser {

    fun parse(result: OcrResult): ParsedReceipt {
        val text = result.text ?: ""
        val amount = AmountExtractor.extract(text)
        val time = TimeExtractor.extract(text)
        val merchant = MerchantExtractor.extract(text)
        val platform = PlatformExtractor.detect(text)

        return ParsedReceipt(
            platform = platform,
            amount = amount,
            currency = "CNY",
            time = time,
            merchant = merchant,
            rawText = text,
            needsReview = amount == null,
        )
    }
}
