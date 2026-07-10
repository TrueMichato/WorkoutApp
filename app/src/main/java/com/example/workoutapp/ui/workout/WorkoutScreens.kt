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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workoutapp.data.model.ExerciseCompletionState
import com.example.workoutapp.data.model.SessionExercise
import com.example.workoutapp.data.model.SessionStatus
import com.example.workoutapp.data.model.SetEntryFieldErrors
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.SetMetricVisibility
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutPlanTemplateSummary
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.model.decodePersistedEnumNameList
import com.example.workoutapp.data.model.displaySummary
import com.example.workoutapp.data.model.toRichPrescriptionDataOrNull
import com.example.workoutapp.domain.PlannedExerciseSummary
import com.example.workoutapp.domain.SessionHistorySummary
import com.example.workoutapp.domain.WorkoutPlanDraft
import com.example.workoutapp.domain.WorkoutTrackingSummary
import com.example.workoutapp.ui.test.TestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onNavigateToGenerateWorkout: () -> Unit,
    onNavigateToActiveWorkout: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAddPlan: () -> Unit,
    onNavigateToEditPlan: (Long) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.overviewUiState.collectAsStateWithLifecycle()
    val generatorUiState by viewModel.generatorUiState.collectAsStateWithLifecycle()

    LaunchedEffect(generatorUiState.generatedSessionId) {
        generatorUiState.generatedSessionId?.let { sessionId ->
            viewModel.clearGeneratedSession()
            onNavigateToActiveWorkout(sessionId)
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.Workout.Screen),
        topBar = {
            TopAppBar(
                title = { Text("Workouts") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "History")
                    }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onNavigateToGenerateWorkout,
                icon = { Icon(Icons.Default.AutoAwesome, null) },
                text = { Text("New Workout") }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OverviewHeroCard(
                        activeSession = uiState.activeSession,
                        onGenerateWorkout = onNavigateToGenerateWorkout,
                        onResumeWorkout = { uiState.activeSession?.id?.let(onNavigateToActiveWorkout) }
                    )
                }

                if (uiState.todaysSessions.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Today",
                            subtitle = "Multiple sessions per day are sorted by time slot."
                        )
                    }
                    items(uiState.todaysSessions, key = { it.id }) { session ->
                        SessionSummaryCard(
                            session = session,
                            showStatus = true,
                            onClick = { onNavigateToActiveWorkout(session.id) }
                        )
                    }
                }

                if (uiState.recentSessions.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recent work",
                            subtitle = "Use history to keep variety without losing progression."
                        )
                    }
                    items(uiState.recentSessions.take(3), key = { it.id }) { session ->
                        SessionSummaryCard(
                            session = session,
                            showStatus = false,
                            onClick = onNavigateToHistory
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "Reusable plans",
                            subtitle = "Save a good draft once, then replay it into a fresh session anytime."
                        )
                        OutlinedButton(
                            onClick = onNavigateToAddPlan,
                            modifier = Modifier.testTag(TestTags.Workout.NewPlanButton)
                        ) {
                            Text("New plan")
                        }
                    }
                }

                if (uiState.savedPlans.isEmpty()) {
                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("No saved plans yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Preview a smart workout, then save it as a reusable plan for fast repeat sessions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = onNavigateToAddPlan, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create your first plan")
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.savedPlans.take(5), key = { it.id }) { plan ->
                        SavedPlanCard(
                            plan = plan,
                            onOpenPlan = { onNavigateToEditPlan(plan.id) },
                            onPlayPlan = { viewModel.playPlan(plan.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutGeneratorScreen(
    onNavigateBack: () -> Unit,
    onWorkoutGenerated: (Long) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.generatorUiState.collectAsStateWithLifecycle()
    var showSavePlanDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.generatedSessionId) {
        uiState.generatedSessionId?.let { sessionId ->
            onWorkoutGenerated(sessionId)
            viewModel.clearGeneratedSession()
        }
    }

    LaunchedEffect(uiState.planSaveMessage) {
        if (uiState.planSaveMessage != null) {
            showSavePlanDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Workout") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Smart generator", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Goal phase: ${uiState.currentPhase.displayName}. Auto mode uses goal weighting, undertrained-category protection, and spacing rules before it saves a session.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.locations.forEach { location ->
                            FilterChip(
                                selected = uiState.selectedLocationId == location.id,
                                onClick = { viewModel.selectLocation(location.id) },
                                label = { Text(location.name) },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp)) }
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Duration", style = MaterialTheme.typography.titleMedium)
                                Text("${uiState.durationMinutes} min", style = MaterialTheme.typography.titleMedium)
                            }
                            Slider(
                                value = uiState.durationMinutes.toFloat(),
                                onValueChange = { viewModel.updateDuration(it.toInt()) },
                                valueRange = 15f..120f,
                                steps = 20
                            )
                            Text(
                                "Short sessions stay tighter and compound-focused. Longer sessions widen category coverage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text("Time slot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimeSlot.entries.forEach { slot ->
                            FilterChip(
                                selected = uiState.selectedTimeSlot == slot,
                                onClick = { viewModel.selectTimeSlot(slot) },
                                label = { Text(slot.displayName) },
                                leadingIcon = { Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (uiState.selectedCategories.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearCategories) {
                                Text("Clear")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WorkoutCategory.rotationCategories().forEach { category ->
                            FilterChip(
                                selected = category in uiState.selectedCategories,
                                onClick = { viewModel.toggleCategory(category) },
                                label = { Text(category.displayName) },
                                leadingIcon = { Icon(category.icon, null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (uiState.selectedCategories.isEmpty()) {
                            "Auto mode can pull in an undertrained category even if it is not your highest-weight focus, so nothing gets forgotten."
                        } else {
                            "Selected categories stay in focus, while the generator still spaces repeated stress and avoids same-category clustering."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.previewDraft?.let { draft ->
                    item {
                        WorkoutPreviewCard(
                            draft = draft,
                            currentPhase = uiState.currentPhase,
                            hasCustomizations = uiState.hasPreviewCustomizations,
                            isBusy = uiState.isPreviewing || uiState.isGenerating,
                            isSavingPlan = uiState.isSavingPlan,
                            onReject = viewModel::rejectPreviewExercise,
                            onSwap = viewModel::swapPreviewExercise,
                            onReset = viewModel::resetPreviewChanges,
                            onSaveAsPlan = { showSavePlanDialog = true }
                        )
                    }
                }

                uiState.planSaveMessage?.let { message ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    message,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(onClick = viewModel::clearPlanSaveMessage) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                uiState.warning?.let { warning ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                warning,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                uiState.error?.let { error ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::previewWorkout,
                            enabled = !uiState.isPreviewing && !uiState.isGenerating,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isPreviewing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.AccessTime, null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.previewDraft == null) "Preview" else "Refresh Preview")
                        }
                        Button(
                            onClick = viewModel::generateWorkout,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isGenerating && !uiState.isPreviewing
                        ) {
                            if (uiState.isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isGenerating) "Building..." else "Generate Workout")
                        }
                    }
                }
            }
        }
    }

    if (showSavePlanDialog) {
        SaveWorkoutPlanDialog(
            initialName = uiState.previewDraft?.session?.name.orEmpty(),
            isSaving = uiState.isSavingPlan,
            onDismiss = {
                showSavePlanDialog = false
                viewModel.clearPlanSaveMessage()
            },
            onSave = { name, description -> viewModel.savePreviewAsPlan(name, description) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutPreviewCard(
    draft: WorkoutPlanDraft,
    currentPhase: TrainingPhase,
    hasCustomizations: Boolean,
    isBusy: Boolean,
    isSavingPlan: Boolean,
    onReject: (PlannedExerciseSummary) -> Unit,
    onSwap: (PlannedExerciseSummary) -> Unit,
    onReset: () -> Unit,
    onSaveAsPlan: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(draft.session.name, style = MaterialTheme.typography.titleMedium)
                }
                if (hasCustomizations) {
                    TextButton(onClick = onReset, enabled = !isBusy) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset")
                    }
                }
            }
            Text(
                "Estimated ${draft.estimatedDurationMinutes} min • ${draft.exerciseSummaries.size} exercises",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(currentPhase.displayName) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp)) }
                )
                OutlinedButton(
                    onClick = onSaveAsPlan,
                    enabled = !isBusy && !isSavingPlan
                ) {
                    if (isSavingPlan) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    if (isSavingPlan) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isSavingPlan) "Saving..." else "Save as plan")
                }
            }
            if (hasCustomizations) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "You’re customizing the suggestion. Reject removes an exercise from this preview. Swap refreshes the preview without it and tries to replace it.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                draft.focusCategories.forEach { category ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            category.displayName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.reasoning.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            draft.exerciseSummaries.forEachIndexed { index, summary ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${index + 1}. ${summary.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${summary.sets} sets • ${summary.reps} • ${summary.restSeconds}s rest • ~${summary.estimatedMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            summary.categories.joinToString(separator = " • ") { it.displayName },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (summary.recommendationReason.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "Why this fits: ${summary.recommendationReason}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onReject(summary) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject")
                            }
                            Button(
                                onClick = { onSwap(summary) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Swap")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPlanCard(
    plan: WorkoutPlanTemplateSummary,
    onOpenPlan: () -> Unit,
    onPlayPlan: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPlan) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (plan.description.isNotBlank()) {
                        Text(
                            plan.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AssistChip(onClick = {}, label = { Text(plan.sourcePhase.displayName) })
            }

            Text(
                "${plan.exerciseCount} exercises • ${plan.targetDurationMinutes} min • ${plan.scheduledTimeSlot.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            parseTargetCategories(plan.targetCategories).takeIf { it.isNotBlank() }?.let { categories ->
                Text(categories, style = MaterialTheme.typography.labelMedium)
            }

            Button(onClick = onPlayPlan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play plan")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveWorkoutPlanDialog(
    initialName: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save reusable workout plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This saves the current preview so you can replay the same structure later as a fresh workout session.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan name") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), description.trim()) },
                enabled = name.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    onWorkoutCompleted: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.activeWorkoutUiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.loadActiveWorkout(sessionId)
    }

    LaunchedEffect(uiState.completedWorkoutId) {
        if (uiState.completedWorkoutId == sessionId) {
            viewModel.clearCompletedWorkout()
            onWorkoutCompleted()
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.ActiveWorkout.Screen),
        topBar = {
            TopAppBar(
                title = { Text(uiState.session?.name ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(paddingValues))
            uiState.session == null -> EmptyState(
                modifier = Modifier.padding(paddingValues),
                title = "Workout not found",
                subtitle = "This session may have been removed."
            )
            else -> {
                val session = requireNotNull(uiState.session)
                val completedCount = uiState.exercises.count { it.completionState == ExerciseCompletionState.COMPLETED }
                val skippedCount = uiState.exercises.count { it.completionState == ExerciseCompletionState.SKIPPED }
                val loggedCount = uiState.exercises.count { it.completionState == ExerciseCompletionState.LOGGED }
                val progress = if (uiState.exercises.isEmpty()) 0f else completedCount / uiState.exercises.size.toFloat()
                val currentItem = uiState.exercises.firstOrNull { it.sessionExercise.id == uiState.focusedExerciseId }
                val upcomingItems = uiState.exercises.filterNot { it.sessionExercise.id == currentItem?.sessionExercise?.id }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .testTag(TestTags.ActiveWorkout.ContentList),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.name, style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            "${session.targetDurationMinutes} min • ${session.scheduledTimeSlot.displayName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SessionStatusBadge(status = session.status)
                                }

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                   "$completedCount done • $skippedCount skipped • $loggedCount logged only",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (session.status == SessionStatus.PLANNED) {
                                    Button(
                                        onClick = viewModel::startWorkout,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Start workout")
                                    }
                                }
                            }
                        }
                    }

                    uiState.error?.let { error ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (currentItem != null) {
                        item {
                            CurrentExerciseCard(
                                item = currentItem,
                                onCompletedChanged = { completed ->
                                    viewModel.toggleExerciseCompleted(currentItem.sessionExercise, completed)
                                },
                                onSkippedChanged = { skipped ->
                                    viewModel.toggleExerciseSkipped(currentItem.sessionExercise, skipped)
                                },
                                onDraftRepsChanged = { viewModel.updateSetDraftReps(currentItem.sessionExercise.id, it) },
                                onDraftWeightChanged = { viewModel.updateSetDraftWeight(currentItem.sessionExercise.id, it) },
                                onDraftDurationChanged = { viewModel.updateSetDraftDuration(currentItem.sessionExercise.id, it) },
                                onDraftRpeChanged = { viewModel.updateSetDraftRpe(currentItem.sessionExercise.id, it) },
                                onDraftNotesChanged = { viewModel.updateSetDraftNotes(currentItem.sessionExercise.id, it) },
                                onSaveSet = { viewModel.saveSetLog(currentItem) },
                                onDeleteSet = viewModel::deleteSetLog,
                                onRepeatLastSet = { viewModel.repeatLastSet(currentItem) }
                            )
                        }
                    }

                    if (upcomingItems.isNotEmpty()) {
                        item {
                            Text(
                                if (currentItem == null) "Exercises" else "Up next",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(upcomingItems, key = { it.sessionExercise.id }) { item ->
                        UpcomingExerciseRow(
                            item = item,
                            onFocus = { viewModel.setFocusedExercise(item.sessionExercise.id) }
                        )
                    }

                    item {
                        CompletionFeedbackCard(
                            uiState = uiState,
                            onDifficultyChanged = viewModel::updatePerceivedDifficulty,
                            onEnergyChanged = viewModel::updateEnergyLevel,
                            onSatisfactionChanged = viewModel::updateSatisfaction,
                            onNotesChanged = viewModel::updateCompletionNotes,
                            onCompleteWorkout = viewModel::completeWorkout
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.historyUiState.collectAsStateWithLifecycle()
    val expandedIds = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(paddingValues))
            uiState.sessions.isEmpty() -> EmptyState(
                modifier = Modifier.padding(paddingValues),
                title = "No workout history yet",
                subtitle = "Generate a workout and complete it to start building your rotation memory."
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status filter chips
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedStatus == null,
                            onClick = { viewModel.setHistoryStatusFilter(null) },
                            label = { Text("All") }
                        )
                        listOf(SessionStatus.COMPLETED, SessionStatus.IN_PROGRESS, SessionStatus.PARTIAL, SessionStatus.SKIPPED).forEach { status ->
                            FilterChip(
                                selected = uiState.selectedStatus == status,
                                onClick = { viewModel.setHistoryStatusFilter(status) },
                                label = { Text(status.name.replace('_', ' ')) }
                            )
                        }
                    }
                }

                items(uiState.sessions, key = { it.session.id }) { summary ->
                    val expanded = expandedIds[summary.session.id] == true
                    HistorySessionCard(
                        summary = summary,
                        expanded = expanded,
                        onToggleExpand = { expandedIds[summary.session.id] = !expanded }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewHeroCard(
    activeSession: WorkoutSession?,
    onGenerateWorkout: () -> Unit,
    onResumeWorkout: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Ultimate workout brain", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Generate sessions based on your available equipment, time, and rotation balance so you keep progressing without repeating the same ideas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onGenerateWorkout, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate")
                }
                Button(
                    onClick = onResumeWorkout,
                    enabled = activeSession != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resume")
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(
    session: WorkoutSession,
    showStatus: Boolean,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatSessionDate(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showStatus) {
                    SessionStatusBadge(status = session.status)
                }
            }

            val categorySummary = parseTargetCategories(session.targetCategories)
            if (categorySummary.isNotEmpty()) {
                Text(
                    categorySummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "${session.targetDurationMinutes} min • ${session.scheduledTimeSlot.displayName}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun CurrentExerciseCard(
    item: SessionExerciseUi,
    onCompletedChanged: (Boolean) -> Unit,
    onSkippedChanged: (Boolean) -> Unit,
    onDraftRepsChanged: (String) -> Unit,
    onDraftWeightChanged: (String) -> Unit,
    onDraftDurationChanged: (String) -> Unit,
    onDraftRpeChanged: (String) -> Unit,
    onDraftNotesChanged: (String) -> Unit,
    onSaveSet: () -> Unit,
    onDeleteSet: (SetLog) -> Unit,
    onRepeatLastSet: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val metrics = item.metricVisibility
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.ActiveWorkout.CurrentExerciseCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "CURRENT EXERCISE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                item.exercise?.name ?: "Exercise ${item.sessionExercise.exerciseId}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            item.exercise?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${item.sessionExercise.plannedSets} sets • ${item.sessionExercise.plannedReps} • ${item.sessionExercise.plannedRestSeconds}s rest",
                style = MaterialTheme.typography.bodyLarge
            )
            val richPrescription = item.sessionExercise.prescriptionJson.toRichPrescriptionDataOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        item.sessionExercise.section.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                richPrescription?.displaySummary()?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val statusLabel = statusLabel(item)
                AssistChip(
                    onClick = {},
                    modifier = Modifier.semantics { stateDescription = statusLabel },
                    label = { Text(statusLabel) }
                )
            }
            item.sessionExercise.notes.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Logging sets saves performance only. Tap Done when the exercise is finished, or Skip if you did not perform it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Complete / Skip toggles — big, unambiguous, one-handed targets.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = item.sessionExercise.isCompleted,
                    onClick = { onCompletedChanged(!item.sessionExercise.isCompleted) },
                    label = { Text("Done") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.ActiveWorkout.DoneButton)
                        .semantics {
                            stateDescription = if (item.sessionExercise.isCompleted) "Marked done" else "Not marked done"
                        }
                )
                FilterChip(
                    selected = item.sessionExercise.isSkipped,
                    onClick = { onSkippedChanged(!item.sessionExercise.isSkipped) },
                    label = { Text("Skip") },
                    leadingIcon = { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.ActiveWorkout.SkipButton)
                        .semantics {
                            stateDescription = if (item.sessionExercise.isSkipped) "Marked skipped" else "Not skipped"
                        }
                )
            }

            // Already logged sets
            if (item.setLogs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Logged sets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    item.setLogs.forEach { setLog ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Set ${setLog.setNumber}", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        buildSetLogLabel(setLog),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSet(setLog) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "Delete set ${setLog.setNumber}")
                                }
                            }
                        }
                    }
                }
            }

            // Log next set form — only the metrics that apply to this exercise's prescription.
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Log set ${item.setLogs.size + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.setLogs.isNotEmpty()) {
                            AssistChip(
                                onClick = onRepeatLastSet,
                                leadingIcon = { Icon(Icons.Default.Replay, null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Repeat last set") },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TestTags.ActiveWorkout.RepeatLastSetButton)
                            )
                        }
                    }

                    if (metrics.showReps || metrics.showWeight) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (metrics.showReps) {
                                SteppedNumberField(
                                    value = item.draft.reps,
                                    onValueChange = onDraftRepsChanged,
                                    label = "Reps",
                                    step = 1.0,
                                    keyboardType = KeyboardType.Number,
                                    errorText = item.fieldErrors.reps,
                                    testTag = TestTags.ActiveWorkout.RepsField,
                                    modifier = Modifier.weight(1f),
                                    focusManager = focusManager
                                )
                            }
                            if (metrics.showWeight) {
                                SteppedNumberField(
                                    value = item.draft.weight,
                                    onValueChange = onDraftWeightChanged,
                                    label = "Weight (kg)",
                                    step = 2.5,
                                    keyboardType = KeyboardType.Decimal,
                                    errorText = item.fieldErrors.weight,
                                    testTag = TestTags.ActiveWorkout.WeightField,
                                    modifier = Modifier.weight(1f),
                                    focusManager = focusManager
                                )
                            }
                        }
                    }
                    if (metrics.showDuration) {
                        OutlinedTextField(
                            value = item.draft.durationSeconds,
                            onValueChange = onDraftDurationChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.ActiveWorkout.DurationField),
                            label = { Text("Duration (s)") },
                            singleLine = true,
                            isError = item.fieldErrors.durationSeconds != null,
                            supportingText = item.fieldErrors.durationSeconds?.let { errorText -> { Text(errorText) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                        )
                    }
                    OutlinedTextField(
                        value = item.draft.rpe,
                        onValueChange = onDraftRpeChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.ActiveWorkout.RpeField),
                        label = { Text("RPE (1-10, optional)") },
                        singleLine = true,
                        isError = item.fieldErrors.rpe != null,
                        supportingText = item.fieldErrors.rpe?.let { errorText -> { Text(errorText) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                    )
                    OutlinedTextField(
                        value = item.draft.notes,
                        onValueChange = onDraftNotesChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.ActiveWorkout.NotesField),
                        label = { Text("Set notes (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSaveSet()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(TestTags.ActiveWorkout.SaveSetButton)
                    ) {
                        Text("Save Set")
                    }
                }
            }
        }
    }
}

@Composable
private fun SteppedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    step: Double,
    keyboardType: KeyboardType,
    errorText: String?,
    testTag: String,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(testTag),
        label = { Text(label) },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange(adjustNumericString(value, -step)) },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Decrease $label" }
                ) {
                    Icon(Icons.Default.Remove, null)
                }
                IconButton(
                    onClick = { onValueChange(adjustNumericString(value, step)) },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Increase $label" }
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    )
}

