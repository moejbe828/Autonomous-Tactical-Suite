
package com.ats.sentinel

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class ATSCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val response = try {
            val phoneNumber = callDetails.handle
                ?.schemeSpecificPart

            if (phoneNumber.isNullOrBlank()) {
                allowCall(callDetails)
                return
            }

            val blocked = ATSBlockedNumbersStore.isBlocked(
                applicationContext,
                phoneNumber
            )

            if (blocked) {
                Log.i(
                    TAG,
                    "Blocked incoming call: ${maskNumber(phoneNumber)}"
                )

                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            } else {
                CallResponse.Builder().build()
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Screening database verification failed. Allowing call.",
                e
            )

            CallResponse.Builder().build()
        }

        try {
            respondToCall(callDetails, response)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to submit call screening response.", e)
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        try {
            respondToCall(
                callDetails,
                CallResponse.Builder().build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to allow call.", e)
        }
    }

    private fun maskNumber(number: String): String {
        val digits = number.filter { it.isDigit() }

        return if (digits.length > 4) {
            "****" + digits.takeLast(4)
        } else {
            "****"
        }
    }

    companion object {
        private const val TAG = "ATS_CALL_SCREENING"
    }
}
