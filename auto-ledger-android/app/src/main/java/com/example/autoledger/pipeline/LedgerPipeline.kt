package com.example.autoledger.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.autoledger.ReviewDraft
import com.example.autoledger.TradeType
import com.example.autoledger.categorize.Categorizer
import com.example.autoledger.data.LedgerDao
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.ocr.OcrEngine
import com.example.autoledger.parse.ScreenshotParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID

/**
 * 编排：Uri/Bitmap → OCR → 可信度校验 → 结构化解析 → 复核草稿。
 *
 * 拆成两个动作（P0 核心闸门）：
 *  - analyzeUri(): 只 OCR + 解析 + 打分，产出 [ReviewDraft]，**绝不碰数据库**
 *  - commit():      用户确认后才写库，写库后清理临时原图
 *
 * 失败 / 非支付截图 → success=false 的草稿，由 UI 提示，不崩溃、不入库。
 */
class LedgerPipeline(
    private val dao: LedgerDao,
    private val engine: OcrEngine,
) {

    /** 分析：产生复核草稿，不写库。 */
    suspend fun analyzeUri(context: Context, uri: Uri, source: String = "manual"): ReviewDraft =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(context, uri)
                val ocr = engine.recognize(bitmap)
                val parsed = ScreenshotParser.parse(ocr)
                val imgPath = saveTempBitmap(context, bitmap)
                if (!ScreenshotValidator.isCrediblePayment(parsed.rawText, parsed.suggestMoney)) {
                    imgPath?.let { File(it).delete() }
                    parsed.copy(
                        success = false,
                        imagePath = null,
                        source = source,
                        warningMsg = "未识别到支付金额，请重新截图或手动记账",
                    )
                } else {
                    parsed.copy(imagePath = imgPath, source = source, success = true)
                }
            } catch (e: Exception) {
                ReviewDraft(
                    success = false,
                    warningMsg = "图片识别失败：${e.message ?: e.javaClass.simpleName}",
                    source = source,
                )
            }
        }

    /** 提交：用户确认后写库。draft 为 UI 修改后的副本（copy）。 */
    suspend fun commit(draft: ReviewDraft): LedgerEntry? = withContext(Dispatchers.IO) {
        if (!draft.success || draft.suggestMoney == null) return@withContext null
        val direction = when (draft.tradeType) {
            TradeType.INCOME -> 1
            TradeType.EXPENSE -> 0
            TradeType.UNKNOWN -> 0
        }
        val now = LocalDateTime.now()
        val time = draft.tradeTime ?: "%04d-%02d-%02d %02d:%02d:%02d".format(
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second,
        )
        val category = draft.category ?: Categorizer.categorize(draft.suggestMerchant, draft.rawText)
        val entry = LedgerEntry(
            id = UUID.randomUUID().toString(),
            platform = draft.platform,
            merchant = draft.suggestMerchant,
            amount = draft.suggestMoney,
            direction = direction,
            category = category,
            time = time,
            currency = "CNY",
            source = draft.source,
            needsReview = draft.tradeType == TradeType.UNKNOWN,
            rawText = draft.rawText,
            createdAt = "%04d-%02d-%02d %02d:%02d:%02d".format(
                now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second,
            ),
        )
        dao.insert(entry)
        draft.imagePath?.let { File(it).delete() }
        entry
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            // 原图识别（不下采样）：支付截图里小号金额/商户文字，缩小后 OCR 容易丢字。
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            return BitmapFactory.decodeStream(stream, null, opts)
                ?: throw IOException("无法解码图片: $uri")
        } ?: throw IOException("无法打开图片: $uri")
    }

    /** 存临时原图（复核页缩略图用），commit/discard 后由调用方删除。 */
    private fun saveTempBitmap(context: Context, bitmap: Bitmap): String? = runCatching {
        val file = File(context.cacheDir, "ocr_tmp_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        file.absolutePath
    }.getOrNull()
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
        // 用绝对值判区间：退款/余额变动可能带负号（-56.70），不应被当异常拒掉
        val a = kotlin.math.abs(amount)
        if (a < MIN_AMOUNT || a > MAX_AMOUNT) return false
        return PAYMENT_SIGNAL.containsMatchIn(text)
    }
}
