package com.patakihara.garageremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.patakihara.garageremote.ui.theme.GarageRemoteTheme
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MapPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN).takeUnless { it.isNaN() }
        val initialLng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN).takeUnless { it.isNaN() }
        setContent {
            GarageRemoteTheme {
                MapPickerScreen(
                    initialLat = initialLat,
                    initialLng = initialLng,
                    onConfirm = { lat, lng ->
                        setResult(RESULT_OK, Intent().apply {
                            putExtra(EXTRA_LAT, lat)
                            putExtra(EXTRA_LNG, lng)
                        })
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    initialLat: Double?,
    initialLng: Double?,
    onConfirm: (Double, Double) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    val initialPoint = remember {
        if (initialLat != null && initialLng != null) GeoPoint(initialLat, initialLng) else null
    }
    var pickedPoint by remember { mutableStateOf<GeoPoint?>(initialPoint) }

    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", 0))
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
    }

    val marker = remember {
        Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
    }

    LaunchedEffect(Unit) {
        val p = initialPoint
        if (p != null) {
            marker.position = p
            mapView.overlays.add(marker)
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(p)
        } else {
            mapView.controller.setZoom(2.0)
        }
        mapView.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                marker.position = p
                if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
                mapView.invalidate()
                pickedPoint = p
                return true
            }
            override fun longPressHelper(p: GeoPoint) = false
        }))
    }

    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                title = {
                    Text(if (pickedPoint == null) "Tap map to pick location" else "Location selected")
                },
                actions = {
                    IconButton(
                        onClick = { pickedPoint?.let { onConfirm(it.latitude, it.longitude) } },
                        enabled = pickedPoint != null,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
