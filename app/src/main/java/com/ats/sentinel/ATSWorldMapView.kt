
package com.ats.sentinel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ATS TACTICAL GEOINT MAP
 *
 * Canvas-based military-style global map.
 * Country boundaries: Natural Earth GeoJSON.
 * Satellite-derived imagery: NASA GIBS WMS.
 *
 * This view displays geographic and satellite-derived data.
 * It does NOT locate a caller or connect directly to a satellite.
 */
class ATSWorldMapView(context: Context) : View(context) {

    private data class CountryShape(
        val isoCode: String,
        val path: Path
    )

    private val density = resources.displayMetrics.density
    private val countries = mutableListOf<CountryShape>()

    private var satelliteBitmap: Bitmap? = null
    private var highlightedCountry = ""
    private var loadingCountries = true
    private var loadingSatellite = true
    private var countryLoadFailed = false
    private var satelliteLoadFailed = false
    private var satelliteLayerEnabled = true

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private var radarAngle = 0f
    private var animationEnabled = false

    private val mapRect = RectF()
    private val screenPath = Path()
    private val mapMatrix = Matrix()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(2, 8, 20)
        style = Paint.Style.FILL
    }

    private val satellitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 175
        filterBitmap = true
    }

    private val satelliteTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 0, 20, 48)
        style = Paint.Style.FILL
    }

    private val countryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(8, 31, 55)
        style = Paint.Style.FILL
    }

    private val countryBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 112, 170)
        style = Paint.Style.STROKE
        strokeWidth = 0.65f * density
    }

    private val selectedCountryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 0, 135, 220)
        style = Paint.Style.FILL
    }

    private val selectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 0, 190, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
    }

    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 225, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.35f * density
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 0, 175, 225)
        style = Paint.Style.STROKE
        strokeWidth = 0.55f * density
    }

    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(125, 0, 190, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val radarSweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 0, 210, 255)
        style = Paint.Style.FILL
    }

    private val hudLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 170, 220)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val hudPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 3, 17, 35)
        style = Paint.Style.FILL
    }

    private val hudAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 190, 245)
        style = Paint.Style.FILL
    }

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 225, 255)
        textSize = 10f * density
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.BOLD
        )
    }

    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 160, 190)
        textSize = 8f * density
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.NORMAL
        )
    }

    private val warningTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 75)
        textSize = 8f * density
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.BOLD
        )
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
                constrainPan()
                invalidate()
                return true
            }
        }
    )

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(Color.TRANSPARENT)

        loadCountryData()
        loadSatelliteImagery()
    }

    // ---------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------

    fun setHighlightedCountry(isoCode: String?) {
        highlightedCountry = isoCode
            ?.trim()
            ?.uppercase()
            .orEmpty()

        invalidate()
    }

    fun setSatelliteLayerEnabled(enabled: Boolean) {
        satelliteLayerEnabled = enabled
        invalidate()
    }

    fun resetMapView() {
        zoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    // ---------------------------------------------------------
    // DATA SOURCES
    // ---------------------------------------------------------

    private fun loadCountryData() {
        Thread {
            try {
                val cacheFile = File(
                    context.filesDir,
                    "ats_world_countries.geojson"
                )

                val jsonText = if (
                    cacheFile.exists() &&
                    cacheFile.length() > 10_000L
                ) {
                    cacheFile.readText()
                } else {
                    val downloaded = downloadText(COUNTRY_GEOJSON_URL)
                    cacheFile.writeText(downloaded)
                    downloaded
                }

                parseGeoJson(jsonText)

                post {
                    loadingCountries = false
                    countryLoadFailed = countries.isEmpty()
                    invalidate()
                }
            } catch (_: Exception) {
                post {
                    loadingCountries = false
                    countryLoadFailed = countries.isEmpty()
                    invalidate()
                }
            }
        }.start()
    }

    private fun loadSatelliteImagery() {
        Thread {
            try {
                val cacheFile = File(
                    context.filesDir,
                    "ats_nasa_global_satellite.jpg"
                )

                val bitmap = if (
                    cacheFile.exists() &&
                    cacheFile.length() > 20_000L
                ) {
                    BitmapFactory.decodeFile(cacheFile.absolutePath)
                        ?: downloadSatelliteImage(cacheFile)
                } else {
                    downloadSatelliteImage(cacheFile)
                }

                post {
                    satelliteBitmap = bitmap
                    loadingSatellite = false
                    satelliteLoadFailed = bitmap == null
                    invalidate()
                }
            } catch (_: Exception) {
                post {
                    loadingSatellite = false
                    satelliteLoadFailed = true
                    invalidate()
                }
            }
        }.start()
    }

    private fun downloadSatelliteImage(cacheFile: File): Bitmap? {
        val connection = URL(NASA_GIBS_WMS_URL)
            .openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "ATS-Tactical-Sentinel"
            )

            connection.connect()

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "NASA GIBS HTTP ${connection.responseCode}"
                )
            }

            val bitmap = connection.inputStream.use {
                BitmapFactory.decodeStream(it)
            } ?: return null

            cacheFile.outputStream().use { output ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    88,
                    output
                )
            }

            return bitmap
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadText(address: String): String {
        val connection = URL(address)
            .openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "ATS-Tactical-Sentinel"
            )

            connection.connect()

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "Map data HTTP ${connection.responseCode}"
                )
            }

            return connection.inputStream.bufferedReader().use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    // ---------------------------------------------------------
    // GEOJSON PARSER
    // ---------------------------------------------------------

    private fun parseGeoJson(source: String) {
        val parsed = mutableListOf<CountryShape>()

        val root = JSONObject(source)
        val features = root.optJSONArray("features") ?: return

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties")
                ?: continue
            val geometry = feature.optJSONObject("geometry")
                ?: continue

            val iso = firstIsoCode(properties)
            if (iso.isBlank()) continue

            val type = geometry.optString("type")
            val coordinates = geometry.optJSONArray("coordinates")
                ?: continue

            val combinedPath = Path().apply {
                fillType = Path.FillType.EVEN_ODD
            }

            when (type) {
                "Polygon" -> appendPolygon(
                    combinedPath,
                    coordinates
                )

                "MultiPolygon" -> {
                    for (i in 0 until coordinates.length()) {
                        val polygon = coordinates.optJSONArray(i)
                            ?: continue

                        appendPolygon(combinedPath, polygon)
                    }
                }
            }

            if (!combinedPath.isEmpty) {
                parsed.add(
                    CountryShape(
                        isoCode = iso,
                        path = combinedPath
                    )
                )
            }
        }

        synchronized(countries) {
            countries.clear()
            countries.addAll(parsed)
        }
    }

    private fun firstIsoCode(properties: JSONObject): String {
        val keys = listOf(
            "ISO_A2",
            "ISO_A2_EH",
            "WB_A2"
        )

        for (key in keys) {
            val value = properties.optString(key, "")
                .trim()
                .uppercase()

            if (value.length == 2 && value != "-99") {
                return value
            }
        }

        return ""
    }

    private fun appendPolygon(
        target: Path,
        polygon: JSONArray
    ) {
        // Include outer ring and inner rings.
        // EVEN_ODD fill type preserves holes in country geometry.
        for (ringIndex in 0 until polygon.length()) {
            val ring = polygon.optJSONArray(ringIndex) ?: continue
            if (ring.length() < 3) continue

            var started = false
            val path = Path()

            for (index in 0 until ring.length()) {
                val coordinate = ring.optJSONArray(index)
                    ?: continue

                if (coordinate.length() < 2) continue

                val longitude = coordinate.optDouble(0)
                val latitude = coordinate.optDouble(1)

                if (
                    longitude.isNaN() ||
                    latitude.isNaN() ||
                    longitude.isInfinite() ||
                    latitude.isInfinite()
                ) continue

                val x = ((longitude + 180.0) / 360.0).toFloat()
                val y = ((90.0 - latitude) / 180.0).toFloat()

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }

            if (started) {
                path.close()
                target.addPath(path)
            }
        }
    }

    // ---------------------------------------------------------
    // MAP GEOMETRY
    // ---------------------------------------------------------

    private fun calculateMapRect() {
        val availableWidth = width.toFloat()
        val availableHeight = height.toFloat()

        if (availableWidth <= 0f || availableHeight <= 0f) return

        // Equirectangular global map has a 2:1 aspect ratio.
        val mapWidth = min(
            availableWidth,
            availableHeight * 2f
        )

        val mapHeight = mapWidth / 2f

        val left = (availableWidth - mapWidth) / 2f
        val top = (availableHeight - mapHeight) / 2f

        mapRect.set(
            left,
            top,
            left + mapWidth,
            top + mapHeight
        )

        mapMatrix.setValues(
            floatArrayOf(
                mapWidth, 0f, mapRect.left,
                0f, mapHeight, mapRect.top,
                0f, 0f, 1f
            )
        )
    }

    private fun constrainPan() {
        val limitX = mapRect.width() * (zoom - 1f) / 2f
        val limitY = mapRect.height() * (zoom - 1f) / 2f

        panX = panX.coerceIn(-limitX, limitX)
        panY = panY.coerceIn(-limitY, limitY)
    }

    // ---------------------------------------------------------
    // DRAWING
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )

        drawBackgroundGrid(canvas)
        calculateMapRect()

        if (mapRect.isEmpty) return

        val save = canvas.save()

        canvas.translate(panX, panY)
        canvas.scale(
            zoom,
            zoom,
            mapRect.centerX(),
            mapRect.centerY()
        )

        drawSatelliteLayer(canvas)
        drawGeographicGrid(canvas)
        drawCountries(canvas)
        drawRadarSweep(canvas)

        canvas.restoreToCount(save)

        drawHud(canvas)
        drawCornerBrackets(canvas)
        drawFooter(canvas)

        if (animationEnabled) {
            radarAngle = (radarAngle + 2.2f) % 360f
            postInvalidateDelayed(45L)
        }
    }

    private fun drawBackgroundGrid(canvas: Canvas) {
        val step = 28f * density

        var x = 0f
        while (x < width) {
            canvas.drawLine(
                x,
                0f,
                x,
                height.toFloat(),
                gridPaint
            )
            x += step
        }

        var y = 0f
        while (y < height) {
            canvas.drawLine(
                0f,
                y,
                width.toFloat(),
                y,
                gridPaint
            )
            y += step
        }
    }

    private fun drawSatelliteLayer(canvas: Canvas) {
        val bitmap = satelliteBitmap

        if (satelliteLayerEnabled && bitmap != null && !bitmap.isRecycled) {
            canvas.drawBitmap(
                bitmap,
                null,
                mapRect,
                satellitePaint
            )

            canvas.drawRect(
                mapRect,
                satelliteTintPaint
            )
        } else {
            canvas.drawRect(
                mapRect,
                countryPaint
            )
        }
    }

    private fun drawGeographicGrid(canvas: Canvas) {
        val longitudeSteps = 12
        val latitudeSteps = 6

        for (i in 1 until longitudeSteps) {
            val x = mapRect.left +
                mapRect.width() * i / longitudeSteps

            canvas.drawLine(
                x,
                mapRect.top,
                x,
                mapRect.bottom,
                gridPaint
            )
        }

        for (i in 1 until latitudeSteps) {
            val y = mapRect.top +
                mapRect.height() * i / latitudeSteps

            canvas.drawLine(
                mapRect.left,
                y,
                mapRect.right,
                y,
                gridPaint
            )
        }
    }

    private fun drawCountries(canvas: Canvas) {
        val snapshot = synchronized(countries) {
            countries.toList()
        }

        for (country in snapshot) {
            country.path.transform(
                mapMatrix,
                screenPath
            )

            if (country.isoCode == highlightedCountry) {
                canvas.drawPath(
                    screenPath,
                    selectedGlowPaint
                )

                canvas.drawPath(
                    screenPath,
                    selectedCountryPaint
                )

                canvas.drawPath(
                    screenPath,
                    selectedBorderPaint
                )
            } else {
                canvas.drawPath(
                    screenPath,
                    countryPaint
                )

                canvas.drawPath(
                    screenPath,
                    countryBorderPaint
                )
            }
        }
    }

    private fun drawRadarSweep(canvas: Canvas) {
        val centerX = mapRect.centerX()
        val centerY = mapRect.centerY()
        val radius = min(
            mapRect.width(),
            mapRect.height()
        ) * 0.43f

        val radarRect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            radarPaint
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.66f,
            radarPaint
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.33f,
            radarPaint
        )

        canvas.drawLine(
            centerX - radius,
            centerY,
            centerX + radius,
            centerY,
            radarPaint
        )

        canvas.drawLine(
            centerX,
            centerY - radius,
            centerX,
            centerY + radius,
            radarPaint
        )

        val sweepPath = Path()
        sweepPath.moveTo(centerX, centerY)
        sweepPath.arcTo(
            radarRect,
            radarAngle,
            34f,
            false
        )
        sweepPath.close()

        canvas.drawPath(
            sweepPath,
            radarSweepPaint
        )

        val angle = Math.toRadians(radarAngle.toDouble())
        val endX = centerX + cos(angle).toFloat() * radius
        val endY = centerY + sin(angle).toFloat() * radius

        canvas.drawLine(
            centerX,
            centerY,
            endX,
            endY,
            hudLinePaint
        )
    }

    // ---------------------------------------------------------
    // TACTICAL HUD
    // ---------------------------------------------------------

    private fun drawHud(canvas: Canvas) {
        val margin = 10f * density
        val panelWidth = min(
            width * 0.58f,
            210f * density
        )

        val panelHeight = 66f * density

        val panel = RectF(
            margin,
            margin,
            margin + panelWidth,
            margin + panelHeight
        )

        canvas.drawRoundRect(
            panel,
            5f * density,
            5f * density,
            hudPanelPaint
        )

        canvas.drawRoundRect(
            panel,
            5f * density,
            5f * density,
            hudLinePaint
        )

        canvas.drawRect(
            panel.left,
            panel.top,
            panel.left + 3f * density,
            panel.bottom,
            hudAccentPaint
        )

        val x = panel.left + 11f * density
        var y = panel.top + 15f * density

        canvas.drawText(
            "ATS // TACTICAL GEOINT",
            x,
            y,
            primaryTextPaint
        )

        y += 14f * density

        val countryStatus = when {
            loadingCountries -> "COUNTRY DATA: LOADING"
            countryLoadFailed -> "COUNTRY DATA: OFFLINE"
            else -> "COUNTRY DATABASE: READY"
        }

        canvas.drawText(
            countryStatus,
            x,
            y,
            secondaryTextPaint
        )

        y += 12f * density

        val satelliteStatus = when {
            loadingSatellite -> "NASA IMAGERY: CONNECTING"
            satelliteBitmap != null -> "NASA IMAGERY: AVAILABLE"
            satelliteLoadFailed -> "NASA IMAGERY: OFFLINE"
            else -> "NASA IMAGERY: UNAVAILABLE"
        }

        canvas.drawText(
            satelliteStatus,
            x,
            y,
            if (satelliteLoadFailed) {
                warningTextPaint
            } else {
                secondaryTextPaint
            }
        )

        val right = RectF(
            width - 93f * density,
            margin,
            width - margin,
            margin + 40f * density
        )

        canvas.drawRoundRect(
            right,
            4f * density,
            4f * density,
            hudPanelPaint
        )

        canvas.drawRoundRect(
            right,
            4f * density,
            4f * density,
            hudLinePaint
        )

        canvas.drawText(
            "SCAN ACTIVE",
            right.left + 7f * density,
            right.top + 15f * density,
            primaryTextPaint
        )

        canvas.drawText(
            "ZOOM ${String.format("%.1f", zoom)}X",
            right.left + 7f * density,
            right.top + 29f * density,
            secondaryTextPaint
        )

        if (highlightedCountry.isNotBlank()) {
            drawSelectedCountryPanel(canvas)
        }
    }

    private fun drawSelectedCountryPanel(canvas: Canvas) {
        val margin = 10f * density
        val panelWidth = 150f * density
        val panelHeight = 37f * density

        val panel = RectF(
            margin,
            height - panelHeight - 27f * density,
            margin + panelWidth,
            height - 27f * density
        )

        canvas.drawRoundRect(
            panel,
            4f * density,
            4f * density,
            hudPanelPaint
        )

        canvas.drawRoundRect(
            panel,
            4f * density,
            4f * density,
            hudLinePaint
        )

        canvas.drawText(
            "NUMBERING REGION",
            panel.left + 8f * density,
            panel.top + 13f * density,
            secondaryTextPaint
        )

        canvas.drawText(
            highlightedCountry,
            panel.left + 8f * density,
            panel.top + 28f * density,
            primaryTextPaint
        )
    }

    private fun drawCornerBrackets(canvas: Canvas) {
        val length = 12f * density
        val offset = 3f * density

        val corners = listOf(
            Pair(mapRect.left, mapRect.top),
            Pair(mapRect.right, mapRect.top),
            Pair(mapRect.left, mapRect.bottom),
            Pair(mapRect.right, mapRect.bottom)
        )

        for ((x, y) in corners) {
            val directionX = if (x == mapRect.left) 1f else -1f
            val directionY = if (y == mapRect.top) 1f else -1f

            canvas.drawLine(
                x + directionX * offset,
                y,
                x + directionX * (length + offset),
                y,
                hudLinePaint
            )

            canvas.drawLine(
                x,
                y + directionY * offset,
                x,
                y + directionY * (length + offset),
                hudLinePaint
            )
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val y = height - 8f * density

        val sourceText = when {
            satelliteBitmap != null && satelliteLayerEnabled ->
                "IMAGERY: NASA GIBS  |  BOUNDARIES: NATURAL EARTH"

            else ->
                "VECTOR MAP: NATURAL EARTH  |  SATELLITE LAYER OFFLINE"
        }

        canvas.drawText(
            sourceText,
            10f * density,
            y,
            secondaryTextPaint
        )

        canvas.drawText(
            "NOT LIVE CALLER LOCATION",
            width - 10f * density,
            y,
            warningTextPaint
        ).also {
            // Right-align the warning label.
        }
    }

    // ---------------------------------------------------------
    // TOUCH / ZOOM / PAN
    // ---------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && zoom > 1f) {
                    panX += event.x - lastX
                    panY += event.y - lastY

                    constrainPan()
                    invalidate()
                }

                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                return true
            }
        }

        return true
    }

    // ---------------------------------------------------------
    // VIEW LIFECYCLE
    // ---------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animationEnabled = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animationEnabled = false
        super.onDetachedFromWindow()
    }

    companion object {
        private const val COUNTRY_GEOJSON_URL =
            "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"

        /**
         * NASA GIBS WMS global true-colour visualization.
         * The service provides satellite-derived imagery products.
         * It is not a direct satellite radio connection.
         */
        private const val NASA_GIBS_WMS_URL =
            "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi" +
                "?SERVICE=WMS" +
                "&REQUEST=GetMap" +
                "&VERSION=1.1.1" +
                "&LAYERS=MODIS_Terra_CorrectedReflectance_TrueColor" +
                "&STYLES=" +
                "&SRS=EPSG:4326" +
                "&BBOX=-180,-90,180,90" +
                "&WIDTH=1200" +
                "&HEIGHT=600" +
                "&FORMAT=image/jpeg"
    }
}
