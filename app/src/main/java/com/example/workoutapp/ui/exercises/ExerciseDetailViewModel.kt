package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.UserGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val equipmentRepository: EquipmentRepository,
    private val userGoalRepository: UserGoalRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

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

                    // Parse media
                    val localUris = try {
                        json.decodeFromString<List<String>>(exercise.localMediaUris)
                            .map { it.toUri() }
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val externalUrls = try {
                        json.decodeFromString<List<String>>(exercise.externalMediaUrls)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val presets = mergePresetsWithDefaults(exercise)

                    _uiState.update { state ->
                        state.copy(
                            exercise = exercise,
                            categories = categories,
                            equipment = equipment,
                            primaryMuscles = primaryMuscles,
                            secondaryMuscles = secondaryMuscles,
                            localMediaUris = localUris,
                            externalUrls = externalUrls,
                            programmingPresets = presets,
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
            try {
                exerciseRepository.archiveExercise(currentExerciseId)
                _uiState.update { it.copy(isDeleted = true, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to archive exercise") }
            }
        }
    }

    fun restoreExercise() {
        viewModelScope.launch {
            try {
                exerciseRepository.unarchiveExercise(currentExerciseId)
                _uiState.update { it.copy(error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to restore exercise") }
            }
        }
    }

    private fun mergePresetsWithDefaults(exercise: Exercise): Map<TrainingPhase, ExerciseProgrammingPreset> {
        val seeded = TrainingPhase.entries.associateWith { phase ->
            defaultPresetForPhase(phase, exercise)
        }.toMutableMap()
        val stored = try {
            json.decodeFromString<Map<String, ExerciseProgrammingPreset>>(exercise.trainingPhasePresets)
        } catch (_: Exception) {
            emptyMap()
        }
        stored.forEach { (phaseName, preset) ->
            TrainingPhase.entries.find { it.name == phaseName }?.let { phase ->
                seeded[phase] = preset
            }
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
    val currentPhase: TrainingPhase = TrainingPhase.BALANCED,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val error: String? = null
)
