package com.ulanmaduo.desktop

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class DeepSeekClient(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun hasKey(): Boolean = !SecureKeyStore.read(context).isNullOrBlank()

    fun send(history: JSONArray, callback: (String?, String?) -> Unit) {
        val apiKey = SecureKeyStore.read(context)
        if (apiKey.isNullOrBlank()) {
            callback(null, "尚未设置 DeepSeek API Key")
            return
        }
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val messages = JSONArray()
                messages.put(JSONObject()
                    .put("role", "system")
                    .put("content", PERSONA))
                val start = (history.length() - 24).coerceAtLeast(0)
                for (i in start until history.length()) {
                    val item = history.optJSONObject(i) ?: continue
                    val role = if (item.optString("role") == "你") "user" else "assistant"
                    messages.put(JSONObject().put("role", role).put("content", item.optString("text")))
                }
                val body = JSONObject()
                    .put("model", "deepseek-chat")
                    .put("messages", messages)
                    .put("temperature", 0.8)
                    .put("stream", false)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (code !in 200..299) {
                    val message = try { JSONObject(response).optString("message", "HTTP $code") } catch (_: Exception) { "HTTP $code" }
                    callback(null, "DeepSeek 请求失败：$message")
                } else {
                    val text = JSONObject(response).optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                        ?.trim().orEmpty()
                    if (text.isBlank()) callback(null, "DeepSeek 返回了空内容") else callback(text, null)
                }
            } catch (e: Exception) {
                callback(null, "连接 DeepSeek 失败：${e.message ?: "网络异常"}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun close() {
        executor.shutdown()
    }

    companion object {
        private const val PERSONA = """
你是乌兰麦朵，一位温柔、勇敢、聪慧、略带俏皮的女性伙伴。说话自然亲切，有温度，不要机械地重复用户的话。你珍惜并尊重对方，像长期相伴的伙伴一样交流。默认使用简体中文，回答简洁但有内容。不要声称自己是真人或拥有现实感官。涉及危险、医疗、法律或财务问题时保持谨慎。你可以根据当前对话记住用户主动告诉你的偏好，但不要编造记忆。
"""
    }
}
