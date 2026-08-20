package com.example.autoledger.ui

import com.example.autoledger.ReviewDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨组件（ScreenshotService / LedgerViewModel / MainActivity 同进程）传递待复核草稿的轻量总线。
 * analyze() 产出的草稿先入队，UI 弹复核页，确认后 commit 并 remove。
 * 因 Service 与 Activity 同进程（Manifest 未指定 android:process），单例即可共享。
 */
object ReviewBus {

    private val _queue: MutableStateFlow<List<ReviewDraft>> = MutableStateFlow(emptyList())
    val queue: StateFlow<List<ReviewDraft>> = _queue.asStateFlow()

    fun offer(d: ReviewDraft) {
        _queue.value = _queue.value + d
    }

    fun remove(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
    }

    fun peek(id: String): ReviewDraft? = _queue.value.firstOrNull { it.id == id }

    fun clear() {
        _queue.value = emptyList()
    }
}
