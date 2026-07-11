package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.local.MediaStorageException
import com.example.workoutapp.data.local.MediaStorageManager
import com.example.workoutapp.data.local.dao.ExerciseFamilyMutation
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.PersistedDataDecodeException
import com.example.workoutapp.data.model.PersistedJsonIssue
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.decodeTrainingPhasePresets
import com.example.workoutapp.data.model.persistedJson
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.EquipmentSaveResult
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.ExerciseSaveError
import com.example.workoutapp.data.repository.ExerciseSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class AddEditExerciseViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val equipmentRepository: EquipmentRepository,
    private val mediaStorageManager: MediaStorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditExerciseUiState())
    val uiState: StateFlow<AddEditExerciseUiState> = _uiState.asStateFlow()

    private var editingExerciseId: Long? = null

    init {
        loadEquipment()
        loadFamilyPickerCandidates()
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            equipmentRepository.getAllEquipment().collect { equipment ->
                _uiState.update { it.copy(allEquipment = equipment) }
            }
        }
    }

    /**
     * Loads the pool of exercises that could become this exercise's main exercise: active,
     * not itself, and not already a variation of something else (a variation can't also be a
     * main exercise - see ExerciseRepository.linkVariation). Exercises that already have their
     * own variations remain valid candidates, since one main exercise can have many variations.
     */
    private fun loadFamilyPickerCandidates() {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().collect { exercises ->
                val variationIds = exerciseRepository.getFamilyRootIdsForAll().keys
                _uiState.update { state ->
                    state.copy(
                        familyParentCandidates = exercises.filter { candidate ->
                            candidate.id != editingExerciseId && candidate.id !in variationIds
                        }
                    )
                }
            }
        }
    }

    fun loadExercise(exerciseId: Long) {
        viewModelScope.launch {
            editingExerciseId = exerciseId
            loadFamilyPickerCandidates()

            val exercise = exerciseRepository.getExerciseById(exerciseId)
            if (exercise == null) {
                _uiState.update {
                    it.copy(isLoading = false, saveError = "Exercise no longer exists.")
                }
                return@launch
            }
            val categories = exerciseRepository.getExerciseCategories(exerciseId)
            val equipmentIds = exerciseRepository.getRequiredEquipmentIds(exerciseId)
            val primaryMuscles = exerciseRepository.getPrimaryMuscles(exerciseId)
            val secondaryMuscles = exerciseRepository.getSecondaryMuscles(exerciseId)

            val existingFamily = exerciseRepository.getFamily(exerciseId)
            val originalParentId = exerciseRepository.getParentExerciseId(exerciseId)
            val originalFocus = existingFamily?.variations
                ?.firstOrNull { it.exercise.id == exerciseId }?.focus.orEmpty()
            // Only exercises that are the family's main/root exercise (not the one being edited)
            // count as "existing variations" for the read-only list below.
            val existingVariations = if (originalParentId == null) {
                existingFamily?.variations?.map { it.exercise } ?: emptyList()
            } else {
                emptyList()
            }

            val selectedEquipment = equipmentIds.mapNotNull { id ->
                equipmentRepository.getEquipmentById(id)
            }

            val decodeIssues = mutableListOf<PersistedJsonIssue>()
            val localUris = try {
                val result = mediaStorageManager.decodeLocalMediaUris(exercise.localMediaUris)
                decodeIssues += result.issues
                result.value
            } catch (e: PersistedDataDecodeException) {
                decodeIssues += PersistedJsonIssue(e.fieldName, e.rawValue, e.message ?: "Saved media could not be decoded.")
                emptyList()
            }

            val externalUrlResult = mediaStorageManager.decodeExternalUrls(exercise.externalMediaUrls)
            decodeIssues += externalUrlResult.issues

            val presets = mergePresetsWithDefaults(
                encodedPresets = exercise.trainingPhasePresets,
                defaultSets = exercise.defaultSets,
                defaultReps = exercise.defaultReps,
                defaultRestSeconds = exercise.defaultRestSeconds,
                decodeIssues = decodeIssues
            )
            val balancedPreset = presets.getValue(TrainingPhase.BALANCED)

            _uiState.update { state ->
                state.copy(
                    name = exercise.name,
                    description = exercise.description,
                    instructions = exercise.instructions,
                    tips = exercise.tips,
                    difficulty = exercise.difficulty,
                    selectedCategories = categories.toSet(),
                    selectedEquipment = selectedEquipment,
                    primaryMuscles = primaryMuscles,
                    secondaryMuscles = secondaryMuscles,
                    defaultSets = resolveSuggestedSetCount(balancedPreset.setsText, exercise.defaultSets),
                    defaultReps = balancedPreset.repsText,
                    defaultRestSeconds = balancedPreset.restSeconds,
                    defaultRounds = balancedPreset.rounds,
                    defaultDurationSeconds = balancedPreset.durationSeconds,
                    defaultTempo = balancedPreset.tempo,
                    defaultEffortTarget = balancedPreset.effortTarget,
                    trainingPhasePresets = presets,
                    isCompound = exercise.isCompound,
                    isUnilateral = exercise.isUnilateral,
                    localMediaUris = localUris,
                    externalUrls = externalUrlResult.value,
                    personalNotes = exercise.personalNotes,
                    originalLocalMediaJson = exercise.localMediaUris,
                    originalExternalMediaJson = exercise.externalMediaUrls,
                    originalTrainingPhasePresetsJson = exercise.trainingPhasePresets,
                    dataWarnings = decodeIssues.map { it.message },
                    originalParentId = originalParentId,
                    selectedParentId = originalParentId,
                    familyFocus = originalFocus,
                    existingVariations = existingVariations,
                    familyError = null,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Pre-fills a brand-new exercise as a variation of [parentExerciseId]: copies shared,
     * non-identity fields (categories, primary/secondary muscles, equipment, difficulty) from the
     * main exercise so the form starts sensibly, but leaves name/description/instructions/
     * tips/personal notes/media blank and never copies tracking history - this is still a fully
     * independent exercise the user names and edits before saving, not a clone. [selectedParentId]
     * is pre-set so the family link is created atomically when the user saves (see
     * [AddEditExerciseViewModel.saveExercise]).
     */
    fun loadNewVariation(parentExerciseId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadFamilyPickerCandidates()

            val parent = exerciseRepository.getExerciseById(parentExerciseId)
            if (parent == null) {
                _uiState.update {
                    it.copy(isLoading = false, familyError = "The selected main exercise no longer exists.")
                }
                return@launch
            }
            val categories = exerciseRepository.getExerciseCategories(parentExerciseId)
            val equipmentIds = exerciseRepository.getRequiredEquipmentIds(parentExerciseId)
            val primaryMuscles = exerciseRepository.getPrimaryMuscles(parentExerciseId)
            val secondaryMuscles = exerciseRepository.getSecondaryMuscles(parentExerciseId)
            val selectedEquipment = equipmentIds.mapNotNull { id -> equipmentRepository.getEquipmentById(id) }

            _uiState.update { state ->
                state.copy(
                    difficulty = parent.difficulty,
                    selectedCategories = categories.toSet(),
                    selectedEquipment = selectedEquipment,
                    primaryMuscles = primaryMuscles,
                    secondaryMuscles = secondaryMuscles,
                    isCompound = parent.isCompound,
                    isUnilateral = parent.isUnilateral,
                    originalParentId = null,
                    selectedParentId = parentExerciseId,
                    familyFocus = "",
                    existingVariations = emptyList(),
                    familyError = null,
                    isLoading = false
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                nameError = if (name.isBlank()) "Name is required" else null,
                saveError = null
            )
        }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description, saveError = null) }
    }

    fun updateInstructions(instructions: String) {
        _uiState.update { it.copy(instructions = instructions, saveError = null) }
    }

    fun updateTips(tips: String) {
        _uiState.update { it.copy(tips = tips, saveError = null) }
    }

    fun updateDifficulty(difficulty: Difficulty) {
        _uiState.update { it.copy(difficulty = difficulty, saveError = null) }
    }

    fun toggleCategory(category: WorkoutCategory) {
        _uiState.update { state ->
            val newCategories = if (category in state.selectedCategories) {
                state.selectedCategories - category
            } else {
                state.selectedCategories + category
            }
            state.copy(selectedCategories = newCategories, saveError = null)
        }
    }

    fun toggleEquipment(equipment: Equipment) {
        _uiState.update { state ->
            val current = state.selectedEquipment
            val newEquipment = if (current.any { it.id == equipment.id }) {
                current.filter { it.id != equipment.id }
            } else {
                current + equipment
            }
            state.copy(selectedEquipment = newEquipment, saveError = null)
        }
    }

    fun removeEquipment(equipment: Equipment) {
        _uiState.update { state ->
            state.copy(selectedEquipment = state.selectedEquipment.filter { it.id != equipment.id }, saveError = null)
        }
    }

    fun createCustomEquipment(name: String, description: String, isPortable: Boolean) {
        if (_uiState.value.isCreatingEquipment) return // debounce double-taps while a save is in flight
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingEquipment = true, equipmentCreationError = null) }
            try {
                when (val result = equipmentRepository.createEquipment(name, description, isPortable)) {
                    is EquipmentSaveResult.Success -> _uiState.update { state ->
                        val equipment = result.equipment
                        val alreadySelected = state.selectedEquipment.any { it.id == equipment.id }
                        state.copy(
                            isCreatingEquipment = false,
                            equipmentCreationError = null,
                            selectedEquipment = if (alreadySelected) state.selectedEquipment else state.selectedEquipment + equipment,
                            createdCustomEquipmentId = equipment.id
                        )
                    }
                    is EquipmentSaveResult.Failure -> _uiState.update {
                        it.copy(isCreatingEquipment = false, equipmentCreationError = result.error.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The repository already converts persistence failures into a typed result; this
                // is a last-resort guard so an unexpected exception never crashes the app or
                // leaves the dialog stuck mid-save.
                _uiState.update {
                    it.copy(isCreatingEquipment = false, equipmentCreationError = "Could not save the equipment. Please try again.")
                }
            }
        }
    }

    /** Clears the one-shot "just created" signal after the caller has reacted to it. */
    fun consumeCreatedCustomEquipmentSignal() {
        _uiState.update { it.copy(createdCustomEquipmentId = null) }
    }

    fun clearEquipmentCreationError() {
        _uiState.update { it.copy(equipmentCreationError = null) }
    }

    fun togglePrimaryMuscle(muscle: MuscleGroup) {
        _uiState.update { state ->
            val newPrimary = if (muscle in state.primaryMuscles) state.primaryMuscles - muscle else state.primaryMuscles + muscle
            val newSecondary = state.secondaryMuscles - muscle
            state.copy(primaryMuscles = newPrimary, secondaryMuscles = newSecondary, saveError = null)
        }
    }

    fun toggleSecondaryMuscle(muscle: MuscleGroup) {
        _uiState.update { state ->
            val newSecondary = if (muscle in state.secondaryMuscles) state.secondaryMuscles - muscle else state.secondaryMuscles + muscle
            val newPrimary = state.primaryMuscles - muscle
            state.copy(secondaryMuscles = newSecondary, primaryMuscles = newPrimary, saveError = null)
        }
    }

    fun removePrimaryMuscle(muscle: MuscleGroup) {
        _uiState.update { state ->
            state.copy(primaryMuscles = state.primaryMuscles - muscle, saveError = null)
        }
    }

    fun removeSecondaryMuscle(muscle: MuscleGroup) {
        _uiState.update { state ->
            state.copy(secondaryMuscles = state.secondaryMuscles - muscle, saveError = null)
        }
    }

    fun updateDefaultSets(sets: Int) {
        val resolved = sets.coerceIn(1, 20)
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(setsText = resolved.toString())
            }
            state.copy(defaultSets = resolved, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateDefaultReps(reps: String) {
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(repsText = reps)
            }
            state.copy(defaultReps = reps, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateDefaultRest(seconds: Int) {
        val resolved = seconds.coerceIn(0, 600)
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(restSeconds = resolved)
            }
            state.copy(defaultRestSeconds = resolved, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateDefaultRounds(value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(1, 20) ?: return
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(rounds = parsed)
            }
            state.copy(defaultRounds = parsed, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateDefaultDuration(value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(1, 3600)
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(durationSeconds = parsed)
            }
            state.copy(defaultDurationSeconds = parsed, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateDefaultTempo(value: String) {
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(tempo = value)
            }
            state.copy(defaultTempo = value, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateDefaultEffortTarget(value: String) {
        _uiState.update { state ->
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[TrainingPhase.BALANCED] = getValue(TrainingPhase.BALANCED).copy(effortTarget = value)
            }
            state.copy(defaultEffortTarget = value, trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun selectPresetPhase(phase: TrainingPhase) {
        _uiState.update { it.copy(selectedPresetPhase = phase) }
    }

    fun updatePresetSetsText(value: String) = updatePreset { copy(setsText = value) }
    fun updatePresetRepsText(value: String) = updatePreset { copy(repsText = value) }
    fun updatePresetRestSeconds(value: Int) = updatePreset { copy(restSeconds = value.coerceIn(0, 600)) }
    fun updatePresetRounds(value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(1, 20) ?: return
        updatePreset { copy(rounds = parsed) }
    }
    fun updatePresetDuration(value: String) = updatePreset { copy(durationSeconds = value.toIntOrNull()?.coerceIn(1, 3600)) }
    fun updatePresetTempo(value: String) = updatePreset { copy(tempo = value) }
    fun updatePresetEffortTarget(value: String) = updatePreset { copy(effortTarget = value) }
    fun updatePresetNotes(value: String) = updatePreset { copy(notes = value) }

    private fun updatePreset(transform: ExerciseProgrammingPreset.() -> ExerciseProgrammingPreset) {
        _uiState.update { state ->
            val phase = state.selectedPresetPhase
            val updatedPresets = state.trainingPhasePresets.toMutableMap().apply {
                this[phase] = (this[phase] ?: defaultPresetForPhase(phase, state.defaultSets, state.defaultReps, state.defaultRestSeconds)).transform()
            }
            state.copy(trainingPhasePresets = updatedPresets, presetsDirty = true, saveError = null)
        }
    }

    fun updateIsCompound(isCompound: Boolean) {
        _uiState.update { it.copy(isCompound = isCompound, saveError = null) }
    }

    fun updateIsUnilateral(isUnilateral: Boolean) {
        _uiState.update { it.copy(isUnilateral = isUnilateral, saveError = null) }
    }

    fun addLocalMedia(uris: List<Uri>) {
        _uiState.update { state ->
            state.copy(
                localMediaUris = state.localMediaUris + uris,
                mediaDirty = true,
                saveError = null
            )
        }
    }

    fun removeLocalMedia(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                localMediaUris = state.localMediaUris - uri,
                removedLocalMediaUris = state.removedLocalMediaUris + uri,
                mediaDirty = true,
                saveError = null
            )
        }
    }

    fun addExternalUrl(url: String) {
        if (url.isNotBlank()) {
            _uiState.update { state -> state.copy(externalUrls = state.externalUrls + url, externalUrlsDirty = true, saveError = null) }
        }
    }

    fun removeExternalUrl(url: String) {
        _uiState.update { state -> state.copy(externalUrls = state.externalUrls - url, externalUrlsDirty = true, saveError = null) }
    }

    fun updatePersonalNotes(notes: String) {
        _uiState.update { it.copy(personalNotes = notes, saveError = null) }
    }

    /**
     * Selects (or clears, when [parentId] is null) which main exercise this exercise is a
     * variation of. Blocked when this exercise already has its own variations (a main exercise
     * can't also become a variation - no multi-level nesting), surfaced via [familyError] the
     * same way [nameError] surfaces a blank-name problem.
     */
    fun onFamilyParentSelected(parentId: Long?) {
        _uiState.update { state ->
            if (parentId != null && state.existingVariations.isNotEmpty()) {
                state.copy(
                    familyError = "This exercise already has its own variations, so it can't also be a variation of another exercise."
                )
            } else {
                state.copy(selectedParentId = parentId, familyError = null)
            }
        }
    }

    fun onFamilyFocusChanged(focus: String) {
        _uiState.update { it.copy(familyFocus = focus, familyError = null) }
    }

    /** Detaches this exercise from its main exercise, leaving it standalone. Save-time only. */
    fun detachFromFamily() {
        _uiState.update { it.copy(selectedParentId = null, familyFocus = "", familyError = null) }
    }

    fun saveExercise() {
        val state = _uiState.value

        if (state.isSaving) return

        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Name is required") }
            return
        }
        if (state.selectedParentId != null && state.existingVariations.isNotEmpty()) {
            _uiState.update {
                it.copy(familyError = "This exercise already has its own variations, so it can't also be a variation of another exercise.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null, familyError = null) }

            var copiedMediaUris: List<Uri> = emptyList()
            try {
                val shouldPreserveRawMedia = state.originalLocalMediaJson != null &&
                    state.localMediaUris.isEmpty() &&
                    !state.mediaDirty &&
                    state.dataWarnings.any { it.contains("media", ignoreCase = true) }
                val ownedMediaUris = if (shouldPreserveRawMedia) {
                    state.localMediaUris
                } else {
                    mediaStorageManager.copyIntoExerciseMedia(state.localMediaUris)
                }
                copiedMediaUris = ownedMediaUris.filterIndexed { index, uri ->
                    val source = state.localMediaUris.getOrNull(index)
                    source != null && uri != source
                }
                val localMediaJson = if (shouldPreserveRawMedia) {
                    state.originalLocalMediaJson.orEmpty()
                } else {
                    persistedJson.encodeToString(ownedMediaUris.map { it.toString() })
                }
                val externalMediaJson = if (!state.externalUrlsDirty && state.originalExternalMediaJson != null && state.externalUrls.isEmpty()) {
                    state.originalExternalMediaJson
                } else {
                    persistedJson.encodeToString(state.externalUrls)
                }
                val presetMap = state.trainingPhasePresets.mapKeys { it.key.name }
                val presetJson = if (!state.presetsDirty && state.originalTrainingPhasePresetsJson != null &&
                    state.dataWarnings.any { it.contains("programming", ignoreCase = true) }
                ) {
                    state.originalTrainingPhasePresetsJson
                } else {
                    persistedJson.encodeToString(presetMap)
                }
                val balancedPreset = state.trainingPhasePresets.getValue(TrainingPhase.BALANCED)

                val exerciseId = editingExerciseId
                val existing = exerciseId?.let { exerciseRepository.getExerciseById(it) }
                if (exerciseId != null && existing == null) {
                    mediaStorageManager.deleteOwnedMediaFiles(copiedMediaUris)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveError = "Exercise no longer exists. Your changes were not saved."
                        )
                    }
                    return@launch
                }

                val editableExercise = Exercise(
                    id = existing?.id ?: 0L,
                    name = state.name.trim(),
                    description = state.description.trim(),
                    instructions = state.instructions.trim(),
                    tips = state.tips.trim(),
                    difficulty = state.difficulty,
                    isUnilateral = state.isUnilateral,
                    isCompound = state.isCompound,
                    defaultSets = resolveSuggestedSetCount(balancedPreset.setsText, state.defaultSets),
                    defaultReps = balancedPreset.repsText.ifBlank { state.defaultReps },
                    defaultRestSeconds = balancedPreset.restSeconds,
                    trainingPhasePresets = presetJson,
                    localMediaUris = localMediaJson,
                    externalMediaUrls = externalMediaJson,
                    personalNotes = state.personalNotes.trim(),
                    // Preserve everything the form doesn't edit (tracking metadata, timestamps)
                    // when updating; use model defaults when creating.
                    timesPerformed = existing?.timesPerformed ?: 0,
                    lastPerformedAt = existing?.lastPerformedAt,
                    isFavorite = existing?.isFavorite ?: false,
                    isArchived = existing?.isArchived ?: false,
                    estimatedDurationSeconds = existing?.estimatedDurationSeconds ?: 180,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // "Change main exercise"/"detach" is a single atomic replace inside the DAO
                // transaction below, so LinkTo covers both first-time linking and reparenting -
                // no separate unlink-then-relink step that could leave the exercise silently
                // detached if the second step failed.
                val familyMutation = when {
                    state.selectedParentId != null -> ExerciseFamilyMutation.LinkTo(state.selectedParentId, state.familyFocus)
                    state.originalParentId != null -> ExerciseFamilyMutation.Detach
                    else -> ExerciseFamilyMutation.NoChange
                }

                val result = exerciseRepository.saveExerciseWithFamily(
                    existingExerciseId = existing?.id,
                    exercise = editableExercise,
                    categories = state.selectedCategories.toList(),
                    equipmentIds = state.selectedEquipment.map { it.id },
                    primaryMuscles = state.primaryMuscles,
                    secondaryMuscles = state.secondaryMuscles,
                    familyMutation = familyMutation
                )

                when (result) {
                    is ExerciseSaveResult.Success -> {
                        // Only now, after the transaction has definitely committed the exercise
                        // row referencing copiedMediaUris, is it safe to clean up media that was
                        // removed from the form - it can never be a currently-referenced file.
                        if (state.removedLocalMediaUris.isNotEmpty()) {
                            mediaStorageManager.deleteUnreferencedMedia(
                                candidateUris = state.removedLocalMediaUris,
                                allExercises = exerciseRepository.getAllExercisesIncludingArchivedSync()
                            )
                        }
                        _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    }
                    is ExerciseSaveResult.Failure -> {
                        // Nothing was persisted (the whole transaction rolled back), so any
                        // newly-copied media is definitely unreferenced and safe to delete.
                        mediaStorageManager.deleteOwnedMediaFiles(copiedMediaUris)
                        val isFamilyError = result.error is ExerciseSaveError.SelfLink ||
                            result.error is ExerciseSaveError.ParentNotFound ||
                            result.error is ExerciseSaveError.ParentIsAlreadyVariation ||
                            result.error is ExerciseSaveError.ExerciseHasOwnVariations
                        _uiState.update {
                            if (isFamilyError) {
                                it.copy(isSaving = false, familyError = result.error.message)
                            } else {
                                it.copy(isSaving = false, saveError = result.error.message)
                            }
                        }
                    }
                }
            } catch (e: MediaStorageException) {
                mediaStorageManager.deleteOwnedMediaFiles(copiedMediaUris)
                _uiState.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Failed to import selected media")
                }
            } catch (e: PersistedDataDecodeException) {
                mediaStorageManager.deleteOwnedMediaFiles(copiedMediaUris)
                _uiState.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Saved data could not be decoded")
                }
            }
        }
    }

    private fun mergePresetsWithDefaults(
        encodedPresets: String,
        defaultSets: Int,
        defaultReps: String,
        defaultRestSeconds: Int,
        decodeIssues: MutableList<PersistedJsonIssue> = mutableListOf()
    ): Map<TrainingPhase, ExerciseProgrammingPreset> {
        val seeded = TrainingPhase.entries.associateWith {
            defaultPresetForPhase(it, defaultSets, defaultReps, defaultRestSeconds)
        }.toMutableMap()
        val stored = decodeTrainingPhasePresets("exercise programming presets", encodedPresets)
        decodeIssues += stored.issues
        stored.value.forEach { (phase, preset) ->
            seeded[phase] = preset
        }
        return seeded
    }

    private fun defaultPresetForPhase(
        phase: TrainingPhase,
        defaultSets: Int,
        defaultReps: String,
        defaultRestSeconds: Int
    ): ExerciseProgrammingPreset = when (phase) {
        TrainingPhase.BALANCED -> ExerciseProgrammingPreset(
            setsText = defaultSets.toString(),
            repsText = defaultReps,
            restSeconds = defaultRestSeconds
        )
        TrainingPhase.STRENGTH_FOCUS -> ExerciseProgrammingPreset("4-6", "3-6", 150, notes = "Lower reps with longer recovery.")
        TrainingPhase.HYPERTROPHY_FOCUS -> ExerciseProgrammingPreset("3-5", "6-12", 75, notes = "Moderate reps and manageable rest for muscle gain.")
        TrainingPhase.ENDURANCE_FOCUS -> ExerciseProgrammingPreset("2-4", "15-30", 45, notes = "Higher reps and shorter rest for stamina.")
        TrainingPhase.SKILL_ACQUISITION -> ExerciseProgrammingPreset("3-6", "2-5", 90, notes = "Crisp quality reps with space to reset technique.")
        TrainingPhase.RECOVERY -> ExerciseProgrammingPreset("2-3", "5-8", 45, notes = "Easy volume that supports recovery.")
        TrainingPhase.MARTIAL_ARTS_FOCUS -> ExerciseProgrammingPreset("3-5", "60-180s rounds", 60, notes = "Conditioning-style rounds for fight prep.")
        TrainingPhase.MOBILITY_REHAB -> ExerciseProgrammingPreset("2-4", "5-10 reps / 30-60s", 30, notes = "Controlled mobility and corrective work.")
    }

    private fun resolveSuggestedSetCount(setsText: String, fallback: Int): Int {
        val numbers = Regex("\\d+").findAll(setsText).mapNotNull { it.value.toIntOrNull() }.toList()
        return when {
            numbers.isEmpty() -> fallback
            numbers.size == 1 -> numbers.first().coerceIn(1, 20)
            else -> ((numbers.first() + numbers.last()) / 2).coerceIn(1, 20)
        }
    }
}

data class AddEditExerciseUiState(
    val name: String = "",
    val nameError: String? = null,
    val description: String = "",
    val instructions: String = "",
    val tips: String = "",
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val selectedCategories: Set<WorkoutCategory> = emptySet(),
    val allEquipment: List<Equipment> = emptyList(),
    val selectedEquipment: List<Equipment> = emptyList(),
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val defaultSets: Int = 3,
    val defaultReps: String = "8-12",
    val defaultRestSeconds: Int = 90,
    val defaultRounds: Int = 1,
    val defaultDurationSeconds: Int? = null,
    val defaultTempo: String = "",
    val defaultEffortTarget: String = "",
    val selectedPresetPhase: TrainingPhase = TrainingPhase.STRENGTH_FOCUS,
    val trainingPhasePresets: Map<TrainingPhase, ExerciseProgrammingPreset> = TrainingPhase.entries.associateWith {
        when (it) {
            TrainingPhase.BALANCED -> ExerciseProgrammingPreset("3", "8-12", 90)
            TrainingPhase.STRENGTH_FOCUS -> ExerciseProgrammingPreset("4-6", "3-6", 150)
            TrainingPhase.HYPERTROPHY_FOCUS -> ExerciseProgrammingPreset("3-5", "6-12", 75)
            TrainingPhase.ENDURANCE_FOCUS -> ExerciseProgrammingPreset("2-4", "15-30", 45)
            TrainingPhase.SKILL_ACQUISITION -> ExerciseProgrammingPreset("3-6", "2-5", 90)
            TrainingPhase.RECOVERY -> ExerciseProgrammingPreset("2-3", "5-8", 45)
            TrainingPhase.MARTIAL_ARTS_FOCUS -> ExerciseProgrammingPreset("3-5", "60-180s rounds", 60)
            TrainingPhase.MOBILITY_REHAB -> ExerciseProgrammingPreset("2-4", "5-10 reps / 30-60s", 30)
        }
    },
    val isCompound: Boolean = true,
    val isUnilateral: Boolean = false,
    val localMediaUris: List<Uri> = emptyList(),
    val externalUrls: List<String> = emptyList(),
    val personalNotes: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null,
    val dataWarnings: List<String> = emptyList(),
    val originalLocalMediaJson: String? = null,
    val originalExternalMediaJson: String? = null,
    val originalTrainingPhasePresetsJson: String? = null,
    val mediaDirty: Boolean = false,
    val externalUrlsDirty: Boolean = false,
    val presetsDirty: Boolean = false,
    val removedLocalMediaUris: List<Uri> = emptyList(),
    val isCreatingEquipment: Boolean = false,
    val equipmentCreationError: String? = null,
    val createdCustomEquipmentId: Long? = null,
    // Exercise family / variations
    val familyParentCandidates: List<Exercise> = emptyList(),
    val originalParentId: Long? = null,
    val selectedParentId: Long? = null,
    val familyFocus: String = "",
    val existingVariations: List<Exercise> = emptyList(),
    val familyError: String? = null
)
