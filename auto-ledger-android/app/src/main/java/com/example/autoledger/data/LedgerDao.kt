package com.example.autoledger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LedgerEntry)

    @Update
    suspend fun update(entry: LedgerEntry)

    @Delete
    suspend fun delete(entry: LedgerEntry)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全部条目，按时间倒序（优先 time，其次 createdAt）。 */
    @Query(
        """
        SELECT * FROM ledger_entries
        ORDER BY
          CASE WHEN time IS NULL THEN 1 ELSE 0 END,
          time DESC,
          created_at DESC
        """,
    )
    fun observeAll(): Flow<List<LedgerEntry>>

    /** 待人工核对（金额为空）。 */
    @Query("SELECT * FROM ledger_entries WHERE needs_review = 1 ORDER BY created_at DESC")
    fun observeNeedsReview(): Flow<List<LedgerEntry>>

    /** 时间段统计：分类 -> 合计金额（direction: 0=支出 1=收入；OCR 没识别出时间时用创建时间兜底）。 */
    @Query(
        """
        SELECT category, SUM(amount) AS total
        FROM ledger_entries
        WHERE COALESCE(time, created_at) >= :start AND COALESCE(time, created_at) < :end
          AND amount IS NOT NULL AND direction = :direction
        GROUP BY category
        """,
    )
    fun rangeCategoryTotals(start: String, end: String, direction: Int): Flow<List<CategoryTotal>>

    /** 时间段合计。 */
    @Query(
        """
        SELECT SUM(amount) FROM ledger_entries
        WHERE COALESCE(time, created_at) >= :start AND COALESCE(time, created_at) < :end
          AND amount IS NOT NULL AND direction = :direction
        """,
    )
    fun rangeTotal(start: String, end: String, direction: Int): Flow<Double?>

    /** 每日支出合计：用于 a/总览 → c/日历 染色（[start, end) 内按日聚合）。返回 POJO 的 d 列形如 'YYYY-MM-DD'。 */
    @Query(
        """
        SELECT substr(COALESCE(time, created_at), 1, 10) AS d, SUM(amount) AS total
        FROM ledger_entries
        WHERE direction = 0 AND amount IS NOT NULL
          AND COALESCE(time, created_at) >= :start AND COALESCE(time, created_at) < :end
        GROUP BY d
        """,
    )
    fun dailyExpenseTotals(start: String, end: String): Flow<List<DayTotal>>

    @Query(
        """
        SELECT substr(COALESCE(time, created_at), 1, 10) AS d, SUM(amount) AS total
        FROM ledger_entries
        WHERE direction = 1 AND amount IS NOT NULL
          AND COALESCE(time, created_at) >= :start AND COALESCE(time, created_at) < :end
        GROUP BY d
        """,
    )
    fun dailyIncomeTotals(start: String, end: String): Flow<List<DayTotal>>

    /** 周期内某分类账目（d 三级钻取：点分类 → 该分类账目列表）。 */
    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE direction = :direction AND category = :category
          AND COALESCE(time, created_at) >= :start AND COALESCE(time, created_at) < :end
        ORDER BY COALESCE(time, created_at) DESC
        """,
    )
    fun observeByCategoryInRange(
        start: String,
        end: String,
        category: String,
        direction: Int,
    ): Flow<List<LedgerEntry>>

    /** 指定日期的所有账目（c 三级钻取：点日历某日 → 该日账目）。day 格式 'YYYY-MM-DD'。 */
    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE substr(COALESCE(time, created_at), 1, 10) = :day
        ORDER BY COALESCE(time, created_at) DESC
        """,
    )
    fun observeByDay(day: String): Flow<List<LedgerEntry>>
}

/**
 * 配合 @Query 的 POJO：每日支出合计。
 */
data class DayTotal(
    @ColumnInfo(name = "d") val date: String,       // 'YYYY-MM-DD'
    @ColumnInfo(name = "total") val total: Double,
)

/**
 * 配合 @Query 的 POJO：分类汇总。
 */
data class CategoryTotal(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "total") val total: Double,
)
