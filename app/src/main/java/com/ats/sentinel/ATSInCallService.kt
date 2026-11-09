
package com.ats.sentinel

import android.content.Intent
import android.telecom.Call
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
                Log.i(TAG, "Call state changed: ${getCallState(state)}")
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

        val intent = Intent(this, ATSCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        try {
            startActivity(intent)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open ATS call screen", error)
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
