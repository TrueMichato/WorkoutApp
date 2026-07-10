package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.local.MediaStorageException
import com.example.workoutapp.data.local.MediaStorageManager
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.PersistedDataDecodeException
import com.example.workoutapp.data.model.PersistedJsonIssue
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.decodeTrainingPhasePresets
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.UserGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val equipmentRepository: EquipmentRepository,
    private val userGoalRepository: UserGoalRepository,
    private val mediaStorageManager: MediaStorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    private var currentExerciseId: Long = 0

    init {
        viewModelScope.launch {
            userGoalRepository.getUserGoalFlow().collect { goal ->
                _uiState.update {
                    it.copy(currentPhase = goal?.currentPhase ?: TrainingPhase.BALANCED)
                }
            }
        }
    }

    fun loadExercise(exerciseId: Long) {
        currentExerciseId = exerciseId
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            exerciseRepository.getExerciseByIdFlow(exerciseId).collect { exercise ->
                if (exercise != null) {
                    // Load related data
                    val categories = exerciseRepository.getExerciseCategories(exerciseId)
                    val equipmentIds = exerciseRepository.getRequiredEquipmentIds(exerciseId)
                    val primaryMuscles = exerciseRepository.getPrimaryMuscles(exerciseId)
                    val secondaryMuscles = exerciseRepository.getSecondaryMuscles(exerciseId)

                    val equipment = equipmentIds.mapNotNull { id ->
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

                    val presets = mergePresetsWithDefaults(exercise, decodeIssues)

                    _uiState.update { state ->
                        state.copy(
                            exercise = exercise,
                            categories = categories,
                            equipment = equipment,
                            primaryMuscles = primaryMuscles,
                            secondaryMuscles = secondaryMuscles,
                            localMediaUris = localUris,
                            externalUrls = externalUrlResult.value,
                            programmingPresets = presets,
                            dataWarnings = decodeIssues.map { it.message },
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Exercise not found") }
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = _uiState.value.exercise ?: return@launch
            exerciseRepository.setFavorite(current.id, !current.isFavorite)
        }
    }

    fun archiveExercise() {
        viewModelScope.launch {
            exerciseRepository.archiveExercise(currentExerciseId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun deleteExercise() {
        viewModelScope.launch {
            val exercise = _uiState.value.exercise ?: return@launch
            try {
                val mediaToDelete = mediaStorageManager.decodeLocalMediaUris(exercise.localMediaUris).value
                exerciseRepository.deleteExercise(exercise)
                mediaStorageManager.deleteUnreferencedMedia(
                    candidateUris = mediaToDelete,
                    allExercises = exerciseRepository.getAllExercisesIncludingArchived()
                )
                _uiState.update { it.copy(isDeleted = true) }
            } catch (e: PersistedDataDecodeException) {
                _uiState.update { it.copy(error = e.message ?: "Saved media could not be decoded.") }
            } catch (e: MediaStorageException) {
                _uiState.update { it.copy(error = e.message ?: "Media cleanup failed.") }
            }
        }
    }

    private fun mergePresetsWithDefaults(
        exercise: Exercise,
        decodeIssues: MutableList<PersistedJsonIssue>
    ): Map<TrainingPhase, ExerciseProgrammingPreset> {
        val seeded = TrainingPhase.entries.associateWith { phase ->
            defaultPresetForPhase(phase, exercise)
        }.toMutableMap()
        val stored = decodeTrainingPhasePresets("exercise programming presets", exercise.trainingPhasePresets)
        decodeIssues += stored.issues
        stored.value.forEach { (phase, preset) ->
            seeded[phase] = preset
        }
        return seeded
    }

    private fun defaultPresetForPhase(phase: TrainingPhase, exercise: Exercise): ExerciseProgrammingPreset = when (phase) {
        TrainingPhase.BALANCED -> ExerciseProgrammingPreset(
            setsText = exercise.defaultSets.toString(),
            repsText = exercise.defaultReps,
            restSeconds = exercise.defaultRestSeconds
        )
        TrainingPhase.STRENGTH_FOCUS -> ExerciseProgrammingPreset("4-6", "3-6", 150, notes = "Lower reps with more rest.")
        TrainingPhase.HYPERTROPHY_FOCUS -> ExerciseProgrammingPreset("3-5", "6-12", 75, notes = "Moderate volume for muscle growth.")
        TrainingPhase.ENDURANCE_FOCUS -> ExerciseProgrammingPreset("2-4", "15-30", 45, notes = "More reps and shorter rest.")
        TrainingPhase.SKILL_ACQUISITION -> ExerciseProgrammingPreset("3-6", "2-5", 90, notes = "Leave room for crisp technique.")
        TrainingPhase.RECOVERY -> ExerciseProgrammingPreset("2-3", "5-8", 45, notes = "Keep effort easy and controlled.")
        TrainingPhase.MARTIAL_ARTS_FOCUS -> ExerciseProgrammingPreset("3-5", "60-180s rounds", 60, notes = "Round-based conditioning or skill work.")
        TrainingPhase.MOBILITY_REHAB -> ExerciseProgrammingPreset("2-4", "5-10 reps / 30-60s", 30, notes = "Correctives, mobility, and control.")
    }
}

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    val categories: List<WorkoutCategory> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val localMediaUris: List<Uri> = emptyList(),
    val externalUrls: List<String> = emptyList(),
    val programmingPresets: Map<TrainingPhase, ExerciseProgrammingPreset> = emptyMap(),
    val dataWarnings: List<String> = emptyList(),
    val currentPhase: TrainingPhase = TrainingPhase.BALANCED,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val error: String? = null
)
