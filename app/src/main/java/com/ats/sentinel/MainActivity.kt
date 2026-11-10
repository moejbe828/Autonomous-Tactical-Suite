
package com.ats.sentinel

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private var pendingOrbStart = false
    private var pendingCallNumber: String? = null

    private val orbRunning = mutableStateOf(false)

    private val initialDialNumber = mutableStateOf("")
    private val openDialerPage = mutableStateOf(false)

    // Overlay permission
    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (pendingOrbStart) {
                pendingOrbStart = false

                if (Settings.canDrawOverlays(this)) {
                    startOrbService()
                }
            }
        }

    // Default dialer role
    private val dialerRoleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (isDefaultDialer()) {
                continuePendingCall()
            } else {
                pendingCallNumber = null

                showMessage(
                    "ATS was not selected as the default dialer."
                )
            }
        }

    // CALL_PHONE runtime permission
    private val callPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                placePendingCall()
            } else {
                pendingCallNumber = null

                showMessage(
                    "Call permission was denied. No call was placed."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleDialIntent(intent)

        setContent {
            ATSNavigationApp(
                isOrbRunning = orbRunning.value,

                initialDialNumber = initialDialNumber.value,
                openDialerPage = openDialerPage.value,

                onPlaceCall = { number ->
                    requestOutgoingCall(number)
                },

                onStartOrb = {
                    requestOrbStart()
                },

                onStopOrb = {
                    stopOrbService()
                }
            )
        }
    }

    // Handle calls opened through ACTION_DIAL
    private fun handleDialIntent(source: Intent?) {

        if (source?.action != Intent.ACTION_DIAL) {
            return
        }

        val number = source.data
            ?.schemeSpecificPart
            .orEmpty()

        initialDialNumber.value = number
        openDialerPage.value = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleDialIntent(intent)
    }

    // Request default dialer role before placing a call
    private fun requestOutgoingCall(rawNumber: String) {

        val number = rawNumber.filter {
            it.isDigit() ||
                it == '+' ||
                it == '*' ||
                it == '#'
        }

        if (number.isBlank() || !number.any { it.isDigit() }) {
            showMessage("Enter a valid phone number.")
            return
        }

        pendingCallNumber = number

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingCallNumber = null

            showMessage(
                "Automatic dialer role selection requires Android 10 or newer."
            )
            return
        }

        val roleManager =
            getSystemService(RoleManager::class.java)

        if (roleManager == null ||
            !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
        ) {
            pendingCallNumber = null
            showMessage("Dialer role is not available on this device.")
            return
        }

        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {

            try {
                val requestIntent =
                    roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_DIALER
                    )

                dialerRoleLauncher.launch(requestIntent)

            } catch (error: Exception) {
                pendingCallNumber = null

                showMessage(
                    "Unable to request the default dialer role."
                )
            }

            return
        }

        continuePendingCall()
    }

    private fun isDefaultDialer(): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val roleManager =
            getSystemService(RoleManager::class.java)
                ?: return false

        return roleManager.isRoleHeld(
            RoleManager.ROLE_DIALER
        )
    }

    // Request CALL_PHONE only after the role is granted
    private fun continuePendingCall() {

        if (pendingCallNumber.isNullOrBlank()) {
            return
        }

        if (!isDefaultDialer()) {
            pendingCallNumber = null
            showMessage("ATS is not the default dialer.")
            return
        }

        if (
            checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callPermissionLauncher.launch(
                Manifest.permission.CALL_PHONE
            )
            return
        }

        placePendingCall()
    }

    // Place the call through Android Telecom
    private fun placePendingCall() {

        val number = pendingCallNumber ?: return

        if (!isDefaultDialer()) {
            pendingCallNumber = null
            showMessage("Default dialer role is required.")
            return
        }

        if (
            checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallNumber = null
            showMessage("Call permission is required.")
            return
        }

        try {
            val telecomManager =
                getSystemService(TelecomManager::class.java)
                    ?: throw IllegalStateException(
                        "Telecom service unavailable"
                    )

            val phoneUri = Uri.fromParts(
                "tel",
                number,
                null
            )

            telecomManager.placeCall(
                phoneUri,
                Bundle()
            )

            pendingCallNumber = null

        } catch (error: SecurityException) {

            pendingCallNumber = null

            showMessage(
                "Android denied the call request. Check call permissions."
            )

        } catch (error: Exception) {

            pendingCallNumber = null

            showMessage(
                "Unable to place the call: ${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // Floating Orb
    private fun requestOrbStart() {

        if (Settings.canDrawOverlays(this)) {
            startOrbService()
            return
        }

        pendingOrbStart = true

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        overlayPermissionLauncher.launch(intent)
    }

    private fun startOrbService() {

        val intent = Intent(
            this,
            TacticalOrbService::class.java
        )

        try {
            startForegroundService(intent)
            orbRunning.value = true

        } catch (_: Exception) {
            orbRunning.value = false
        }
    }

    private fun stopOrbService() {

        val intent = Intent(
            this,
            TacticalOrbService::class.java
        ).apply {
            action = TacticalOrbService.ACTION_STOP
        }

        try {
            startService(intent)
        } catch (_: Exception) {
        }

        orbRunning.value = false
    }

    override fun onResume() {
        super.onResume()

        if (Settings.canDrawOverlays(this)) {
            // Overlay permission is available.
        }
    }
}
