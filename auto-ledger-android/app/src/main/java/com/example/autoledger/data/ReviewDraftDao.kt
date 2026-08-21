package com.example.autoledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 复核草稿持久化 DAO（P1：进程被杀后从表恢复草稿队列）。 */
@Dao
interface ReviewDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReviewDraftEntity)

    @Query("DELETE FROM review_drafts WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全部草稿，按入队时间升序（先入先核对）。 */
    @Query("SELECT * FROM review_drafts ORDER BY createdAt ASC")
    suspend fun getAll(): List<ReviewDraftEntity>

    /** 清理全部草稿（暂时不用，保留作兜底）。 */
    @Query("DELETE FROM review_drafts")
    suspend fun clearAll()
}
