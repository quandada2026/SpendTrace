package com.example.autoledger.watcher

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore

/**
 * 监听 MediaStore 图片变化，过滤出"系统截图"，回调其 Uri。
 * 与桌面版 chokidar 监听截图目录的角色完全等价——这是安卓上
 * 实现「付完款→截图→零操作自动记账」的唯一可行路径。
 */
class ScreenshotObserver(
    handler: Handler,
    private val resolver: ContentResolver,
    private val onScreenshot: (Uri) -> Unit,
) : ContentObserver(handler) {

    /** 记录上一次处理过的图片 id，去重（同一截图可能触发多次 onChange）。 */
    private var lastId: Long = -1

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        emitLatest()
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        emitLatest()
    }

    private fun emitLatest() {
        val found = queryLatestScreenshot() ?: return
        if (found.first != lastId) {
            lastId = found.first
            onScreenshot(found.second)
        }
    }

    private fun queryLatestScreenshot(): Pair<Long, Uri>? {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
        )
        // DATE_ADDED 倒序，取最新一张
        resolver.query(
            base,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val rel = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
            val data = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: ""
            if (!mime.startsWith("image/")) return null
            val pathLike = ((rel ?: "") + (data ?: "")).lowercase()
            if (!pathLike.contains("screenshot")) return null
            val imgUri = ContentUris.withAppendedId(base, id)
            return id to imgUri
        }
        return null
    }
}
