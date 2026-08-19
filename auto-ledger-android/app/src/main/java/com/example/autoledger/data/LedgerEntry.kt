package com.example.autoledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账本条目。字段与核心引擎的 LedgerEntry 完全一致。
 * platform / category / source 以字符串持久化（枚举语义在业务层约束）。
 */
@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "platform")
    val platform: String,            // wechat | alipay | bank | unknown

    @ColumnInfo(name = "merchant")
    val merchant: String?,

    @ColumnInfo(name = "amount")
    val amount: Double?,            // 元；识别失败为 null

    @ColumnInfo(name = "direction")
    val direction: Int,             // 0=支出 1=收入

    @ColumnInfo(name = "category")
    val category: String,           // 餐饮|交通|...|其他

    @ColumnInfo(name = "time")
    val time: String?,              // 归一化 'YYYY-MM-DD HH:MM:SS'

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "source")
    val source: String,             // auto | manual

    @ColumnInfo(name = "needs_review")
    val needsReview: Boolean,

    @ColumnInfo(name = "raw_text")
    val rawText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String,
)
