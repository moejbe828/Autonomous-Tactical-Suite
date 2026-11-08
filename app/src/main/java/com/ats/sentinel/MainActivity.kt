
package com.ats.sentinel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val TacticalBackground = Color(0xFF050B16)
private val TacticalSurface = Color(0xFF101D30)
private val TacticalBlue = Color(0xFF00BFFF)
private val TacticalMuted = Color(0xFF7895B5)
private val TacticalWhite = Color(0xFFE8F5FF)

class MainActivity : ComponentActivity() {

    private var pendingOrbStart = false

    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (pendingOrbStart) {
                pendingOrbStart = false

                if (Settings.canDrawOverlays(this)) {
                    startOrbService()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ATSApp(
                onStartOrb = { requestOrbStart() },
                onStopOrb = { stopOrbService() }
            )
        }
    }

    private fun requestOrbStart() {

        if (Settings.canDrawOverlays(this)) {
            startOrbService()
            return
        }

        pendingOrbStart = true

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        overlayPermissionLauncher.launch(intent)
    }

    private fun startOrbService() {

        val intent = Intent(
            this,
            TacticalOrbService::class.java
        )

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun stopOrbService() {

        val intent = Intent(
            this,
            TacticalOrbService::class.java
        ).apply {
            action = TacticalOrbService.ACTION_STOP
        }

        startService(intent)
    }
}

@Composable
fun ATSApp(
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit
) {

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TacticalBlue,
            background = TacticalBackground,
            surface = TacticalSurface,
            onBackground = TacticalWhite,
            onSurface = TacticalWhite
        )
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TacticalBackground
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {

                HeaderSection()

                Spacer(modifier = Modifier.height(28.dp))

                OrbSection(
                    onStartOrb = onStartOrb,
                    onStopOrb = onStopOrb
                )

                Spacer(modifier = Modifier.height(28.dp))

                SectionTitle("SYSTEM OVERVIEW")

                Spacer(modifier = Modifier.height(12.dp))

                StatusCard(
                    "EDGE AI ENGINE",
                    "Local inference engine",
                    "NOT CONNECTED",
                    TacticalMuted
                )

                StatusCard(
                    "NETWORK DEFENSE",
                    "Local network monitoring",
                    "NOT ACTIVE",
                    TacticalMuted
                )

                StatusCard(
                    "THREAT ANALYSIS",
                    "Security analysis module",
                    "STANDBY",
                    TacticalBlue
                )

                StatusCard(
                    "VOICE ENGINE",
                    "Offline speech services",
                    "NOT CONFIGURED",
                    TacticalMuted
                )

                Spacer(modifier = Modifier.height(24.dp))

                SectionTitle("SYSTEM INFORMATION")

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow("PLATFORM", "ANDROID 11")
                InfoRow("APPLICATION", "ATS SENTINEL")
                InfoRow("VERSION", "0.1.0")
                InfoRow("OPERATION MODE", "OFFLINE-FIRST")

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = "AUTONOMOUS TACTICAL SUITE",
                    color = TacticalMuted,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(
                        Alignment.CenterHorizontally
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeaderSection() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column {

            Text(
                text = "ATS",
                color = TacticalBlue,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Text(
                text = "TACTICAL SENTINEL",
                color = TacticalWhite,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {

            Text(
                text = "SYSTEM STATUS",
                color = TacticalMuted,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )

            Text(
                text = "HOLO GRID READY",
                color = TacticalBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OrbSection(
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "HOLOGRAPHIC CORE",
            color = TacticalWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        HoloGridPreview()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "HOLO GRID // BLUE EDITION",
            color = TacticalBlue,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )

        Text(
            text = "Floating holographic interface",
            color = TacticalMuted,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStartOrb,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF064C79),
                contentColor = Color.White
            )
        ) {

            Text(
                text = "START HOLO GRID",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(9.dp))

        OutlinedButton(
            onClick = onStopOrb,
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TacticalBlue
            )
        ) {

            Text(
                text = "STOP HOLO GRID",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun HoloGridPreview() {

    Box(
        modifier = Modifier.size(210.dp),
        contentAlignment = Alignment.Center
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val cx = center.x
            val cy = center.y
            val radius = size.minDimension * 0.36f

            // Outer glow rings

            drawCircle(
                color = TacticalBlue.copy(alpha = 0.12f),
                radius = radius * 1.35f,
                center = center
            )

            drawCircle(
                color = TacticalBlue.copy(alpha = 0.25f),
                radius = radius * 1.12f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Orbital rings

            drawOval(
                color = TacticalBlue.copy(alpha = 0.65f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    cx - radius * 1.25f,
                    cy - radius * 0.32f
                ),
                size = androidx.compose.ui.geometry.Size(
                    radius * 2.5f,
                    radius * 0.64f
                ),
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = TacticalBlue.copy(alpha = 0.35f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Sphere body

            drawCircle(
                color = Color(0xFF062B50),
                radius = radius * 0.88f,
                center = center
            )

            // Latitude lines

            for (i in -3..3) {

                val y = cy + i * radius * 0.22f

                val widthFactor =
                    kotlin.math.sqrt(
                        (1f - (i * 0.22f) * (i * 0.22f))
                            .coerceAtLeast(0f)
                    )

                val halfWidth = radius * 0.88f * widthFactor

                drawOval(
                    color = TacticalBlue.copy(alpha = 0.65f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        cx - halfWidth,
                        y - 3.dp.toPx()
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        halfWidth * 2f,
                        6.dp.toPx()
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Longitude lines

            for (i in -3..3) {

                val x = cx + i * radius * 0.22f

                drawOval(
                    color = TacticalBlue.copy(alpha = 0.55f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x - radius * 0.12f,
                        cy - radius * 0.88f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        radius * 0.24f,
                        radius * 1.76f
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Central core

            drawCircle(
                color = TacticalBlue.copy(alpha = 0.25f),
                radius = radius * 0.28f,
                center = center
            )

            drawCircle(
                color = TacticalBlue,
                radius = radius * 0.10f,
                center = center
            )

            drawCircle(
                color = Color.White,
                radius = radius * 0.035f,
                center = center
            )

            // Orbit particle

            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    cx + radius * 1.25f,
                    cy
                )
            )
        }

        Text(
            text = "ATS",
            color = TacticalWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun SectionTitle(title: String) {

    Text(
        text = title,
        color = TacticalBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    status: String,
    indicator: Color
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TacticalSurface
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(indicator)
            )

            Spacer(modifier = Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    color = TacticalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    color = TacticalMuted,
                    fontSize = 10.sp
                )
            }

            Text(
                text = status,
                color = indicator,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            text = label,
            color = TacticalMuted,
            fontSize = 10.sp
        )

        Text(
            text = value,
            color = TacticalWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }

    HorizontalDivider(
        color = TacticalSurface,
        thickness = 1.dp
    )
}
