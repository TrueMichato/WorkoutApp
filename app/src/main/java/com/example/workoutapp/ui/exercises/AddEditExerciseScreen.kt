package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.workoutapp.data.model.*
import com.example.workoutapp.ui.test.TestTags

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditExerciseScreen(
    exerciseId: Long?,
    onNavigateBack: () -> Unit,
    newVariationOfParentId: Long? = null,
    viewModel: AddEditExerciseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = exerciseId != null

    // Load exercise if editing, or pre-fill a brand-new variation of newVariationOfParentId.
    LaunchedEffect(exerciseId, newVariationOfParentId) {
        if (exerciseId != null) {
            viewModel.loadExercise(exerciseId)
        } else if (newVariationOfParentId != null) {
            viewModel.loadNewVariation(newVariationOfParentId)
        }
    }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addLocalMedia(uris)
    }

    // Video picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addLocalMedia(listOf(it)) }
    }

    // Handle save success
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.AddEditExercise.Screen),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Exercise" else "Add Exercise") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveExercise() },
                        enabled = uiState.name.isNotBlank() && !uiState.isSaving,
                        modifier = Modifier.testTag(TestTags.AddEditExercise.SaveButton)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        var showEquipmentDialog by remember { mutableStateOf(false) }
        var showMuscleDialog by remember { mutableStateOf(false) }
        var showUrlDialog by remember { mutableStateOf(false) }
        var showFamilyParentDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag(TestTags.AddEditExercise.ContentList),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Exercise Name *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.AddEditExercise.NameField),
                singleLine = true,
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } }
            )

            // Description
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Instructions
            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = viewModel::updateInstructions,
                label = { Text("Instructions") },
                placeholder = { Text("Step-by-step instructions...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            // Tips
            OutlinedTextField(
                value = uiState.tips,
                onValueChange = viewModel::updateTips,
                label = { Text("Tips & Common Mistakes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            HorizontalDivider()

            // Difficulty
            Text(
                "Difficulty",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Difficulty.entries.forEachIndexed { index, difficulty ->
                    SegmentedButton(
                        selected = uiState.difficulty == difficulty,
                        onClick = { viewModel.updateDifficulty(difficulty) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Difficulty.entries.size
                        )
                    ) {
                        Text(
                            difficulty.displayName,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            HorizontalDivider()

            // Categories
            Text(
                "Categories *",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Select all that apply",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkoutCategory.rotationCategories().forEach { category ->
                    FilterChip(
                        selected = category in uiState.selectedCategories,
                        onClick = { viewModel.toggleCategory(category) },
                        label = { Text(category.displayName) },
                        leadingIcon = if (category in uiState.selectedCategories) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            HorizontalDivider()

            // Equipment
            Text(
                "Required Equipment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Equipment selection
            if (uiState.selectedEquipment.isEmpty()) {
                OutlinedCard(
                    onClick = { showEquipmentDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Equipment")
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.selectedEquipment.forEach { equipment ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeEquipment(equipment) },
                            label = { Text(equipment.name) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp))
                            }
                        )
                    }
                    AssistChip(
                        onClick = { showEquipmentDialog = true },
                        label = { Text("Add more") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
                    )
                }
            }

            HorizontalDivider()

            // Muscle Groups
            Text(
                "Target Muscles",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (uiState.primaryMuscles.isEmpty() && uiState.secondaryMuscles.isEmpty()) {
                OutlinedCard(
                    onClick = { showMuscleDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Target Muscles")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.primaryMuscles.isNotEmpty()) {
                        Text("Primary:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.primaryMuscles.forEach { muscle ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removePrimaryMuscle(muscle) },
                                    label = { Text(muscle.displayName) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                    if (uiState.secondaryMuscles.isNotEmpty()) {
                        Text("Secondary:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.secondaryMuscles.forEach { muscle ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removeSecondaryMuscle(muscle) },
                                    label = { Text(muscle.displayName) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                    AssistChip(
                        onClick = { showMuscleDialog = true },
                        label = { Text("Edit muscles") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
                    )
                }
            }

            HorizontalDivider()

            // Balanced / Default Programming
            Text(
                "Balanced / Default Programming",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "This is the fallback prescription when no phase-specific preset applies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.defaultSets.toString(),
                    onValueChange = { viewModel.updateDefaultSets(it.toIntOrNull() ?: 3) },
                    label = { Text("Sets") },
                    supportingText = { Text("Exact or anchor value") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.defaultReps,
                    onValueChange = viewModel::updateDefaultReps,
                    label = { Text("Reps / duration") },
                    placeholder = { Text("8-12") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.defaultRestSeconds.toString(),
                    onValueChange = { viewModel.updateDefaultRest(it.toIntOrNull() ?: 90) },
                    label = { Text("Rest (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.defaultRounds.toString(),
                    onValueChange = viewModel::updateDefaultRounds,
                    label = { Text("Rounds") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.defaultDurationSeconds?.toString().orEmpty(),
                    onValueChange = viewModel::updateDefaultDuration,
                    label = { Text("Work (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.defaultTempo,
                    onValueChange = viewModel::updateDefaultTempo,
                    label = { Text("Tempo") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.defaultEffortTarget,
                    onValueChange = viewModel::updateDefaultEffortTarget,
                    label = { Text("Effort target") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Goal-specific Programming",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Give the same exercise different prescriptions depending on the training goal. For example, pushups can be heavy and low-rep for strength or high-rep with short rest for endurance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrainingPhase.entries.filter { it != TrainingPhase.BALANCED }.forEach { phase ->
                            FilterChip(
                                selected = uiState.selectedPresetPhase == phase,
                                onClick = { viewModel.selectPresetPhase(phase) },
                                label = { Text(phase.displayName) }
                            )
                        }
                    }

                    val selectedPreset = uiState.trainingPhasePresets.getValue(uiState.selectedPresetPhase)
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                uiState.selectedPresetPhase.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                uiState.selectedPresetPhase.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPreset.setsText,
                                    onValueChange = viewModel::updatePresetSetsText,
                                    label = { Text("Set range") },
                                    placeholder = { Text("3-5") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = selectedPreset.repsText,
                                    onValueChange = viewModel::updatePresetRepsText,
                                    label = { Text("Rep / time range") },
                                    placeholder = { Text("6-12") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPreset.rounds.toString(),
                                    onValueChange = viewModel::updatePresetRounds,
                                    label = { Text("Rounds") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = selectedPreset.restSeconds.toString(),
                                    onValueChange = { viewModel.updatePresetRestSeconds(it.toIntOrNull() ?: selectedPreset.restSeconds) },
                                    label = { Text("Rest (s)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = selectedPreset.durationSeconds?.toString().orEmpty(),
                                    onValueChange = viewModel::updatePresetDuration,
                                    label = { Text("Work (s)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPreset.tempo,
                                    onValueChange = viewModel::updatePresetTempo,
                                    label = { Text("Tempo") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = selectedPreset.effortTarget,
                                    onValueChange = viewModel::updatePresetEffortTarget,
                                    label = { Text("Effort target") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPreset.notes,
                                    onValueChange = viewModel::updatePresetNotes,
                                    label = { Text("Intent notes") },
                                    placeholder = { Text("Explosive focus, leave 2 reps in reserve...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }

            // Exercise properties
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = uiState.isCompound,
                        onCheckedChange = viewModel::updateIsCompound
                    )
                    Text("Compound")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = uiState.isUnilateral,
                        onCheckedChange = viewModel::updateIsUnilateral
                    )
                    Text("Unilateral")
                }
            }

            HorizontalDivider()

            // Exercise Family Section
            val selectedParentName = uiState.selectedParentName
            Text(
                "Exercise Family",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (uiState.existingVariations.isNotEmpty()) {
                Text(
                    "This is a main exercise with ${uiState.existingVariations.size} variation(s):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                uiState.existingVariations.forEach { variation ->
                    AssistChip(onClick = {}, label = { Text(variation.name) })
                }
            } else if (uiState.selectedParentId != null && selectedParentName != null) {
                Text(
                    "Variation of \"$selectedParentName\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = uiState.familyFocus,
                    onValueChange = viewModel::onFamilyFocusChanged,
                    label = { Text("Focus (what makes this variation different)") },
                    placeholder = { Text("e.g. Triceps emphasis") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.AddEditExercise.FamilyFocusField),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showFamilyParentDialog = true },
                        modifier = Modifier.testTag(TestTags.AddEditExercise.FamilyParentPickerButton)
                    ) {
                        Text("Change main exercise")
                    }
                    TextButton(
                        onClick = viewModel::detachFromFamily,
                        modifier = Modifier.testTag(TestTags.AddEditExercise.FamilyDetachButton)
                    ) {
                        Text("Detach")
                    }
                }
            } else {
                Text(
                    "Optionally link this exercise as a variation of an existing main exercise (e.g. Push-up).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showFamilyParentDialog = true },
                    modifier = Modifier.testTag(TestTags.AddEditExercise.FamilyParentPickerButton)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Make this a variation of…")
                }
            }
            if (uiState.familyError != null) {
                Text(
                    uiState.familyError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()

            // Media Section
            Text(
                "Media",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Add photos, videos, or external links. Local media is copied into WorkoutApp storage on save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.dataWarnings.isNotEmpty()) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
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

            // Media preview row
            if (uiState.localMediaUris.isNotEmpty() || uiState.externalUrls.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.localMediaUris) { uri ->
                        MediaThumbnail(
                            uri = uri,
                            onRemove = { viewModel.removeLocalMedia(uri) }
                        )
                    }
                    items(uiState.externalUrls) { url ->
                        ExternalUrlThumbnail(
                            url = url,
                            onRemove = { viewModel.removeExternalUrl(url) }
                        )
                    }
                }
            }

            // Media action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Photo")
                }
                OutlinedButton(
                    onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VideoLibrary, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Video")
                }
            }

            // External URL input
            OutlinedButton(
                onClick = { showUrlDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("Add External Link (YouTube, etc.)")
            }

            HorizontalDivider()

            // Personal Notes
            OutlinedTextField(
                value = uiState.personalNotes,
                onValueChange = viewModel::updatePersonalNotes,
                label = { Text("Personal Notes") },
                placeholder = { Text("Your own notes about this exercise...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                supportingText = uiState.saveError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Equipment selection dialog
        if (showEquipmentDialog) {
            EquipmentSelectionDialog(
                allEquipment = uiState.allEquipment,
                selectedEquipment = uiState.selectedEquipment,
                isCreatingEquipment = uiState.isCreatingEquipment,
                creationError = uiState.equipmentCreationError,
                createdEquipmentId = uiState.createdCustomEquipmentId,
                onEquipmentToggle = viewModel::toggleEquipment,
                onCreateCustomEquipment = viewModel::createCustomEquipment,
                onConsumeCreatedEquipment = viewModel::consumeCreatedCustomEquipmentSignal,
                onDismiss = {
                    showEquipmentDialog = false
                    viewModel.clearEquipmentCreationError()
                }
            )
        }

        // Muscle selection dialog
        if (showMuscleDialog) {
            MuscleSelectionDialog(
                primaryMuscles = uiState.primaryMuscles,
                secondaryMuscles = uiState.secondaryMuscles,
                onPrimaryToggle = viewModel::togglePrimaryMuscle,
                onSecondaryToggle = viewModel::toggleSecondaryMuscle,
                onDismiss = { showMuscleDialog = false }
            )
        }

        // Exercise family / main-exercise picker dialog
        if (showFamilyParentDialog) {
            FamilyParentSelectionDialog(
                candidates = uiState.familyParentCandidates,
                selectedParentId = uiState.selectedParentId,
                onSelect = { parent ->
                    viewModel.onFamilyParentSelected(parent)
                    showFamilyParentDialog = false
                },
                onDismiss = { showFamilyParentDialog = false }
            )
        }

        // URL dialog
        if (showUrlDialog) {
            ExternalUrlDialog(
                onConfirm = { url ->
                    viewModel.addExternalUrl(url)
                    showUrlDialog = false
                },
                onDismiss = { showUrlDialog = false }
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    uri: Uri,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Media",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ExternalUrlThumbnail(
    url: String,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.OndemandVideo,
            contentDescription = "External video",
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentSelectionDialog(
    allEquipment: List<Equipment>,
    selectedEquipment: List<Equipment>,
    isCreatingEquipment: Boolean,
    creationError: String?,
    createdEquipmentId: Long?,
    onEquipmentToggle: (Equipment) -> Unit,
    onCreateCustomEquipment: (String, String, Boolean) -> Unit,
    onConsumeCreatedEquipment: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = selectedEquipment.map { it.id }.toSet()
    var customName by remember { mutableStateOf("") }
    var customDescription by remember { mutableStateOf("") }
    var customPortable by remember { mutableStateOf(false) }

    // Only clear the custom-equipment form once the ViewModel confirms the row was actually
    // persisted (createdEquipmentId is set after a successful DB write), never optimistically.
    LaunchedEffect(createdEquipmentId) {
        if (createdEquipmentId != null) {
            customName = ""
            customDescription = ""
            customPortable = false
            onConsumeCreatedEquipment()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Equipment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                allEquipment.forEach { equipment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEquipmentToggle(equipment) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = equipment.id in selectedIds,
                            onCheckedChange = { onEquipmentToggle(equipment) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(equipment.name)
                            val meta = buildList {
                                if (equipment.description.isNotEmpty()) add(equipment.description)
                                if (equipment.isPortable) add("Portable")
                                if (equipment.isCustom) add("Custom")
                            }.joinToString(" • ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    "Add custom equipment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCreatingEquipment,
                    isError = creationError != null
                )
                OutlinedTextField(
                    value = customDescription,
                    onValueChange = { customDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    enabled = !isCreatingEquipment
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = customPortable, onCheckedChange = { customPortable = it }, enabled = !isCreatingEquipment)
                    Text("Portable / travel-friendly")
                }
                if (creationError != null) {
                    Text(
                        creationError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { onCreateCustomEquipment(customName, customDescription, customPortable) },
                    enabled = customName.isNotBlank() && !isCreatingEquipment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCreatingEquipment) {
                        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create & Select")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilyParentSelectionDialog(
    candidates: List<Exercise>,
    selectedParentId: Long?,
    onSelect: (Exercise?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = candidates.filter { it.name.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make this a variation of…") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search exercises") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        "No matching exercises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                filtered.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(candidate) }
                            .testTag(TestTags.AddEditExercise.familyParentOption(candidate.id))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = candidate.id == selectedParentId,
                            onClick = { onSelect(candidate) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(candidate.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(null) },
                modifier = Modifier.testTag(TestTags.AddEditExercise.FamilyClearParentButton)
            ) {
                Text("Clear selection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MuscleSelectionDialog(
    primaryMuscles: List<MuscleGroup>,
    secondaryMuscles: List<MuscleGroup>,
    onPrimaryToggle: (MuscleGroup) -> Unit,
    onSecondaryToggle: (MuscleGroup) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Target Muscles") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Primary") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Secondary") }
                    )
                }
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    BodyRegion.entries.forEach { region ->
                        val musclesInRegion = MuscleGroup.byRegion(region)
                        if (musclesInRegion.isNotEmpty()) {
                            Text(
                                region.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                musclesInRegion.forEach { muscle ->
                                    val isSelected = when (selectedTab) {
                                        0 -> muscle in primaryMuscles
                                        else -> muscle in secondaryMuscles
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            when (selectedTab) {
                                                0 -> onPrimaryToggle(muscle)
                                                else -> onSecondaryToggle(muscle)
                                            }
                                        },
                                        label = { Text(muscle.displayName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ExternalUrlDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add External Link") },
        text = {
            Column {
                Text(
                    "Paste a YouTube or other video URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://youtube.com/watch?v=...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
