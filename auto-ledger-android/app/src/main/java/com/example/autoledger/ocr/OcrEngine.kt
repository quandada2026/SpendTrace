package com.example.autoledger.ocr

import android.graphics.Bitmap
import com.example.autoledger.OcrResult

/**
 * OCR 抽象层。与核心引擎的 OcrEngine 接口等价：
 * 外壳（截图监听服务）只依赖此接口，可随时在端侧 ML Kit 与云端之间切换。
 */
interface OcrEngine {
    val name: String
    suspend fun recognize(bitmap: Bitmap): OcrResult
}
