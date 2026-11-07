package com.ulanmaduo.desktop

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm:WindowManager
    private var bubble:View?=null
    override fun onCreate(){
        super.onCreate()
        val id="ulan_overlay"
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"乌兰麦朵悬浮桌宠",NotificationManager.IMPORTANCE_LOW))
        val pending=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val builder=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,id) else Notification.Builder(this)
        startForeground(1,builder.setContentTitle("乌兰麦朵正在待命").setContentText("点击返回桌宠控制页").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pending).build())
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val v=TextView(this).apply { text="乌兰\n麦朵"; textSize=15f; gravity=Gravity.CENTER; setTextColor(-1); setBackgroundColor(0xCC553F35.toInt()); setPadding(18,14,18,14) }
        val type=if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p=WindowManager.LayoutParams(-2,-2,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=80; y=220 }
        v.setOnTouchListener(object:View.OnTouchListener{
            var sx=0f; var sy=0f; var ox=0; var oy=0
            override fun onTouch(view:View,e:MotionEvent):Boolean { when(e.action){ MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=p.x;oy=p.y;return true}; MotionEvent.ACTION_MOVE->{p.x=ox+(e.rawX-sx).toInt();p.y=oy+(e.rawY-sy).toInt();wm.updateViewLayout(view,p);return true} };return true }
        })
        bubble=v; wm.addView(v,p)
    }
    override fun onDestroy(){ bubble?.let{wm.removeView(it)};bubble=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
