package com.patakihara.garageremote

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class GarageTileService : TileService() {
    abstract val slot: Int

    override fun onStartListening() {
        super.onStartListening()
        val a = getTileAssignment(this, slot)
        qsTile?.apply {
            if (a != null) {
                state = Tile.STATE_INACTIVE
                label = a.name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Tap to open"
            } else {
                state = Tile.STATE_UNAVAILABLE
                label = "Not set up"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Open the app"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val a = getTileAssignment(this, slot) ?: run {
            launchApp(); return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            launchApp(); return
        }
        val hasAnswer = checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) ==
                PackageManager.PERMISSION_GRANTED
        if (isLocked) {
            unlockAndRun { openGarage(this, a.phoneNumber, hasAnswer) }
        } else {
            // Collapse the panel and start the call
            val callIntent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:${a.phoneNumber}"))
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, slot, callIntent, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(callIntent)
            }
            scheduleHangupAndCleanup(this, a.phoneNumber, hasAnswer)
        }
    }

    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class GarageTile1 : GarageTileService() { override val slot = 1 }
class GarageTile2 : GarageTileService() { override val slot = 2 }
class GarageTile3 : GarageTileService() { override val slot = 3 }
class GarageTile4 : GarageTileService() { override val slot = 4 }
