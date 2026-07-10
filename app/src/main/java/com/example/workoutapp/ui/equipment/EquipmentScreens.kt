package com.example.workoutapp.ui.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.workoutapp.data.model.Equipment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLocation: (Long) -> Unit,
    viewModel: EquipmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddEquipmentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equipment & Locations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Locations") },
                    icon = { Icon(Icons.Default.LocationOn, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Equipment") },
                    icon = { Icon(Icons.Default.FitnessCenter, null) }
                )
            }

            when (selectedTab) {
                0 -> LocationsTab(
                    uiState = uiState,
                    onNavigateToLocation = onNavigateToLocation,
                    onAddLocation = { showAddLocationDialog = true }
                )
                1 -> EquipmentTab(
                    uiState = uiState,
                    onAddEquipment = { showAddEquipmentDialog = true },
                    onDeleteCustomEquipment = viewModel::deleteCustomEquipment
                )
            }
        }
    }

    if (showAddLocationDialog) {
        CreateLocationDialog(
            onConfirm = { name, description, setDefault ->
                viewModel.createLocation(name, description, setDefault)
                showAddLocationDialog = false
            },
            onDismiss = { showAddLocationDialog = false }
        )
    }

    if (showAddEquipmentDialog) {
        CreateEquipmentDialog(
            onConfirm = { name, description, portable ->
                viewModel.createEquipment(name, description, portable)
                showAddEquipmentDialog = false
            },
            onDismiss = { showAddEquipmentDialog = false }
        )
    }
}

@Composable
private fun LocationsTab(
    uiState: EquipmentManagementUiState,
    onNavigateToLocation: (Long) -> Unit,
    onAddLocation: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Define the places you train and the equipment available in each one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedButton(
                onClick = onAddLocation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Location")
            }
        }

        items(uiState.locations, key = { it.location.id }) { summary ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToLocation(summary.location.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(summary.location.name, fontWeight = FontWeight.SemiBold)
                            if (summary.location.isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        "Default",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        if (summary.location.description.isNotBlank()) {
                            Text(
                                summary.location.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${summary.equipmentCount} equipment items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
private fun EquipmentTab(
    uiState: EquipmentManagementUiState,
    onAddEquipment: () -> Unit,
    onDeleteCustomEquipment: (Equipment) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Manage the equipment library used by exercise tagging and location filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedButton(
                onClick = onAddEquipment,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Custom Equipment")
            }
        }

        if (uiState.customEquipment.isNotEmpty()) {
            item {
                Text("Custom Equipment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(uiState.customEquipment, key = { it.id }) { equipment ->
                ListItem(
                    headlineContent = { Text(equipment.name) },
                    supportingContent = {
                        Text(
                            buildList {
                                if (equipment.description.isNotBlank()) add(equipment.description)
                                if (equipment.isPortable) add("Portable")
                            }.joinToString(" • ")
                        )
                    },
                    leadingContent = { Icon(Icons.Default.FitnessCenter, null) },
                    trailingContent = {
                        IconButton(onClick = { onDeleteCustomEquipment(equipment) }) {
                            Icon(Icons.Default.Delete, "Delete custom equipment")
                        }
                    }
                )
            }
        }

        item {
            Text("All Equipment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        items(uiState.allEquipment, key = { it.id }) { equipment ->
            ListItem(
                headlineContent = { Text(equipment.name) },
                supportingContent = {
                    Text(
                        buildList {
                            if (equipment.description.isNotBlank()) add(equipment.description)
                            if (equipment.isPortable) add("Portable")
                            if (equipment.isCustom) add("Custom")
                        }.joinToString(" • ")
                    )
                },
                leadingContent = {
                    Icon(
                        if (equipment.isPortable) Icons.Default.Settings else Icons.Default.FitnessCenter,
                        null
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    locationId: Long,
    onNavigateBack: () -> Unit,
    viewModel: EquipmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.locationDetailState.collectAsState()

    LaunchedEffect(locationId) {
        viewModel.loadLocation(locationId)
    }
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            viewModel.clearLocationSavedState()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::saveLocation, enabled = !uiState.isSaving) {
                        Icon(Icons.Default.Edit, "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::updateLocationName,
                        label = { Text("Location name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::updateLocationDescription,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.isDefault,
                            onCheckedChange = viewModel::updateLocationDefault
                        )
                        Text("Use as default workout location")
                    }
                }
                uiState.saveError?.let { error ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Available equipment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.allEquipment, key = { it.id }) { equipment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleLocationEquipment(equipment.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = equipment.id in uiState.selectedEquipmentIds,
                            onCheckedChange = { viewModel.toggleLocationEquipment(equipment.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(equipment.name, fontWeight = FontWeight.Medium)
                            val meta = buildList {
                                if (equipment.description.isNotBlank()) add(equipment.description)
                                if (equipment.isPortable) add("Portable")
                                if (equipment.isCustom) add("Custom")
                            }.joinToString(" • ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteLocation()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                        Button(
                            onClick = viewModel::saveLocation,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Edit, null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isSaving) "Saving..." else "Save Location")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateLocationDialog(
    onConfirm: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var setDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = setDefault, onCheckedChange = { setDefault = it })
                    Text("Use as default")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description, setDefault) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateEquipmentDialog(
    onConfirm: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var portable by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Equipment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = portable, onCheckedChange = { portable = it })
                    Text("Portable / travel-friendly")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description, portable) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
