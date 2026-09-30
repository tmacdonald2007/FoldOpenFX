package com.tmacdonald2007.foldopenfx

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FoldFxService : Service(), SensorEventListener {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CH = "foldfx"
        private const val NOTIF = 7
    }

    private lateinit var sensorManager: SensorManager
    private var hinge: Sensor? = null
    private var lastAngle = 180f
    private var moving = false

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var latestBitmap: Bitmap? = null

    private var wm: WindowManager? = null
    private var overlay: FoldOverlay? = null
    private var animator: ValueAnimator? = null
    private var lastTrigger = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF, notification())
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hinge = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (hinge != null) sensorManager.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (code != -1 && data != null && projection == null) startCapture(code, data)
        return START_STICKY
    }

    private fun startCapture(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, Handler(Looper.getMainLooper()))

        val dm = getSystemService(DisplayManager::class.java)
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ ir ->
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                b.copyPixelsFromBuffer(plane.buffer)
                synchronized(this) {
                    latestBitmap?.recycle()
                    latestBitmap = b
                }
            } catch (_: Throwable) {
            } finally {
                image.close()
            }
        }, Handler(Looper.getMainLooper()))

        virtualDisplay = projection!!.createVirtualDisplay(
            "FoldOpenFXCapture", w, h, metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null
        )
    }

    override fun onSensorChanged(e: SensorEvent) {
        val angle = e.values[0]
        val delta = abs(angle - lastAngle)
        if (delta > 0.7f) {
            if (!moving) {
                moving = true
                val now = SystemClock.uptimeMillis()
                if (now - lastTrigger > 500) {
                    lastTrigger = now
                    val opening = angle > lastAngle
                    val bmp = synchronized(this) { latestBitmap?.copy(Bitmap.Config.ARGB_8888, false) }
                    if (bmp != null) animateSnapshot(bmp, opening)
                }
            }
        } else if (moving && delta < 0.15f) {
            moving = false
        }
        lastAngle = angle
    }

    private fun animateSnapshot(bitmap: Bitmap, opening: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            bitmap.recycle()
            return
        }
        Handler(Looper.getMainLooper()).post {
            animator?.cancel()
            overlay?.let { removeOverlay(it) }
            val ov = FoldOverlay(this, bitmap)
            overlay = ov
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            try { wm!!.addView(ov, p) } catch (_: Throwable) { return@post }

            val start = if (opening) 0f else 1f
            val end = if (opening) 1f else 0f
            animator = ValueAnimator.ofFloat(start, end).apply {
                duration = 420
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { a ->
                    ov.progress = a.animatedValue as Float
                    ov.invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeOverlay(ov)
                    }
                })
                start()
            }
        }
    }

    private fun removeOverlay(v: View) {
        try { wm?.removeViewImmediate(v) } catch (_: Throwable) {}
        if (overlay === v) overlay = null
    }

    private fun stopCapture() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        virtualDisplay = null
        try { reader?.close() } catch (_: Throwable) {}
        reader = null
        synchronized(this) {
            latestBitmap?.recycle()
            latestBitmap = null
        }
        projection = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        animator?.cancel()
        overlay?.let { removeOverlay(it) }
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "FoldOpenFX", NotificationManager.IMPORTANCE_LOW))
        }
    }
    private fun notification(): Notification {
        return Notification.Builder(this, CH)
            .setContentTitle("FoldOpenFX active")
            .setContentText("Capturing the current screen for fold animation")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    class FoldOverlay(ctx: Context, private val source: Bitmap) : View(ctx) {
        var progress = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            val mid = w / 2f
            val gapMax = w * 0.28f
            val gap = gapMax * kotlin.math.sin(progress * Math.PI).toFloat()
            val leftInset = gap / 2f
            val rightInset = gap / 2f

            val srcW = source.width.toFloat()
            val srcH = source.height.toFloat()
            val sx = w / srcW
            val sy = h / srcH

            c.save()
            c.clipRect(0f, 0f, mid, h)
            val dstL = RectF(0f, 0f, mid - leftInset, h)
            val srcL = Rect(0, 0, source.width / 2, source.height)
            c.drawBitmap(source, srcL, dstL, paint)
            c.restore()

            c.save()
            c.clipRect(mid, 0f, w, h)
            val dstR = RectF(mid + rightInset, 0f, w, h)
            val srcR = Rect(source.width / 2, 0, source.width, source.height)
            c.drawBitmap(source, srcR, dstR, paint)
            c.restore()

            if (gap > 1f) {
                shadow.shader = LinearGradient(
                    mid - 28f, 0f, mid + 28f, 0f,
                    0x99000000.toInt(), 0x00000000,
                    Shader.TileMode.CLAMP
                )
                c.drawRect(mid - 28f, 0f, mid + 28f, h, shadow)
            }
        }
    }
}
