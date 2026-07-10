package com.example.workoutapp.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.displaySummary
import com.example.workoutapp.data.model.resolveBalancedProgrammingPreset
import com.example.workoutapp.data.model.toRichPrescriptionData
import com.example.workoutapp.ui.test.TestTags

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutPlanEditorScreen(
    templateId: Long?,
    onNavigateBack: () -> Unit,
    onNavigateToActiveWorkout: (Long) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.planEditorUiState.collectAsStateWithLifecycle()
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(templateId) {
        if (templateId == null) viewModel.initializeNewPlanEditor()
        else viewModel.loadPlanEditor(templateId)
    }

    LaunchedEffect(uiState.saveSuccess, uiState.isDeleted) {
        if (uiState.saveSuccess && uiState.startedSessionId == null) {
            viewModel.clearPlanEditorConsumedState()
            onNavigateBack()
        }
        if (uiState.isDeleted) {
            viewModel.clearPlanEditorConsumedState()
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.startedSessionId) {
        uiState.startedSessionId?.let { sessionId ->
            viewModel.clearPlanEditorConsumedState()
            onNavigateToActiveWorkout(sessionId)
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.WorkoutPlanEditor.Screen),
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "New Workout Plan" else "Edit Workout Plan") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.templateId != null) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !uiState.isSaving) {
                            Icon(Icons.Default.Delete, "Delete plan")
                        }
                        IconButton(onClick = viewModel::playPlanFromEditor, enabled = !uiState.isSaving) {
                            Icon(Icons.Default.PlayArrow, "Play plan")
                        }
                    }
                    IconButton(onClick = viewModel::savePlanEditor, enabled = !uiState.isSaving) {
                        Icon(Icons.Default.Save, "Save plan")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .testTag(TestTags.WorkoutPlanEditor.ContentList),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        PlanEditorHeaderCard(
                            isExisting = uiState.templateId != null,
                            onSave = viewModel::savePlanEditor,
                            onPlay = viewModel::playPlanFromEditor,
                            isSaving = uiState.isSaving
                        )
                    }

                    uiState.error?.let { message ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        uiState.dataWarning?.let { message ->
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Plan details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = uiState.name,
                                    onValueChange = viewModel::updatePlanEditorName,
                                    label = { Text("Plan name") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(TestTags.WorkoutPlanEditor.NameField),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = uiState.description,
                                    onValueChange = viewModel::updatePlanEditorDescription,
                                    label = { Text("Description") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(TestTags.WorkoutPlanEditor.DescriptionField),
                                    minLines = 2,
                                    maxLines = 3
                                )
                                OutlinedTextField(
                                    value = uiState.notes,
                                    onValueChange = viewModel::updatePlanEditorNotes,
                                    label = { Text("Plan notes") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(TestTags.WorkoutPlanEditor.NotesField),
                                    minLines = 2,
                                    maxLines = 4
                                )
                            }
                        }
                    }

                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${uiState.durationMinutes} min")
                                }
                                Slider(
                                    value = uiState.durationMinutes.toFloat(),
                                    onValueChange = { viewModel.updatePlanEditorDuration(it.toInt()) },
                                    valueRange = 10f..240f,
                                    steps = 22
                                )

                                HorizontalDivider()

                                Text("Location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = uiState.selectedLocationId == null,
                                        onClick = { viewModel.updatePlanEditorLocation(null) },
                                        label = { Text("Any") }
                                    )
                                    uiState.locations.forEach { location ->
                                        FilterChip(
                                            selected = uiState.selectedLocationId == location.id,
                                            onClick = { viewModel.updatePlanEditorLocation(location.id) },
                                            label = { Text(location.name) }
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text("Time slot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TimeSlot.entries.forEach { slot ->
                                        FilterChip(
                                            selected = uiState.selectedTimeSlot == slot,
                                            onClick = { viewModel.updatePlanEditorTimeSlot(slot) },
                                            label = { Text(slot.displayName) }
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text("Plan intent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TrainingPhase.entries.forEach { phase ->
                                        FilterChip(
                                            selected = uiState.sourcePhase == phase,
                                            onClick = { viewModel.updatePlanEditorPhase(phase) },
                                            label = { Text(phase.displayName) }
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text("Focus categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WorkoutCategory.rotationCategories().forEach { category ->
                                        FilterChip(
                                            selected = category in uiState.selectedCategories,
                                            onClick = { viewModel.togglePlanEditorCategory(category) },
                                            label = { Text(category.displayName) },
                                            leadingIcon = { Icon(category.icon, null, modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Set the order and prescription for each exercise in the plan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showAddExerciseDialog = true },
                                modifier = Modifier.testTag(TestTags.WorkoutPlanEditor.AddExerciseButton)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add")
                            }
                        }
                    }

                    if (uiState.selectedExercises.isEmpty()) {
                        item {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("No exercises yet", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Add exercises to define the structure of this reusable workout plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        PlanExerciseSection.entries.forEach { section ->
                            val sectionExercises = uiState.selectedExercises.filter { it.section == section }
                            if (sectionExercises.isNotEmpty()) {
                                item {
                                    Text(
                                        section.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                items(sectionExercises, key = { it.exerciseId }) { exercise ->
                                    PlanExerciseEditorCard(
                                        item = exercise,
                                        canMoveUp = uiState.selectedExercises.firstOrNull()?.exerciseId != exercise.exerciseId,
                                        canMoveDown = uiState.selectedExercises.lastOrNull()?.exerciseId != exercise.exerciseId,
                                        onSectionChanged = { viewModel.updatePlanExerciseSection(exercise.exerciseId, it) },
                                        onSetsChanged = { viewModel.updatePlanExerciseSets(exercise.exerciseId, it) },
                                        onRepsChanged = { viewModel.updatePlanExerciseReps(exercise.exerciseId, it) },
                                        onRestChanged = { viewModel.updatePlanExerciseRest(exercise.exerciseId, it) },
                                        onRoundsChanged = { viewModel.updatePlanExerciseRounds(exercise.exerciseId, it) },
                                        onDurationChanged = { viewModel.updatePlanExerciseDuration(exercise.exerciseId, it) },
                                        onTempoChanged = { viewModel.updatePlanExerciseTempo(exercise.exerciseId, it) },
                                        onEffortTargetChanged = { viewModel.updatePlanExerciseEffortTarget(exercise.exerciseId, it) },
                                        onNotesChanged = { viewModel.updatePlanExerciseNotes(exercise.exerciseId, it) },
                                        onMoveUp = { viewModel.moveExerciseUpInPlanEditor(exercise.exerciseId) },
                                        onMoveDown = { viewModel.moveExerciseDownInPlanEditor(exercise.exerciseId) },
                                        onRemove = { viewModel.removeExerciseFromPlanEditor(exercise.exerciseId) }
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
                                onClick = viewModel::savePlanEditor,
                                enabled = !uiState.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(TestTags.WorkoutPlanEditor.SaveButton)
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (uiState.isSaving) "Saving..." else "Save plan")
                            }
                            Button(
                                onClick = viewModel::playPlanFromEditor,
                                enabled = !uiState.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(TestTags.WorkoutPlanEditor.SaveAndPlayButton)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save & play")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExerciseDialog) {
        AddExerciseToPlanDialog(
            exercises = uiState.availableExercises,
            selectedExerciseIds = uiState.selectedExercises.map { it.exerciseId }.toSet(),
            onDismiss = { showAddExerciseDialog = false },
            onSelectExercise = {
                viewModel.addExerciseToPlanEditor(it)
                showAddExerciseDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete workout plan?") },
            text = { Text("This removes the reusable plan but does not delete completed workout history.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePlanEditor()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlanEditorHeaderCard(
    isExisting: Boolean,
    onSave: () -> Unit,
    onPlay: () -> Unit,
    isSaving: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (isExisting) "Refine and reuse this plan" else "Build a reusable workout plan from scratch",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Manual plans are ideal when you already know the structure you want and just need a fast way to replay it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onSave, label = { Text(if (isSaving) "Saving..." else "Save") })
                AssistChip(onClick = onPlay, label = { Text("Save & play") })
            }
        }
    }
}

@Composable
private fun PlanExerciseEditorCard(
    item: PlanExerciseEditorItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSectionChanged: (PlanExerciseSection) -> Unit,
    onSetsChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onRestChanged: (String) -> Unit,
    onRoundsChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onTempoChanged: (String) -> Unit,
    onEffortTargetChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tune the prescription for this plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Default.ArrowUpward, "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Default.ArrowDownward, "Move down")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, "Remove")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanExerciseSection.entries.forEach { section ->
                    FilterChip(
                        selected = item.section == section,
                        onClick = { onSectionChanged(section) },
                        label = { Text(section.displayName) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.sets.toString(),
                    onValueChange = onSetsChanged,
                    label = { Text("Sets") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.reps,
                    onValueChange = onRepsChanged,
                    label = { Text("Reps / time") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.restSeconds.toString(),
                    onValueChange = onRestChanged,
                    label = { Text("Rest (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.rounds.toString(),
                    onValueChange = onRoundsChanged,
                    label = { Text("Rounds") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.durationSeconds?.toString().orEmpty(),
                    onValueChange = onDurationChanged,
                    label = { Text("Work (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.tempo,
                    onValueChange = onTempoChanged,
                    label = { Text("Tempo") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.effortTarget,
                    onValueChange = onEffortTargetChanged,
                    label = { Text("Effort target") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = item.notes,
                onValueChange = onNotesChanged,
                label = { Text("Coaching notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseToPlanDialog(
    exercises: List<Exercise>,
    selectedExerciseIds: Set<Long>,
    onDismiss: () -> Unit,
    onSelectExercise: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val available = remember(exercises, selectedExerciseIds, query) {
        exercises
            .filter { it.id !in selectedExerciseIds }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise to plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.WorkoutPlanEditor.ExerciseSearchField),
                    singleLine = true
                )
                if (available.isEmpty()) {
                    Text(
                        "No matching exercises available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(320.dp)) {
                        items(available, key = { it.id }) { exercise ->
                            OutlinedCard(
                                onClick = { onSelectExercise(exercise.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.WorkoutPlanEditor.exerciseOption(exercise.id))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (exercise.description.isNotBlank()) {
                                        Text(
                                            exercise.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        "${exercise.defaultSets} sets • ${exercise.defaultReps} • ${exercise.defaultRestSeconds}s rest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    exercise.resolveBalancedProgrammingPreset()
                                        .toRichPrescriptionData()
                                        .displaySummary()
                                        .takeIf { it.isNotBlank() }
                                        ?.let { summary ->
                                            Text(
                                                summary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