private fun adjustNumericString(current: String, delta: Double): String {
    // Guard against non-finite base values (NaN/Infinity from paste or accessibility input) so
    // the stepper never produces a non-finite draft; fall back to a fresh baseline instead.
    val parsedBase = current.trim().toDoubleOrNull()
    val base = if (parsedBase != null && parsedBase.isFinite()) parsedBase else 0.0
    val next = (base + delta).coerceAtLeast(0.0)
    return if (next % 1.0 == 0.0) next.toInt().toString() else String.format(Locale.US, "%.1f", next)
}

private fun statusLabel(item: SessionExerciseUi): String = when (item.completionState) {
    ExerciseCompletionState.NOT_STARTED -> "Not started"
    ExerciseCompletionState.LOGGED -> "${item.setLogs.size} set(s) logged"
    ExerciseCompletionState.COMPLETED -> "Done"
    ExerciseCompletionState.SKIPPED -> "Skipped"
}

@Composable
private fun UpcomingExerciseRow(
    item: SessionExerciseUi,
    onFocus: () -> Unit
) {
    val statusLabel = statusLabel(item)
    OutlinedCard(
        onClick = onFocus,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.ActiveWorkout.upcomingRow(item.sessionExercise.id))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.exercise?.name ?: "Exercise ${item.sessionExercise.exerciseId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${item.sessionExercise.plannedSets} sets • ${item.sessionExercise.plannedReps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val statusContainer = when (item.completionState) {
                ExerciseCompletionState.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                ExerciseCompletionState.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            Surface(
                color = statusContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.semantics { stateDescription = statusLabel }
            ) {
                Text(
                    statusLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun CompletionFeedbackCard(
    uiState: ActiveWorkoutUiState,
    onDifficultyChanged: (Int) -> Unit,
    onEnergyChanged: (Int) -> Unit,
    onSatisfactionChanged: (Int) -> Unit,
    onNotesChanged: (String) -> Unit,
    onCompleteWorkout: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Feedback", style = MaterialTheme.typography.titleLarge)
            RatingSlider(
                label = "Perceived difficulty",
                value = uiState.completionInput.perceivedDifficulty,
                rangeLabel = "1 = easy, 10 = brutal",
                onValueChanged = onDifficultyChanged
            )
            RatingSlider(
                label = "Energy after workout",
                value = uiState.completionInput.energyLevel,
                rangeLabel = "1 = drained, 10 = energized",
                onValueChanged = onEnergyChanged
            )
            RatingSlider(
                label = "Satisfaction",
                value = uiState.completionInput.satisfactionRating,
                rangeLabel = "1 = weak fit, 5 = nailed it",
                min = 1,
                max = 5,
                onValueChanged = onSatisfactionChanged
            )
            OutlinedTextField(
                value = uiState.completionInput.notes,
                onValueChange = onNotesChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Session notes") },
                placeholder = { Text("What felt good, bad, or worth repeating?") },
                minLines = 3
            )
            val finalStatusLabel = if (
                uiState.exercises.isNotEmpty() &&
                uiState.exercises.all { it.completionState == ExerciseCompletionState.COMPLETED }
            ) {
                "This will save a completed workout."
            } else {
                "This will save a partial workout; logged-only and skipped exercises will not count as completed."
            }
            Text(
                finalStatusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCompleteWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(TestTags.ActiveWorkout.CompleteWorkoutButton),
                enabled = !uiState.isCompleting
            ) {
                if (uiState.isCompleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Check, null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.isCompleting) "Saving workout..." else "Finish Workout")
            }
        }
    }
}

