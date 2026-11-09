
package com.ats.sentinel

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class ATSInCallService : InCallService() {

    companion object {
        private const val TAG = "ATSInCallService"
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        Log.i(
            TAG,
            "Call connected to ATS service. State=${getCallState(call.state)}"
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        Log.i(
            TAG,
            "Call removed from ATS service. State=${getCallState(call.state)}"
        )
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
