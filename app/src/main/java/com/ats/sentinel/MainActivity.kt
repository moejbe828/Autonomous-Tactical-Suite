
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

private val Background = Color(0xFF050B16)
private val SurfaceDark = Color(0xFF101D30)
private val Blue = Color(0xFF00BFFF)
private val White = Color(0xFFE8F5FF)
private val Muted = Color(0xFF7895B5)
private val Green = Color(0xFF35D69A)

class MainActivity : ComponentActivity() {

    private var pendingOrbStart = false
    private val orbRunning = mutableStateOf(false)

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
                isOrbRunning = orbRunning.value,
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

        try {
            startForegroundService(intent)
            orbRunning.value = true
        } catch (_: Exception) {
            orbRunning.value = false
        }
    }

    private fun stopOrbService() {
        val intent = Intent(
            this,
            TacticalOrbService::class.java
        ).apply {
            action = TacticalOrbService.ACTION_STOP
        }

        try {
            startService(intent)
        } catch (_: Exception) {
        }

        orbRunning.value = false
    }
}

@Composable
fun ATSApp(
    isOrbRunning: Boolean,
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            background = Background,
            surface = SurfaceDark
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(20.dp)
            ) {
                DashboardHeader(isOrbRunning)

                Spacer(modifier = Modifier.height(24.dp))

                SystemStatusCard(isOrbRunning)

                Spacer(modifier = Modifier.height(22.dp))

                OrbControlCard(
                    isOrbRunning = isOrbRunning,
                    onStartOrb = onStartOrb,
                    onStopOrb = onStopOrb
                )

                Spacer(modifier = Modifier.height(22.dp))

                SectionTitle("SYSTEM MODULES")

                Spacer(modifier = Modifier.height(12.dp))

                ModuleCard(
                    "HOLO GRID",
                    "Floating visual interface",
                    if (isOrbRunning) "RUNNING" else "READY",
                    if (isOrbRunning) Green else Blue
                )

                ModuleCard(
                    "EDGE AI ENGINE",
                    "Local inference engine",
                    "NOT CONFIGURED",
                    Muted
                )

                ModuleCard(
                    "NETWORK DEFENSE",
                    "Local network monitoring",
                    "NOT ACTIVE",
                    Muted
                )

                ModuleCard(
                    "EVENT LOGGER",
                    "Local event recording",
                    "PLANNED",
                    Muted
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "AUTONOMOUS TACTICAL SUITE",
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(
                        Alignment.CenterHorizontally
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ATS 1.0 • OFFLINE-FIRST • ANDROID 11",
                    color = Muted.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    modifier = Modifier.align(
                        Alignment.CenterHorizontally
                    )
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(isOrbRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "ATS",
                color = Blue,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Text(
                text = "TACTICAL SENTINEL",
                color = White,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "SYSTEM",
                color = Muted,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOrbRunning) Green else Blue
                        )
                )

                Spacer(modifier = Modifier.width(7.dp))

                Text(
                    text = if (isOrbRunning) "ACTIVE" else "STANDBY",
                    color = if (isOrbRunning) Green else Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SystemStatusCard(isOrbRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {
        Column(
            modifier = Modifier.padding(17.dp)
        ) {
            Text(
                text = "TACTICAL SYSTEM STATUS",
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Blue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ATS",
                        color = Blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(13.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isOrbRunning)
                            "HOLO GRID ACTIVE"
                        else
                            "SYSTEM READY",
                        color = White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (isOrbRunning)
                            "Floating interface requested"
                        else
                            "Waiting for operator command",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = if (isOrbRunning) "ON" else "OFF",
                    color = if (isOrbRunning) Green else Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OrbControlCard(
    isOrbRunning: Boolean,
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HOLOGRAPHIC CORE",
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            HoloGridPreview()

            Text(
                text = "BLUE HOLOGRAPHIC ORB",
                color = Blue,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onStartOrb,
                enabled = !isOrbRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07568A),
                    contentColor = White,
                    disabledContainerColor = Color(0xFF18364B),
                    disabledContentColor = Muted
                )
            ) {
                Text(
                    text = if (isOrbRunning)
                        "HOLO GRID IS RUNNING"
                    else
                        "START HOLO GRID",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            OutlinedButton(
                onClick = onStopOrb,
                enabled = isOrbRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Blue,
                    disabledContentColor = Muted
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
}

@Composable
private fun HoloGridPreview() {
    Box(
        modifier = Modifier.size(175.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val cx = center.x
            val cy = center.y
            val radius = size.minDimension * 0.36f

            drawCircle(
                color = Blue.copy(alpha = 0.10f),
                radius = radius * 1.35f
            )

            drawOval(
                color = Blue.copy(alpha = 0.65f),
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
                color = Blue.copy(alpha = 0.9f),
                radius = radius,
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawCircle(
                color = Color(0xFF062B50),
                radius = radius * 0.88f
            )

            for (i in -3..3) {
                val y = cy + i * radius * 0.22f

                val factor = sqrt(
                    (1f - (i * 0.22f) * (i * 0.22f))
                        .coerceAtLeast(0f)
                )

                val halfWidth = radius * 0.88f * factor

                drawOval(
                    color = Blue.copy(alpha = 0.65f),
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

            for (i in -3..3) {
                val x = cx + i * radius * 0.22f

                drawOval(
                    color = Blue.copy(alpha = 0.55f),
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

            drawCircle(
                color = Blue.copy(alpha = 0.25f),
                radius = radius * 0.28f
            )

            drawCircle(
                color = Blue,
                radius = radius * 0.10f
            )

            drawCircle(
                color = Color.White,
                radius = radius * 0.035f
            )

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
            color = White,
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
        color = Blue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
private fun ModuleCard(
    title: String,
    description: String,
    status: String,
    indicator: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicator)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = description,
                    color = Muted,
                    fontSize = 9.sp
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
