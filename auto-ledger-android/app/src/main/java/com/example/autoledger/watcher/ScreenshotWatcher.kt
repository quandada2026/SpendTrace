package com.example.autoledger.watcher

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread

/**
 * 在后台线程持有 ContentObserver，注册/注销截图监听。
 * 与桌面版 watcher.ts（chokidar）同构：新截图落盘即触发回调。
 */
class ScreenshotWatcher(
    private val context: Context,
    private val onScreenshot: (Uri) -> Unit,
) {

    private var thread: HandlerThread? = null
    private var observer: ScreenshotObserver? = null

    fun start() {
        if (thread != null) return
        val t = HandlerThread("autoledger-screenshot-watch").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        observer = ScreenshotObserver(handler, context.contentResolver, onScreenshot)
        context.contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!,
        )
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        thread?.quitSafely()
        thread = null
    }
}
