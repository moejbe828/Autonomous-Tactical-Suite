
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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.RadialGradient
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
import androidx.core.app.NotificationCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TacticalOrbService : Service() {

    companion object {
        const val ACTION_STOP =
            "com.ats.sentinel.action.STOP_ORB"

        private const val CHANNEL_ID = "ats_tactical_orb"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager

    private var orbContainer: View? = null
    private var radarView: RadarView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isOverlayAdded = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Start foreground before creating the overlay.
        startForeground(
            NOTIFICATION_ID,
            buildNotification()
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

    // --------------------------------------------------
    // NOTIFICATION
    // --------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "ATS Tactical Sentinel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Status notification for ATS Tactical Orb"
                setShowBadge(false)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        val openIntent = Intent(
            this,
            MainActivity::class.java
        )

        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent = Intent(
            this,
            TacticalOrbService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                11,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setContentTitle("ATS Tactical Sentinel")
            .setContentText("Tactical Radar is active")
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP",
                stopPendingIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --------------------------------------------------
    // OVERLAY WINDOW
    // --------------------------------------------------

    private fun showOrb() {

        val container = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            setPadding(
                dp(11),
                dp(10),
                dp(11),
                dp(10)
            )

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(2, 9, 5),
                    Color.rgb(8, 25, 14),
                    Color.rgb(1, 7, 4)
                )
            ).apply {
                cornerRadius = dp(23).toFloat()

                setStroke(
                    dp(1),
                    Color.rgb(39, 220, 105)
                )
            }
        }

        val header = TextView(this).apply {

            text = "ATS  //  TACTICAL SENTINEL"

            setTextColor(
                Color.rgb(100, 255, 150)
            )

            textSize = 12f
            gravity = Gravity.CENTER
            letterSpacing = 0.10f
            typeface = Typeface.DEFAULT_BOLD
        }

        val subHeader = TextView(this).apply {

            text = "AUTONOMOUS RADAR SYSTEM"

            setTextColor(
                Color.rgb(65, 165, 95)
            )

            textSize = 8f
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }

        val radar = RadarView(this)
        radarView = radar

        val status = TextView(this).apply {

            text = "● RADAR ONLINE  |  VISUAL MODE"

            setTextColor(
                Color.rgb(75, 235, 125)
            )

            textSize = 9f
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }

        val stopButton = TextView(this).apply {

            text = "■  STOP ORB"

            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD

            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 40, 22))
                cornerRadius = dp(10).toFloat()
                setStroke(
                    dp(1),
                    Color.rgb(45, 220, 105)
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
            subHeader,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(18)
            )
        )

        container.addView(
            radar,
            LinearLayout.LayoutParams(
                dp(205),
                dp(205)
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
                dp(34)
            )
        )

        val params = WindowManager.LayoutParams(
            dp(235),
            dp(330),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.TOP or Gravity.START

            x = dp(18)
            y = dp(130)
        }

        // Drag from header and radar only.
        // The STOP button remains independently clickable.

        val dragListener =
            View.OnTouchListener { view, event ->

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {

                        initialX = params.x
                        initialY = params.y

                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        val deltaX =
                            (event.rawX - initialTouchX).toInt()

                        val deltaY =
                            (event.rawY - initialTouchY).toInt()

                        params.x = initialX + deltaX
                        params.y = initialY + deltaY

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
        radar.setOnTouchListener(dragListener)

        try {

            windowManager.addView(
                container,
                params
            )

            orbContainer = container
            isOverlayAdded = true

        } catch (_: SecurityException) {

            stopSelf()

        } catch (_: Exception) {

            stopSelf()
        }
    }

    // --------------------------------------------------
    // SERVICE CLEANUP
    // --------------------------------------------------

    override fun onDestroy() {

        if (isOverlayAdded) {

            try {
                orbContainer?.let {
                    windowManager.removeView(it)
                }
            } catch (_: Exception) {
            }
        }

        isOverlayAdded = false

        radarView = null
        orbContainer = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --------------------------------------------------
    // TACTICAL RADAR VIEW
    // --------------------------------------------------

    private class RadarView(
        context: Context
    ) : View(context) {

        private val neonGreen =
            Color.rgb(40, 255, 115)

        private val softGreen =
            Color.rgb(20, 145, 65)

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val radarRect =
            RectF()

        private var rotation = 0f
        private var pulse = 0f

        private val blips = listOf(
            Blip(-0.48f, -0.32f, 0.5f),
            Blip(0.55f, -0.12f, 1.3f),
            Blip(0.30f, 0.55f, 2.0f),
            Blip(-0.42f, 0.36f, 2.7f),
            Blip(0.08f, -0.68f, 3.4f)
        )

        private val animator =
            ValueAnimator.ofFloat(0f, 360f).apply {

                duration = 4200L

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
                                rotation * 2.0
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

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f

            val radius =
                min(width, height) * 0.375f

            radarRect.set(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius
            )

            drawBackground(
                canvas,
                cx,
                cy,
                radius
            )

            drawRadarGrid(
                canvas,
                cx,
                cy,
                radius
            )

            drawSweep(
                canvas,
                cx,
                cy,
                radius
            )

            drawTargets(
                canvas,
                cx,
                cy,
                radius
            )

            drawCenter(
                canvas,
                cx,
                cy,
                radius
            )

            drawOuterScale(
                canvas,
                cx,
                cy,
                radius
            )

            drawDirectionLabels(
                canvas,
                cx,
                cy,
                radius
            )
        }

        // ----------------------------------------------
        // BACKGROUND
        // ----------------------------------------------

        private fun drawBackground(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL

            paint.shader = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                    Color.rgb(9, 55, 25),
                    Color.rgb(3, 23, 11),
                    Color.BLACK
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

            paint.color = neonGreen
            paint.alpha = 35
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f

            canvas.drawCircle(
                cx,
                cy,
                radius,
                paint
            )
        }

        // ----------------------------------------------
        // RADAR GRID
        // ----------------------------------------------

        private fun drawRadarGrid(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = neonGreen
            paint.alpha = 90

            for (i in 1..4) {

                canvas.drawCircle(
                    cx,
                    cy,
                    radius * i / 4f,
                    paint
                )
            }

            // Horizontal and vertical crosshair

            paint.alpha = 100

            canvas.drawLine(
                cx - radius,
                cy,
                cx + radius,
                cy,
                paint
            )

            canvas.drawLine(
                cx,
                cy - radius,
                cx,
                cy + radius,
                paint
            )

            // Radial direction lines

            for (angle in 0 until 360 step 30) {

                val radians =
                    Math.toRadians(angle.toDouble())

                val x =
                    cx + cos(radians).toFloat() * radius

                val y =
                    cy + sin(radians).toFloat() * radius

                paint.alpha = 55

                canvas.drawLine(
                    cx,
                    cy,
                    x,
                    y,
                    paint
                )
            }

            // Inner rotating reference circle

            paint.alpha = 100
            paint.strokeWidth = 1.3f

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.50f,
                paint
            )
        }

        // ----------------------------------------------
        // RADAR SWEEP
        // ----------------------------------------------

        private fun drawSweep(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            canvas.save()

            canvas.rotate(
                rotation,
                cx,
                cy
            )

            val sweepPath = Path().apply {

                moveTo(cx, cy)

                arcTo(
                    radarRect,
                    -58f,
                    58f
                )

                close()
            }

            paint.style = Paint.Style.FILL
            paint.color = neonGreen
            paint.alpha = 55

            canvas.drawPath(
                sweepPath,
                paint
            )

            // Bright leading edge

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = neonGreen
            paint.alpha = 240

            canvas.drawLine(
                cx,
                cy,
                cx + radius,
                cy,
                paint
            )

            // Sweep glow

            paint.strokeWidth = 8f
            paint.alpha = 35

            canvas.drawLine(
                cx,
                cy,
                cx + radius,
                cy,
                paint
            )

            canvas.restore()
        }

        // ----------------------------------------------
        // RADAR TARGETS
        // ----------------------------------------------

        private fun drawTargets(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            for (blip in blips) {

                val x =
                    cx + blip.x * radius

                val y =
                    cy + blip.y * radius

                val wave = (
                    sin(
                        Math.toRadians(
                            rotation * 2.0
                        ) + blip.phase
                    ) + 1.0
                ).toFloat() / 2f

                val alpha =
                    (100 + wave * 155).toInt()

                paint.style = Paint.Style.FILL
                paint.color = neonGreen
                paint.alpha = alpha

                canvas.drawCircle(
                    x,
                    y,
                    3.5f + wave * 2f,
                    paint
                )

                paint.alpha = 45

                canvas.drawCircle(
                    x,
                    y,
                    8f + wave * 5f,
                    paint
                )

                // Small target crosshair

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.alpha = 130

                canvas.drawLine(
                    x - 8f,
                    y,
                    x + 8f,
                    y,
                    paint
                )

                canvas.drawLine(
                    x,
                    y - 8f,
                    x,
                    y + 8f,
                    paint
                )
            }
        }

        // ----------------------------------------------
        // CENTRAL CORE
        // ----------------------------------------------

        private fun drawCenter(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL
            paint.color = neonGreen
            paint.alpha =
                (45 + pulse * 65).toInt()

            canvas.drawCircle(
                cx,
                cy,
                radius * (0.22f + pulse * 0.035f),
                paint
            )

            paint.alpha = 230

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.105f,
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

            // Core outline

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = neonGreen
            paint.alpha = 220

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.26f,
                paint
            )
        }

        // ----------------------------------------------
        // OUTER SCALE
        // ----------------------------------------------

        private fun drawOuterScale(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.STROKE
            paint.color = neonGreen
            paint.alpha = 210

            for (angle in 0 until 360 step 5) {

                val radians =
                    Math.toRadians(angle.toDouble())

                val inner =
                    radius * 1.05f

                val outer =
                    if (angle % 30 == 0) {
                        radius * 1.16f
                    } else {
                        radius * 1.09f
                    }

                paint.strokeWidth =
                    if (angle % 30 == 0) 2f else 1f

                canvas.drawLine(
                    cx + cos(radians).toFloat() * inner,
                    cy + sin(radians).toFloat() * inner,
                    cx + cos(radians).toFloat() * outer,
                    cy + sin(radians).toFloat() * outer,
                    paint
                )
            }
        }

        // ----------------------------------------------
        // COMPASS LABELS
        // ----------------------------------------------

        private fun drawDirectionLabels(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float
        ) {

            paint.style = Paint.Style.FILL
            paint.color = neonGreen
            paint.alpha = 230
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER

            canvas.drawText(
                "N",
                cx,
                cy - radius * 1.23f,
                paint
            )

            canvas.drawText(
                "S",
                cx,
                cy + radius * 1.29f,
                paint
            )

            canvas.drawText(
                "W",
                cx - radius * 1.23f,
                cy + 3f,
                paint
            )

            canvas.drawText(
                "E",
                cx + radius * 1.23f,
                cy + 3f,
                paint
            )
        }

        private data class Blip(
            val x: Float,
            val y: Float,
            val phase: Float
        )
    }
}
