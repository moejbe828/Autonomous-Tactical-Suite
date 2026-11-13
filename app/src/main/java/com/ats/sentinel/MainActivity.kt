
package com.ats.sentinel

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var pendingOrbStart = false
    private var pendingCallNumber: String? = null

    private val orbRunning = mutableStateOf(false)
    private val initialDialNumber = mutableStateOf("")
    private val openDialerPage = mutableStateOf(false)

    private val contactsPermissionGranted = mutableStateOf(false)
    private val callLogPermissionGranted = mutableStateOf(false)

    private val contacts =
        mutableStateOf<List<ATSContact>>(emptyList())

    private val callHistory =
        mutableStateOf<List<ATSCallLogEntry>>(emptyList())

    private val deviceDataExecutor =
        Executors.newSingleThreadExecutor()

    // ---------------------------------------------------------
    // OVERLAY PERMISSION
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // DEFAULT DIALER ROLE
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // CALL SCREENING ROLE
    // ---------------------------------------------------------

    private val callScreeningRoleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (isCallScreeningRoleHeld()) {
                showMessage(
                    "ATS CALL SCREENING ENABLED."
                )
            } else {
                showMessage(
                    "Call screening role was not granted. Blocking remains inactive."
                )
            }
        }

    // ---------------------------------------------------------
    // CALL PERMISSION
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // CONTACTS PERMISSION
    // ---------------------------------------------------------

    private val contactsPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            contactsPermissionGranted.value = granted

            if (granted) {
                showMessage("Contacts permission granted.")
                loadContacts()
            } else {
                showMessage("Contacts permission was denied.")
            }
        }

    // ---------------------------------------------------------
    // CALL LOG PERMISSION
    // ---------------------------------------------------------

    private val callLogPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            callLogPermissionGranted.value = granted

            if (granted) {
                showMessage("Call history permission granted.")
                loadCallHistory()
            } else {
                showMessage("Call history permission was denied.")
            }
        }

    // ---------------------------------------------------------
    // ACTIVITY CREATION
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshDataPermissions()
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
                },

                contactsPermissionGranted =
                    contactsPermissionGranted.value,

                callLogPermissionGranted =
                    callLogPermissionGranted.value,

                onRequestContactsPermission = {
                    requestContactsPermission()
                },

                onRequestCallLogPermission = {
                    requestCallLogPermission()
                },

                contacts = contacts.value,
                callHistory = callHistory.value,

                onOpenContact = { contactId ->
                    openContact(contactId)
                },

                onAddContact = { name, number ->
                    addContact(name, number)
                },

                onAddNumberToContacts = { number ->
                    addContact("", number)
                },

                onManageBlockedNumbers = {
                    requestCallScreeningRole()
                }
            )
        }
    }

    // ---------------------------------------------------------
    // DEVICE DATA
    // ---------------------------------------------------------

    private fun refreshDataPermissions() {

        contactsPermissionGranted.value =
            checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED

        callLogPermissionGranted.value =
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) ==
                    PackageManager.PERMISSION_GRANTED

        if (contactsPermissionGranted.value) {
            loadContacts()
        } else {
            contacts.value = emptyList()
        }

        if (callLogPermissionGranted.value) {
            loadCallHistory()
        } else {
            callHistory.value = emptyList()
        }
    }

    private fun loadContacts() {

        if (
            checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            contactsPermissionGranted.value = false
            contacts.value = emptyList()
            return
        }

        deviceDataExecutor.execute {
            val result =
                ATSDeviceDataReader.readContacts(contentResolver)

            runOnUiThread {
                if (
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    contacts.value = result
                }
            }
        }
    }

    private fun loadCallHistory() {

        if (
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callLogPermissionGranted.value = false
            callHistory.value = emptyList()
            return
        }

        deviceDataExecutor.execute {
            val result =
                ATSDeviceDataReader.readCallHistory(contentResolver)

            runOnUiThread {
                if (
                    checkSelfPermission(Manifest.permission.READ_CALL_LOG) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    callHistory.value = result
                }
            }
        }
    }

    private fun requestContactsPermission() {

        if (
            checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            contactsPermissionGranted.value = true
            loadContacts()
            return
        }

        contactsPermissionLauncher.launch(
            Manifest.permission.READ_CONTACTS
        )
    }

    private fun requestCallLogPermission() {

        if (
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            callLogPermissionGranted.value = true
            loadCallHistory()
            return
        }

        callLogPermissionLauncher.launch(
            Manifest.permission.READ_CALL_LOG
        )
    }

    // ---------------------------------------------------------
    // CONTACT ACTIONS
    // ---------------------------------------------------------

    private fun openContact(contactId: Long) {

        if (contactId <= 0L) {
            showMessage("Invalid contact.")
            return
        }

        try {
            val contactUri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_URI,
                contactId.toString()
            )

            val intent = Intent(
                Intent.ACTION_VIEW,
                contactUri
            )

            startActivity(intent)

        } catch (_: ActivityNotFoundException) {
            showMessage("No contacts application is available.")
        } catch (error: Exception) {
            showMessage(
                "Unable to open contact: ${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun addContact(
        name: String,
        rawNumber: String
    ) {

        val number = rawNumber.trim()

        if (number.isBlank()) {
            showMessage("Enter a phone number first.")
            return
        }

        try {
            val intent = Intent(
                Intent.ACTION_INSERT
            ).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE

                if (name.isNotBlank()) {
                    putExtra(
                        ContactsContract.Intents.Insert.NAME,
                        name
                    )
                }

                putExtra(
                    ContactsContract.Intents.Insert.PHONE,
                    number
                )
            }

            startActivity(intent)

        } catch (_: ActivityNotFoundException) {
            showMessage("No contacts application is available.")
        } catch (error: Exception) {
            showMessage(
                "Unable to create contact: ${error.message ?: "Unknown error"}"
            )
        }
    }

    // ---------------------------------------------------------
    // CALL SCREENING ROLE MANAGEMENT
    // ---------------------------------------------------------

    private fun isCallScreeningRoleHeld(): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val roleManager =
            getSystemService(RoleManager::class.java)
                ?: return false

        return roleManager.isRoleHeld(
            RoleManager.ROLE_CALL_SCREENING
        )
    }

    private fun requestCallScreeningRole() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showMessage(
                "ATS Call Screening requires Android 10 or newer."
            )
            return
        }

        val roleManager =
            getSystemService(RoleManager::class.java)

        if (roleManager == null) {
            showMessage("Android Role Manager is unavailable.")
            return
        }

        if (
            !roleManager.isRoleAvailable(
                RoleManager.ROLE_CALL_SCREENING
            )
        ) {
            showMessage(
                "Call screening role is unavailable on this device."
            )
            return
        }

        if (
            roleManager.isRoleHeld(
                RoleManager.ROLE_CALL_SCREENING
            )
        ) {
            showMessage(
                "ATS Call Screening is already enabled."
            )
            return
        }

        try {
            val requestIntent =
                roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_CALL_SCREENING
                )

            callScreeningRoleLauncher.launch(requestIntent)

        } catch (error: Exception) {
            showMessage(
                "Unable to request call screening role: " +
                        (error.message ?: "Unknown error")
            )
        }
    }

    // ---------------------------------------------------------
    // BLOCKED NUMBERS
    // ---------------------------------------------------------

    private fun openBlockedNumbersSettings(number: String) {

        val cleanNumber = number.trim()

        try {
            val telecomManager =
                getSystemService(TelecomManager::class.java)
                    ?: throw IllegalStateException(
                        "Telecom service unavailable"
                    )

            val intent =
                telecomManager.createManageBlockedNumbersIntent()

            startActivity(intent)

            if (cleanNumber.isNotBlank()) {
                showMessage(
                    "Review the blocked numbers screen for $cleanNumber."
                )
            }

        } catch (_: ActivityNotFoundException) {
            showMessage(
                "Blocked-number management is not available on this device."
            )
        } catch (error: Exception) {
            showMessage(
                "Unable to open blocked-number settings: ${error.message ?: "Unknown error"}"
            )
        }
    }

    // ---------------------------------------------------------
    // DIAL INTENTS
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // OUTGOING CALLS
    // ---------------------------------------------------------

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

        if (
            roleManager == null ||
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

            } catch (_: Exception) {
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
            checkSelfPermission(Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callPermissionLauncher.launch(
                Manifest.permission.CALL_PHONE
            )
            return
        }

        placePendingCall()
    }

    private fun placePendingCall() {

        val number = pendingCallNumber ?: return

        if (!isDefaultDialer()) {
            pendingCallNumber = null
            showMessage("Default dialer role is required.")
            return
        }

        if (
            checkSelfPermission(Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
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

        } catch (_: SecurityException) {

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

    // ---------------------------------------------------------
    // MESSAGES
    // ---------------------------------------------------------

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------------------------------------------------------
    // FLOATING ORB
    // ---------------------------------------------------------

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
            showMessage("Unable to start ATS Orb.")
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

    // ---------------------------------------------------------
    // LIFECYCLE
    // ---------------------------------------------------------

    override fun onResume() {
        super.onResume()
        refreshDataPermissions()
    }

    override fun onDestroy() {
        deviceDataExecutor.shutdownNow()
        super.onDestroy()
    }
}
