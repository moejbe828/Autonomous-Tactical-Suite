
package com.ats.sentinel

import android.os.Bundle
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.Log
import android.view.WindowManager
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
import android.telecom.CallAudioState

private val CallBackground = Color(0xFF050B16)
private val CallSurface = Color(0xFF101D30)
private val CallBlue = Color(0xFF00BFFF)
private val CallText = Color(0xFFE8F5FF)
private val CallMuted = Color(0xFF7895B5)
private val CallGreen = Color(0xFF25C987)
private val CallRed = Color(0xFFE5485B)

object ATSCallSession {

    var currentCall by mutableStateOf<Call?>(null)
    var callState by mutableIntStateOf(Call.STATE_NEW)

    var callerName by mutableStateOf("UNKNOWN CALLER")
    var callerNumber by mutableStateOf("PRIVATE / UNAVAILABLE")

    var isMuted by mutableStateOf(false)

    var audioRoute by mutableIntStateOf(0)
    var supportedAudioRoutes by mutableIntStateOf(0)
    var isSpeakerOn by mutableStateOf(false)

    // ATS TRACK SCAN — SATELLITE INTELLIGENCE ORB
    // These states are controlled by the floating orb service.
    var isTrackScanVisible by mutableStateOf(false)
    var isTrackScanCallActive by mutableStateOf(false)

    private var trackedCall: Call? = null

    var service: ATSInCallService? = null

    private val dtmfHandler = Handler(Looper.getMainLooper())
    private var pendingDtmfStop: Runnable? = null

    fun toggleTrackScan() {
        if (isTrackScanCallActive) {
            isTrackScanVisible = !isTrackScanVisible
        }
    }

    private fun resetTrackScan() {
        isTrackScanVisible = false
        isTrackScanCallActive = false
        trackedCall = null
    }

    fun update(call: Call?) {
        currentCall = call

        if (call == null) {
            stopPendingDtmf()
            callState = Call.STATE_DISCONNECTED
            callerName = "NO ACTIVE CALL"
            callerNumber = "—"
            isMuted = false
            isSpeakerOn = false
            audioRoute = 0
            supportedAudioRoutes = 0
            resetTrackScan()
            return
        }

        callState = call.state

        if (call.state == Call.STATE_DISCONNECTED) {
            resetTrackScan()
        } else {
            if (trackedCall !== call) {
                trackedCall = call
                isTrackScanCallActive = true
                isTrackScanVisible = true
            }
        }

        val details = call.details
        val name = details.callerDisplayName
        val number = details.handle?.schemeSpecificPart

        callerName = name?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN CALLER"

        callerNumber = number?.takeIf { it.isNotBlank() }
            ?: "PRIVATE / UNAVAILABLE"
    }

    fun updateAudioState(route: Int, supportedRoutes: Int) {
        audioRoute = route
        supportedAudioRoutes = supportedRoutes
        isSpeakerOn = route == CallAudioState.ROUTE_SPEAKER
    }

    fun isRouteSupported(route: Int): Boolean {
        return (supportedAudioRoutes and route) != 0
    }

    fun selectAudioRoute(route: Int) {
        if (!isRouteSupported(route)) {
            Log.w("ATS_AUDIO", "Requested audio route is unavailable")
            return
        }

        try {
            service?.setAudioRoute(route)
            Log.i("ATS_AUDIO", "Audio route requested: $route")
        } catch (error: Exception) {
            Log.e("ATS_AUDIO", "Unable to change audio route", error)
        }
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
        stopPendingDtmf()
        currentCall?.disconnect()
    }

    fun toggleMute() {
        val next = !isMuted
        service?.setMuted(next)
        isMuted = next
    }

    fun toggleHold() {
        val call = currentCall ?: return

        try {
            when (call.state) {
                Call.STATE_ACTIVE -> {
                    stopPendingDtmf()
                    call.hold()
                    Log.i("ATS_CALL", "Hold requested")
                }

                Call.STATE_HOLDING -> {
                    call.unhold()
                    Log.i("ATS_CALL", "Resume requested")
                }
            }
        } catch (error: Exception) {
            Log.e("ATS_CALL", "Unable to change hold state", error)
        }
    }

