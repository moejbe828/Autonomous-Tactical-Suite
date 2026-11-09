
package com.ats.sentinel

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.telecom.Call

private val CallBackground = Color(0xFF050B16)
private val CallSurface = Color(0xFF101D30)
private val CallBlue = Color(0xFF00BFFF)
private val CallText = Color(0xFFE8F5FF)
private val CallMuted = Color(0xFF7895B5)
private val CallGreen = Color(0xFF25C987)
private val CallRed = Color(0xFFE5485B)

/**
 * Shared state between ATSInCallService and ATSCallActivity.
 * Both run inside the same ATS application process.
 */
object ATSCallSession {
    var currentCall by mutableStateOf<Call?>(null)
    var callState by mutableIntStateOf(Call.STATE_NEW)
    var callerName by mutableStateOf("UNKNOWN CALLER")
    var callerNumber by mutableStateOf("PRIVATE / UNAVAILABLE")
    var isMuted by mutableStateOf(false)
    var isSpeakerOn by mutableStateOf(false)

    var service: ATSInCallService? = null

    fun update(call: Call?) {
        currentCall = call

        if (call == null) {
            callState = Call.STATE_DISCONNECTED
            callerName = "NO ACTIVE CALL"
            callerNumber = "—"
            isMuted = false
            isSpeakerOn = false
            return
        }

        callState = call.state

        val details = call.details
        val name = details.callerDisplayName
        val number = details.handle?.schemeSpecificPart

        callerName = name?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN CALLER"

        callerNumber = number?.takeIf { it.isNotBlank() }
            ?: "PRIVATE / UNAVAILABLE"
    }

    fun answer() {
        currentCall?.answer(
            android.telecom.VideoProfile.STATE_AUDIO_ONLY
        )
    }

    fun reject() {
        currentCall?.reject(false, null)
    }

    fun end() {
        currentCall?.disconnect()
    }

    fun toggleMute() {
        val next = !isMuted
        service?.setMuted(next)
        isMuted = next
    }

    fun toggleSpeaker() {
        val next = !isSpeakerOn
        service?.setAudioRoute(
            if (next) {
                android.telecom.CallAudioState.ROUTE_SPEAKER
            } else {
                android.telecom.CallAudioState.ROUTE_EARPIECE
            }
        )
        isSpeakerOn = next
    }
}

class ATSCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ATSCallScreen(
                onAnswer = { ATSCallSession.answer() },
                onReject = { ATSCallSession.reject() },
                onEnd = { ATSCallSession.end() },
                onMute = { ATSCallSession.toggleMute() },
                onSpeaker = { ATSCallSession.toggleSpeaker() },
                onClose = { finish() }
            )
        }
    }
}

@Composable
private fun ATSCallScreen(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onClose: () -> Unit
) {
    val state = ATSCallSession.callState
    val name = ATSCallSession.callerName
    val number = ATSCallSession.callerNumber
    val muted = ATSCallSession.isMuted
    val speaker = ATSCallSession.isSpeakerOn

    val isRinging = state == Call.STATE_RINGING
    val isActive = state == Call.STATE_ACTIVE
    val isConnecting =
        state == Call.STATE_CONNECTING ||
        state == Call.STATE_DIALING

    val status = when {
        isRinging -> "INCOMING CALL"
        isActive -> "CALL CONNECTED"
        isConnecting -> "CONNECTING"
        state == Call.STATE_HOLDING -> "CALL ON HOLD"
        state == Call.STATE_DISCONNECTED -> "CALL ENDED"
        else -> "CALL STATUS"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CallBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ATS",
                    color = CallBlue,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                )

                Text(
                    text = "TACTICAL COMMUNICATIONS",
                    color = CallMuted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .background(CallSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ATS",
                        color = CallBlue,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = name,
                    color = CallText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = number,
                    color = CallMuted,
                    fontSize = 14.sp
                )

                Text(
                    text = status,
                    color = if (isActive) CallGreen else CallBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {

                if (isActive || state == Call.STATE_HOLDING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallOption(
                            label = if (muted) "UNMUTE" else "MUTE",
                            active = muted,
                            onClick = onMute
                        )

                        CallOption(
                            label = if (speaker) "SPEAKER ON" else "SPEAKER",
                            active = speaker,
                            onClick = onSpeaker
                        )
                    }

                    CallActionButton(
                        label = "END CALL",
                        color = CallRed,
                        onClick = onEnd
                    )
                } else if (isRinging) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallActionButton(
                            label = "DECLINE",
                            color = CallRed,
                            onClick = onReject
                        )

                        CallActionButton(
                            label = "ANSWER",
                            color = CallGreen,
                            onClick = onAnswer
                        )
                    }
                } else if (isConnecting) {
                    CallActionButton(
                        label = "CANCEL CALL",
                        color = CallRed,
                        onClick = onEnd
                    )
                } else {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CallSurface
                        )
                    ) {
                        Text("CLOSE")
                    }
                }

                Text(
                    text = "ATS CALL INTERFACE • ANDROID TELECOM",
                    color = CallMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CallOption(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (active) CallBlue else CallText
        )
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CallActionButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(58.dp)
            .widthIn(min = 130.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
