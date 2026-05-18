package com.patakihara.garageremote

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patakihara.garageremote.ui.theme.GarageRemoteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GarageRemoteTheme { GarageRemoteApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageRemoteApp(vm: GarageViewModel = viewModel()) {
    val garages by vm.garages.collectAsState()
    val currentLocation by vm.currentLocation.collectAsState()
    val context = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGarage by remember { mutableStateOf<Garage?>(null) }
    var selectedGarageId by remember { mutableStateOf<String?>(null) }
    var isCalling by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(12) }
    var isLocating by remember { mutableStateOf(false) }

    var hasCallPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAnswerPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED)
    }

    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setLocationPermission(granted)
    }
    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        hasCallPermission = perms[Manifest.permission.CALL_PHONE] == true
        hasAnswerPermission = perms[Manifest.permission.ANSWER_PHONE_CALLS] == true
        if (hasCallPermission) {
            val garage = garages.find { it.id == selectedGarageId } ?: garages.firstOrNull()
            garage?.let { openGarage(context, it.phoneNumber, hasAnswerPermission); returnToApp(context); isCalling = true }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@rememberLauncherForActivityResult
        vm.importJson(json)
    }

    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        vm.setLocationPermission(already)
        if (!already) locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Show loader while waiting for first GPS fix (only if garages have location data)
    LaunchedEffect(Unit) {
        val permGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!permGranted) return@LaunchedEffect

        val loadedGarages = withTimeoutOrNull(3_000L) {
            snapshotFlow { garages }.first { it.isNotEmpty() }
        } ?: return@LaunchedEffect

        if (loadedGarages.none { it.latitude != null } || currentLocation != null) return@LaunchedEffect

        isLocating = true
        withTimeoutOrNull(5_000L) { snapshotFlow { currentLocation }.first { it != null } }
        delay(200L) // let VM's combine() re-sort garages
        isLocating = false
        selectedGarageId = garages.firstOrNull()?.id
    }

    // Auto-select nearest when garages change (if nothing selected or selection deleted)
    LaunchedEffect(garages) {
        if (!isLocating && (selectedGarageId == null || garages.none { it.id == selectedGarageId }))
            selectedGarageId = garages.firstOrNull()?.id
    }

    LaunchedEffect(isCalling) {
        if (isCalling) {
            secondsLeft = 12
            repeat(12) { delay(1_000L); secondsLeft-- }
            delay(2_500L)
            isCalling = false
        }
    }

    val selectedGarage = garages.find { it.id == selectedGarageId } ?: garages.firstOrNull()
    val isNearest = currentLocation != null && selectedGarage?.latitude != null &&
        selectedGarage == garages.firstOrNull { it.latitude != null }
    val distance = selectedGarage?.let { vm.distanceTo(it, currentLocation) }

    if (showSettings) {
        SettingsScreen(
            garages = garages,
            onBack = { showSettings = false },
            onAdd = { showAddDialog = true },
            onEdit = { editingGarage = it },
            onDelete = { g -> clearTileForGarage(context, g.id); vm.delete(g) },
            onExport = { exportLauncher.launch("garages.json") },
            onImport = { importLauncher.launch(arrayOf("application/json")) },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        ) { padding ->
            if (isLocating) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(80.dp), strokeWidth = 6.dp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = 16.dp)
                        .pointerInput(garages) {
                            var acc = 0f
                            detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { _, drag ->
                                acc += drag
                                if (kotlin.math.abs(acc) > 200f) {
                                    val idx = garages.indexOfFirst { it.id == selectedGarageId }
                                    if (acc < 0 && idx in 0 until garages.lastIndex) selectedGarageId = garages[idx + 1].id
                                    else if (acc > 0 && idx > 0) selectedGarageId = garages[idx - 1].id
                                    acc = 0f
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        HeroCircle(garage = selectedGarage, currentLocation = currentLocation, isNearest = isNearest, distance = distance)
                    }

                    if (isCalling) {
                        Text(
                            "Hangs up automatically · 0:${secondsLeft.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }

                    GarageSelector(
                        garages = garages,
                        selectedId = selectedGarageId,
                        onSelect = { selectedGarageId = it },
                        onAdd = { showAddDialog = true },
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            selectedGarage ?: return@Button
                            if (hasCallPermission) {
                                openGarage(context, selectedGarage.phoneNumber, hasAnswerPermission)
                                returnToApp(context)
                                isCalling = true
                            } else {
                                callPermLauncher.launch(arrayOf(
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.ANSWER_PHONE_CALLS,
                                    Manifest.permission.WRITE_CALL_LOG,
                                ))
                            }
                        },
                        enabled = !isCalling && selectedGarage != null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                    ) {
                        if (isCalling) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Garage, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        GarageDialog(
            currentLocation = currentLocation,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, number, lat, lng ->
                vm.add(Garage(name = name, phoneNumber = number, latitude = lat, longitude = lng))
                showAddDialog = false
            },
        )
    }

    editingGarage?.let { garage ->
        GarageDialog(
            initialName = garage.name,
            initialNumber = garage.phoneNumber,
            initialLatitude = garage.latitude,
            initialLongitude = garage.longitude,
            currentLocation = currentLocation,
            onDismiss = { editingGarage = null },
            onConfirm = { name, number, lat, lng ->
                val updated = garage.copy(name = name, phoneNumber = number, latitude = lat, longitude = lng)
                vm.update(updated)
                getGarageSlot(context, garage.id)?.let { setTileAssignment(context, it, updated) }
                editingGarage = null
            },
        )
    }
}

@Composable
fun HeroCircle(garage: Garage?, currentLocation: Location?, isNearest: Boolean, distance: Float?) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", 0))
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            isEnabled = false
            isFocusable = false
            controller.setZoom(17.0)
        }
    }

    val locationMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createBlueDot(context)
            setInfoWindow(null)
        }
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

    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (garage?.latitude != null && garage.longitude != null) {
                AndroidView(
                    factory = { mapView },
                    update = { map ->
                        map.controller.setCenter(GeoPoint(garage.latitude, garage.longitude))
                        val loc = currentLocation
                        if (loc != null) {
                            locationMarker.position = GeoPoint(loc.latitude, loc.longitude)
                            if (!map.overlays.contains(locationMarker)) map.overlays.add(locationMarker)
                        } else {
                            map.overlays.remove(locationMarker)
                        }
                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize().alpha(0.35f),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (isNearest && distance != null) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("NEAREST · ${formatDistanceShort(distance)}", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp)) },
                )
            }
            Text(garage?.name ?: "", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(garage?.phoneNumber ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun createBlueDot(context: Context): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = (16 * dp).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(bmp).drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1976D2.toInt() })
    return BitmapDrawable(context.resources, bmp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageSelector(garages: List<Garage>, selectedId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow {
                garages.forEachIndexed { index, garage ->
                    SegmentedButton(
                        selected = garage.id == selectedId,
                        onClick = { onSelect(garage.id) },
                        shape = SegmentedButtonDefaults.itemShape(index, garages.size),
                    ) { Text(garage.name) }
                }
            }
        }
        IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Add garage") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    garages: List<Garage>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Garage) -> Unit,
    onDelete: (Garage) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<Garage?>(null) }
    var tileRevision by remember { mutableIntStateOf(0) }

    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val callGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SettingsSectionHeader("GARAGES") }
            items(garages, key = { it.id }) { garage ->
                val slot = remember(tileRevision, garage.id) { getGarageSlot(context, garage.id) }
                val canPin = remember(tileRevision) { nextAvailableSlot(context) != null }
                ListItem(
                    headlineContent = { Text(garage.name) },
                    supportingContent = { Text(garage.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    if (slot != null) {
                                        setTileAssignment(context, slot, null)
                                    } else {
                                        nextAvailableSlot(context)?.let { s ->
                                            setTileAssignment(context, s, garage)
                                            requestAddTile(context, s, garage.name)
                                        }
                                    }
                                    tileRevision++
                                },
                                enabled = slot != null || canPin,
                            ) {
                                if (slot != null) {
                                    BadgedBox(badge = { Badge { Text("$slot") } }) {
                                        Icon(Icons.Filled.PushPin, "Remove tile", tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Icon(Icons.Outlined.PushPin, "Add to Quick Settings")
                                }
                            }
                            IconButton(onClick = { onEdit(garage) }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { deleteTarget = garage }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                )
            }
            item {
                TextButton(onClick = onAdd, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add garage")
                }
            }

            item { SettingsSectionHeader("QUICK SETTINGS") }
            item {
                ListItem(
                    headlineContent = { Text("Nearest garage tile") },
                    supportingContent = { Text("Opens the closest garage automatically") },
                    trailingContent = {
                        TextButton(onClick = { requestAddNearestTile(context) }) { Text("Add tile") }
                    },
                )
            }

            item { SettingsSectionHeader("DATA") }
            item { ListItem(headlineContent = { Text("Export garages") }, supportingContent = { Text("Save as JSON file") }, modifier = Modifier.clickable { onExport() }) }
            item { ListItem(headlineContent = { Text("Import garages") }, supportingContent = { Text("Load from JSON file") }, modifier = Modifier.clickable { onImport() }) }

            item { SettingsSectionHeader("PERMISSIONS") }
            item { PermissionRow("Location", "Used to detect nearest garage", locationGranted) }
            item { PermissionRow("Phone calls", "Required to dial the remote", callGranted) }

            item { SettingsSectionHeader("ABOUT") }
            item { ListItem(headlineContent = { Text("Version") }, trailingContent = { Text(versionName ?: "—") }) }
        }
    }

    deleteTarget?.let { garage ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${garage.name}\"?") },
            text = { Text("This garage will be removed.") },
            confirmButton = { TextButton(onClick = { onDelete(garage); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun PermissionRow(title: String, description: String, granted: Boolean) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (granted) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Granted", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                } else {
                    Text("Denied", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

@Composable
fun GarageDialog(
    initialName: String = "",
    initialNumber: String = "",
    initialLatitude: Double? = null,
    initialLongitude: Double? = null,
    currentLocation: Location?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double?, Double?) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }
    var latitude by remember { mutableStateOf(initialLatitude) }
    var longitude by remember { mutableStateOf(initialLongitude) }

    val mapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val lat = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN)
            val lng = data.getDoubleExtra(MapPickerActivity.EXTRA_LNG, Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) { latitude = lat; longitude = lng }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Add Garage" else "Edit Garage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (latitude != null) {
                            Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Location set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.LocationOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No location", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row {
                        if (latitude != null) {
                            TextButton(onClick = { latitude = null; longitude = null }) { Text("Clear") }
                        }
                        TextButton(onClick = {
                            val intent = Intent(context, MapPickerActivity::class.java).apply {
                                latitude?.let { putExtra(MapPickerActivity.EXTRA_LAT, it) }
                                longitude?.let { putExtra(MapPickerActivity.EXTRA_LNG, it) }
                            }
                            mapLauncher.launch(intent)
                        }) { Text("Map") }
                        TextButton(
                            onClick = { latitude = currentLocation?.latitude; longitude = currentLocation?.longitude },
                            enabled = currentLocation != null,
                        ) { Text("Use GPS") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), number.trim(), latitude, longitude) }, enabled = name.isNotBlank() && number.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatDistanceShort(metres: Float): String = when {
    metres < 1_000 -> "${metres.toInt()} M"
    else -> "${"%.1f".format(metres / 1_000)} KM"
}

private fun requestAddTile(context: Context, slot: Int, garageName: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val tileClass: Class<*> = when (slot) {
        1 -> GarageTile1::class.java; 2 -> GarageTile2::class.java
        3 -> GarageTile3::class.java; else -> GarageTile4::class.java
    }
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, tileClass), garageName,
        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
        { it.run() }, {},
    )
}

private fun requestAddNearestTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, GarageTileNearest::class.java), "Nearest Garage",
        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
        { it.run() }, {},
    )
}
