
package com.ats.sentinel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ATSBackground = Color(0xFF050B16)
private val ATSSurface = Color(0xFF101D30)
private val ATSBlue = Color(0xFF00BFFF)
private val ATSText = Color(0xFFE8F5FF)
private val ATSMuted = Color(0xFF7895B5)
private val ATSGreen = Color(0xFF35D69A)

private enum class ATSPage {
    COMMAND,
    COMMUNICATIONS,
    INTELLIGENCE,
    DIALER,
    CONTACTS,
    HISTORY
}

@Composable
fun ATSNavigationApp(
    isOrbRunning: Boolean,
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit
) {
    var currentPage by remember {
        mutableStateOf(ATSPage.COMMAND)
    }

    var menuExpanded by remember {
        mutableStateOf(false)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ATSBlue,
            background = ATSBackground,
            surface = ATSSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ATSBackground)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF091525))
                    .padding(
                        horizontal = 20.dp,
                        vertical = 15.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Column {
                    Text(
                        text = "ATS",
                        color = ATSBlue,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )

                    Text(
                        text = "TACTICAL SENTINEL",
                        color = ATSText,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                }

                Box {
                    Text(
                        text = "⋮",
                        color = ATSBlue,
                        fontSize = 34.sp,
                        modifier = Modifier
                            .clickable {
                                menuExpanded = true
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 2.dp
                            )
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                        },
                        modifier = Modifier
                            .background(ATSSurface)
                    ) {
                        MenuEntry("COMMAND CENTER") {
                            currentPage = ATSPage.COMMAND
                            menuExpanded = false
                        }

                        MenuEntry("COMMUNICATIONS") {
                            currentPage = ATSPage.COMMUNICATIONS
                            menuExpanded = false
                        }

                        MenuEntry("INTELLIGENCE") {
                            currentPage = ATSPage.INTELLIGENCE
                            menuExpanded = false
                        }

                        HorizontalDivider(
                            color = ATSBlue.copy(alpha = 0.3f)
                        )

                        MenuEntry("DIALER") {
                            currentPage = ATSPage.DIALER
                            menuExpanded = false
                        }

                        MenuEntry("CONTACTS") {
                            currentPage = ATSPage.CONTACTS
                            menuExpanded = false
                        }

                        MenuEntry("CALL HISTORY") {
                            currentPage = ATSPage.HISTORY
                            menuExpanded = false
                        }
                    }
                }
            }

            when (currentPage) {

                ATSPage.COMMAND -> {
                    CommandCenterScreen(
                        isOrbRunning = isOrbRunning,
                        onStartOrb = onStartOrb,
                        onStopOrb = onStopOrb
                    )
                }

                ATSPage.COMMUNICATIONS -> {
                    CommunicationsScreen(
                        onDialer = {
                            currentPage = ATSPage.DIALER
                        },
                        onContacts = {
                            currentPage = ATSPage.CONTACTS
                        },
                        onHistory = {
                            currentPage = ATSPage.HISTORY
                        }
                    )
                }

                ATSPage.INTELLIGENCE -> {
                    IntelligenceScreen()
                }

                ATSPage.DIALER -> {
                    DialerScreen()
                }

                ATSPage.CONTACTS -> {
                    ContactsScreen()
                }

                ATSPage.HISTORY -> {
                    HistoryScreen()
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(
    label: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = ATSText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        onClick = onClick
    )
}

@Composable
private fun CommandCenterScreen(
    isOrbRunning: Boolean,
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text(
                text = "COMMAND CENTER",
                color = ATSBlue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "AUTONOMOUS TACTICAL OPERATIONS",
                color = ATSMuted,
                fontSize = 9.sp
            )
        }

        item {
            StatusPanel(
                title = "SYSTEM STATUS",
                value = "OPERATIONAL",
                detail = "ATS core interface initialized",
                statusColor = ATSGreen
            )
        }

        item {
            StatusPanel(
                title = "HOLO GRID",
                value = if (isOrbRunning) "RUNNING" else "STANDBY",
                detail = "Floating tactical interface",
                statusColor = if (isOrbRunning) ATSGreen else ATSBlue
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartOrb,
                    enabled = !isOrbRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("START")
                }

                OutlinedButton(
                    onClick = onStopOrb,
                    enabled = isOrbRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("STOP")
                }
            }
        }

        item {
            Text(
                text = "TACTICAL MODULES",
                color = ATSBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            StatusPanel(
                "DEVICE MONITOR",
                "READY",
                "Battery and memory monitoring",
                ATSBlue
            )
        }

        item {
            StatusPanel(
                "CYBER DEFENSE",
                "NOT INTEGRATED",
                "Security engine development pending",
                ATSMuted
            )
        }

        item {
            StatusPanel(
                "ATS AI ENGINE",
                "RUNTIME PENDING",
                "Local AI runtime integration pending",
                ATSMuted
            )
        }

        item {
            StatusPanel(
                "COMMUNICATIONS",
                "IN DEVELOPMENT",
                "Telecom service registered",
                ATSBlue
            )
        }

        item {
            Text(
                text = "OFFLINE-FIRST • ANDROID 11",
                color = ATSMuted,
                fontSize = 9.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun CommunicationsScreen(
    onDialer: () -> Unit,
    onContacts: () -> Unit,
    onHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "COMMUNICATIONS",
            color = ATSBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Text(
            text = "TACTICAL COMMUNICATIONS SYSTEM",
            color = ATSMuted,
            fontSize = 9.sp
        )

        NavigationCard(
            "DIALER",
            "Keypad and outgoing calls",
            onDialer
        )

        NavigationCard(
            "CONTACTS",
            "Contact directory and search",
            onContacts
        )

        NavigationCard(
            "CALL HISTORY",
            "Incoming, outgoing and missed calls",
            onHistory
        )

        StatusPanel(
            "TELECOM SERVICE",
            "REGISTERED",
            "Waiting for default phone role and call UI",
            ATSBlue
        )
    }
}

@Composable
private fun IntelligenceScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text(
                text = "INTELLIGENCE",
                color = ATSBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        item {
            StatusPanel(
                "CALLER IDENTIFICATION",
                "PENDING",
                "Contact-based identification will be integrated",
                ATSMuted
            )
        }

        item {
            StatusPanel(
                "COUNTRY INDICATOR",
                "PENDING",
                "Based on available international number prefix",
                ATSMuted
            )
        }

        item {
            StatusPanel(
                "SPAM INDICATORS",
                "NOT CONFIGURED",
                "No unverified number reputation claims",
                ATSMuted
            )
        }

        item {
            StatusPanel(
                "LOCAL SECURITY ANALYSIS",
                "PLANNED",
                "Privacy-conscious on-device analysis",
                ATSBlue
            )
        }

        item {
            Text(
                text = "ATS displays only information it can verify. It will not claim to identify a caller's location or identity from a phone number alone.",
                color = ATSMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DialerScreen() {
    var number by remember {
        mutableStateOf("")
    }

    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "ATS SECURE DIALER",
            color = ATSBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = number.ifEmpty { "ENTER NUMBER" },
            color = ATSText,
            fontSize = 23.sp,
            modifier = Modifier.padding(18.dp)
        )

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            number += key
                        },
                        modifier = Modifier.size(76.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = key,
                            color = ATSText,
                            fontSize = 22.sp
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    number = number.dropLast(1)
                }
            ) {
                Text("DELETE")
            }

            Button(
                onClick = {},
                enabled = false
            ) {
                Text("CALL • NEXT PHASE")
            }
        }

        Text(
            text = "Call placement will be connected after Telecom role and permissions are tested.",
            color = ATSMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ContactsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "CONTACTS",
            color = ATSBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        StatusPanel(
            "CONTACT DIRECTORY",
            "NOT CONNECTED",
            "Contact permission and device directory integration are next",
            ATSMuted
        )
    }
}

@Composable
private fun HistoryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "CALL HISTORY",
            color = ATSBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        StatusPanel(
            "CALL LOG",
            "NOT CONNECTED",
            "Call history permission and data integration are pending",
            ATSMuted
        )
    }
}

@Composable
private fun NavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ATSSurface
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = title,
                color = ATSBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                text = description,
                color = ATSMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StatusPanel(
    title: String,
    value: String,
    detail: String,
    statusColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ATSSurface
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                color = ATSMuted,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )

            Text(
                text = value,
                color = statusColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = detail,
                color = ATSText,
                fontSize = 10.sp
            )
        }
    }
}