    fun sendDtmfTone(digit: Char) {
        if (digit !in "0123456789*#") return

        val call = currentCall ?: return
        if (call.state != Call.STATE_ACTIVE) return

        try {
            stopPendingDtmf()
            call.playDtmfTone(digit)

            val stopAction = Runnable {
                try {
                    call.stopDtmfTone()
                } catch (error: Exception) {
                    Log.e("ATS_DTMF", "Unable to stop DTMF tone", error)
                }
                pendingDtmfStop = null
            }

            pendingDtmfStop = stopAction
            dtmfHandler.postDelayed(stopAction, 150L)

            Log.i("ATS_DTMF", "DTMF digit sent")
        } catch (error: Exception) {
            Log.e("ATS_DTMF", "Unable to send DTMF digit", error)
        }
    }

    private fun stopPendingDtmf() {
        pendingDtmfStop?.let { action ->
            dtmfHandler.removeCallbacks(action)
        }

        pendingDtmfStop = null

        try {
            currentCall?.stopDtmfTone()
        } catch (error: Exception) {
            Log.e("ATS_DTMF", "Unable to stop active tone", error)
        }
    }
}

class ATSCallActivity : ComponentActivity() {

    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var proximityRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenDisplay()
        initializeProximityWakeLock()

        setContent {
            ATSCallScreen(
                onAnswer = { ATSCallSession.answer() },
                onReject = { ATSCallSession.reject() },
                onEnd = { ATSCallSession.end() },
                onMute = { ATSCallSession.toggleMute() },
                onAudioRoute = { route ->
                    ATSCallSession.selectAudioRoute(route)
                },
                onClose = { finish() },
                onProximityChanged = { enabled ->
                    setProximityEnabled(enabled)
                }
            )
        }
    }

    private fun configureLockScreenDisplay() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }

            Log.i("ATS_LOCKSCREEN", "Lock screen display configured")
        } catch (error: Exception) {
            Log.e("ATS_LOCKSCREEN", "Unable to configure lock screen", error)
        }
    }

    private fun initializeProximityWakeLock() {
        try {
            val powerManager =
                getSystemService(POWER_SERVICE) as PowerManager

            val sensorManager =
                getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

            val proximitySensor = sensorManager.getDefaultSensor(
                android.hardware.Sensor.TYPE_PROXIMITY
            )

            if (proximitySensor == null) {
                Log.w("ATS_PROXIMITY", "Proximity sensor not available")
                return
            }

            val wakeLockLevel =
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK

            if (!powerManager.isWakeLockLevelSupported(wakeLockLevel)) {
                Log.w("ATS_PROXIMITY", "Proximity wake lock not supported")
                return
            }

            proximityWakeLock = powerManager.newWakeLock(
                wakeLockLevel,
                "ATS::ProximityWakeLock"
            )

            proximityWakeLock?.setReferenceCounted(false)

            Log.i("ATS_PROXIMITY", "Proximity sensor initialized")
        } catch (error: Exception) {
            Log.e("ATS_PROXIMITY", "Initialization failed", error)
        }
    }

    private fun setProximityEnabled(enabled: Boolean) {
        proximityRequested = enabled

        if (!lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.RESUMED
            )
        ) {
            return
        }

        try {
            if (enabled) {
                val wakeLock = proximityWakeLock ?: return

                if (!wakeLock.isHeld) {
                    wakeLock.acquire()
                    Log.i("ATS_PROXIMITY", "Proximity enabled")
                }
            } else {
                releaseProximityWakeLock()
            }
        } catch (error: Exception) {
            Log.e("ATS_PROXIMITY", "Unable to change proximity state", error)
        }
    }

    private fun releaseProximityWakeLock() {
        try {
            proximityWakeLock?.let { wakeLock ->
                if (wakeLock.isHeld) {
                    wakeLock.release()
                    Log.i("ATS_PROXIMITY", "Proximity released")
                }
            }
        } catch (error: Exception) {
            Log.e("ATS_PROXIMITY", "Wake lock release failed", error)
        }
    }

    override fun onResume() {
        super.onResume()

        if (proximityRequested) {
            setProximityEnabled(true)
        }
    }

    override fun onPause() {
        releaseProximityWakeLock()
        super.onPause()
    }

    override fun onDestroy() {
        proximityRequested = false
        releaseProximityWakeLock()
        proximityWakeLock = null
        super.onDestroy()
    }
}

