package com.example.workoutapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workoutapp.data.local.ExerciseStorageInfo
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════
// Main Settings Screen (unchanged except for imports)
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToGoals: () -> Unit,
    onNavigateToEquipment: () -> Unit,
    onNavigateToStorage: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { paddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(paddingValues)) {
            item { SettingsSection("Training") }
            item { SettingsItem(Icons.Default.Flag, "Training Goals", "Set your focus and category priorities", onNavigateToGoals) }
            item { SettingsItem(Icons.Default.FitnessCenter, "Equipment & Locations", "Manage your gym setups", onNavigateToEquipment) }
            item { SettingsSection("Data") }
            item { SettingsItem(Icons.Default.Storage, "Storage Management", "Manage media files and cleanup", onNavigateToStorage) }
            item { SettingsItem(Icons.Default.Backup, "Backup & Restore", "Export and import your data", status = "Soon") }
            item { SettingsSection("App") }
            item { SettingsItem(Icons.Default.Palette, "Appearance", "Theme and display settings", status = "Soon") }
            item { SettingsItem(Icons.Default.Notifications, "Notifications", "Workout and PT reminders", status = "Soon") }
            item { SettingsItem(Icons.Default.Info, "About", "Version 1.0") }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    status: String? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            if (status != null) {
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (onClick != null) {
                Icon(Icons.Default.ChevronRight, null)
            }
        },
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Goal Settings Screen — now wired to ViewModel
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.goalState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Goals") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { error ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                item { Text("Training Phase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item { Text("Choose a phase to automatically adjust category emphasis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                items(TrainingPhase.entries) { phase ->
                    PhaseCard(
                        phase = phase,
                        isSelected = state.currentPhase == phase,
                        onSelect = { viewModel.setTrainingPhase(phase) }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
                item { Text("Category Weights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item { Text("Fine-tune how often each category appears in generated workouts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                items(WorkoutCategory.rotationCategories()) { category ->
                    val weight = state.categoryWeights[category] ?: category.defaultWeight
                    CategoryWeightSlider(
                        category = category,
                        weight = weight,
                        onWeightChange = { viewModel.setCategoryWeight(category, it) }
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun PhaseCard(phase: TrainingPhase, isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(phase.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                Text(phase.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryWeightSlider(category: WorkoutCategory, weight: Float, onWeightChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(category.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(category.displayName, style = MaterialTheme.typography.bodyMedium)
            }
            Text(String.format(Locale.getDefault(), "%.1fx", weight), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = weight,
            onValueChange = onWeightChange,
            valueRange = 0.1f..3f,
            steps = 28  // increments of 0.1
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Storage Settings Screen — now wired to real data
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.storageState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::refreshStorage) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.error?.let { error ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                // Storage usage card
                item { StorageUsageCard(state) }

                // Offload suggestions
                if (state.offloadCandidates.isNotEmpty()) {
                    item { Text("Offload Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item { Text("These exercises have large local media that could be replaced with external URLs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.offloadCandidates.take(5), key = { it.exerciseId }) { info ->
                        StorageExerciseItem(info, showOffloadHint = true)
                    }
                }

                // Cleanup suggestions
                if (state.cleanupCandidates.isNotEmpty()) {
                    item { Text("Cleanup Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item { Text("These exercises haven't been used in 90+ days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.cleanupCandidates.take(5), key = { it.exerciseId }) { info ->
                        StorageExerciseItem(info, showCleanupHint = true)
                    }
                }

                // Full breakdown
                if (state.exerciseBreakdown.isNotEmpty()) {
                    item { Text("Storage by Exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(state.exerciseBreakdown.take(10), key = { it.exerciseId }) { info ->
                        StorageExerciseItem(info)
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun StorageUsageCard(state: StorageSettingsState) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Storage Used", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(state.formattedTotalSize, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${state.localMediaCount} local files", style = MaterialTheme.typography.bodySmall)
                Text("${state.externalUrlCount} external URLs", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StorageExerciseItem(
    info: ExerciseStorageInfo,
    showOffloadHint: Boolean = false,
    showCleanupHint: Boolean = false
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${info.localMediaCount} files • ${info.formattedSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showOffloadHint) {
                    Text("Could use external URL instead", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (showCleanupHint) {
                    Text("Not used recently", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (info.hasExternalUrls) {
                Icon(Icons.Default.Link, "Has external URLs", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
