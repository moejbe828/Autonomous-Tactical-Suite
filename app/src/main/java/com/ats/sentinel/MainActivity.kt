
package com.ats.sentinel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private var pendingOrbStart = false

    private val orbRunning = mutableStateOf(false)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ATSNavigationApp(
                isOrbRunning = orbRunning.value,

                onStartOrb = {
                    requestOrbStart()
                },

                onStopOrb = {
                    stopOrbService()
                }
            )
        }
    }

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
