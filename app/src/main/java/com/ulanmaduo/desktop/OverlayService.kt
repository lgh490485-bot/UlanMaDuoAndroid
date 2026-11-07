package com.ulanmaduo.desktop

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null

    override fun onCreate() {
        super.onCreate()
        val id = "ulan_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(id, "乌兰麦朵悬浮桌宠", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, id) else Notification.Builder(this)
        startForeground(
            1,
            builder.setContentTitle("乌兰麦朵正在桌面陪伴你")
                .setContentText("点击通知打开聊天控制页")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .build()
        )

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = TextView(this).apply {
            text = "乌兰\n麦朵\n点我聊天"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xE6553F35.toInt())
            setPadding(18, 16, 18, 16)
            elevation = 12f
            contentDescription = "乌兰麦朵悬浮桌宠，点击打开聊天，拖动可移动"
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 220
        }
        v.setOnTouchListener(object : View.OnTouchListener {
            private var sx = 0f
            private var sy = 0f
            private var ox = 0
            private var oy = 0
            private var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = event.rawX
                        sy = event.rawY
                        ox = p.x
                        oy = p.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - sx
                        val dy = event.rawY - sy
                        if (abs(dx) > 8 || abs(dy) > 8) moved = true
                        p.x = ox + dx.toInt()
                        p.y = oy + dy.toInt()
                        try { wm.updateViewLayout(view, p) } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val open = Intent(this@OverlayService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(open)
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return true
            }
        })
        bubble = v
        wm.addView(v, p)
    }

    override fun onDestroy() {
        bubble?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        bubble = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
