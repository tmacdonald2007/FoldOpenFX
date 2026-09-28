package com.tmacdonald2007.foldopenfx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class FoldFxService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var overlay: FoldFxOverlay
    private lateinit var sensorManager: SensorManager
    private var hingeSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(42, notification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

        if (hingeSensor == null || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        overlay = FoldFxOverlay(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlay, params)
        sensorManager.registerListener(this, hingeSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onDestroy() {
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        if (::overlay.isInitialized) runCatching { windowManager.removeView(overlay) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) {
            overlay.hingeAngle = event.values.firstOrNull() ?: 180f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "foldopenfx",
                "FoldOpenFX",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "foldopenfx")
        else
            Notification.Builder(this)

        return builder
            .setContentTitle("FoldOpenFX active")
            .setContentText("Monitoring the fold hinge")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}

private class FoldFxOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var hingeAngle: Float = 180f
        set(value) {
            field = value.coerceIn(0f, 180f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val center = width / 2f
        val open = (hingeAngle / 180f).coerceIn(0f, 1f)
        val closed = 1f - open
        val spread = 22f + 110f * closed

        shaderPaint.shader = LinearGradient(
            center - spread, 0f, center + spread, 0f,
            Color.argb((100f * closed).toInt(), 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(center - spread, 0f, center + spread, height.toFloat(), shaderPaint)
        shaderPaint.shader = null

        paint.color = Color.argb((35f + 110f * closed).toInt(), 255, 255, 255)
        canvas.drawRect(center - 1.5f, 0f, center + 1.5f, height.toFloat(), paint)

        paint.color = Color.argb((55f * closed).toInt(), 0, 0, 0)
        canvas.drawRect(center - spread, 0f, center - 1.5f, height.toFloat(), paint)
        canvas.drawRect(center + 1.5f, 0f, center + spread, height.toFloat(), paint)
    }
}
