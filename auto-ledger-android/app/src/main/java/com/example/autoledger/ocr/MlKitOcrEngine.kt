package com.example.autoledger.ocr

import android.graphics.Bitmap
import com.example.autoledger.OcrBlock
import com.example.autoledger.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * 端侧 OCR：Google ML Kit 简体中文识别。
 * 免费、离线、无需 API Key，隐私最优；这是默认引擎，与桌面版 Tesseract 本地方案对应。
 */
class MlKitOcrEngine : OcrEngine {

    override val name: String = "mlkit-zh"

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val blocks = result.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val rect = line.boundingBox
                OcrBlock(
                    text = line.text,
                    confidence = line.confidence ?: 0f,
                    bbox = rect,
                )
            }
        }
        return OcrResult(text = result.text, blocks = blocks, engine = name)
    }
}
