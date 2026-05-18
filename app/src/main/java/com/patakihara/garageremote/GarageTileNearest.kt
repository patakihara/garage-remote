package com.patakihara.garageremote

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GarageTileNearest : TileService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    override fun onTileAdded() { refreshTile() }
    override fun onStartListening() { refreshTile() }

    private fun refreshTile() {
        qsTile?.apply {
            label = "Nearest Garage"
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Tap to open"
            updateTile()
        }
    }

    override fun onClick() {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            launchApp(); return
        }
        unlockAndRun {
            scope.launch {
                val garages = GarageRepository(applicationContext).garages.first()
                if (garages.isEmpty()) { launchApp(); return@launch }

                val withLocation = garages.filter { it.latitude != null && it.longitude != null }
                val nearest = if (withLocation.isNotEmpty()) {
                    val loc = oneShortLocation()
                    if (loc != null) {
                        withLocation.minByOrNull { g ->
                            FloatArray(1).also {
                                Location.distanceBetween(
                                    loc.latitude, loc.longitude,
                                    g.latitude!!, g.longitude!!, it,
                                )
                            }[0]
                        } ?: withLocation.first()
                    } else withLocation.first()
                } else garages.first()

                withContext(Dispatchers.Main) {
                    val hasAnswer = checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) ==
                        PackageManager.PERMISSION_GRANTED
                    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${nearest.phoneNumber}"))
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startActivityAndCollapse(
                            PendingIntent.getActivity(
                                this@GarageTileNearest, 0, callIntent, PendingIntent.FLAG_IMMUTABLE,
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(callIntent)
                    }
                    scheduleHangupAndCleanup(applicationContext, nearest.phoneNumber, hasAnswer)
                    returnToApp(applicationContext)
                }
            }
        }
    }

    private suspend fun oneShortLocation(): Location? {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                LocationServices.getFusedLocationProviderClient(applicationContext)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    }

    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
