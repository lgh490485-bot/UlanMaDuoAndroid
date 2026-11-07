package com.ulanmaduo.desktop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private val prefs by lazy { getSharedPreferences("ulan_maduo", MODE_PRIVATE) }
    private val history by lazy { JSONArray(prefs.getString("history", "[]")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildScreen()
        restoreHistory()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 18)
            setBackgroundColor(0xFFF4F0E8.toInt())
        }
        root.addView(TextView(this).apply {
            text = "乌兰麦朵"
            textSize = 30f
            setTextColor(0xFF382D27.toInt())
        })
        root.addView(TextView(this).apply {
            text = "桌面伙伴 · 0.2.0 开发版"
            textSize = 13f
            setTextColor(0xFF76685D.toInt())
        })
        root.addView(TextView(this).apply {
            text = "本地陪伴模式 · 聊天记录保存在本机"
            textSize = 12f
            setTextColor(0xFF76685D.toInt())
            setPadding(0, 8, 0, 8)
        })

        scroll = ScrollView(this)
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
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

    private fun restoreHistory() {
        if (history.length() == 0) {
            addBubble("乌兰麦朵", "我在。你可以和我说说今天的事，也可以把我放到桌面陪着你。")
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
        val reply = localReply(message)
        append("乌兰麦朵", reply)
        tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "ulan_reply")
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
            textSize = 16f
            setTextColor(if (role == "你") 0xFF4C514E.toInt() else 0xFF382D27.toInt())
            setBackgroundColor(if (role == "你") 0xFFE5E8E3.toInt() else 0xFFE9DFD2.toInt())
            setPadding(16, 12, 16, 12)
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
        chat.addView(view, lp)
    }

    private fun historyClear() {
        historyClearInternal()
        chat.removeAllViews()
        addBubble("乌兰麦朵", "记录清空了。我们重新开始。")
    }

    private fun historyClearInternal() {
        while (history.length() > 0) history.remove(0)
        prefs.edit().remove("history").apply()
    }

    private fun localReply(message: String): String {
        val m = message.lowercase(Locale.ROOT)
        return when {
            listOf("你好", "嗨", "在吗", "早上好", "晚上好").any { m.contains(it) } ->
                "我在这里。见到你，我很高兴。今天想从什么事情聊起？"
            listOf("谢谢", "感谢").any { m.contains(it) } ->
                "不用客气。能帮上你，我就安心了。"
            listOf("再见", "晚安", "睡觉").any { m.contains(it) } ->
                "好好休息吧。我会在这里等你回来。"
            listOf("你是谁", "介绍一下").any { m.contains(it) } ->
                "我是乌兰麦朵，你的桌面伙伴。现在运行的是本地基础陪伴模式。"
            listOf("时间", "几点").any { m.contains(it) } ->
                "现在是 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date()) + "。"
            listOf("难过", "伤心", "压力", "烦").any { m.contains(it) } ->
                "先别急着一个人扛着。愿意的话，告诉我发生了什么，我会认真听。"
            listOf("开心", "高兴", "成功").any { m.contains(it) } ->
                "听起来是件好事。把这份开心记住吧，也和我分享一点细节。"
            else ->
                "我听见了。现在的版本使用本地基础对话策略，还没有接入完整离线大模型；你可以继续告诉我，我会把这段对话保存在本机。"
        }
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                runOnUiThread { Toast.makeText(this, "系统未安装中文语音包，请在系统设置中安装", Toast.LENGTH_LONG).show() }
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
