package com.example.workoutapp.ui.physicaltherapy

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
import com.example.workoutapp.data.model.PTFrequency
import com.example.workoutapp.data.model.PTSessionLog
import com.example.workoutapp.data.model.PhysicalTherapyRoutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════
// List Screen
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalTherapyScreen(
    onNavigateToRoutineDetail: (Long) -> Unit,
    onNavigateToAddRoutine: () -> Unit,
    viewModel: PTViewModel = hiltViewModel()
) {
    val uiState by viewModel.listUiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physical Therapy") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                actions = {
                    if (uiState.archivedRoutines.isNotEmpty()) {
                        IconButton(onClick = viewModel::toggleShowArchived) {
                            Icon(
                                if (uiState.showArchived) Icons.Default.VisibilityOff else Icons.Default.Inventory2,
                                contentDescription = "Toggle archived"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddRoutine,
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(Icons.Default.Add, "Add Routine")
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.mustDoRoutines.isEmpty() && uiState.otherRoutines.isEmpty() && !uiState.showArchived -> {
                PTEmptyState(modifier = Modifier.padding(paddingValues), onAdd = onNavigateToAddRoutine)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Info banner
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "PT routines are separate from workouts and appear as daily must-do reminders.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Must-do section
                    if (uiState.mustDoRoutines.isNotEmpty()) {
                        item {
                            Text("Must-Do Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(uiState.mustDoRoutines, key = { it.routine.id }) { summary ->
                            PTRoutineCard(
                                summary = summary,
                                onClick = { onNavigateToRoutineDetail(summary.routine.id) },
                                onArchive = { viewModel.archiveRoutine(summary.routine.id) },
                                onToggleMustDo = { viewModel.toggleMustDo(summary.routine.id, !summary.routine.isMustDo) }
                            )
                        }
                    }

                    // Other routines
                    if (uiState.otherRoutines.isNotEmpty()) {
                        item {
                            Text("Other Routines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(uiState.otherRoutines, key = { it.routine.id }) { summary ->
                            PTRoutineCard(
                                summary = summary,
                                onClick = { onNavigateToRoutineDetail(summary.routine.id) },
                                onArchive = { viewModel.archiveRoutine(summary.routine.id) },
                                onToggleMustDo = { viewModel.toggleMustDo(summary.routine.id, !summary.routine.isMustDo) }
                            )
                        }
                    }

                    // Archived section
                    if (uiState.showArchived && uiState.archivedRoutines.isNotEmpty()) {
                        item {
                            Text("Archived", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(uiState.archivedRoutines, key = { it.id }) { routine ->
                            ArchivedRoutineCard(
                                routine = routine,
                                onUnarchive = { viewModel.unarchiveRoutine(routine.id) },
                                onDelete = { viewModel.deleteRoutine(routine.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PTEmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Healing, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(16.dp))
            Text("No PT Routines", style = MaterialTheme.typography.titleMedium)
            Text("Add a routine prescribed by your therapist", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add PT Routine") }
        }
    }
}

@Composable
private fun PTRoutineCard(
    summary: PTRoutineSummary,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onToggleMustDo: () -> Unit
) {
    val r = summary.routine
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (r.condition.isNotBlank()) {
                        Text(r.condition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (summary.isDueNow) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Text("DUE", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("✓ Done", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            Text("${r.frequency.displayName} • ${summary.completedToday}/${r.timesPerDay} today • Priority ${r.priority}", style = MaterialTheme.typography.labelMedium)
            summary.lastCompletedAt?.let {
                Text("Last: ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(it))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleMustDo) {
                    Text(if (r.isMustDo) "Remove must-do" else "Set must-do")
                }
                TextButton(onClick = onArchive) { Text("Archive") }
            }
        }
    }
}

@Composable
private fun ArchivedRoutineCard(routine: PhysicalTherapyRoutine, onUnarchive: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleSmall)
                if (routine.condition.isNotBlank()) Text(routine.condition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onUnarchive) { Text("Restore") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Detail Screen
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTRoutineDetailScreen(
    routineId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: PTViewModel = hiltViewModel()
) {
    val uiState by viewModel.detailUiState.collectAsStateWithLifecycle()

    LaunchedEffect(routineId) { viewModel.loadRoutineDetail(routineId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.routine?.name ?: "PT Routine") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    uiState.routine?.let { r ->
                        IconButton(onClick = { onNavigateToEdit(r.id) }) { Icon(Icons.Default.Edit, "Edit") }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.routine == null -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { Text("Routine not found") }
            else -> {
                val routine = uiState.routine!!
                LazyColumn(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header card
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(routine.name, style = MaterialTheme.typography.headlineSmall)
                                if (routine.condition.isNotBlank()) Text("Condition: ${routine.condition}", style = MaterialTheme.typography.bodyMedium)
                                if (routine.therapistName.isNotBlank()) Text("Therapist: ${routine.therapistName}", style = MaterialTheme.typography.bodyMedium)
                                Text("${routine.frequency.displayName} • ${uiState.completedToday}/${routine.timesPerDay} today", style = MaterialTheme.typography.labelLarge)
                                if (routine.precautions.isNotBlank()) {
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(8.dp))
                                            Text(routine.precautions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                }
                                uiState.averagePainReduction?.let { avg ->
                                    val label = when {
                                        avg < 0 -> "Pain improves by ${String.format(Locale.getDefault(), "%.1f", -avg)} pts on average"
                                        avg > 0 -> "Pain worsens by ${String.format(Locale.getDefault(), "%.1f", avg)} pts on average"
                                        else -> "No average pain change"
                                    }
                                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Start / In-progress session
                    item {
                        if (uiState.activeSessionLogId != null) {
                            ActivePTSessionCard(
                                feedback = uiState.sessionFeedback,
                                onPainBeforeChanged = viewModel::updatePainBefore,
                                onPainAfterChanged = viewModel::updatePainAfter,
                                onNotesChanged = viewModel::updateSessionNotes,
                                onSymptomChanged = viewModel::updateSymptomChanges,
                                onComplete = viewModel::completePTSession,
                                onCancel = viewModel::cancelPTSession
                            )
                        } else {
                            Button(onClick = viewModel::startPTSession, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start Session")
                            }
                        }
                    }

                    // Exercises
                    if (uiState.exercises.isNotEmpty()) {
                        item { Text("Exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        items(uiState.exercises, key = { it.ref.exerciseId }) { item ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.exercise?.name ?: "Exercise ${item.ref.exerciseId}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("${item.ref.prescribedSets} sets • ${item.ref.prescribedReps}" +
                                            (item.ref.prescribedHoldSeconds?.let { " • ${it}s hold" } ?: "") +
                                            " • ${item.ref.prescribedRestSeconds}s rest",
                                        style = MaterialTheme.typography.bodySmall)
                                    if (item.ref.specialInstructions.isNotBlank()) {
                                        Text(item.ref.specialInstructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // Recent history
                    if (uiState.recentLogs.isNotEmpty()) {
                        item { Text("Recent History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        items(uiState.recentLogs, key = { it.id }) { log ->
                            PTSessionLogCard(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePTSessionCard(
    feedback: PTSessionFeedback,
    onPainBeforeChanged: (Int) -> Unit,
    onPainAfterChanged: (Int) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSymptomChanged: (String) -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Session In Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            PainSlider("Pain before session", feedback.painBefore, onPainBeforeChanged)
            PainSlider("Pain after session", feedback.painAfter, onPainAfterChanged)
            OutlinedTextField(feedback.notes, onNotesChanged, Modifier.fillMaxWidth(), label = { Text("Notes") }, singleLine = false, minLines = 2)
            OutlinedTextField(feedback.symptomChanges, onSymptomChanged, Modifier.fillMaxWidth(), label = { Text("Symptom changes (better/worse/same)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onComplete, Modifier.weight(1f)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Complete") }
            }
        }
    }
}

@Composable
private fun PainSlider(label: String, value: Int, onChanged: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("$value/10", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.toFloat(), onValueChange = { onChanged(it.toInt().coerceIn(0, 10)) }, valueRange = 0f..10f, steps = 9)
        Text("0 = no pain, 10 = worst", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PTSessionLogCard(log: PTSessionLog) {
    val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateFmt.format(Date(log.startedAt)), style = MaterialTheme.typography.labelLarge)
                val statusText = if (log.completedAt != null) "✓ Completed" else "○ Incomplete"
                Text(statusText, style = MaterialTheme.typography.labelSmall)
            }
            if (log.painLevelBefore != null && log.painLevelAfter != null) {
                val delta = log.painLevelAfter!! - log.painLevelBefore!!
                val label = when { delta < 0 -> "Pain: ${log.painLevelBefore} → ${log.painLevelAfter} (improved ↓${-delta})"; delta > 0 -> "Pain: ${log.painLevelBefore} → ${log.painLevelAfter} (worsened ↑$delta)"; else -> "Pain: ${log.painLevelBefore} → ${log.painLevelAfter} (same)" }
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            if (log.notes.isNotBlank()) Text(log.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Add/Edit Screen
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPTRoutineScreen(
    editRoutineId: Long? = null,
    onNavigateBack: () -> Unit,
    onRoutineSaved: (Long) -> Unit = {},
    viewModel: PTViewModel = hiltViewModel()
) {
    val uiState by viewModel.editUiState.collectAsStateWithLifecycle()

    LaunchedEffect(editRoutineId) {
        if (editRoutineId != null) viewModel.initEditRoutine(editRoutineId) else viewModel.initNewRoutine()
    }
    LaunchedEffect(uiState.savedRoutineId) {
        uiState.savedRoutineId?.let { id -> viewModel.clearSavedRoutineId(); onRoutineSaved(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit PT Routine" else "Add PT Routine") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.Close, "Cancel") } },
                actions = {
                    TextButton(onClick = viewModel::saveRoutine, enabled = uiState.form.name.isNotBlank() && !uiState.isSaving) {
                        if (uiState.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        val form = uiState.form
        LazyColumn(
            Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic info
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(form.name, viewModel::updateFormName, Modifier.fillMaxWidth(), label = { Text("Routine Name *") }, singleLine = true)
                    OutlinedTextField(form.condition, viewModel::updateFormCondition, Modifier.fillMaxWidth(), label = { Text("Condition Being Treated") }, singleLine = true)
                    OutlinedTextField(form.therapistName, viewModel::updateFormTherapist, Modifier.fillMaxWidth(), label = { Text("Therapist Name (optional)") }, singleLine = true)
                    OutlinedTextField(form.description, viewModel::updateFormDescription, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
                }
            }

            // Frequency
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Frequency", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PTFrequency.entries.forEach { freq ->
                            FilterChip(selected = form.frequency == freq, onClick = { viewModel.updateFormFrequency(freq) }, label = { Text(freq.displayName) })
                        }
                    }
                    if (form.frequency == PTFrequency.MULTIPLE_DAILY) {
                        OutlinedTextField(
                            form.timesPerDay.toString(),
                            { viewModel.updateFormTimesPerDay(it.toIntOrNull() ?: 1) },
                            Modifier.fillMaxWidth(),
                            label = { Text("Times per day") },
                            singleLine = true
                        )
                    }
                }
            }

            // Priority & Must-do
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(form.isMustDo, viewModel::updateFormMustDo)
                        Spacer(Modifier.width(8.dp))
                        Text("Must-do (appears in daily reminders)")
                    }
                    OutlinedTextField(form.priority.toString(), { viewModel.updateFormPriority(it.toIntOrNull() ?: 1) }, Modifier.fillMaxWidth(), label = { Text("Priority (1 = highest)") }, singleLine = true)
                }
            }

            // Precautions
            item {
                OutlinedTextField(form.precautions, viewModel::updateFormPrecautions, Modifier.fillMaxWidth(), label = { Text("Precautions / Restrictions") }, minLines = 2)
            }

            // Exercise picker
            item {
                Text("Exercises", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (uiState.availableExercises.isEmpty()) {
                item { Text("No exercises in library yet. Add exercises first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(uiState.availableExercises, key = { it.id }) { exercise ->
                    val selected = exercise.id in form.selectedExerciseIds
                    OutlinedCard(
                        onClick = { viewModel.toggleExerciseSelected(exercise.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selected) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else CardDefaults.outlinedCardColors()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selected, { viewModel.toggleExerciseSelected(exercise.id) })
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                                if (exercise.description.isNotBlank()) Text(exercise.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // Inline config for selected exercises
                    if (selected) {
                        val cfg = form.exerciseConfigs[exercise.id] ?: PTExerciseConfig()
                        Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(cfg.sets.toString(), { viewModel.updateExerciseConfig(exercise.id, cfg.copy(sets = it.toIntOrNull() ?: 3)) }, Modifier.weight(1f), label = { Text("Sets") }, singleLine = true)
                            OutlinedTextField(cfg.reps, { viewModel.updateExerciseConfig(exercise.id, cfg.copy(reps = it)) }, Modifier.weight(1f), label = { Text("Reps") }, singleLine = true)
                            OutlinedTextField((cfg.holdSeconds ?: 0).toString(), { viewModel.updateExerciseConfig(exercise.id, cfg.copy(holdSeconds = it.toIntOrNull()?.takeIf { v -> v > 0 })) }, Modifier.weight(1f), label = { Text("Hold(s)") }, singleLine = true)
                        }
                    }
                }
            }

            // Notes
            item {
                OutlinedTextField(form.notes, viewModel::updateFormNotes, Modifier.fillMaxWidth(), label = { Text("Additional Notes") }, minLines = 2)
            }

            // Bottom spacer for FAB
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}
