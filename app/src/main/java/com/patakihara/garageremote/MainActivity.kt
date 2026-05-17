package com.patakihara.garageremote

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patakihara.garageremote.ui.theme.GarageRemoteTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GarageRemoteTheme {
                GarageRemoteApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageRemoteApp(vm: GarageViewModel = viewModel()) {
    val garages by vm.garages.collectAsState()
    val currentLocation by vm.currentLocation.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGarage by remember { mutableStateOf<Garage?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    // Seed location permission state on first composition and after grants.
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setLocationPermission(granted) }

    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        vm.setLocationPermission(already)
        if (!already) locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().decodeToString() } ?: return@rememberLauncherForActivityResult
        vm.importJson(json)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garage Remote") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export garages") },
                            onClick = { exportLauncher.launch("garages.json"); showMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Import garages") },
                            onClick = { importLauncher.launch(arrayOf("application/json")); showMenu = false },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add garage")
            }
        },
    ) { padding ->
        if (garages.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No garages yet.\nTap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(garages, key = { it.id }) { garage ->
                    val isNearest = currentLocation != null &&
                            garage.latitude != null &&
                            garage == garages.firstOrNull { it.latitude != null }
                    GarageCard(
                        garage = garage,
                        currentLocation = currentLocation,
                        isNearest = isNearest,
                        onEdit = { editingGarage = garage },
                        onDelete = {
                            clearTileForGarage(context, garage.id)
                            vm.delete(garage)
                        },
                        onLocationPermissionChanged = { vm.setLocationPermission(it) },
                    )
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
fun GarageCard(
    garage: Garage,
    currentLocation: Location?,
    isNearest: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLocationPermissionChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pinnedSlot by remember { mutableIntStateOf(getGarageSlot(context, garage.id) ?: 0) }
    var isCalling by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(12) }

    LaunchedEffect(isCalling) {
        if (isCalling) {
            secondsLeft = 12
            repeat(12) { delay(1_000L); secondsLeft-- }
            delay(2_500L)
            isCalling = false
        }
    }

    val distance = remember(currentLocation?.latitude, currentLocation?.longitude, garage.latitude, garage.longitude) {
        val lat = garage.latitude ?: return@remember null
        val lng = garage.longitude ?: return@remember null
        val loc = currentLocation ?: return@remember null
        FloatArray(1).also { Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, it) }[0]
    }

    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAnswerPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        hasCallPermission = perms[Manifest.permission.CALL_PHONE] == true
        hasAnswerPermission = perms[Manifest.permission.ANSWER_PHONE_CALLS] == true
        onLocationPermissionChanged(perms[Manifest.permission.ACCESS_FINE_LOCATION] == true)
        if (hasCallPermission) {
            openGarage(context, garage.phoneNumber, hasAnswerPermission)
            returnToApp(context)
            isCalling = true
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(garage.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isNearest) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Nearest", style = MaterialTheme.typography.labelSmall) },
                                icon = { Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp)) },
                            )
                        }
                    }
                    Text(
                        garage.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    distance?.let {
                        Text(
                            formatDistance(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row {
                    IconButton(onClick = {
                        if (pinnedSlot > 0) {
                            setTileAssignment(context, pinnedSlot, null); pinnedSlot = 0
                        } else {
                            nextAvailableSlot(context)?.let { slot ->
                                setTileAssignment(context, slot, garage)
                                pinnedSlot = slot
                                requestAddTile(context, slot, garage.name)
                            }
                        }
                    }) {
                        if (pinnedSlot > 0) {
                            BadgedBox(badge = { Badge { Text("$pinnedSlot") } }) {
                                Icon(Icons.Filled.PushPin, "Remove from Quick Settings", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Icon(Icons.Outlined.PushPin, "Add to Quick Settings")
                        }
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (hasCallPermission) {
                        openGarage(context, garage.phoneNumber, hasAnswerPermission)
                        returnToApp(context)
                        isCalling = true
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.ANSWER_PHONE_CALLS,
                                Manifest.permission.WRITE_CALL_LOG,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Garage, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open", fontSize = 18.sp)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${garage.name}\"?") },
            text = { Text("This garage will be removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (isCalling) {
        AlertDialog(
            onDismissRequest = { isCalling = false },
            title = { Text("Opening ${garage.name}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hanging up automatically in $secondsLeft s…")
                    LinearProgressIndicator(progress = { secondsLeft / 12f }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { isCalling = false }) { Text("Dismiss") } },
        )
    }
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
    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }
    var latitude by remember { mutableStateOf(initialLatitude) }
    var longitude by remember { mutableStateOf(initialLongitude) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Add Garage" else "Edit Garage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Location row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        TextButton(
                            onClick = { latitude = currentLocation?.latitude; longitude = currentLocation?.longitude },
                            enabled = currentLocation != null,
                        ) { Text("Use GPS") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), number.trim(), latitude, longitude) },
                enabled = name.isNotBlank() && number.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatDistance(metres: Float): String = when {
    metres < 1_000 -> "${metres.toInt()} m away"
    else -> "${"%.1f".format(metres / 1_000)} km away"
}

private fun returnToApp(context: android.content.Context) {
    Handler(Looper.getMainLooper()).postDelayed({
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }, 1_500L)
}

private fun requestAddTile(context: android.content.Context, slot: Int, garageName: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val tileClass: Class<*> = when (slot) {
        1 -> GarageTile1::class.java
        2 -> GarageTile2::class.java
        3 -> GarageTile3::class.java
        else -> GarageTile4::class.java
    }
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, tileClass),
        garageName,
        Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
        { it.run() },
        {},
    )
}
