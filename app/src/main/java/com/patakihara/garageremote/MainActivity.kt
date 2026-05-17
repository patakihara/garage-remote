package com.patakihara.garageremote

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGarage by remember { mutableStateOf<Garage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garage Remote") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
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
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(garages, key = { it.id }) { garage ->
                    GarageCard(
                        garage = garage,
                        onEdit = { editingGarage = garage },
                        onDelete = {
                            clearTileForGarage(context, garage.id)
                            vm.delete(garage)
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        GarageDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, number ->
                vm.add(Garage(name = name, phoneNumber = number))
                showAddDialog = false
            },
        )
    }

    editingGarage?.let { garage ->
        GarageDialog(
            initialName = garage.name,
            initialNumber = garage.phoneNumber,
            onDismiss = { editingGarage = null },
            onConfirm = { name, number ->
                val updated = garage.copy(name = name, phoneNumber = number)
                vm.update(updated)
                // Keep tile assignment in sync with renamed/renumbered garage
                getGarageSlot(context, garage.id)?.let { setTileAssignment(context, it, updated) }
                editingGarage = null
            },
        )
    }
}

@Composable
fun GarageCard(garage: Garage, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pinnedSlot by remember { mutableIntStateOf(getGarageSlot(context, garage.id) ?: 0) }
    var isCalling by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(12) }

    LaunchedEffect(isCalling) {
        if (isCalling) {
            secondsLeft = 12
            repeat(12) {
                delay(1_000L)
                secondsLeft--
            }
            delay(2_500L)
            isCalling = false
        }
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
        if (hasCallPermission) {
            openGarage(context, garage.phoneNumber, hasAnswerPermission)
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
                    Text(
                        garage.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        garage.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    // Pin / unpin quick-settings tile
                    IconButton(onClick = {
                        if (pinnedSlot > 0) {
                            setTileAssignment(context, pinnedSlot, null)
                            pinnedSlot = 0
                        } else {
                            val slot = nextAvailableSlot(context)
                            if (slot != null) {
                                setTileAssignment(context, slot, garage)
                                pinnedSlot = slot
                                requestAddTile(context, slot, garage.name)
                            }
                        }
                    }) {
                        if (pinnedSlot > 0) {
                            BadgedBox(badge = { Badge { Text("$pinnedSlot") } }) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = "Remove from Quick Settings",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Icon(Icons.Outlined.PushPin, contentDescription = "Add to Quick Settings")
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (hasCallPermission) {
                        openGarage(context, garage.phoneNumber, hasAnswerPermission)
                        isCalling = true
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.ANSWER_PHONE_CALLS,
                                Manifest.permission.WRITE_CALL_LOG,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Garage, contentDescription = null, Modifier.size(20.dp))
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
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete")
                }
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Hanging up automatically in $secondsLeft s…")
                    LinearProgressIndicator(
                        progress = { secondsLeft / 12f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isCalling = false }) { Text("Dismiss") }
            },
        )
    }
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

@Composable
fun GarageDialog(
    initialName: String = "",
    initialNumber: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Add Garage" else "Edit Garage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), number.trim()) },
                enabled = name.isNotBlank() && number.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
