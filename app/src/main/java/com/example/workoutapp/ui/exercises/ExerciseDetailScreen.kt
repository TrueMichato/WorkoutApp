package com.example.workoutapp.ui.exercises

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.workoutapp.data.model.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToExercise: (Long) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(exerciseId) {
        viewModel.loadExercise(exerciseId)
    }

    // Archive removes the exercise from normal library flows and returns to the list.
    LaunchedEffect(uiState.archiveCompleted) {
        if (uiState.archiveCompleted) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (uiState.exercise?.isFavorite == true)
                                Icons.Default.Favorite
                            else
                                Icons.Default.FavoriteBorder,
                            "Favorite",
                            tint = if (uiState.exercise?.isFavorite == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (uiState.exercise?.isArchived == true) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                onClick = {
                                    showMenu = false
                                    viewModel.restoreExercise()
                                },
                                leadingIcon = { Icon(Icons.Default.Unarchive, null) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = {
                                    showMenu = false
                                    viewModel.archiveExercise()
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.exercise != null) {
            val exercise = uiState.exercise!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Media gallery
                if (uiState.localMediaUris.isNotEmpty() || uiState.externalUrls.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.localMediaUris) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Exercise media",
                                modifier = Modifier
                                    .height(200.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        items(uiState.externalUrls) { url ->
                            ExternalVideoCard(
                                url = url,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.error?.let { error ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (uiState.dataWarnings.isNotEmpty()) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Saved data needs attention",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                uiState.dataWarnings.forEach { warning ->
                                    Text(
                                        warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Header info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                exercise.difficulty.displayName,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (exercise.isCompound) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "Compound",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        if (exercise.isUnilateral) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "Unilateral",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        if (exercise.isArchived) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "Archived",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Description
                    if (exercise.description.isNotEmpty()) {
                        Text(
                            exercise.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Exercise family: this exercise's main exercise (if it's a variation), or
                    // its own variations (if it is the main exercise).
                    val parentExercise = uiState.parentExercise
                    if (parentExercise != null) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToExercise(parentExercise.id) }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Variation of \"${parentExercise.name}\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (uiState.variationFocus.isNotBlank()) {
                                    Text(
                                        uiState.variationFocus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (uiState.variations.isNotEmpty()) {
                        SectionHeader("Variations")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.variations.forEach { variation ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToExercise(variation.exercise.id) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(variation.exercise.name, style = MaterialTheme.typography.titleSmall)
                                        if (variation.focus.isNotBlank()) {
                                            Text(
                                                variation.focus,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Categories
                    if (uiState.categories.isNotEmpty()) {
                        SectionHeader("Categories")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.categories.forEach { category ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(category.displayName) },
                                    leadingIcon = {
                                        Icon(category.icon, null, Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }

                    // Equipment
                    if (uiState.equipment.isNotEmpty()) {
                        SectionHeader("Equipment Required")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.equipment.forEach { equipment ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(equipment.name) },
                                    leadingIcon = {
                                        Icon(Icons.Default.FitnessCenter, null, Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }

                    // Target Muscles
                    if (uiState.primaryMuscles.isNotEmpty() || uiState.secondaryMuscles.isNotEmpty()) {
                        SectionHeader("Target Muscles")
                        if (uiState.primaryMuscles.isNotEmpty()) {
                            Text(
                                "Primary",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                uiState.primaryMuscles.forEach { muscle ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(muscle.displayName) }
                                    )
                                }
                            }
                        }
                        if (uiState.secondaryMuscles.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Secondary",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                uiState.secondaryMuscles.forEach { muscle ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(muscle.displayName) }
                                    )
                                }
                            }
                        }
                    }

                    // Programming presets
                    SectionHeader("Programming")
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Current goal phase: ${uiState.currentPhase.displayName}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrainingPhase.entries.forEach { phase ->
                            val preset = uiState.programmingPresets[phase] ?: return@forEach
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (phase == uiState.currentPhase) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
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
                                            Text(
                                                phase.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                phase.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (phase == uiState.currentPhase) {
                                            AssistChip(onClick = {}, label = { Text("Current") })
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        ParameterItem("Sets", preset.setsText)
                                        ParameterItem("Reps", preset.repsText)
                                        ParameterItem("Rest", "${preset.restSeconds}s")
                                    }
                                    preset.toRichPrescriptionData().displaySummary().takeIf { it.isNotBlank() }?.let { summary ->
                                        Text(
                                            summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (preset.notes.isNotBlank()) {
                                        Text(
                                            preset.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Instructions
                    if (exercise.instructions.isNotEmpty()) {
                        SectionHeader("Instructions")
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                exercise.instructions,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Tips
                    if (exercise.tips.isNotEmpty()) {
                        SectionHeader("Tips & Common Mistakes")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    exercise.tips,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Personal Notes
                    if (exercise.personalNotes.isNotEmpty()) {
                        SectionHeader("Your Notes")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(
                                exercise.personalNotes,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Stats
                    SectionHeader("Statistics")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            StatRow("Times Performed", exercise.timesPerformed.toString())
                            if (exercise.lastPerformedAt != null) {
                                val daysAgo = ((System.currentTimeMillis() - exercise.lastPerformedAt) / (1000 * 60 * 60 * 24)).toInt()
                                StatRow(
                                    "Last Performed",
                                    when {
                                        daysAgo == 0 -> "Today"
                                        daysAgo == 1 -> "Yesterday"
                                        else -> "$daysAgo days ago"
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    uiState.error?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(error)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ParameterItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExternalVideoCard(
    url: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .height(200.dp)
            .aspectRatio(16f / 9f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Play video",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Watch Video",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    when {
                        url.contains("youtube") -> "YouTube"
                        url.contains("vimeo") -> "Vimeo"
                        else -> "External Link"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
