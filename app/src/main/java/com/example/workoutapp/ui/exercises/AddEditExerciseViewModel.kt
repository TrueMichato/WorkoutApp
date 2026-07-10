package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.local.MediaStorageException
import com.example.workoutapp.data.local.MediaStorageManager
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
import com.example.workoutapp.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            equipmentRepository.getAllEquipment().collect { equipment ->
                _uiState.update { it.copy(allEquipment = equipment) }
            }
        }
    }

    fun loadExercise(exerciseId: Long) {
        viewModelScope.launch {
            editingExerciseId = exerciseId

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
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = equipmentRepository.createEquipment(name.trim(), description.trim(), isPortable)
            val newEquipment = equipmentRepository.getEquipmentById(newId) ?: return@launch
            _uiState.update { state ->
                if (state.selectedEquipment.any { it.id == newId }) state
                else state.copy(selectedEquipment = state.selectedEquipment + newEquipment, saveError = null)
            }
        }
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

    fun saveExercise() {
        val state = _uiState.value

        if (state.isSaving) return

        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }

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

                val editableExercise = Exercise(
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
                    updatedAt = System.currentTimeMillis()
                )

                val exerciseId = editingExerciseId
                if (exerciseId != null) {
                    val existing = exerciseRepository.getExerciseById(exerciseId)
                    if (existing == null) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveError = "Exercise no longer exists. Your changes were not saved."
                            )
                        }
                        return@launch
                    }
                    exerciseRepository.updateExerciseWithRelations(
                        exercise = existing.copy(
                            name = editableExercise.name,
                            description = editableExercise.description,
                            instructions = editableExercise.instructions,
                            tips = editableExercise.tips,
                            difficulty = editableExercise.difficulty,
                            isUnilateral = editableExercise.isUnilateral,
                            isCompound = editableExercise.isCompound,
                            defaultSets = editableExercise.defaultSets,
                            defaultReps = editableExercise.defaultReps,
                            defaultRestSeconds = editableExercise.defaultRestSeconds,
                            trainingPhasePresets = editableExercise.trainingPhasePresets,
                            localMediaUris = editableExercise.localMediaUris,
                            externalMediaUrls = editableExercise.externalMediaUrls,
                            personalNotes = editableExercise.personalNotes,
                            updatedAt = editableExercise.updatedAt
                        ),
                        categories = state.selectedCategories.toList(),
                        equipmentIds = state.selectedEquipment.map { it.id },
                        primaryMuscles = state.primaryMuscles,
                        secondaryMuscles = state.secondaryMuscles
                    )
                } else {
                    exerciseRepository.createExerciseWithRelations(
                        exercise = editableExercise,
                        categories = state.selectedCategories.toList(),
                        equipmentIds = state.selectedEquipment.map { it.id },
                        primaryMuscles = state.primaryMuscles,
                        secondaryMuscles = state.secondaryMuscles
                    )
                }

                if (state.removedLocalMediaUris.isNotEmpty()) {
                    mediaStorageManager.deleteUnreferencedMedia(
                        candidateUris = state.removedLocalMediaUris,
                        allExercises = exerciseRepository.getAllExercisesIncludingArchivedSync()
                    )
                }

                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
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
            } catch (e: IllegalStateException) {
                mediaStorageManager.deleteOwnedMediaFiles(copiedMediaUris)
                _uiState.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Failed to save exercise")
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
    val removedLocalMediaUris: List<Uri> = emptyList()
)