@Composable
private fun RatingSlider(
    label: String,
    value: Int,
    rangeLabel: String,
    min: Int = 1,
    max: Int = 10,
    onValueChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.toInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0)
        )
        Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionStatusBadge(status: SessionStatus) {
    val (container, content) = when (status) {
        SessionStatus.PLANNED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SessionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SessionStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SessionStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        SessionStatus.PARTIAL -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text = status.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySessionCard(
    summary: SessionHistorySummary,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(onClick = onToggleExpand, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatSessionDate(summary.session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SessionStatusBadge(status = summary.session.status)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Summary row
            Text(
                "${summary.completedExerciseCount}/${summary.exercises.size} exercises • ${summary.totalLoggedSets} sets • vol ${WorkoutTrackingSummary.formatVolume(summary.totalVolume)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            summary.session.postSessionNotes.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            // Expanded: per-exercise detail
            if (expanded) {
                summary.exercises.forEach { exSummary ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                exSummary.exercise?.name ?: "Exercise ${exSummary.sessionExercise.exerciseId}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${exSummary.setLogs.size} sets • ${exSummary.totalReps} reps • vol ${WorkoutTrackingSummary.formatVolume(exSummary.totalVolume)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            exSummary.setLogs.forEach { log ->
                                Text(
                                    "• ${buildSetLogLabel(log)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildSetLogLabel(setLog: SetLog): String {
    val parts = buildList {
        setLog.reps?.let { add("$it reps") }
        setLog.weight?.let { add("${if (it % 1f == 0f) it.toInt() else it} kg") }
        setLog.durationSeconds?.let { add("${it}s") }
        setLog.rpe?.let { add("RPE $it") }
    }
    return parts.joinToString(" • ").ifBlank { "Logged" }
}

private fun parseTargetCategories(targetCategories: String): String {
    val result = decodePersistedEnumNameList<WorkoutCategory>("target categories", targetCategories)
    return if (result.hasIssues) {
        "Saved categories need attention"
    } else {
        result.value.joinToString(separator = " • ") { it.displayName }
    }
}

private fun formatSessionDate(session: WorkoutSession): String {
    val timestamp = session.completedAt ?: session.startedAt ?: session.plannedDate
    val dateLabel = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    return "$dateLabel • ${session.scheduledTimeSlot.displayName}"
}
