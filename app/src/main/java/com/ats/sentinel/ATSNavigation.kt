
package com.ats.sentinel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
private val ATSRed = Color(0xFFFF5B6E)

private enum class ATSPage {
    COMMAND,
    COMMUNICATIONS,
    INTELLIGENCE,
    DIALER,
    CONTACTS,
    HISTORY,
    BLOCKED_NUMBERS
}

private fun normalizeATSNumber(number: String): String {
    return number.filter { it.isDigit() }
}

private fun isATSNumberBlocked(
    number: String,
    blockedNumbers: List<ATSBlockedNumber>
): Boolean {
    val normalized = normalizeATSNumber(number)

    if (normalized.isBlank()) return false

    return blockedNumbers.any {
        normalizeATSNumber(it.number) == normalized
    }
}

@Composable
fun ATSNavigationApp(
    isOrbRunning: Boolean,
    initialDialNumber: String,
    openDialerPage: Boolean,
    onPlaceCall: (String) -> Unit,
    onStartOrb: () -> Unit,
    onStopOrb: () -> Unit,
    contactsPermissionGranted: Boolean = false,
    callLogPermissionGranted: Boolean = false,
    onRequestContactsPermission: () -> Unit = {},
    onRequestCallLogPermission: () -> Unit = {},
    contacts: List<ATSContact> = emptyList(),
    callHistory: List<ATSCallLogEntry> = emptyList(),
    onOpenContact: (Long) -> Unit = {},
    onAddContact: (String, String) -> Unit = { _, _ -> },
    onAddNumberToContacts: (String) -> Unit = {},
    onManageBlockedNumbers: (String) -> Unit = {},
    blockedNumbers: List<ATSBlockedNumber> = emptyList(),
    blockingAuditLog: List<ATSBlockingAuditEvent> = emptyList(),
    blockingDatabaseStatus: String = "NOT LOADED",
    callScreeningEnabled: Boolean = false,
    onBlockNumber: (String) -> Unit = {},
    onUnblockNumber: (String) -> Unit = {},
    onRequestCallScreening: () -> Unit = {},
    onRefreshBlockedNumbers: () -> Unit = {}
) {
    var currentPage by remember {
        mutableStateOf(
            if (openDialerPage) ATSPage.DIALER
            else ATSPage.COMMAND
        )
    }

    var menuExpanded by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(openDialerPage, initialDialNumber) {
        if (openDialerPage) {
            currentPage = ATSPage.DIALER
        }
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
                    .padding(horizontal = 20.dp, vertical = 15.dp),
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
                            .clickable { menuExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(ATSSurface)
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

                        MenuEntry("BLOCKED NUMBERS") {
                            currentPage = ATSPage.BLOCKED_NUMBERS
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
                        },
                        onBlockedNumbers = {
                            currentPage = ATSPage.BLOCKED_NUMBERS
                        }
                    )
                }

                ATSPage.INTELLIGENCE -> {
                    IntelligenceScreen()
                }

                ATSPage.DIALER -> {
                    DialerScreen(
                        initialNumber = initialDialNumber,
                        onPlaceCall = onPlaceCall,
                        onAddNumberToContacts = onAddNumberToContacts
                    )
                }

                ATSPage.CONTACTS -> {
                    ContactsScreen(
                        isPermissionGranted = contactsPermissionGranted,
                        onRequestPermission = onRequestContactsPermission,
                        contacts = contacts,
                        onOpenContact = onOpenContact,
                        onPlaceCall = onPlaceCall,
                        onAddContact = onAddContact,
                        blockedNumbers = blockedNumbers,
                        onBlockNumber = onBlockNumber,
                        onUnblockNumber = onUnblockNumber
                    )
                }

                ATSPage.HISTORY -> {
                    HistoryScreen(
                        isPermissionGranted = callLogPermissionGranted,
                        onRequestPermission = onRequestCallLogPermission,
                        callHistory = callHistory,
                        onPlaceCall = onPlaceCall,
                        onAddNumberToContacts = onAddNumberToContacts,
                        blockedNumbers = blockedNumbers,
                        onBlockNumber = onBlockNumber,
                        onUnblockNumber = onUnblockNumber
                    )
                }

                ATSPage.BLOCKED_NUMBERS -> {
                    ATSBlockedNumbersScreen(
                        blockedNumbers = blockedNumbers,
                        auditLog = blockingAuditLog,
                        databaseStatus = blockingDatabaseStatus,
                        callScreeningEnabled = callScreeningEnabled,
                        onBlockNumber = onBlockNumber,
                        onUnblockNumber = onUnblockNumber,
                        onRequestCallScreening = onRequestCallScreening,
                        onRefresh = onRefreshBlockedNumbers
                    )
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
    onHistory: () -> Unit,
    onBlockedNumbers: () -> Unit
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
            "Contact directory",
            onContacts
        )

        NavigationCard(
            "CALL HISTORY",
            "Incoming, outgoing and missed calls",
            onHistory
        )

        NavigationCard(
            "BLOCKED NUMBERS",
            "Manage blocked callers and security events",
            onBlockedNumbers
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
private fun DialerScreen(
    initialNumber: String,
    onPlaceCall: (String) -> Unit,
    onAddNumberToContacts: (String) -> Unit
) {
    var number by remember(initialNumber) {
        mutableStateOf(initialNumber)
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

        OutlinedButton(
            onClick = {
                if (number.isEmpty()) {
                    number = "+"
                }
            },
            enabled = number.isEmpty()
        ) {
            Text(
                text = "+",
                color = ATSBlue,
                fontSize = 24.sp
            )
        }

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { number += key },
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { number = number.dropLast(1) },
                enabled = number.isNotEmpty()
            ) {
                Text("DELETE")
            }

            Button(
                onClick = { onPlaceCall(number) },
                enabled = number.any { it.isDigit() }
            ) {
                Text("CALL")
            }
        }

        OutlinedButton(
            onClick = {
                onAddNumberToContacts(number)
            },
            enabled = number.any { it.isDigit() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ADD TO CONTACTS")
        }

        Text(
            text = "ATS will request default dialer status and call permission before placing a call.",
            color = ATSMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ContactsScreen(
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    contacts: List<ATSContact>,
    onOpenContact: (Long) -> Unit,
    onPlaceCall: (String) -> Unit,
    onAddContact: (String, String) -> Unit,
    blockedNumbers: List<ATSBlockedNumber>,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit
) {
    var newContactName by remember {
        mutableStateOf("")
    }

    var newContactNumber by remember {
        mutableStateOf("")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "CONTACTS",
                color = ATSBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            StatusPanel(
                "CONTACT DIRECTORY",
                if (isPermissionGranted) "ACCESS GRANTED" else "ACCESS REQUIRED",
                if (isPermissionGranted)
                    "${contacts.size} phone entries loaded from this device"
                else
                    "Permission is required to read device contacts.",
                if (isPermissionGranted) ATSGreen else ATSMuted
            )
        }

        item {
            Text(
                text = "CREATE NEW CONTACT",
                color = ATSBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = newContactName,
                onValueChange = { newContactName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true
            )

            OutlinedTextField(
                value = newContactNumber,
                onValueChange = { newContactNumber = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                singleLine = true
            )

            Button(
                onClick = {
                    onAddContact(
                        newContactName.trim(),
                        newContactNumber.trim()
                    )
                    newContactName = ""
                    newContactNumber = ""
                },
                enabled = newContactNumber.any { it.isDigit() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CREATE CONTACT")
            }
        }

        if (!isPermissionGranted) {
            item {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GRANT CONTACTS ACCESS")
                }
            }
        } else if (contacts.isEmpty()) {
            item {
                Text(
                    text = "NO CONTACTS FOUND",
                    color = ATSMuted
                )
            }
        } else {
            item {
                Text(
                    text = "SAVED CONTACTS",
                    color = ATSBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            items(
                items = contacts,
                key = { it.phoneId }
            ) { contact ->

                val isBlocked = isATSNumberBlocked(
                    contact.phoneNumber,
                    blockedNumbers
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenContact(contact.contactId)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = ATSSurface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = contact.name.ifBlank {
                                "UNKNOWN CONTACT"
                            },
                            color = ATSText,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = contact.phoneNumber,
                            color = ATSBlue,
                            fontSize = 13.sp
                        )

                        Text(
                            text = if (isBlocked) {
                                "SECURITY STATUS: BLOCKED"
                            } else {
                                "SECURITY STATUS: NOT BLOCKED"
                            },
                            color = if (isBlocked) ATSRed else ATSGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onPlaceCall(contact.phoneNumber)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CALL")
                            }

                            OutlinedButton(
                                onClick = {
                                    onOpenContact(contact.contactId)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("OPEN")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (isBlocked) {
                                    onUnblockNumber(contact.phoneNumber)
                                } else {
                                    onBlockNumber(contact.phoneNumber)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isBlocked) ATSGreen else ATSRed
                            )
                        ) {
                            Text(
                                if (isBlocked) {
                                    "UNBLOCK NUMBER"
                                } else {
                                    "BLOCK NUMBER"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    callHistory: List<ATSCallLogEntry>,
    onPlaceCall: (String) -> Unit,
    onAddNumberToContacts: (String) -> Unit,
    blockedNumbers: List<ATSBlockedNumber>,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "CALL HISTORY",
                color = ATSBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            StatusPanel(
                "DEVICE CALL LOG",
                if (isPermissionGranted) "ACCESS GRANTED" else "ACCESS REQUIRED",
                if (isPermissionGranted)
                    "${callHistory.size} recent call records loaded"
                else
                    "Permission is required to read device call history.",
                if (isPermissionGranted) ATSGreen else ATSMuted
            )
        }

        if (!isPermissionGranted) {
            item {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GRANT CALL LOG ACCESS")
                }
            }
        } else if (callHistory.isEmpty()) {
            item {
                Text(
                    text = "NO CALL RECORDS FOUND",
                    color = ATSMuted
                )
            }
        } else {
            items(
                items = callHistory,
                key = { it.id }
            ) { entry ->

                val isBlocked = isATSNumberBlocked(
                    entry.phoneNumber,
                    blockedNumbers
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ATSSurface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = entry.name.ifBlank {
                                "UNKNOWN NUMBER"
                            },
                            color = ATSText,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = entry.phoneNumber,
                            color = ATSBlue
                        )

                        Text(
                            text = "${entry.callType}  •  ${entry.date}",
                            color = ATSMuted,
                            fontSize = 11.sp
                        )

                        Text(
                            text = "Duration: ${entry.duration}",
                            color = ATSText,
                            fontSize = 11.sp
                        )

                        Text(
                            text = if (isBlocked) {
                                "SECURITY STATUS: BLOCKED"
                            } else {
                                "SECURITY STATUS: NOT BLOCKED"
                            },
                            color = if (isBlocked) ATSRed else ATSGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(
                            color = ATSBlue.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    onPlaceCall(entry.phoneNumber)
                                },
                                enabled = entry.phoneNumber.any {
                                    it.isDigit()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CALL")
                            }

                            OutlinedButton(
                                onClick = {
                                    onAddNumberToContacts(
                                        entry.phoneNumber
                                    )
                                },
                                enabled = entry.phoneNumber.any {
                                    it.isDigit()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("SAVE")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (isBlocked) {
                                    onUnblockNumber(entry.phoneNumber)
                                } else {
                                    onBlockNumber(entry.phoneNumber)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isBlocked) ATSGreen else ATSRed
                            )
                        ) {
                            Text(
                                if (isBlocked) {
                                    "UNBLOCK NUMBER"
                                } else {
                                    "BLOCK NUMBER"
                                }
                            )
                        }
                    }
                }
            }
        }
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
