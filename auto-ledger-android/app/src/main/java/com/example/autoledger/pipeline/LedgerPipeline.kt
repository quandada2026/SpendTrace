package com.example.autoledger.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.autoledger.categorize.Categorizer
import com.example.autoledger.data.LedgerDao
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.ocr.OcrEngine
import com.example.autoledger.parse.ScreenshotParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID

/**
 * 编排：Uri/Bitmap → OCR → 可信度校验 → 结构化解析 → 自动分类 → 入库。
 * 返回 null 表示「空白截图 / 非支付截图」，不产生任何账目（杜绝瞎编数据）。
 * 这是外壳（截图服务 / 手动上传）唯一需要调用的入口，与核心引擎
 * processScreenshot() 完全一致。
 */
class LedgerPipeline(
    private val dao: LedgerDao,
    private val engine: OcrEngine,
) {

    suspend fun processUri(context: Context, uri: Uri, source: String = "auto"): LedgerEntry? =
        processBitmap(loadBitmap(context, uri), source)

    suspend fun processBitmap(bitmap: Bitmap, source: String = "manual"): LedgerEntry? {
        val ocr = withContext(Dispatchers.IO) { engine.recognize(bitmap) }
        val text = ocr.text ?: ""
        // 先解析一次拿金额：兜底提取会把订单号/手机号/余额误当金额，必须用范围卡死
        val parsed = ScreenshotParser.parse(ocr)
        if (!ScreenshotValidator.isCrediblePayment(text, parsed.amount)) return null
        val category = Categorizer.categorize(parsed.merchant, parsed.rawText)

        val entry = LedgerEntry(
            id = UUID.randomUUID().toString(),
            platform = parsed.platform,
            merchant = parsed.merchant,
            amount = parsed.amount,
            direction = 0,
            category = category,
            time = parsed.time,
            currency = parsed.currency,
            source = source,
            needsReview = parsed.needsReview,
            rawText = parsed.rawText,
            createdAt = LocalDateTime.now().let {
                "%04d-%02d-%02d %02d:%02d:%02d".format(
                    it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute, it.second,
                )
            },
        )
        withContext(Dispatchers.IO) { dao.insert(entry) }
        return entry
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            // 原图识别（不下采样）：支付截图里小号金额/商户文字，缩小后 OCR 容易丢字。
            // 手动上传场景图片数量少，原图识别的性能开销可接受。
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            return BitmapFactory.decodeStream(stream, null, opts)
                ?: throw IOException("无法解码图片: $uri")
        } ?: throw IOException("无法打开图片: $uri")
    }
}

/**
 * 支付截图可信度校验。
 *
 * 空白截图、纯状态栏/桌面截图会被 OCR 出空文本或零星数字，
 * 若不设防，兜底金额提取会把时间戳（如 08:56 → 56）或订单号/手机号/余额当金额瞎编入库。
 *
 * 规则（必须全部通过）：
 *  1) 文本非空
 *  2) 命中任一支付上下文信号（关键词 / ¥ 符号 / 平台标识）
 *  3) 金额必须识别出来（OCR 没拿到金额视为识别失败，不入账）
 *  4) 金额必须在 [MIN, MAX] 合理区间（过滤订单号/手机号/账户余额等大数字噪声）
 */
object ScreenshotValidator {

    /** 个人支付合理区间：0.5 ~ 9999 元。超出视为识别异常。 */
    const val MIN_AMOUNT = 0.5
    const val MAX_AMOUNT = 9999.0

    private val PAYMENT_SIGNAL = Regex(
        "支付|付款|金额|交易|收款|消费|扣款|实付|应付|需付款|总额|合计|转账|收入|支出|¥|￥|微信|支付宝|云闪付|银联"
    )

    fun isCrediblePayment(text: String, amount: Double?): Boolean {
        if (text.isBlank()) return false
        if (amount == null) return false
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) return false
        return PAYMENT_SIGNAL.containsMatchIn(text)
    }
}
