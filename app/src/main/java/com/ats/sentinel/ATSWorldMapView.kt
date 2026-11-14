
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ATS TACTICAL GEOINT MAP
 *
 * Offline-first vector map with optional NASA GIBS imagery.
 * Telecom and network layers are not presented as real data
 * until verified data providers are integrated.
 *
 * This view does not locate callers or connect to satellites.
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
    private var trackScanReport: ATSTrackScanReport? = null

    @Volatile
    private var loadingCountries = true

    @Volatile
    private var loadingSatellite = false

    private var countryLoadFailed = false
    private var satelliteLoadFailed = false

    private var satelliteLayerEnabled = false
    private var boundariesLayerEnabled = true
    private var gridLayerEnabled = true
    private var radarLayerEnabled = true
    private var telecomLayerEnabled = false
    private var networkLayerEnabled = false
    private var regionalLayerEnabled = false

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

    private val cacheDirectory = File(context.cacheDir, "ats_geoint")
    private val countryCache = File(context.filesDir, COUNTRY_CACHE_NAME)
    private val satelliteCache = File(cacheDirectory, SATELLITE_CACHE_NAME)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(2, 8, 20)
    }

    private val satellitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 185
        isFilterBitmap = true
    }

    private val satelliteTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 15, 38)
    }

    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(8, 31, 55)
        style = Paint.Style.FILL
    }

    private val countryBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 125, 180)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f * density
    }

    private val selectedCountryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 0, 145, 225)
        style = Paint.Style.FILL
    }

    private val selectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 0, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
    }

    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 230, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 175, 225)
        style = Paint.Style.STROKE
        strokeWidth = 0.55f * density
    }

    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 0, 190, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
    }

    private val radarSweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 0, 210, 255)
        style = Paint.Style.FILL
    }

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(238, 3, 17, 35)
        style = Paint.Style.FILL
    }

    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 145, 205)
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 195, 245)
    }

    private val primaryText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(125, 225, 255)
        textSize = 10f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val secondaryText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(135, 170, 195)
        textSize = 8f * density
    }

    private val warningText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 75)
        textSize = 8f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val activeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 235, 170)
        textSize = 8f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
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
        cacheDirectory.mkdirs()
        loadCountryData()
    }

    // ---------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------

    fun setHighlightedCountry(isoCode: String?) {
        highlightedCountry = isoCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        invalidate()
    }

    /**
     * Supplies the latest ATS TRACK SCAN analysis to the map.
     * The highlighted region is the phone-numbering region,
     * not a live physical location.
     */
    fun setTrackScanReport(report: ATSTrackScanReport?) {
        trackScanReport = report
        highlightedCountry = report?.regionCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        invalidate()
    }

    fun setSatelliteLayerEnabled(enabled: Boolean) {
        satelliteLayerEnabled = enabled

        if (enabled && satelliteBitmap == null && !loadingSatellite) {
            loadSatelliteImagery()
        }

        invalidate()
    }

    fun setBoundariesLayerEnabled(enabled: Boolean) {
        boundariesLayerEnabled = enabled
        invalidate()
    }

    fun setGridLayerEnabled(enabled: Boolean) {
        gridLayerEnabled = enabled
        invalidate()
    }

    fun setRadarLayerEnabled(enabled: Boolean) {
        radarLayerEnabled = enabled
        invalidate()
    }

    fun setTelecomLayerEnabled(enabled: Boolean) {
        telecomLayerEnabled = enabled
        invalidate()
    }

    fun setNetworkLayerEnabled(enabled: Boolean) {
        networkLayerEnabled = enabled
        invalidate()
    }

    fun setRegionalLayerEnabled(enabled: Boolean) {
        regionalLayerEnabled = enabled
        invalidate()
    }

    fun resetMapView() {
        zoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    // ---------------------------------------------------------
    // COUNTRY DATA - LOCAL CACHE FIRST
    // ---------------------------------------------------------

    private fun loadCountryData() {
        Thread({
            try {
                val jsonText = if (
                    countryCache.exists() &&
                    countryCache.length() > MIN_COUNTRY_CACHE_BYTES
                ) {
                    countryCache.readText()
                } else {
                    val downloaded = downloadText(COUNTRY_GEOJSON_URL)

                    require(downloaded.length > MIN_COUNTRY_CACHE_BYTES) {
                        "Country dataset is incomplete"
                    }

                    val temporary = File(
                        context.filesDir,
                        "$COUNTRY_CACHE_NAME.tmp"
                    )

                    temporary.writeText(downloaded)

                    if (!temporary.renameTo(countryCache)) {
                        temporary.copyTo(countryCache, overwrite = true)
                        temporary.delete()
                    }

                    downloaded
                }

                parseGeoJson(jsonText)

                post {
                    loadingCountries = false
                    countryLoadFailed = synchronized(countries) {
                        countries.isEmpty()
                    }
                    invalidate()
                }
            } catch (_: Exception) {
                post {
                    loadingCountries = false
                    countryLoadFailed = synchronized(countries) {
                        countries.isEmpty()
                    }
                    invalidate()
                }
            }
        }, "ATS-Country-Data").start()
    }

    // ---------------------------------------------------------
    // OPTIONAL SATELLITE IMAGERY
    // STRICT 5 MB IMAGE CACHE
    // ---------------------------------------------------------

    private fun loadSatelliteImagery() {
        if (loadingSatellite) return

        loadingSatellite = true
        satelliteLoadFailed = false
        invalidate()

        Thread({
            var resultBitmap: Bitmap? = null

            try {
                if (
                    satelliteCache.exists() &&
                    satelliteCache.length() in 20_000L..MAX_SATELLITE_CACHE_BYTES
                ) {
                    resultBitmap = BitmapFactory.decodeFile(
                        satelliteCache.absolutePath
                    )
                }

                if (resultBitmap == null) {
                    resultBitmap = downloadSatelliteImage()
                }

                val finalBitmap = resultBitmap

                post {
                    val oldBitmap = satelliteBitmap

                    if (oldBitmap !== finalBitmap) {
                        oldBitmap?.takeIf { !it.isRecycled }?.recycle()
                    }

                    satelliteBitmap = finalBitmap
                    loadingSatellite = false
                    satelliteLoadFailed = finalBitmap == null
                    invalidate()
                }
            } catch (_: Exception) {
                resultBitmap?.takeIf { !it.isRecycled }?.recycle()

                post {
                    loadingSatellite = false
                    satelliteLoadFailed = true
                    invalidate()
                }
            }
        }, "ATS-Satellite-Imagery").start()
    }

    private fun downloadSatelliteImage(): Bitmap? {
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

            val compressed = try {
                compressWithinLimit(bitmap)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            if (compressed == null) return null

            cacheDirectory.mkdirs()

            val temporary = File(
                cacheDirectory,
                "$SATELLITE_CACHE_NAME.tmp"
            )

            temporary.writeBytes(compressed)

            if (temporary.length() > MAX_SATELLITE_CACHE_BYTES) {
                temporary.delete()
                return null
            }

            /*
             * This directory is dedicated to the satellite image.
             * Remove only old image/temp files, never the country database.
             */
            if (satelliteCache.exists()) {
                satelliteCache.delete()
            }

            if (!temporary.renameTo(satelliteCache)) {
                temporary.copyTo(satelliteCache, overwrite = true)
                temporary.delete()
            }

            enforceSatelliteCacheLimit()

            if (
                !satelliteCache.exists() ||
                satelliteCache.length() > MAX_SATELLITE_CACHE_BYTES
            ) {
                return null
            }

            return BitmapFactory.decodeFile(satelliteCache.absolutePath)
        } finally {
            connection.disconnect()
        }
    }

    private fun compressWithinLimit(bitmap: Bitmap): ByteArray? {
        var quality = 82

        while (quality >= 35) {
            val output = ByteArrayOutputStream()

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                quality,
                output
            )

            val result = output.toByteArray()

            if (result.size <= MAX_SATELLITE_CACHE_BYTES) {
                return result
            }

            quality -= 10
        }

        return null
    }

    /**
     * The image cache contains only the optional satellite image.
     * The maximum allowed size is 5 MB.
     * The country GeoJSON file is stored separately in filesDir.
     */
    private fun enforceSatelliteCacheLimit() {
        if (!cacheDirectory.exists()) return

        val temporary = File(
            cacheDirectory,
            "$SATELLITE_CACHE_NAME.tmp"
        )

        if (temporary.exists()) {
            temporary.delete()
        }

        if (
            satelliteCache.exists() &&
            satelliteCache.length() > MAX_SATELLITE_CACHE_BYTES
        ) {
            satelliteCache.delete()
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
            val properties = feature.optJSONObject("properties") ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue

            val iso = firstIsoCode(properties)
            if (iso.isBlank()) continue

            val type = geometry.optString("type")
            val coordinates = geometry.optJSONArray("coordinates") ?: continue

            val combinedPath = Path().apply {
                fillType = Path.FillType.EVEN_ODD
            }

            when (type) {
                "Polygon" -> appendPolygon(combinedPath, coordinates)

                "MultiPolygon" -> {
                    for (i in 0 until coordinates.length()) {
                        val polygon = coordinates.optJSONArray(i) ?: continue
                        appendPolygon(combinedPath, polygon)
                    }
                }
            }

            if (!combinedPath.isEmpty) {
                parsed.add(CountryShape(iso, combinedPath))
            }
        }

        synchronized(countries) {
            countries.clear()
            countries.addAll(parsed)
        }
    }

    private fun firstIsoCode(properties: JSONObject): String {
        for (key in listOf("ISO_A2", "ISO_A2_EH", "WB_A2")) {
            val value = properties.optString(key, "")
                .trim()
                .uppercase(Locale.ROOT)

            if (value.length == 2 && value != "-99") {
                return value
            }
        }

        return ""
    }

    private fun appendPolygon(target: Path, polygon: JSONArray) {
        for (ringIndex in 0 until polygon.length()) {
            val ring = polygon.optJSONArray(ringIndex) ?: continue
            if (ring.length() < 3) continue

            val path = Path()
            var started = false

            for (index in 0 until ring.length()) {
                val coordinate = ring.optJSONArray(index) ?: continue
                if (coordinate.length() < 2) continue

                val longitude = coordinate.optDouble(0)
                val latitude = coordinate.optDouble(1)

                if (
                    longitude.isNaN() || latitude.isNaN() ||
                    longitude.isInfinite() || latitude.isInfinite()
                ) {
                    continue
                }

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

        val mapWidth = min(availableWidth, availableHeight * 2f)
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
    // MAIN DRAW
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

        if (gridLayerEnabled) {
            drawGeographicGrid(canvas)
        }

        if (boundariesLayerEnabled) {
            drawCountries(canvas)
        }

        if (radarLayerEnabled) {
            drawRadarSweep(canvas)
        }

        canvas.restoreToCount(save)

        drawHud(canvas)
        drawLayerPanel(canvas)
        drawCornerBrackets(canvas)
        drawFooter(canvas)

        if (animationEnabled && radarLayerEnabled) {
            radarAngle = (radarAngle + 2.2f) % 360f
            postInvalidateDelayed(55L)
        }
    }

    private fun drawBackgroundGrid(canvas: Canvas) {
        val step = 30f * density
        var x = 0f

        while (x < width) {
            canvas.drawLine(
                x, 0f,
                x, height.toFloat(),
                gridPaint
            )
            x += step
        }

        var y = 0f

        while (y < height) {
            canvas.drawLine(
                0f, y,
                width.toFloat(), y,
                gridPaint
            )
            y += step
        }
    }

    private fun drawSatelliteLayer(canvas: Canvas) {
        val bitmap = satelliteBitmap

        if (
            satelliteLayerEnabled &&
            bitmap != null &&
            !bitmap.isRecycled
        ) {
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
                landPaint
            )
        }
    }

    private fun drawGeographicGrid(canvas: Canvas) {
        for (i in 1 until 12) {
            val x = mapRect.left + mapRect.width() * i / 12f

            canvas.drawLine(
                x,
                mapRect.top,
                x,
                mapRect.bottom,
                gridPaint
            )
        }

        for (i in 1 until 6) {
            val y = mapRect.top + mapRect.height() * i / 6f

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

        val hasSatelliteImage =
            satelliteLayerEnabled &&
            satelliteBitmap != null &&
            !satelliteBitmap!!.isRecycled

        landPaint.color = if (hasSatelliteImage) {
            Color.argb(55, 8, 31, 55)
        } else {
            Color.rgb(8, 31, 55)
        }

        for (country in snapshot) {
            country.path.transform(
                mapMatrix,
                screenPath
            )

            if (country.isoCode == highlightedCountry) {
                canvas.drawPath(screenPath, selectedGlowPaint)
                canvas.drawPath(screenPath, selectedCountryPaint)
                canvas.drawPath(screenPath, selectedBorderPaint)
            } else {
                canvas.drawPath(screenPath, landPaint)
                canvas.drawPath(screenPath, countryBorderPaint)
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

        canvas.drawCircle(centerX, centerY, radius, radarPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.66f, radarPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.33f, radarPaint)

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

        val sweep = Path()
        sweep.moveTo(centerX, centerY)
        sweep.arcTo(radarRect, radarAngle, 34f, false)
        sweep.close()

        canvas.drawPath(sweep, radarSweepPaint)

        val angle = Math.toRadians(radarAngle.toDouble())

        val endX = centerX + cos(angle).toFloat() * radius
        val endY = centerY + sin(angle).toFloat() * radius

        canvas.drawLine(
            centerX,
            centerY,
            endX,
            endY,
            panelBorderPaint
        )
    }

    // ---------------------------------------------------------
    // GEOINT HUD
    // ---------------------------------------------------------

    private fun drawHud(canvas: Canvas) {
        val margin = 9f * density
        val panelWidth = min(width * 0.58f, 220f * density)
        val panelHeight = 108f * density

        val panel = RectF(
            margin,
            margin,
            margin + panelWidth,
            margin + panelHeight
        )

        drawPanel(canvas, panel)

        val x = panel.left + 10f * density
        var y = panel.top + 15f * density

        canvas.drawText(
            "ATS // GEOINT COMMAND",
            x,
            y,
            primaryText
        )

        y += 14f * density

        canvas.drawText(
            countryStatus(),
            x,
            y,
            if (countryLoadFailed) warningText else secondaryText
        )

        y += 12f * density

        canvas.drawText(
            satelliteStatus(),
            x,
            y,
            if (satelliteLoadFailed) warningText else secondaryText
        )

        y += 12f * density

        val report = trackScanReport
        val regionLine = if (report != null && report.regionCode.isNotBlank()) {
            "NUMBER REGION: ${report.regionCode}  ${report.callingCode}"
        } else {
            "NUMBER REGION: NOT AVAILABLE"
        }

        y += 12f * density
        canvas.drawText(
            regionLine,
            x,
            y,
            if (report?.isValid == true) activeText else warningText
        )

        y += 12f * density
        val typeLine = if (report != null) {
            "TYPE: ${report.numberType}"
        } else {
            "TRACK SCAN: WAITING FOR REPORT"
        }
        canvas.drawText(typeLine, x, y, secondaryText)

        y += 12f * density
        val carrierLine = when {
            report == null -> "CARRIER: NOT ANALYZED"
            report.originalCarrier.isBlank() ||
                report.originalCarrier == "NOT AVAILABLE IN PREFIX DATA" ->
                "CARRIER: NOT AVAILABLE"
            else -> "ORIGINAL: ${report.originalCarrier}"
        }
        canvas.drawText(
            carrierLine,
            x,
            y,
            if (report?.originalCarrier?.startsWith("NOT AVAILABLE") == true) {
                warningText
            } else {
                secondaryText
            }
        )

        val statusWidth = 92f * density

        val status = RectF(
            width - statusWidth - margin,
            margin,
            width - margin,
            margin + 42f * density
        )

        drawPanel(canvas, status)

        canvas.drawText(
            "MAP STATUS",
            status.left + 7f * density,
            status.top + 14f * density,
            secondaryText
        )

        canvas.drawText(
            if (countryLoadFailed) "LIMITED" else "GEOINT",
            status.left + 7f * density,
            status.top + 29f * density,
            if (countryLoadFailed) warningText else activeText
        )

        if (highlightedCountry.isNotBlank()) {
            drawCountryProfile(canvas)
        }
    }

    private fun drawLayerPanel(canvas: Canvas) {
        val margin = 9f * density
        val lineHeight = 12f * density
        val panelWidth = min(width * 0.55f, 190f * density)
        val panelHeight = 88f * density

        val panel = RectF(
            width - panelWidth - margin,
            height - panelHeight - 24f * density,
            width - margin,
            height - 24f * density
        )

        drawPanel(canvas, panel)

        val x = panel.left + 8f * density
        val markerX = panel.right - 28f * density
        var y = panel.top + 13f * density

        canvas.drawText(
            "INTELLIGENCE LAYERS",
            x,
            y,
            primaryText
        )

        y += lineHeight

        drawLayerLine(
            canvas, x, markerX, y,
            "SATELLITE IMAGERY",
            satelliteLayerEnabled
        )

        y += lineHeight

        drawLayerLine(
            canvas, x, markerX, y,
            "COUNTRY BOUNDARIES",
            boundariesLayerEnabled
        )

        y += lineHeight

        val reportAvailable = trackScanReport != null
        drawLayerLine(
            canvas, x, markerX, y,
            "NUMBER ANALYSIS",
            reportAvailable,
            unavailable = !reportAvailable
        )

        y += lineHeight

        val carrierAvailable = trackScanReport?.originalCarrier
            ?.let { it.isNotBlank() && !it.startsWith("NOT AVAILABLE") }
            ?: false
        drawLayerLine(
            canvas, x, markerX, y,
            "CARRIER PREFIX DATA",
            carrierAvailable,
            unavailable = !carrierAvailable
        )

        y += lineHeight

        canvas.drawText(
            when {
                trackScanReport?.regionCode?.isNotBlank() == true ->
                    "REGION: ${trackScanReport?.regionCode}"
                regionalLayerEnabled -> "REGIONAL: NO REPORT"
                else -> "REGIONAL: STANDBY"
            },
            x,
            y,
            warningText
        )
    }

    private fun drawLayerLine(
        canvas: Canvas,
        x: Float,
        markerX: Float,
        y: Float,
        label: String,
        enabled: Boolean,
        unavailable: Boolean = false
    ) {
        val marker = when {
            unavailable -> "N/A"
            enabled -> "ON"
            else -> "OFF"
        }

        val paint = when {
            unavailable -> warningText
            enabled -> activeText
            else -> secondaryText
        }

        canvas.drawText(
            label,
            x,
            y,
            secondaryText
        )

        canvas.drawText(
            marker,
            markerX,
            y,
            paint
        )
    }

    private fun drawCountryProfile(canvas: Canvas) {
        val margin = 9f * density

        val panel = RectF(
            margin,
            height - 68f * density,
            margin + 100f * density,
            height - 24f * density
        )

        drawPanel(canvas, panel)

        canvas.drawText(
            "COUNTRY PROFILE",
            panel.left + 8f * density,
            panel.top + 14f * density,
            secondaryText
        )

        canvas.drawText(
            highlightedCountry,
            panel.left + 8f * density,
            panel.top + 31f * density,
            primaryText
        )

        val report = trackScanReport
        val profileLine = when {
            report == null -> "TRACK SCAN: WAITING"
            report.regionCode.isBlank() -> "REGION: UNAVAILABLE"
            else -> "REGION: ${report.regionCode}  ${report.callingCode}"
        }

        canvas.drawText(
            profileLine,
            panel.left + 8f * density,
            panel.top + 41f * density,
            if (report?.isValid == true) activeText else warningText
        )
    }

    private fun drawPanel(canvas: Canvas, rect: RectF) {
        canvas.drawRoundRect(
            rect,
            4f * density,
            4f * density,
            panelPaint
        )

        canvas.drawRoundRect(
            rect,
            4f * density,
            4f * density,
            panelBorderPaint
        )

        canvas.drawRect(
            rect.left,
            rect.top,
            rect.left + 2.5f * density,
            rect.bottom,
            accentPaint
        )
    }

    private fun countryStatus(): String {
        return when {
            loadingCountries -> "COUNTRY DATABASE: LOADING"
            countryLoadFailed -> "COUNTRY DATABASE: UNAVAILABLE"
            else -> "COUNTRY BOUNDARIES: ${countries.size} FEATURES"
        }
    }

    private fun satelliteStatus(): String {
        return when {
            loadingSatellite -> "SATELLITE IMAGERY: LOADING"
            satelliteBitmap != null -> "NASA GIBS: CACHED IMAGERY"
            satelliteLoadFailed -> "SATELLITE IMAGERY: OFFLINE"
            else -> "SATELLITE IMAGERY: NOT LOADED"
        }
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
            val dx = if (x == mapRect.left) 1f else -1f
            val dy = if (y == mapRect.top) 1f else -1f

            canvas.drawLine(
                x + dx * offset,
                y,
                x + dx * (length + offset),
                y,
                panelBorderPaint
            )

            canvas.drawLine(
                x,
                y + dy * offset,
                x,
                y + dy * (length + offset),
                panelBorderPaint
            )
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val y = height - 8f * density

        val source = if (
            satelliteBitmap != null && satelliteLayerEnabled
        ) {
            "NASA GIBS IMAGERY | NATURAL EARTH BOUNDARIES"
        } else {
            "NATURAL EARTH VECTOR DATA"
        }

        canvas.drawText(
            source,
            9f * density,
            y,
            secondaryText
        )

        val warning = "NOT LIVE CALLER LOCATION"
        val warningWidth = warningText.measureText(warning)

        canvas.drawText(
            warning,
            width - 9f * density - warningWidth,
            y,
            warningText
        )
    }

    private fun formatCacheDate(): String {
        if (!satelliteCache.exists()) {
            return "NOT CACHED"
        }

        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date(satelliteCache.lastModified()))
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
        private const val MAX_SATELLITE_CACHE_BYTES =
            5L * 1024L * 1024L

        private const val MIN_COUNTRY_CACHE_BYTES = 10_000L

        private const val COUNTRY_CACHE_NAME =
            "ats_world_countries.geojson"

        private const val SATELLITE_CACHE_NAME =
            "ats_nasa_global_satellite.jpg"

        private const val COUNTRY_GEOJSON_URL =
            "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"

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
