
package com.ats.sentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TacticalBackground = Color(0xFF090E0B)
private val TacticalSurface = Color(0xFF141C17)
private val TacticalGreen = Color(0xFF39D477)
private val TacticalMuted = Color(0xFF829589)
private val TacticalWhite = Color(0xFFE8F0EA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ATSApp()
        }
    }
}

@Composable
fun ATSApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TacticalGreen,
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

                OrbSection()

                Spacer(modifier = Modifier.height(28.dp))

                SectionTitle("SYSTEM OVERVIEW")

                Spacer(modifier = Modifier.height(12.dp))

                StatusCard(
                    title = "EDGE AI ENGINE",
                    subtitle = "Local inference engine",
                    status = "NOT CONNECTED",
                    indicator = TacticalMuted
                )

                StatusCard(
                    title = "NETWORK DEFENSE",
                    subtitle = "Local network monitoring",
                    status = "NOT ACTIVE",
                    indicator = TacticalMuted
                )

                StatusCard(
                    title = "THREAT ANALYSIS",
                    subtitle = "Security analysis module",
                    status = "STANDBY",
                    indicator = TacticalGreen
                )

                StatusCard(
                    title = "VOICE ENGINE",
                    subtitle = "Offline speech services",
                    status = "NOT CONFIGURED",
                    indicator = TacticalMuted
                )

                Spacer(modifier = Modifier.height(24.dp))

                SectionTitle("SYSTEM INFORMATION")

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow("PLATFORM", "ANDROID")
                InfoRow("APPLICATION", "ATS SENTINEL")
                InfoRow("VERSION", "0.1.0")
                InfoRow("OPERATION MODE", "OFFLINE-FIRST")

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = "AUTONOMOUS TACTICAL SUITE",
                    color = TacticalMuted,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
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
                color = TacticalGreen,
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
                text = "INITIALIZING",
                color = TacticalGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OrbSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TACTICAL AI ORB",
            color = TacticalWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        TacticalOrbGraphic()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "TACTICAL CORE STANDBY",
            color = TacticalGreen,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )

        Text(
            text = "Floating service not started",
            color = TacticalMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TacticalOrbGraphic() {
    Box(
        modifier = Modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = this.center
            val radius = size.minDimension / 2

            drawCircle(
                color = TacticalGreen.copy(alpha = 0.12f),
                radius = radius * 0.95f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = TacticalGreen.copy(alpha = 0.35f),
                radius = radius * 0.78f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawCircle(
                color = TacticalGreen.copy(alpha = 0.65f),
                radius = radius * 0.58f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = Color(0xFF193C27),
                radius = radius * 0.43f,
                center = center
            )

            drawCircle(
                color = TacticalGreen.copy(alpha = 0.8f),
                radius = radius * 0.25f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = TacticalGreen,
                radius = radius * 0.10f,
                center = center
            )

            drawArc(
                color = TacticalGreen,
                startAngle = -90f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    center.x - radius * 0.78f,
                    center.y - radius * 0.78f
                ),
                size = androidx.compose.ui.geometry.Size(
                    radius * 1.56f,
                    radius * 1.56f
                ),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        Text(
            text = "ATS",
            color = TacticalWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TacticalGreen,
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