@Composable
private fun ATSCallScreen(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onMute: () -> Unit,
    onAudioRoute: (Int) -> Unit,
    onClose: () -> Unit,
    onProximityChanged: (Boolean) -> Unit
) {
    val state = ATSCallSession.callState
    val name = ATSCallSession.callerName
    val number = ATSCallSession.callerNumber
    val muted = ATSCallSession.isMuted

    val audioRoute = ATSCallSession.audioRoute
    val supportedRoutes = ATSCallSession.supportedAudioRoutes

    var keypadVisible by remember { mutableStateOf(false) }
    var dtmfDisplay by remember { mutableStateOf("") }

    val isRinging = state == Call.STATE_RINGING
    val isActive = state == Call.STATE_ACTIVE
    val isHolding = state == Call.STATE_HOLDING

    val isConnecting =
        state == Call.STATE_CONNECTING ||
        state == Call.STATE_DIALING

    val proximityShouldBeActive = isActive || isHolding

    DisposableEffect(proximityShouldBeActive) {
        onProximityChanged(proximityShouldBeActive)
        onDispose { onProximityChanged(false) }
    }

    val status = when {
        isRinging -> "INCOMING CALL"
        isActive -> "CALL CONNECTED"
        isConnecting -> "CONNECTING"
        isHolding -> "CALL ON HOLD"
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER
            Column(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
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

            // CALL DETAILS
            // ATS TRACK SCAN is now handled by the floating satellite overlay.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .background(CallSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ATS",
                        color = CallBlue,
                        fontSize = 28.sp,
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

            // FIXED CALL CONTROLS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isActive || isHolding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if ((supportedRoutes and CallAudioState.ROUTE_EARPIECE) != 0) {
                            CallOption(
                                label = "EARPIECE",
                                active = audioRoute == CallAudioState.ROUTE_EARPIECE,
                                onClick = {
                                    onAudioRoute(CallAudioState.ROUTE_EARPIECE)
                                }
                            )
                        }

                        if ((supportedRoutes and CallAudioState.ROUTE_SPEAKER) != 0) {
                            CallOption(
                                label = "SPEAKER",
                                active = audioRoute == CallAudioState.ROUTE_SPEAKER,
                                onClick = {
                                    onAudioRoute(CallAudioState.ROUTE_SPEAKER)
                                }
                            )
                        }

                        if ((supportedRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0) {
                            CallOption(
                                label = "BLUETOOTH",
                                active = audioRoute == CallAudioState.ROUTE_BLUETOOTH,
                                onClick = {
                                    onAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                                }
                            )
                        }
                    }

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
                            label = if (keypadVisible) "HIDE" else "KEYPAD",
                            active = keypadVisible,
                            onClick = {
                                keypadVisible = !keypadVisible
                                dtmfDisplay = ""
                            }
                        )

                        CallOption(
                            label = if (isHolding) "RESUME" else "HOLD",
                            active = isHolding,
                            onClick = {
                                ATSCallSession.toggleHold()
                            }
                        )
                    }

                    if (keypadVisible) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    CallSurface,
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (dtmfDisplay.isEmpty()) {
                                    "ENTER DTMF DIGITS"
                                } else {
                                    dtmfDisplay
                                },
                                color = CallBlue,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            val keypadRows = listOf(
                                listOf('1', '2', '3'),
                                listOf('4', '5', '6'),
                                listOf('7', '8', '9'),
                                listOf('*', '0', '#')
                            )

                            keypadRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEach { digit ->
                                        Button(
                                            onClick = {
                                                ATSCallSession.sendDtmfTone(digit)
                                                dtmfDisplay =
                                                    (dtmfDisplay + digit).takeLast(16)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 5.dp)
                                                .height(42.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = CallBackground,
                                                contentColor = CallText
                                            )
                                        ) {
                                            Text(
                                                text = digit.toString(),
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "DTMF • TELECOM",
                                color = CallMuted,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }
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
            .height(54.dp)
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
