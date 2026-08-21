package com.example.autoledger.ui

import com.example.autoledger.ReviewDraft
import com.example.autoledger.data.ReviewDraftDao
import com.example.autoledger.data.toDraft
import com.example.autoledger.data.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨组件（ScreenshotService / LedgerViewModel / MainActivity 同进程）传递待复核草稿的轻量总线。
 * analyze() 产出的草稿先入队，UI 弹复核页，确认后 commit 并 remove。
 * 因 Service 与 Activity 同进程（Manifest 未指定 android:process），单例即可共享。
 *
 * P1：offer/remove 同时落库（review_drafts 表），App 启动时 loadFromDb 恢复——
 * 进程被杀后草稿不丢，下次打开仍待核对。
 */
object ReviewBus {

    private val _queue: MutableStateFlow<List<ReviewDraft>> = MutableStateFlow(emptyList())
    val queue: StateFlow<List<ReviewDraft>> = _queue.asStateFlow()

    @Volatile
    private var dao: ReviewDraftDao? = null

    /** 应用启动时注入一次（幂等）。 */
    fun init(d: ReviewDraftDao) {
        if (dao == null) dao = d
    }

    private fun requireDao(): ReviewDraftDao =
        dao ?: throw IllegalStateException("ReviewBus 未初始化：请先在 Application/ViewModel 调用 init(dao)")

    /** 入队 + 落库。 */
    suspend fun offer(d: ReviewDraft) {
        _queue.value = _queue.value + d
        requireDao().upsert(d.toEntity())
    }

    /** 出队 + 删库。 */
    suspend fun remove(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
        requireDao().deleteById(id)
    }

    /** 仅查内存（不落库）。 */
    fun peek(id: String): ReviewDraft? = _queue.value.firstOrNull { it.id == id }

    /** 启动时从库恢复全部草稿（先入先核对）。 */
    suspend fun loadFromDb() {
        val drafts = requireDao().getAll().map { it.toDraft() }
        _queue.value = drafts
    }

    fun clear() {
        _queue.value = emptyList()
    }
}
