package com.ulanmaduo.desktop

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var ai: DeepSeekClient
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("ulan_maduo", MODE_PRIVATE) }
    private val history by lazy { JSONArray(prefs.getString("history", "[]")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ai = DeepSeekClient(this)
        tts = TextToSpeech(this, this)
        buildScreen()
        restoreHistory()
        refreshStatus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(12))
            setBackgroundColor(0xFFF4F0E8.toInt())
        }
        root.addView(TextView(this).apply {
            text = "乌兰麦朵"
            textSize = 28f
            setTextColor(0xFF382D27.toInt())
        })
        status = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF76685D.toInt())
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(status)

        val avatar = Character3DView(this)
        root.addView(avatar, LinearLayout.LayoutParams(-1, dp(250)))

        scroll = ScrollView(this)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(8))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        input = EditText(this).apply {
            hint = "对乌兰麦朵说点什么"
            textSize = 16f
            maxLines = 3
        }
        root.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sendRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sendRow.addView(Button(this).apply {
            text = "发送"
            setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        sendRow.addView(Button(this).apply {
            text = "语音输入"
            setOnClickListener { startVoiceInput() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(sendRow)

        val configRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        configRow.addView(Button(this).apply {
            text = "设置 DeepSeek"
            setOnClickListener { showApiKeyDialog() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        configRow.addView(Button(this).apply {
            text = "选择甜美女声"
            setOnClickListener { showVoicePicker() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(configRow)

        val overlayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        overlayRow.addView(Button(this).apply {
            text = "开启悬浮桌宠"
            setOnClickListener { startOverlay() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        overlayRow.addView(Button(this).apply {
            text = "关闭悬浮桌宠"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(overlayRow)
        root.addView(Button(this).apply {
            text = "清空本机聊天记录"
            setOnClickListener {
                historyClear()
                Toast.makeText(this@MainActivity, "聊天记录已清空", Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(root)
    }

    private fun refreshStatus() {
        status.text = if (ai.hasKey()) {
            "DeepSeek 已配置 · 对话通过网络请求 · 密钥加密保存在本机"
        } else {
            "3D 角色预览 · 请点“设置 DeepSeek”填入你自己的 API Key"
        }
    }

    private fun showApiKeyDialog() {
        val field = EditText(this).apply {
            hint = "sk-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(field)
            addView(TextView(this@MainActivity).apply {
                text = "密钥只保存在本机 Android Keystore 加密存储中，不会写入 GitHub。"
                textSize = 12f
                setTextColor(0xFF76685D.toInt())
            })
        }
        AlertDialog.Builder(this)
            .setTitle("配置 DeepSeek API Key")
            .setView(wrap)
            .setPositiveButton("加密保存") { _, _ ->
                val value = field.text.toString().trim()
                if (value.isNotEmpty()) {
                    SecureKeyStore.save(this, value)
                    refreshStatus()
                    Toast.makeText(this, "API Key 已加密保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("清除密钥") { _, _ ->
                SecureKeyStore.clear(this)
                refreshStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showVoicePicker() {
        val engine = tts ?: return
        val voices = engine.voices?.filter { it.locale.language == "zh" }?.sortedBy { it.name }.orEmpty()
        if (voices.isEmpty()) {
            Toast.makeText(this, "系统暂未提供中文 TTS 声音，请安装中文语音包", Toast.LENGTH_LONG).show()
            return
        }
        val names = voices.map { voice ->
            val quality = if (voice.quality >= Voice.QUALITY_HIGH) "高音质" else "标准"
            "${voice.name} · $quality"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择中文声音（已调整为柔和语速和音调）")
            .setItems(names) { _, which ->
                engine.voice = voices[which]
                prefs.edit().putString("tts_voice", voices[which].name).apply()
                engine.setPitch(1.16f)
                engine.setSpeechRate(0.92f)
                Toast.makeText(this, "已切换声音：${voices[which].name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun restoreHistory() {
        if (history.length() == 0) {
            addBubble("乌兰麦朵", "我在。现在可以看见我的 3D 形象了。把 DeepSeek 密钥配置好，我们就能真正聊天。")
            return
        }
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            addBubble(item.optString("role"), item.optString("text"))
        }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        input.text.clear()
        append("你", message)
        if (!ai.hasKey()) {
            append("乌兰麦朵", "先把 DeepSeek API Key 配置好，我就能认真回答你啦。")
            showApiKeyDialog()
            return
        }
        status.text = "乌兰麦朵正在思考…"
        ai.send(history) { reply, error ->
            runOnUiThread {
                if (reply != null) {
                    append("乌兰麦朵", reply)
                    speak(reply)
                    refreshStatus()
                } else {
                    append("系统提示", error ?: "请求失败，请检查网络和 API Key")
                    refreshStatus()
                }
            }
        }
    }

    private fun speak(text: String) {
        tts?.let {
            it.setPitch(1.16f)
            it.setSpeechRate(0.92f)
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ulan_reply")
        }
    }

    private fun append(role: String, text: String) {
        addBubble(role, text)
        history.put(JSONObject().put("role", role).put("text", text))
        while (history.length() > 100) history.remove(0)
        prefs.edit().putString("history", history.toString()).apply()
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun addBubble(role: String, text: String) {
        val view = TextView(this).apply {
            this.text = "$role：$text"
            textSize = 15f
            setTextColor(if (role == "你") 0xFF4C514E.toInt() else 0xFF382D27.toInt())
            setBackgroundColor(if (role == "你") 0xFFE5E8E3.toInt() else 0xFFE9DFD2.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        chat.addView(view, lp)
    }

    private fun historyClear() {
        while (history.length() > 0) history.remove(0)
        prefs.edit().remove("history").apply()
        chat.removeAllViews()
        addBubble("乌兰麦朵", "记录清空了。我们重新开始。")
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话")
        }
        try {
            startActivityForResult(intent, 9)
        } catch (_: Exception) {
            Toast.makeText(this, "当前设备没有可用的语音识别服务", Toast.LENGTH_LONG).show()
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "悬浮桌宠已启动", Toast.LENGTH_SHORT).show()
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                engine.setPitch(1.16f)
                engine.setSpeechRate(0.92f)
                val saved = prefs.getString("tts_voice", null)
                val available = engine.voices.orEmpty()
                val selected = available.firstOrNull { it.name == saved }
                    ?: available.firstOrNull { it.locale.language == "zh" && it.name.contains("female", true) }
                    ?: available.firstOrNull { it.locale.language == "zh" }
                if (selected != null) engine.voice = selected
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            input.setText(spoken)
            input.setSelection(input.text.length)
        }
    }

    override fun onDestroy() {
        ai.close()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
