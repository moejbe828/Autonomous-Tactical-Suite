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
import java.util.Locale
import kotlin.math.min

/**
 * ATS TRACK SCAN - CLEAN WORLD MAP
 *
 * Displays the phone-numbering country only.
 * The blue marker is an approximate country-map marker,
 * NOT the caller's live location or physical position.
 *
 * Offline-first country boundaries with optional cached imagery.
 */
class ATSWorldMapView(context: Context) : View(context) {

    private data class CountryShape(
        val isoCode: String,
        val path: Path
    )

    private val density = resources.displayMetrics.density
    private val countries = mutableListOf<CountryShape>()

    private val mapRect = RectF()
    private val mapMatrix = Matrix()
    private val screenPath = Path()

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
    private var gridLayerEnabled = false
    private var radarLayerEnabled = false
    private var telecomLayerEnabled = false
    private var networkLayerEnabled = false
    private var regionalLayerEnabled = false

    private var satelliteBitmap: Bitmap? = null

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val cacheDirectory =
        File(context.cacheDir, "ats_geoint")

    private val countryCache =
        File(context.filesDir, COUNTRY_CACHE_NAME)

    private val satelliteCache =
        File(cacheDirectory, SATELLITE_CACHE_NAME)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(3, 10, 22)
    }

    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(12, 34, 57)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(65, 112, 145)
        style = Paint.Style.STROKE
        strokeWidth = 0.65f * density
    }

    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 0, 119, 210)
        style = Paint.Style.FILL
    }

    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 205, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val selectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 0, 160, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
    }

    private val markerOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 145, 255)
        style = Paint.Style.FILL
    }

    private val satellitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 210
        isFilterBitmap = true
    }

    private val satelliteTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 12, 30)
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
    // PUBLIC API - KEPT FOR EXISTING ATS INTEGRATION
    // ---------------------------------------------------------

    fun setHighlightedCountry(isoCode: String?) {
        highlightedCountry = isoCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        invalidate()
    }

    fun setTrackScanReport(report: ATSTrackScanReport?) {
        trackScanReport = report
        highlightedCountry = report?.regionCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        resetMapView()
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
                val jsonText =
                    if (
                        countryCache.exists() &&
                        countryCache.length() > MIN_COUNTRY_CACHE_BYTES
                    ) {
                        countryCache.readText()
                    } else {
                        val downloaded = downloadText(COUNTRY_GEOJSON_URL)

                        require(
                            downloaded.length > MIN_COUNTRY_CACHE_BYTES
                        ) {
                            "Country dataset is incomplete"
                        }

                        val temporary = File(
                            context.filesDir,
                            "$COUNTRY_CACHE_NAME.tmp"
                        )

                        temporary.writeText(downloaded)

                        if (!temporary.renameTo(countryCache)) {
                            temporary.copyTo(
                                countryCache,
                                overwrite = true
                            )
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

    private fun parseGeoJson(source: String) {
        val parsed = mutableListOf<CountryShape>()
        val root = JSONObject(source)
        val features = root.optJSONArray("features") ?: return

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties =
                feature.optJSONObject("properties") ?: continue
            val geometry =
                feature.optJSONObject("geometry") ?: continue

            val iso = firstIsoCode(properties)
            if (iso.isBlank()) continue

            val type = geometry.optString("type")
            val coordinates =
                geometry.optJSONArray("coordinates") ?: continue

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
                        val polygon =
                            coordinates.optJSONArray(i) ?: continue
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

    private fun appendPolygon(
        target: Path,
        polygon: JSONArray
    ) {
        for (ringIndex in 0 until polygon.length()) {
            val ring = polygon.optJSONArray(ringIndex) ?: continue
            if (ring.length() < 3) continue

            val path = Path()
            var started = false

            for (index in 0 until ring.length()) {
                val coordinate =
                    ring.optJSONArray(index) ?: continue

                if (coordinate.length() < 2) continue

                val longitude = coordinate.optDouble(0)
                val latitude = coordinate.optDouble(1)

                if (
                    longitude.isNaN() ||
                    latitude.isNaN() ||
                    longitude.isInfinite() ||
                    latitude.isInfinite()
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
    // OPTIONAL SATELLITE IMAGERY
    // ---------------------------------------------------------

    private fun loadSatelliteImagery() {
        if (loadingSatellite) return

        loadingSatellite = true
        satelliteLoadFailed = false

        Thread({
            var resultBitmap: Bitmap? = null

            try {
                if (
                    satelliteCache.exists() &&
                    satelliteCache.length() in
                    20_000L..MAX_SATELLITE_CACHE_BYTES
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
                        oldBitmap
                            ?.takeIf { !it.isRecycled }
                            ?.recycle()
                    }

                    satelliteBitmap = finalBitmap
                    loadingSatellite = false
                    satelliteLoadFailed = finalBitmap == null
                    invalidate()
                }
            } catch (_: Exception) {
                resultBitmap
                    ?.takeIf { !it.isRecycled }
                    ?.recycle()

                post {
                    loadingSatellite = false
                    satelliteLoadFailed = true
                    invalidate()
                }
            }
        }, "ATS-Satellite-Imagery").start()
    }

    private fun downloadSatelliteImage(): Bitmap? {
        val connection =
            URL(NASA_GIBS_WMS_URL).openConnection()
                as HttpURLConnection

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

            if (satelliteCache.exists()) {
                satelliteCache.delete()
            }

            if (!temporary.renameTo(satelliteCache)) {
                temporary.copyTo(
                    satelliteCache,
                    overwrite = true
                )
                temporary.delete()
            }

            if (
                !satelliteCache.exists() ||
                satelliteCache.length() > MAX_SATELLITE_CACHE_BYTES
            ) {
                return null
            }

            return BitmapFactory.decodeFile(
                satelliteCache.absolutePath
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun compressWithinLimit(bitmap: Bitmap): ByteArray? {
        var quality = 82

        while (quality >= 35) {
            val output = java.io.ByteArrayOutputStream()

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

    private fun downloadText(address: String): String {
        val connection =
            URL(address).openConnection() as HttpURLConnection

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
    // MAP GEOMETRY
    // ---------------------------------------------------------

    private fun calculateMapRect() {
        val availableWidth = width.toFloat()
        val availableHeight = height.toFloat()

        if (availableWidth <= 0f || availableHeight <= 0f) {
            mapRect.setEmpty()
            return
        }

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
        if (mapRect.isEmpty) return

        val limitX = mapRect.width() * (zoom - 1f) / 2f
        val limitY = mapRect.height() * (zoom - 1f) / 2f

        panX = panX.coerceIn(-limitX, limitX)
        panY = panY.coerceIn(-limitY, limitY)
    }

    // ---------------------------------------------------------
    // CLEAN MAP DRAWING
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

        drawBaseLayer(canvas)

        if (boundariesLayerEnabled) {
            drawCountries(canvas)
        }

        drawCountryMarker(canvas)

        canvas.restoreToCount(save)
    }

    private fun drawBaseLayer(canvas: Canvas) {
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
                    selectedFillPaint
                )

                canvas.drawPath(
                    screenPath,
                    selectedBorderPaint
                )
            } else {
                canvas.drawPath(
                    screenPath,
                    landPaint
                )

                canvas.drawPath(
                    screenPath,
                    borderPaint
                )
            }
        }
    }

    /**
     * Places a blue marker at the center of the selected
     * country's geographic bounding box.
     *
     * This is only a visual country-region indicator.
     * It is not a caller location estimate.
     */
    private fun drawCountryMarker(canvas: Canvas) {
        if (highlightedCountry.isBlank()) return

        val country = synchronized(countries) {
            countries.firstOrNull {
                it.isoCode == highlightedCountry
            }
        } ?: return

        val bounds = RectF()
        country.path.computeBounds(bounds, true)

        if (bounds.isEmpty) return

        val points = floatArrayOf(
            bounds.centerX(),
            bounds.centerY()
        )

        mapMatrix.mapPoints(points)

        val markerX = points[0]
        val markerY = points[1]

        val outerRadius = 5.2f * density
        val innerRadius = 3.4f * density

        canvas.drawCircle(
            markerX,
            markerY,
            outerRadius,
            markerOuterPaint
        )

        canvas.drawCircle(
            markerX,
            markerY,
            innerRadius,
            markerPaint
        )
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
    // LIFECYCLE
    // ---------------------------------------------------------

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_SATELLITE_CACHE_BYTES =
            5L * 1024L * 1024L

        private const val MIN_COUNTRY_CACHE_BYTES =
            10_000L

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
