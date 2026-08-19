package com.example.autoledger.ocr

import android.graphics.Bitmap
import android.util.Base64
import com.example.autoledger.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 云端 OCR（预留可切换）。满足"两者都做可切换"要求：
 * App 设置里把 OCR 模式切到 cloud 并填入 endpoint / apiKey 后启用。
 *
 * 说明：各厂商（腾讯/百度/阿里）的请求体与返回字段不同，这里给出
 *  - 统一接口（OcrEngine）
 *  - 一个最小可编译的 HTTP 实现（JPEG → Base64 → JSON POST）
 *  - parseResponse() 留作按厂商适配的扩展点（默认原样返回响应体，
 *    子类或配置可覆盖为提取 text 字段的逻辑）
 *
 * 接入具体厂商时，只需继承本类并重写 [parseResponse]，或在设置层注入
 * 不同的 endpoint / 鉴权头即可，无需改动监听与解析链路。
 */
open class CloudOcrEngine(
    private val endpoint: String,
    private val apiKey: String,
) : OcrEngine {

    override val name: String = "cloud"

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val body = postImage(bitmap)
        val text = parseResponse(body)
        OcrResult(text = text, blocks = emptyList(), engine = name)
    }

    /** 把图片编码为 Base64 并以 JSON 体 POST；鉴权头用 Bearer。子类可重写。 */
    protected open fun postImage(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val payload = "{\"image\":\"$b64\"}"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /** 响应体 → OCR 文本。默认原样返回（占位）；按厂商字段映射在此实现。 */
    protected open fun parseResponse(body: String): String = body
}
