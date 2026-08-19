package com.example.autoledger

import android.app.Application
import com.example.autoledger.data.AppDatabase

/**
 * 应用入口：初始化 Room 数据库与全局 OCR 引擎。
 * 与桌面版的 JsonStore 等价，这里用 Room 持久化到本机 SQLite，无任何云端依赖。
 */
class AutoLedgerApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
