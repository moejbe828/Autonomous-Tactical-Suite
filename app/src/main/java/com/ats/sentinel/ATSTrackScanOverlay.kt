
package com.ats.sentinel

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class ATSTrackScanOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {

    // ---------------------------------------------------------
    // EXECUTION
    // ---------------------------------------------------------

    private val worker = Executors.newSingleThreadExecutor()

    private val mainHandler = android.os.Handler(
        android.os.Looper.getMainLooper()
    )

    @Volatile
    private var destroyed = false

    private var scanGeneration = 0L

    // ---------------------------------------------------------
    // WINDOW
    // ---------------------------------------------------------

    private var root: LinearLayout? = null
    private var mapView: ATSWorldMapView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var added = false

    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    // ---------------------------------------------------------
    // UI COMPONENTS
    // ---------------------------------------------------------

    private var callerNameView: TextView? = null
    private var numberView: TextView? = null
    private var statusView: TextView? = null

    private var countryView: TextView? = null
    private var codeView: TextView? = null
    private var typeView: TextView? = null
    private var carrierView: TextView? = null
    private var riskView: TextView? = null
    private var confidenceView: TextView? = null
    private var sourceView: TextView? = null

    // ---------------------------------------------------------
    // ANALYSIS STATE
    // ---------------------------------------------------------

    private var lastNumber = ""
    private var currentReport: ATSTrackScanReport? = null

    private val density =
        context.resources.displayMetrics.density

    private fun dp(value: Int): Int {
        return (value * density).toInt()
    }

    // ---------------------------------------------------------
    // PUBLIC SYNCHRONIZATION
    // ---------------------------------------------------------

    fun sync(
        callActive: Boolean,
        visible: Boolean,
        callerName: String,
        callerNumber: String,
        callState: Int,
        orbX: Int,
        orbY: Int
    ) {
        if (destroyed) return

        if (!callActive || !visible) {
            hide()
            return
        }

        if (!added) {
            createWindow()
        }

        if (!added) return

        updatePosition(orbX, orbY)

        updateCallDetails(
            callerName,
            callerNumber,
            callState
        )

        analyzeIfNeeded(callerNumber)
    }

    // ---------------------------------------------------------
    // WINDOW CREATION
    // ---------------------------------------------------------

    private fun createWindow() {
        if (added || destroyed) return

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )

            background = roundedBackground()

            clipToPadding = true
            clipChildren = true
        }

        // HEADER

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "ATS TRACK SCAN"
            textSize = 14f

            setTextColor(
                Color.rgb(105, 225, 255)
            )

            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }

        val close = TextView(context).apply {
            text = "×"
            textSize = 26f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER

            setPadding(
                dp(12),
                0,
                dp(4),
                0
            )

            isClickable = true

            setOnClickListener {
                ATSCallSession.isTrackScanVisible = false
                hide()
            }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                dp(34),
                1f
            )
        )

        header.addView(
            close,
            LinearLayout.LayoutParams(
                dp(36),
                dp(34)
            )
        )

        panel.addView(header)

        // CALLER INFORMATION

        callerNameView = makeText(
            "UNKNOWN CALLER",
            12f,
            Color.WHITE,
            true
        )

        numberView = makeText(
            "NUMBER PENDING",
            11f,
            Color.rgb(130, 190, 220)
        )

        statusView = makeText(
            "SCANNING CALL",
            10f,
            Color.rgb(0, 205, 255),
            true
        )

        panel.addView(callerNameView)
        panel.addView(numberView)
        panel.addView(statusView)

        // DIVIDER

        val divider = View(context).apply {
            setBackgroundColor(
                Color.rgb(0, 95, 145)
            )
        }

        panel.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(7)
                bottomMargin = dp(6)
            }
        )

        // SCROLLABLE CONTENT

        val scroll = ScrollView(context).apply {
            isFillViewport = false

            overScrollMode =
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        countryView = addInfoRow(
            content,
            "COUNTRY",
            "ANALYZING"
        )

        codeView = addInfoRow(
            content,
            "CALLING CODE",
            "—"
        )

        typeView = addInfoRow(
            content,
            "NUMBER TYPE",
            "—"
        )

        carrierView = addInfoRow(
            content,
            "ORIGINAL CARRIER",
            "—"
        )

        riskView = addInfoRow(
            content,
            "RISK ASSESSMENT",
            "—"
        )

        confidenceView = addInfoRow(
            content,
            "CONFIDENCE",
            "—"
        )

        sourceView = addInfoRow(
            content,
            "DATA SOURCE",
            "—"
        )

        // MAP HEADER

        content.addView(
            makeText(
                "GLOBAL COUNTRY INTELLIGENCE",
                10f,
                Color.rgb(100, 220, 255),
                true
            )
        )

        // ATS WORLD MAP

        mapView = ATSWorldMapView(context)

        content.addView(
            mapView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(138)
            ).apply {
                topMargin = dp(5)
            }
        )

        // LOCATION DISCLAIMER

        content.addView(
            makeText(
                "Highlighted country represents the numbering region, not the caller's live location.",
                9f,
                Color.rgb(145, 165, 185)
            )
        )

        scroll.addView(content)

        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // WINDOW PARAMETERS

        val params = WindowManager.LayoutParams(
            dp(270),
            dp(390),

            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            x = dp(95)
            y = dp(30)
        }

        try {
            windowManager.addView(
                panel,
                params
            )

            root = panel
            layoutParams = params

            added = true

            lastX = Int.MIN_VALUE
            lastY = Int.MIN_VALUE

        } catch (_: Exception) {
            root = null
            layoutParams = null
            mapView = null

            added = false
        }
    }

    // ---------------------------------------------------------
    // INFORMATION ROWS
    // ---------------------------------------------------------

    private fun addInfoRow(
        parent: LinearLayout,
        label: String,
        initialValue: String
    ): TextView {

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                0,
                dp(3),
                0,
                dp(3)
            )
        }

        val labelView = makeText(
            label,
            8f,
            Color.rgb(90, 160, 195),
            true
        )

        val valueView = makeText(
            initialValue,
            10f,
            Color.WHITE
        )

        row.addView(labelView)
        row.addView(valueView)

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return valueView
    }

    // ---------------------------------------------------------
    // TEXT FACTORY
    // ---------------------------------------------------------

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {

        return TextView(context).apply {
            text = value
            textSize = size

            setTextColor(color)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }

            setPadding(
                0,
                dp(2),
                0,
                dp(2)
            )

            isSingleLine = false
        }
    }

    // ---------------------------------------------------------
    // WINDOW BACKGROUND
    // ---------------------------------------------------------

    private fun roundedBackground(): GradientDrawable {

        return GradientDrawable().apply {
            setColor(
                Color.rgb(5, 17, 34)
            )

            cornerRadius =
                dp(15).toFloat()

            setStroke(
                dp(1),
                Color.rgb(0, 145, 205)
            )
        }
    }

    // ---------------------------------------------------------
    // CALL STATE
    // ---------------------------------------------------------

    private fun updateCallDetails(
        callerName: String,
        callerNumber: String,
        callState: Int
    ) {

        callerNameView?.text =
            callerName
                .takeIf { it.isNotBlank() }
                ?: "UNKNOWN CALLER"

        numberView?.text =
            callerNumber
                .takeIf { it.isNotBlank() }
                ?: "NUMBER UNAVAILABLE"

        statusView?.text = when (callState) {

            Call.STATE_RINGING ->
                "INCOMING CALL • SCANNING"

            Call.STATE_DIALING ->
                "OUTGOING CALL • SCANNING"

            Call.STATE_CONNECTING ->
                "CONNECTING • SCANNING"

            Call.STATE_ACTIVE ->
                "CALL ACTIVE • LIVE ANALYSIS"

            Call.STATE_HOLDING ->
                "CALL ON HOLD"

            Call.STATE_DISCONNECTED ->
                "CALL DISCONNECTED"

            Call.STATE_DISCONNECTING ->
                "CALL DISCONNECTING"

            else ->
                "CALL SESSION • SCANNING"
        }
    }

    // ---------------------------------------------------------
    // RESET ANALYSIS DISPLAY
    // ---------------------------------------------------------

    private fun resetAnalysisDisplay(
        status: String
    ) {

        countryView?.text = status
        codeView?.text = "—"
        typeView?.text = "—"
        carrierView?.text = "—"
        riskView?.text = "—"
        confidenceView?.text = "—"
        sourceView?.text = "—"

        mapView?.setTrackScanReport(null)
    }

    // ---------------------------------------------------------
    // ANALYSIS ENGINE
    // ---------------------------------------------------------

    private fun analyzeIfNeeded(number: String) {

        if (number == lastNumber) return

        lastNumber = number

        scanGeneration++
        val generation = scanGeneration

        currentReport = null

        if (number.isBlank()) {
            resetAnalysisDisplay(
                "NUMBER UNAVAILABLE"
            )

            statusView?.text =
                "WAITING FOR CALL NUMBER"

            return
        }

        resetAnalysisDisplay(
            "ANALYZING..."
        )

        sourceView?.text =
            "LOADING METADATA"

        statusView?.text =
            "ATS ENGINE • ANALYZING"

        try {

            worker.execute {

                val result = try {

                    ATSTrackScanEngine.analyze(
                        context.applicationContext,
                        number
                    )

                } catch (exception: Exception) {

                    null
                }

                mainHandler.post {

                    if (destroyed) return@post

                    if (!added) return@post

                    if (generation != scanGeneration) {
                        return@post
                    }

                    if (number != lastNumber) {
                        return@post
                    }

                    if (result == null) {

                        currentReport = null

                        resetAnalysisDisplay(
                            "ANALYSIS FAILED"
                        )

                        statusView?.text =
                            "ATS ENGINE • ANALYSIS ERROR"

                        return@post
                    }

                    currentReport = result

                    renderReport(result)
                }
            }

        } catch (_: RejectedExecutionException) {

            resetAnalysisDisplay(
                "ENGINE UNAVAILABLE"
            )

            statusView?.text =
                "ATS ENGINE • UNAVAILABLE"
        }
    }

    // ---------------------------------------------------------
    // RENDER ANALYSIS REPORT
    // ---------------------------------------------------------

    private fun renderReport(
        report: ATSTrackScanReport
    ) {

        countryView?.text =
            report.countryName
                .ifBlank { "UNKNOWN" }

        codeView?.text =
            report.callingCode
                .ifBlank { "—" }

        typeView?.text =
            report.numberType
                .ifBlank { "UNKNOWN" }

        carrierView?.text =
            report.originalCarrier
                .ifBlank { "NOT AVAILABLE" } +
                "\n" +
                report.carrierNotice

        riskView?.text =
            report.riskAssessment
                .ifBlank { "NOT ASSESSED" }

        confidenceView?.text =
            "${report.confidence}%"

        sourceView?.text =
            report.metadataSource +
                "\n" +
                report.metadataVersion +
                "\n" +
                "Country reference: " +
                report.countrySource

        statusView?.text =
            "ATS ENGINE • ${report.analysisStatus}"

        // IMPORTANT:
        // Send the complete report to the map.
        // The map highlights the numbering region,
        // not the physical location of the caller.

        mapView?.setTrackScanReport(report)
    }

    // ---------------------------------------------------------
    // DYNAMIC WINDOW POSITION
    // ---------------------------------------------------------

    private fun updatePosition(
        orbX: Int,
        orbY: Int
    ) {

        val params = layoutParams ?: return
        val panel = root ?: return

        val displayMetrics =
            context.resources.displayMetrics

        val screenWidth =
            displayMetrics.widthPixels

        val screenHeight =
            displayMetrics.heightPixels

        val panelWidth = dp(270)
        val panelHeight = dp(390)
        val orbSize = dp(72)

        // Prefer placing the scan panel to the right
        // of the floating tactical orb.

        val rightPosition =
            orbX + orbSize + dp(8)

        val x = if (
            rightPosition + panelWidth <= screenWidth
        ) {

            rightPosition

        } else {

            (orbX - panelWidth - dp(8))
                .coerceIn(
                    0,
                    maxOf(0, screenWidth - panelWidth)
                )
        }

        // Keep the bottom call controls visible.

        val safeBottom = dp(220)

        val maxY = maxOf(
            0,
            screenHeight - panelHeight - safeBottom
        )

        val y = (orbY - dp(55))
            .coerceIn(0, maxY)

        if (
            x == lastX &&
            y == lastY
        ) {
            return
        }

        params.x = x
        params.y = y

        try {

            windowManager.updateViewLayout(
                panel,
                params
            )

            lastX = x
            lastY = y

        } catch (_: Exception) {
            // Window may already have been removed.
        }
    }

    // ---------------------------------------------------------
    // HIDE WINDOW
    // ---------------------------------------------------------

    private fun hide() {

        // Invalidate all pending analysis callbacks.

        scanGeneration++

        lastNumber = ""
        currentReport = null

        if (added) {

            try {

                root?.let {
                    windowManager.removeView(it)
                }

            } catch (_: Exception) {
                // Window already removed.
            }
        }

        root = null
        mapView = null
        layoutParams = null

        added = false

        lastX = Int.MIN_VALUE
        lastY = Int.MIN_VALUE

        callerNameView = null
        numberView = null
        statusView = null

        countryView = null
        codeView = null
        typeView = null
        carrierView = null
        riskView = null
        confidenceView = null
        sourceView = null
    }

    // ---------------------------------------------------------
    // DESTROY
    // ---------------------------------------------------------

    fun destroy() {

        if (destroyed) return

        destroyed = true

        scanGeneration++

        // Remove the window on the main thread.
        if (
            android.os.Looper.myLooper() ==
            android.os.Looper.getMainLooper()
        ) {

            hide()

        } else {

            mainHandler.post {
                hide()
            }
        }

        worker.shutdownNow()

        mainHandler.removeCallbacksAndMessages(null)
    }
}
