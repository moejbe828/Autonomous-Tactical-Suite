
package com.ats.sentinel

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

class ATSInCallService : InCallService() {

    companion object {
        private const val TAG = "ATSInCallService"
    }

    private var activeCall: Call? = null

    private val callCallback = object : Call.Callback() {

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)

            if (call === activeCall) {
                ATSCallSession.update(call)

                Log.i(
                    TAG,
                    "Call state changed: ${getCallState(state)}"
                )

                if (state == Call.STATE_ACTIVE) {
                    ensureDefaultAudioRoute()
                    synchronizeAudioState()
                }
            }
        }

        override fun onDetailsChanged(
            call: Call,
            details: Call.Details
        ) {
            super.onDetailsChanged(call, details)

            if (call === activeCall) {
                ATSCallSession.update(call)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        ATSCallSession.service = this

        Log.i(TAG, "ATS InCallService created")

        synchronizeAudioState()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        activeCall = call
        call.registerCallback(callCallback)

        ATSCallSession.update(call)

        Log.i(
            TAG,
            "Call added: ${getCallState(call.state)}"
        )

        ensureDefaultAudioRoute()
        synchronizeAudioState()

        val intent = Intent(
            this,
            ATSCallActivity::class.java
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        try {
            startActivity(intent)
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to open ATS call screen",
                error
            )
        }
    }

    /**
     * Synchronize the actual Android audio route
     * and supported routes with the ATS interface.
     */
    override fun onCallAudioStateChanged(
        audioState: CallAudioState
    ) {
        super.onCallAudioStateChanged(audioState)

        ATSCallSession.updateAudioState(
            audioState.route,
            audioState.supportedRouteMask
        )

        Log.i(
            TAG,
            "Audio route changed: ${getAudioRouteName(audioState.route)}"
        )

        Log.i(
            TAG,
            "Supported audio routes: ${getSupportedRoutes(audioState.supportedRouteMask)}"
        )
    }

    /**
     * Read the current Android audio state.
     */
    private fun synchronizeAudioState() {
        try {
            val audioState = callAudioState ?: return

            ATSCallSession.updateAudioState(
                audioState.route,
                audioState.supportedRouteMask
            )

            Log.i(
                TAG,
                "Audio state synchronized: ${getAudioRouteName(audioState.route)}"
            )

        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to synchronize audio state",
                error
            )
        }
    }

    /**
     * Keep the earpiece as the default route when available.
     * Do not override an already connected Bluetooth
     * or wired headset.
     */
    private fun ensureDefaultAudioRoute() {
        try {
            val audioState = callAudioState ?: return

            val earpieceAvailable =
                (audioState.supportedRouteMask and
                    CallAudioState.ROUTE_EARPIECE) != 0

            val externalHeadsetActive =
                audioState.route == CallAudioState.ROUTE_BLUETOOTH ||
                audioState.route == CallAudioState.ROUTE_WIRED_HEADSET

            val speakerCurrentlyActive =
                audioState.route == CallAudioState.ROUTE_SPEAKER

            if (
                earpieceAvailable &&
                speakerCurrentlyActive &&
                !externalHeadsetActive
            ) {
                setAudioRoute(
                    CallAudioState.ROUTE_EARPIECE
                )

                Log.i(
                    TAG,
                    "Default audio route requested: EARPIECE"
                )
            }

        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to set default audio route",
                error
            )
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        call.unregisterCallback(callCallback)

        if (call === activeCall) {
            activeCall = null
            ATSCallSession.update(null)
        }

        Log.i(
            TAG,
            "Call removed: ${getCallState(call.state)}"
        )
    }

    override fun onDestroy() {
        ATSCallSession.service = null

        super.onDestroy()

        Log.i(TAG, "ATS InCallService destroyed")
    }

    private fun getAudioRouteName(route: Int): String {
        return when (route) {
            CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
            CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
            CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
            CallAudioState.ROUTE_WIRED_HEADSET -> "WIRED_HEADSET"
            else -> "UNKNOWN"
        }
    }

    private fun getSupportedRoutes(routes: Int): String {
        val available = mutableListOf<String>()

        if (
            (routes and CallAudioState.ROUTE_EARPIECE) != 0
        ) {
            available.add("EARPIECE")
        }

        if (
            (routes and CallAudioState.ROUTE_SPEAKER) != 0
        ) {
            available.add("SPEAKER")
        }

        if (
            (routes and CallAudioState.ROUTE_BLUETOOTH) != 0
        ) {
            available.add("BLUETOOTH")
        }

        if (
            (routes and CallAudioState.ROUTE_WIRED_HEADSET) != 0
        ) {
            available.add("WIRED_HEADSET")
        }

        return available.joinToString(", ")
    }

    private fun getCallState(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            Call.STATE_PULLING_CALL -> "PULLING_CALL"
            else -> "UNKNOWN"
        }
    }
}
