package com.example.autoledger.ocr

import android.content.Context
import android.content.SharedPreferences

/**
 * 按设置提供 OCR 引擎：local = ML Kit 端侧（默认，离线免费）；
 * cloud = 填入 endpoint / apiKey 后切换（满足"可切换"要求）。
 */
object OcrEngineProvider {

    private const val PREFS = "autoledger"
    private const val KEY_MODE = "ocr_mode"
    private const val KEY_ENDPOINT = "cloud_endpoint"
    private const val KEY_API_KEY = "cloud_api_key"

    fun getEngine(context: Context): OcrEngine {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getString(KEY_MODE, "local") == "cloud") {
            CloudOcrEngine(
                endpoint = prefs.getString(KEY_ENDPOINT, "") ?: "",
                apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            )
        } else {
            MlKitOcrEngine()
        }
    }

    fun saveCloudConfig(context: Context, endpoint: String, apiKey: String) {
        edit(context) {
            putString(KEY_MODE, "cloud")
            putString(KEY_ENDPOINT, endpoint)
            putString(KEY_API_KEY, apiKey)
        }
    }

    fun useLocal(context: Context) {
        edit(context) { putString(KEY_MODE, "local") }
    }

    private fun edit(context: Context, block: SharedPreferences.Editor.() -> Unit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply()
    }
}
