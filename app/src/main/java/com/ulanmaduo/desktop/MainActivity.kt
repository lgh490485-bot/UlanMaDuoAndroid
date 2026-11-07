package com.ulanmaduo.desktop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var log: TextView
    private lateinit var input: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,36,28,24); setBackgroundColor(0xFFF4F0E8.toInt()) }
        root.addView(TextView(this).apply { text="乌兰麦朵"; textSize=28f; setTextColor(0xFF382D27.toInt()) })
        root.addView(TextView(this).apply { text="桌面伙伴 · 开发预览版"; textSize=14f; setTextColor(0xFF76685D.toInt()) })
        log=TextView(this).apply { text="“我在这里。先把悬浮窗打开，我们再继续。”\n\n当前版本是功能底座，尚未集成本地大语言模型和正式 3D 角色。"; textSize=16f; setPadding(0,32,0,24) }
        root.addView(log,LinearLayout.LayoutParams(-1,0,1f))
        input=EditText(this).apply { hint="对乌兰麦朵说点什么"; textSize=16f }; root.addView(input)
        root.addView(Button(this).apply { text="发送文字"; setOnClickListener { val msg=input.text.toString().trim(); if(msg.isNotEmpty()){ log.text="你：$msg\n\n乌兰麦朵：我听见了。当前预览版尚未接入本地 AI 模型。"; input.text.clear() } } })
        root.addView(Button(this).apply { text="语音输入（系统识别服务）"; setOnClickListener { startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),9) } })
        root.addView(Button(this).apply { text="开启桌面悬浮角色"; setOnClickListener { if(!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) else { startForegroundService(Intent(this@MainActivity,OverlayService::class.java)); Toast.makeText(this@MainActivity,"悬浮角色已启动",Toast.LENGTH_SHORT).show() } } })
        setContentView(root)
    }
    override fun onInit(status:Int) { if(status==TextToSpeech.SUCCESS) tts?.language=Locale.SIMPLIFIED_CHINESE }
    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==9 && resultCode==RESULT_OK) input.setText(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()) }
    override fun onDestroy(){ tts?.shutdown(); super.onDestroy() }
}
