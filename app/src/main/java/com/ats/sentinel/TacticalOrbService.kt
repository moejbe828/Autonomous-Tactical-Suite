
package com.ats.sentinel

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
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

    private var orbContainer: View? = null
    private var holoView: HoloGridView? = null

    private var added = false

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

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    // -----------------------------------------------
    // NOTIFICATION
    // -----------------------------------------------

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

        val stopIntent = Intent(
            this,
            TacticalOrbService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1102,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle("ATS TACTICAL SENTINEL")
                .setContentText("HOLO GRID is running")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "STOP",
                    stopPendingIntent
                )

        return builder.build()
    }

    // -----------------------------------------------
    // HOLOGRAPHIC OVERLAY
    // -----------------------------------------------

    private fun showOrb() {

        val container = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(2, 8, 20),
                    Color.rgb(4, 19, 42),
                    Color.rgb(1, 5, 15)
                )
            ).apply {

                cornerRadius = dp(22).toFloat()

                setStroke(
                    dp(1),
                    Color.rgb(0, 175, 255)
                )
            }
        }

        val header = TextView(this).apply {

            text = "ATS // HOLO GRID"

            textSize = 13f
            gravity = Gravity.CENTER
            letterSpacing = 0.12f

            typeface = Typeface.DEFAULT_BOLD

            setTextColor(
                Color.rgb(75, 210, 255)
            )
        }

        val subtitle = TextView(this).apply {

            text = "HOLOGRAPHIC MONITORING CORE"

            textSize = 8f
            gravity = Gravity.CENTER
            letterSpacing = 0.15f

            setTextColor(
                Color.rgb(70, 145, 205)
            )
        }

        val holo = HoloGridView(this)
        holoView = holo

        val status = TextView(this).apply {

            text = "● HOLO CORE ONLINE"

            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = 0.10f

            setTextColor(
                Color.rgb(50, 195, 255)
            )
        }

        val stopButton = TextView(this).apply {

            text = "■  STOP ORB"

            gravity = Gravity.CENTER
            textSize = 11f
            letterSpacing = 0.12f

            typeface = Typeface.DEFAULT_BOLD

            setTextColor(Color.WHITE)

            background = GradientDrawable().apply {

                setColor(
                    Color.rgb(5, 35, 65)
                )

                cornerRadius = dp(10).toFloat()

                setStroke(
                    dp(1),
                    Color.rgb(0, 160, 255)
                )
            }

            setOnClickListener {
                stopSelf()
            }
        }

        container.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(25)
            )
        )

        container.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(18)
            )
        )

        container.addView(
            holo,
            LinearLayout.LayoutParams(
                dp(210),
                dp(210)
            )
        )

        container.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(25)
            )
        )

        container.addView(
            stopButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(35)
            )
        )

        val params = WindowManager.LayoutParams(
            dp(240),
            dp(330),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.TOP or Gravity.START

            x = dp(20)
            y = dp(130)
        }

        // Dragging without blocking STOP button.

        val dragListener =
            View.OnTouchListener { _, event ->

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {
                        dragX = params.x
                        dragY = params.y

                        touchX = event.rawX
                        touchY = event.rawY

                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        params.x = dragX +
                            (event.rawX - touchX).toInt()

                        params.y = dragY +
                            (event.rawY - touchY).toInt()

                        try {
                            windowManager.updateViewLayout(
                                container,
                                params
                            )
                        } catch (_: Exception) {
                        }

                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> true

                    else -> false
                }
            }

        header.setOnTouchListener(dragListener)
        holo.setOnTouchListener(dragListener)

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

    private var dragX = 0
    private var dragY = 0

    private var touchX = 0f
    private var touchY = 0f

    // -----------------------------------------------
    // CLEANUP
    // -----------------------------------------------

    override fun onDestroy() {

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

    // ===============================================
    // HOLO GRID GRAPHICS ENGINE
    // ===============================================

    private class HoloGridView(
        context: Context
    ) : View(context) {

        private val blue = Color.rgb(0, 190, 255)
        private val brightBlue = Color.rgb(100, 225, 255)
        private val deepBlue = Color.rgb(0, 65, 130)

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val spherePath = Path()

        private var rotation = 0f
        private var pulse = 0f

        private var backgroundShader: Shader? = null

        private val animator =
            ValueAnimator.ofFloat(0f, 360f).apply {

                duration = 6500L

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    LinearInterpolator()

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

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int
        ) {
            super.onSizeChanged(w, h, oldw, oldh)

            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) * 0.36f

            backgroundShader = RadialGradient(
                cx,
                cy,
                radius * 1.5f,
                intArrayOf(
                    Color.rgb(0, 85, 160),
                    Color.rgb(0, 28, 65),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
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

            drawAmbientGlow(
                canvas,
                cx,
                cy,
                radius
            )

            drawOrbitalRings(
                canvas,
                cx,
                cy,
                radius
            )

            drawSphere(
                canvas,
                cx,
                cy,
                radius
            )

            drawSphereGrid(
                canvas,
                cx,
                cy,
                radius
            )

            drawSurfaceLights(
                canvas,
                cx,
                cy,
                radius
            )

            drawCore(
                canvas,
                cx,
                cy,
                radius
            )

            drawOrbitParticles(
                canvas,
                cx,
                cy,
                radius
            )
        }

        // -------------------------------------------
        // AMBIENT GLOW
        // -------------------------------------------

        private fun drawAmbientGlow(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL
            paint.shader = backgroundShader

            canvas.drawCircle(
                cx,
                cy,
                radius * 1.5f,
                paint
            )

            paint.shader = null
        }

        // -------------------------------------------
        // ORBITAL RINGS
        // -------------------------------------------

        private fun drawOrbitalRings(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            for (ring in 0..2) {

                val angle =
                    rotation + ring * 120f

                val orbitPath = Path()

                for (i in 0..100) {

                    val t =
                        Math.toRadians(
                            i * 3.6
                        )

                    val a =
                        Math.toRadians(
                            angle.toDouble()
                        )

                    val x =
                        cx + cos(t).toFloat() *
                            radius * 1.28f

                    val y =
                        cy + sin(t).toFloat() *
                            radius * 0.30f

                    val dx = x - cx
                    val dy = y - cy

                    val rx =
                        dx * cos(a).toFloat() -
                            dy * sin(a).toFloat()

                    val ry =
                        dx * sin(a).toFloat() +
                            dy * cos(a).toFloat()

                    if (i == 0) {
                        orbitPath.moveTo(
                            cx + rx,
                            cy + ry
                        )
                    } else {
                        orbitPath.lineTo(
                            cx + rx,
                            cy + ry
                        )
                    }
                }

                orbitPath.close()

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                paint.color = blue
                paint.alpha = 125

                canvas.drawPath(
                    orbitPath,
                    paint
                )
            }
        }

        // -------------------------------------------
        // SPHERE BODY
        // -------------------------------------------

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
                    Color.rgb(20, 105, 165),
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
            paint.strokeWidth = 1.7f
            paint.color = brightBlue
            paint.alpha = 210

            canvas.drawCircle(
                cx,
                cy,
                radius,
                paint
            )
        }

        // -------------------------------------------
        // HOLOGRAPHIC LATITUDE / LONGITUDE GRID
        // -------------------------------------------

        private fun drawSphereGrid(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            spherePath.reset()

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

                val baseAngle =
                    line * 0.30f

                val angle =
                    baseAngle +
                        Math.toRadians(
                            rotation.toDouble()
                        ).toFloat()

                val visibility =
                    cos(angle)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth =
                    if (line == 0) 1.8f else 1.0f

                paint.color = brightBlue
                paint.alpha =
                    (65 + 140 *
                        ((visibility + 1f) / 2f)
                    ).toInt()

                val path = Path()

                for (step in 0..80) {

                    val latitude =
                        -Math.PI / 2.0 +
                            step * Math.PI / 80.0

                    val x =
                        cx + sin(angle.toDouble()).toFloat() *
                            cos(latitude).toFloat() * radius

                    val y =
                        cy + sin(latitude).toFloat() * radius

                    if (step == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                canvas.drawPath(
                    path,
                    paint
                )
            }

            // Latitude lines

            for (line in -4..4) {

                val latitude =
                    line * 0.30

                val y =
                    cy + sin(latitude).toFloat() * radius

                val halfWidth =
                    cos(latitude).toFloat() * radius

                paint.color = blue
                paint.alpha = 175
                paint.strokeWidth = 1f

                canvas.drawOval(
                    cx - halfWidth,
                    y - halfWidth * 0.10f,
                    cx + halfWidth,
                    y + halfWidth * 0.10f,
                    paint
                )
            }

            canvas.restoreToCount(save)
        }

        // -------------------------------------------
        // SURFACE LIGHTS
        // -------------------------------------------

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

                val wave =
                    (sin(
                        Math.toRadians(
                            rotation.toDouble() * 2.0
                        ) + x * 8.0
                    ) + 1.0).toFloat() / 2f

                paint.style = Paint.Style.FILL
                paint.color = brightBlue
                paint.alpha =
                    (100 + wave * 155).toInt()

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

        // -------------------------------------------
        // CENTRAL ENERGY CORE
        // -------------------------------------------

        private fun drawCore(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL
            paint.color = blue
            paint.alpha =
                (35 + pulse * 55).toInt()

            canvas.drawCircle(
                cx,
                cy,
                radius * (0.25f + pulse * 0.04f),
                paint
            )

            paint.alpha = 220
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

        // -------------------------------------------
        // ORBIT PARTICLES
        // -------------------------------------------

        private fun drawOrbitParticles(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            for (i in 0 until 4) {

                val angle =
                    Math.toRadians(
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
                paint.alpha = 230

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
