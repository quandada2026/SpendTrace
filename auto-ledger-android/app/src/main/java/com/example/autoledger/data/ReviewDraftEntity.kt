package com.example.autoledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.autoledger.AmountCandidate
import com.example.autoledger.ReviewDraft
import com.example.autoledger.TradeType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 复核草稿持久化实体（P1：ReviewBus 内存队列 → Room 表，进程被杀不丢单）。
 * 复杂字段（候选金额/商户候选）以 JSON 字符串存储，用 org.json（Android 内置）序列化，避免额外依赖。
 */
@Entity(tableName = "review_drafts")
data class ReviewDraftEntity(
    @PrimaryKey val id: String,
    val success: Boolean,
    val rawText: String,
    val confidence: Float,
    /** 临时原图绝对路径（cacheDir），进程杀后文件仍在磁盘，可恢复。 */
    val imagePath: String?,
    val candidateMoneyList: String,
    val suggestMoney: Double?,
    val merchantCandidates: String,
    val suggestMerchant: String?,
    val tradeTime: String?,
    /** TradeType.name（EXPENSE / INCOME / UNKNOWN）。 */
    val tradeType: String,
    val platform: String,
    val category: String?,
    val warningMsg: String?,
    val source: String,
    /** 'YYYY-MM-DD'，null 表示无浏览日期上下文。 */
    val contextDate: String?,
    /** 入队时间戳（排序用）。 */
    val createdAt: Long,
)

/** ReviewDraft → Entity（落库前序列化）。 */
fun ReviewDraft.toEntity(createdAt: Long = System.currentTimeMillis()): ReviewDraftEntity =
    ReviewDraftEntity(
        id = id,
        success = success,
        rawText = rawText,
        confidence = confidence,
        imagePath = imagePath,
        candidateMoneyList = candidatesToJson(candidateMoneyList),
        suggestMoney = suggestMoney,
        merchantCandidates = stringsToJson(merchantCandidates),
        suggestMerchant = suggestMerchant,
        tradeTime = tradeTime,
        tradeType = tradeType.name,
        platform = platform,
        category = category,
        warningMsg = warningMsg,
        source = source,
        contextDate = contextDate?.toString(),
        createdAt = createdAt,
    )

/** Entity → ReviewDraft（恢复）。解析失败时兜底为空列表/UNKNOWN，不崩溃。 */
fun ReviewDraftEntity.toDraft(): ReviewDraft =
    ReviewDraft(
        id = id,
        success = success,
        rawText = rawText,
        confidence = confidence,
        imagePath = imagePath,
        candidateMoneyList = candidatesFromJson(candidateMoneyList),
        suggestMoney = suggestMoney,
        merchantCandidates = stringsFromJson(merchantCandidates),
        suggestMerchant = suggestMerchant,
        tradeTime = tradeTime,
        tradeType = runCatching { TradeType.valueOf(tradeType) }.getOrDefault(TradeType.UNKNOWN),
        platform = platform,
        category = category,
        warningMsg = warningMsg,
        source = source,
        contextDate = contextDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
    )

private fun candidatesToJson(list: List<AmountCandidate>): String =
    JSONArray().apply {
        list.forEach { c ->
            put(JSONObject().put("value", c.value).put("score", c.score).put("line", c.sourceLine))
        }
    }.toString()

private fun candidatesFromJson(s: String): List<AmountCandidate> =
    runCatching {
        val arr = JSONArray(s)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(AmountCandidate(o.getDouble("value"), o.getInt("score"), o.optString("line")))
            }
        }
    }.getOrDefault(emptyList())

private fun stringsToJson(list: List<String>): String = JSONArray(list).toString()

private fun stringsFromJson(s: String): List<String> =
    runCatching {
        val a = JSONArray(s)
        buildList { for (i in 0 until a.length()) add(a.getString(i)) }
    }.getOrDefault(emptyList())
