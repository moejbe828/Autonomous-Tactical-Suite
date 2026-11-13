package com.ats.sentinel

import androidx.compose.foundation.background
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

private val BlockBackground = Color(0xFF050B16)
private val BlockSurface = Color(0xFF101D30)
private val BlockBlue = Color(0xFF00BFFF)
private val BlockText = Color(0xFFE8F5FF)
private val BlockMuted = Color(0xFF7895B5)
private val BlockRed = Color(0xFFFF5364)
private val BlockGreen = Color(0xFF35D69A)

@Composable
fun ATSBlockedNumbersScreen(
    blockedNumbers: List<ATSBlockedNumber>,
    auditLog: List<ATSBlockingAuditEvent>,
    databaseStatus: String,
    callScreeningEnabled: Boolean,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit,
    onRequestCallScreening: () -> Unit,
    onRefresh: () -> Unit
) {
    var newNumber by remember {
        mutableStateOf("")
    }

    var showAudit by remember {
        mutableStateOf(false)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BlockBackground)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "ATS BLOCKING CENTER",
                color = BlockBlue,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "SECURE NUMBER MANAGEMENT",
                color = BlockMuted,
                fontSize = 10.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = BlockSurface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CALL SCREENING",
                        color = BlockMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (callScreeningEnabled)
                            "PROTECTION ROLE ACTIVE"
                        else
                            "PROTECTION ROLE NOT ENABLED",
                        color = if (callScreeningEnabled)
                            BlockGreen else BlockRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (callScreeningEnabled)
                            "ATS can screen supported incoming calls."
                        else
                            "Enable the Android Call Screening role to activate call rejection.",
                        color = BlockMuted,
                        fontSize = 11.sp
                    )

                    if (!callScreeningEnabled) {
                        Button(
                            onClick = onRequestCallScreening,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ENABLE CALL SCREENING")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = BlockSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MANUAL NUMBER BLOCKING",
                        color = BlockBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Phone number")
                        },
                        placeholder = {
                            Text("+201xxxxxxxxx")
                        },
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            onBlockNumber(newNumber.trim())
                            newNumber = ""
                        },
                        enabled = newNumber.any { it.isDigit() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BLOCK NUMBER")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLOCKED NUMBERS (${blockedNumbers.size})",
                    color = BlockBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                TextButton(onClick = onRefresh) {
                    Text("REFRESH")
                }
            }
        }

        if (blockedNumbers.isEmpty()) {
            item {
                Text(
                    text = "NO BLOCKED NUMBERS",
                    color = BlockMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(
                items = blockedNumbers,
                key = { it.number }
            ) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = BlockSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = entry.number,
                                color = BlockText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "BLOCKED",
                                color = BlockRed,
                                fontSize = 10.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onUnblockNumber(entry.number)
                            }
                        ) {
                            Text(
                                text = "UNBLOCK",
                                color = BlockRed
                            )
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(
                color = BlockBlue.copy(alpha = 0.25f)
            )

            TextButton(
                onClick = {
                    showAudit = !showAudit
                }
            ) {
                Text(
                    if (showAudit)
                        "HIDE SECURITY AUDIT"
                    else
                        "SHOW SECURITY AUDIT"
                )
            }
        }

        if (showAudit) {
            if (auditLog.isEmpty()) {
                item {
                    Text(
                        text = "NO AUDIT EVENTS",
                        color = BlockMuted
                    )
                }
            } else {
                items(
                    items = auditLog,
                    key = {
                        "${it.timestamp}_${it.action}_${it.maskedNumber}"
                    }
                ) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = BlockSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = event.action,
                                color = BlockBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )

                            Text(
                                text = event.maskedNumber,
                                color = BlockText,
                                fontSize = 12.sp
                            )

                            Text(
                                text = "Source: ${event.source}",
                                color = BlockMuted,
                                fontSize = 10.sp
                            )

                            Text(
                                text = "Time: ${event.timestamp}",
                                color = BlockMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = BlockSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "ENCRYPTED DATABASE STATUS",
                        color = BlockBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    Text(
                        text = databaseStatus,
                        color = BlockGreen,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
