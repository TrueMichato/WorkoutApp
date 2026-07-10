package com.example.workoutapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.local.ExerciseStorageInfo
import com.example.workoutapp.data.local.MediaStorageException
import com.example.workoutapp.data.local.MediaStorageManager
import com.example.workoutapp.data.model.PersistedDataDecodeException
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.toUserMessage
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.UserGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userGoalRepository: UserGoalRepository,
    private val exerciseRepository: ExerciseRepository,
    private val mediaStorageManager: MediaStorageManager
) : ViewModel() {

    // ── Goal settings state ──────────────────────────────────────────
    private val _goalState = MutableStateFlow(GoalSettingsState())
    val goalState: StateFlow<GoalSettingsState> = _goalState.asStateFlow()

    // ── Storage settings state ───────────────────────────────────────
    private val _storageState = MutableStateFlow(StorageSettingsState())
    val storageState: StateFlow<StorageSettingsState> = _storageState.asStateFlow()

    init {
        loadGoalSettings()
        loadStorageSettings()
    }

    // ── Goal settings ────────────────────────────────────────────────

    private fun loadGoalSettings() {
        viewModelScope.launch {
            userGoalRepository.getUserGoalFlow().collect { goal ->
                val g = goal ?: UserGoal()
                val weightsResult = userGoalRepository.getCategoryWeightsResult()
                val weights = weightsResult.value.ifEmpty { g.currentPhase.defaultWeights }
                _goalState.update {
                    it.copy(
                        currentPhase = g.currentPhase,
                        categoryWeights = weights,
                        isLoading = false,
                        error = weightsResult.issues.toUserMessage().ifBlank { null }
                    )
                }
            }
        }
    }

    fun setTrainingPhase(phase: TrainingPhase) {
        viewModelScope.launch {
            userGoalRepository.setTrainingPhase(phase)
            // Also reset category weights to the phase defaults
            userGoalRepository.setCategoryWeights(phase.defaultWeights)
        }
    }

    fun setCategoryWeight(category: WorkoutCategory, weight: Float) {
        val newWeights = _goalState.value.categoryWeights.toMutableMap()
        newWeights[category] = weight.coerceIn(0.1f, 3f)
        viewModelScope.launch {
            userGoalRepository.setCategoryWeights(newWeights)
        }
    }

    // ── Storage settings ─────────────────────────────────────────────

    private fun loadStorageSettings() {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().collect { exercises ->
                try {
                    val totalBytes = mediaStorageManager.computeTotalStorageBytes(exercises)
                    val localCount = mediaStorageManager.countLocalMediaFiles(exercises)
                    val urlCount = mediaStorageManager.countExternalUrls(exercises)
                    val breakdown = mediaStorageManager.getStorageBreakdown(exercises)
                    val offloadCandidates = mediaStorageManager.getOffloadCandidates(exercises)
                    val cleanupCandidates = mediaStorageManager.getCleanupCandidates(exercises)

                    _storageState.update {
                        it.copy(
                            totalBytes = totalBytes,
                            localMediaCount = localCount,
                            externalUrlCount = urlCount,
                            exerciseBreakdown = breakdown,
                            offloadCandidates = offloadCandidates,
                            cleanupCandidates = cleanupCandidates,
                            isLoading = false,
                            error = null
                        )
                    }
                } catch (e: PersistedDataDecodeException) {
                    _storageState.update { it.copy(isLoading = false, error = e.message ?: "Saved media data could not be decoded.") }
                } catch (e: MediaStorageException) {
                    _storageState.update { it.copy(isLoading = false, error = e.message ?: "Saved media could not be opened.") }
                }
            }
        }
    }

    fun refreshStorage() {
        _storageState.update { it.copy(isLoading = true) }
        loadStorageSettings()
    }
}

data class GoalSettingsState(
    val currentPhase: TrainingPhase = TrainingPhase.BALANCED,
    val categoryWeights: Map<WorkoutCategory, Float> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class StorageSettingsState(
    val totalBytes: Long = 0,
    val localMediaCount: Int = 0,
    val externalUrlCount: Int = 0,
    val exerciseBreakdown: List<ExerciseStorageInfo> = emptyList(),
    val offloadCandidates: List<ExerciseStorageInfo> = emptyList(),
    val cleanupCandidates: List<ExerciseStorageInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val formattedTotalSize: String
        get() = ExerciseStorageInfo.formatBytes(totalBytes)
}
