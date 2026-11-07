package com.ulanmaduo.desktop

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var pet: Character3DView? = null
    private var label: TextView? = null
    private var container: FrameLayout? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
                .setContentText("点击桌宠打开聊天，长按拖动")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .build()
        )

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val frame = FrameLayout(this)
        val character = Character3DView(this)
        character.setCasualMode(getSharedPreferences("ulan_maduo", MODE_PRIVATE).getBoolean("casual_outfit", false))
        frame.addView(character, FrameLayout.LayoutParams(dp(164), dp(208), Gravity.CENTER))
        val caption = TextView(this).apply {
            text = "乌兰麦朵"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF493A32.toInt())
            setBackgroundColor(0xBFF4F0E8.toInt())
        }
        frame.addView(caption, FrameLayout.LayoutParams(-1, dp(26), Gravity.BOTTOM))
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(
            dp(176), dp(238), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(42)
            y = dp(150)
        }
        val characterParams = character.layoutParams as FrameLayout.LayoutParams
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newWidth = (p.width * detector.scaleFactor).toInt().coerceIn(dp(124), dp(300))
                val newHeight = (p.height * detector.scaleFactor).toInt().coerceIn(dp(168), dp(390))
                p.width = newWidth
                p.height = newHeight
                characterParams.width = (dp(164) * newWidth / dp(176)).coerceIn(dp(112), dp(280))
                characterParams.height = (dp(208) * newHeight / dp(238)).coerceIn(dp(150), dp(360))
                character.layoutParams = characterParams
                try { wm.updateViewLayout(frame, p) } catch (_: Exception) { }
                return true
            }
        })
        frame.setOnTouchListener(object : View.OnTouchListener {
            private var sx = 0f
            private var sy = 0f
            private var ox = 0
            private var oy = 0
            private var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                scaleDetector.onTouchEvent(event)
                if (scaleDetector.isInProgress || event.pointerCount > 1) {
                    moved = true
                    return true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = event.rawX; sy = event.rawY
                        ox = p.x; oy = p.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - sx
                        val dy = event.rawY - sy
                        if (abs(dx) > dp(7) || abs(dy) > dp(7)) moved = true
                        p.x = ox + dx.toInt()
                        p.y = oy + dy.toInt()
                        try { wm.updateViewLayout(view, p) } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            startActivity(Intent(this@OverlayService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            })
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return true
            }
        })
        container = frame
        pet = character
        label = caption
        wm.addView(frame, p)
    }

    override fun onDestroy() {
        pet?.onPause()
        container?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        pet = null
        label = null
        container = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
