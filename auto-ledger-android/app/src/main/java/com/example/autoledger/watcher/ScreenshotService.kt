package com.example.autoledger.watcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.autoledger.AutoLedgerApplication
import com.example.autoledger.R
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.ocr.OcrEngineProvider
import com.example.autoledger.pipeline.LedgerPipeline
import com.example.autoledger.ReviewDraft
import com.example.autoledger.ui.ReviewBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 零操作核心：常驻前台服务。
 * 启动后监听系统截图目录，新截图落盘立即 OCR→解析→分类→入库，并弹通知。
 * 与桌面版 watcher.ts + server.ts 的角色完全等价。
 */
class ScreenshotService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var watcher: ScreenshotWatcher
    private lateinit var pipeline: LedgerPipeline
    private lateinit var notifManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        startForeground(NOTIF_ID, buildWatchNotification())

        val app = application as AutoLedgerApplication
        pipeline = LedgerPipeline(app.database.dao(), OcrEngineProvider.getEngine(this))
        watcher = ScreenshotWatcher(this) { uri -> handleScreenshot(uri) }
        watcher.start()
    }

    private fun handleScreenshot(uri: Uri) {
        scope.launch {
            try {
                // 只分析，不入库：草稿入 ReviewBus，由 UI 弹复核页让用户确认。
                val draft = pipeline.analyzeUri(this@ScreenshotService, uri, "auto")
                ReviewBus.offer(draft)
                if (draft.success) notifyReview(draft) else notifyIgnored(draft.warningMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        watcher.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                getString(R.string.channel_id),
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            notifManager.createNotificationChannel(ch)
        }
    }

    private fun buildWatchNotification(): android.app.Notification =
        NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getString(R.string.watch_notification_title))
            .setContentText(getString(R.string.watch_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setOngoing(true)
            .build()

    private fun notifyEntry(entry: LedgerEntry) {
        val intent = Intent(this, com.example.autoledger.ui.MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (entry.amount != null) {
            "${entry.merchant ?: "未知商户"}  ¥${"%.2f".format(entry.amount)}"
        } else {
            "${entry.merchant ?: "未知商户"}  (待核对)"
        }
        val n = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getString(R.string.entry_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        notifManager.notify(entry.id.hashCode(), n)
    }

    /** 空白/非支付截图：弹一次提示，不记任何账。 */
    private fun notifyIgnored(msg: String? = null) {
        val n = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle("已忽略")
            .setContentText(msg ?: "这张截图不是支付截图（空白或无支付信息），未记账")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setAutoCancel(true)
            .build()
        notifManager.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    /** 识别成功但需人工确认：通知提醒打开 App 复核。 */
    private fun notifyReview(draft: ReviewDraft) {
        val intent = Intent(this, com.example.autoledger.ui.MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pi = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val amt = draft.suggestMoney?.let { "¥%.2f".format(it) } ?: "待核对"
        val n = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle("账单待核对 · $amt")
            .setContentText("${draft.suggestMerchant ?: "未知商户"} · 点此确认或修正")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        notifManager.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}
