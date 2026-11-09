
package com.ats.sentinel

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TacticalOrbService : Service() {

    companion object {
        const val ACTION_STOP =
            "com.ats.sentinel.action.STOP_ORB"

        private const val CHANNEL_ID = "ats_holo_grid"
        private const val NOTIFICATION_ID = 1101
    }

    private lateinit var windowManager: WindowManager

    private var orbContainer: FrameLayout? = null
    private var holoView: HoloGridView? = null
    private var added = false

    private val handler = Handler(Looper.getMainLooper())

    private var dragX = 0
    private var dragY = 0
    private var touchX = 0f
    private var touchY = 0f

    private var isDragging = false
    private var isLongPress = false

    private val touchSlop by lazy {
        ViewConfiguration.get(this).scaledTouchSlop
    }

    private val longPressRunnable = Runnable {
        isLongPress = true

        Toast.makeText(
            this,
            "ATS AI voice input will be enabled in the next phase",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        showOrb()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    // -----------------------------------------
    // NOTIFICATION
    // -----------------------------------------

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "ATS Holographic Orb",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "ATS floating holographic interface"

            channel.setShowBadge(false)

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ATS TACTICAL SENTINEL")
            .setContentText("ATS AI Orb is running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    // -----------------------------------------
    // ATS AI FLOATING ORB
    // -----------------------------------------

    private fun showOrb() {

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        val holo = HoloGridView(this)
        holoView = holo

        container.addView(
            holo,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            dp(72),
            dp(72),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.TOP or Gravity.START
            x = dp(20)
            y = dp(130)
        }

        // -------------------------------------
        // TOUCH, DRAG AND LONG PRESS
        // -------------------------------------

        val touchListener =
            View.OnTouchListener { _, event ->

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {

                        handler.removeCallbacks(
                            longPressRunnable
                        )

                        dragX = params.x
                        dragY = params.y

                        touchX = event.rawX
                        touchY = event.rawY

                        isDragging = false
                        isLongPress = false

                        handler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )

                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        val deltaX = event.rawX - touchX
                        val deltaY = event.rawY - touchY

                        if (
                            abs(deltaX) > touchSlop ||
                            abs(deltaY) > touchSlop
                        ) {
                            isDragging = true

                            handler.removeCallbacks(
                                longPressRunnable
                            )
                        }

                        if (isDragging) {

                            params.x = dragX + deltaX.toInt()
                            params.y = dragY + deltaY.toInt()

                            try {
                                windowManager.updateViewLayout(
                                    container,
                                    params
                                )
                            } catch (_: Exception) {
                            }
                        }

                        true
                    }

                    MotionEvent.ACTION_UP -> {

                        handler.removeCallbacks(
                            longPressRunnable
                        )

                        if (!isDragging && !isLongPress) {
                            openATS()
                        }

                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {

                        handler.removeCallbacks(
                            longPressRunnable
                        )

                        isDragging = false
                        isLongPress = false

                        true
                    }

                    else -> false
                }
            }

        holo.setOnTouchListener(touchListener)

        try {

            windowManager.addView(
                container,
                params
            )

            orbContainer = container
            added = true

        } catch (_: Exception) {

            stopSelf()
        }
    }

    // -----------------------------------------
    // OPEN ATS APPLICATION
    // -----------------------------------------

    private fun openATS() {

        try {

            val intent = Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            startActivity(intent)

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Unable to open ATS",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // -----------------------------------------
    // CLEANUP
    // -----------------------------------------

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        holoView?.stopAnimation()

        if (added) {
            try {
                orbContainer?.let {
                    windowManager.removeView(it)
                }
            } catch (_: Exception) {
            }
        }

        added = false
        holoView = null
        orbContainer = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // =========================================
    // HOLOGRAPHIC SPHERE ENGINE
    // =========================================

    private class HoloGridView(
        context: Context
    ) : View(context) {

        private val blue = Color.rgb(0, 190, 255)
        private val brightBlue = Color.rgb(110, 230, 255)

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private var rotation = 0f
        private var pulse = 0f

        private val animator =
            ValueAnimator.ofFloat(0f, 360f).apply {

                duration = 6500L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()

                addUpdateListener {

                    rotation =
                        it.animatedValue as Float

                    pulse = (
                        sin(
                            Math.toRadians(
                                rotation.toDouble() * 2.0
                            )
                        ) + 1.0
                    ).toFloat() / 2f

                    invalidate()
                }
            }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            if (!animator.isStarted) {
                animator.start()
            }
        }

        fun stopAnimation() {
            animator.cancel()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.36f

            drawAmbientGlow(canvas, cx, cy, radius)
            drawOrbitalRings(canvas, cx, cy, radius)
            drawSphere(canvas, cx, cy, radius)
            drawSphereGrid(canvas, cx, cy, radius)
            drawSurfaceLights(canvas, cx, cy, radius)
            drawCore(canvas, cx, cy, radius)
            drawOrbitParticles(canvas, cx, cy, radius)
        }

        // -------------------------------------
        // AMBIENT LIGHT
        // -------------------------------------

        private fun drawAmbientGlow(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL

            paint.shader = RadialGradient(
                cx,
                cy,
                radius * 1.45f,
                intArrayOf(
                    Color.argb(75, 0, 150, 255),
                    Color.argb(25, 0, 90, 180),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawCircle(
                cx,
                cy,
                radius * 1.45f,
                paint
            )

            paint.shader = null
        }

        // -------------------------------------
        // ROTATING ORBITAL RINGS
        // -------------------------------------

        private fun drawOrbitalRings(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            for (i in 0..2) {

                val save = canvas.save()

                canvas.rotate(
                    rotation + i * 60f,
                    cx,
                    cy
                )

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                paint.color = blue
                paint.alpha = 155

                canvas.drawOval(
                    cx - radius * 1.28f,
                    cy - radius * 0.30f,
                    cx + radius * 1.28f,
                    cy + radius * 0.30f,
                    paint
                )

                canvas.restoreToCount(save)
            }
        }

        // -------------------------------------
        // SPHERE BODY
        // -------------------------------------

        private fun drawSphere(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL

            paint.shader = RadialGradient(
                cx - radius * 0.30f,
                cy - radius * 0.35f,
                radius * 1.4f,
                intArrayOf(
                    Color.rgb(15, 85, 135),
                    Color.rgb(3, 35, 75),
                    Color.rgb(0, 8, 25)
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawCircle(
                cx,
                cy,
                radius,
                paint
            )

            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = brightBlue
            paint.alpha = 220

            canvas.drawCircle(
                cx,
                cy,
                radius,
                paint
            )
        }

        // -------------------------------------
        // LONGITUDE AND LATITUDE GRID
        // -------------------------------------

        private fun drawSphereGrid(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            val clip = Path().apply {
                addCircle(
                    cx,
                    cy,
                    radius,
                    Path.Direction.CW
                )
            }

            val save = canvas.save()
            canvas.clipPath(clip)

            // Longitude lines

            for (line in -5..5) {

                val angle =
                    Math.toRadians(
                        (line * 18f + rotation).toDouble()
                    )

                val horizontalRadius =
                    kotlin.math.abs(cos(angle)).toFloat() *
                        radius

                paint.style = Paint.Style.STROKE
                paint.strokeWidth =
                    if (line == 0) 1.5f else 0.9f

                paint.color = brightBlue
                paint.alpha = 165

                canvas.drawOval(
                    cx - horizontalRadius,
                    cy - radius,
                    cx + horizontalRadius,
                    cy + radius,
                    paint
                )
            }

            // Latitude lines

            for (line in -4..4) {

                val latitude = line * 0.30f

                val y =
                    cy + sin(latitude.toDouble()).toFloat() *
                        radius

                val halfWidth =
                    cos(latitude.toDouble()).toFloat() *
                        radius

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = blue
                paint.alpha = 180

                canvas.drawOval(
                    cx - halfWidth,
                    y - radius * 0.075f,
                    cx + halfWidth,
                    y + radius * 0.075f,
                    paint
                )
            }

            canvas.restoreToCount(save)
        }

        // -------------------------------------
        // SURFACE LIGHTS
        // -------------------------------------

        private fun drawSurfaceLights(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            val points = arrayOf(
                Pair(-0.48f, -0.30f),
                Pair(0.42f, -0.35f),
                Pair(0.58f, 0.10f),
                Pair(-0.40f, 0.43f),
                Pair(0.25f, 0.57f),
                Pair(-0.10f, -0.62f)
            )

            for ((x, y) in points) {

                val px = cx + x * radius
                val py = cy + y * radius

                val wave = (
                    sin(
                        Math.toRadians(
                            rotation.toDouble() * 2.0
                        ) + x * 8.0
                    ) + 1.0
                ).toFloat() / 2f

                paint.style = Paint.Style.FILL
                paint.color = brightBlue
                paint.alpha = (100 + wave * 155).toInt()

                canvas.drawCircle(
                    px,
                    py,
                    2.5f + wave * 2f,
                    paint
                )

                paint.alpha = 45

                canvas.drawCircle(
                    px,
                    py,
                    7f + wave * 4f,
                    paint
                )
            }
        }

        // -------------------------------------
        // CENTRAL PULSING CORE
        // -------------------------------------

        private fun drawCore(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL
            paint.color = blue
            paint.alpha = (35 + pulse * 55).toInt()

            canvas.drawCircle(
                cx,
                cy,
                radius * (0.25f + pulse * 0.04f),
                paint
            )

            paint.alpha = 225
            paint.color = brightBlue

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.10f,
                paint
            )

            paint.color = Color.WHITE
            paint.alpha = 255

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.035f,
                paint
            )

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = brightBlue
            paint.alpha = 220

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.30f,
                paint
            )
        }

        // -------------------------------------
        // MOVING ORBIT PARTICLES
        // -------------------------------------

        private fun drawOrbitParticles(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            for (i in 0 until 4) {

                val angle = Math.toRadians(
                    (rotation + i * 90f).toDouble()
                )

                val x =
                    cx + cos(angle).toFloat() *
                        radius * 1.28f

                val y =
                    cy + sin(angle).toFloat() *
                        radius * 0.30f

                paint.style = Paint.Style.FILL
                paint.color = brightBlue
                paint.alpha = 235

                canvas.drawCircle(
                    x,
                    y,
                    3f,
                    paint
                )
            }
        }
    }
}
