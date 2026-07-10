package com.example.workoutapp.ui.physicaltherapy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.PTFrequency
import com.example.workoutapp.data.model.PTRoutineExerciseCrossRef
import com.example.workoutapp.data.model.PTSessionLog
import com.example.workoutapp.data.model.PhysicalTherapyRoutine
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.PhysicalTherapyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PTViewModel @Inject constructor(
    private val ptRepository: PhysicalTherapyRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    // ========== List screen state ==========
    private val _showArchived = MutableStateFlow(false)

    val listUiState: StateFlow<PTListUiState> = combine(
        ptRepository.getActiveRoutines(),
        ptRepository.getArchivedRoutines(),
        _showArchived
    ) { active, archived, showArchived ->
        // Enrich each routine with today's completion count
        val enriched = active.map { routine ->
            val completedToday = ptRepository.getTimesCompletedToday(routine.id)
            val lastLog = ptRepository.getLastLogForRoutine(routine.id)
            PTRoutineSummary(
                routine = routine,
                completedToday = completedToday,
                isDueNow = routine.isMustDo && completedToday < routine.timesPerDay,
                lastCompletedAt = lastLog?.completedAt
            )
        }
        PTListUiState(
            mustDoRoutines = enriched.filter { it.routine.isMustDo },
            otherRoutines = enriched.filter { !it.routine.isMustDo },
            archivedRoutines = archived,
            showArchived = showArchived,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PTListUiState(isLoading = true))

    // ========== Detail screen state ==========
    private val _detailRoutineId = MutableStateFlow<Long?>(null)
    private val _activeSessionLogId = MutableStateFlow<Long?>(null)
    private val _sessionFeedback = MutableStateFlow(PTSessionFeedback())

    @Suppress("UNCHECKED_CAST")
    val detailUiState: StateFlow<PTDetailUiState> = _detailRoutineId.flatMapLatest { routineId ->
        if (routineId == null) return@flatMapLatest flowOf(PTDetailUiState())
        combine(
            ptRepository.getRoutineByIdFlow(routineId),
            ptRepository.getExercisesForRoutineFlow(routineId),
            exerciseRepository.getAllExercises(),
            ptRepository.getLogsForRoutine(routineId),
            _activeSessionLogId,
            _sessionFeedback
        ) { values ->
            val routine = values[0] as PhysicalTherapyRoutine?
            val refs = values[1] as List<PTRoutineExerciseCrossRef>
            val allExercises = values[2] as List<Exercise>
            val logs = values[3] as List<PTSessionLog>
            val activeLogId = values[4] as Long?
            val feedback = values[5] as PTSessionFeedback

            val exerciseById = allExercises.associateBy { it.id }
            val exercises = refs.map { ref ->
                PTExerciseItem(ref = ref, exercise = exerciseById[ref.exerciseId])
            }
            val completedToday = routine?.let { ptRepository.getTimesCompletedToday(it.id) } ?: 0
            val avgPainReduction = routine?.let { ptRepository.getAveragePainReduction(it.id) }

            PTDetailUiState(
                routine = routine,
                exercises = exercises,
                recentLogs = logs.take(10),
                completedToday = completedToday,
                averagePainReduction = avgPainReduction,
                activeSessionLogId = activeLogId,
                sessionFeedback = feedback,
                isLoading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PTDetailUiState(isLoading = true))

    // ========== Add/Edit screen state ==========
    private val _editForm = MutableStateFlow(PTRoutineForm())
    private val _editRoutineId = MutableStateFlow<Long?>(null)
    private val _isSaving = MutableStateFlow(false)
    private val _savedRoutineId = MutableStateFlow<Long?>(null)

    val editUiState: StateFlow<PTEditUiState> = combine(
        _editForm, _editRoutineId, _isSaving, _savedRoutineId,
        exerciseRepository.getAllExercises()
    ) { form, editId, saving, savedId, allExercises ->
        PTEditUiState(
            form = form,
            isEditing = editId != null,
            isSaving = saving,
            savedRoutineId = savedId,
            availableExercises = allExercises.filter { !it.isArchived },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PTEditUiState(isLoading = true))

    // ========== List actions ==========
    fun toggleShowArchived() { _showArchived.value = !_showArchived.value }

    fun archiveRoutine(routineId: Long) {
        viewModelScope.launch { ptRepository.archiveRoutine(routineId) }
    }

    fun unarchiveRoutine(routineId: Long) {
        viewModelScope.launch { ptRepository.unarchiveRoutine(routineId) }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch { ptRepository.deleteRoutine(routineId) }
    }

    fun toggleMustDo(routineId: Long, isMustDo: Boolean) {
        viewModelScope.launch { ptRepository.setMustDo(routineId, isMustDo) }
    }

    // ========== Detail actions ==========
    fun loadRoutineDetail(routineId: Long) {
        if (_detailRoutineId.value == routineId) return
        _detailRoutineId.value = routineId
        _activeSessionLogId.value = null
        _sessionFeedback.value = PTSessionFeedback()
    }

    fun startPTSession() {
        val routineId = _detailRoutineId.value ?: return
        viewModelScope.launch {
            val logId = ptRepository.startSession(routineId)
            _activeSessionLogId.value = logId
            _sessionFeedback.value = PTSessionFeedback()
        }
    }

    fun updatePainBefore(value: Int) {
        _sessionFeedback.value = _sessionFeedback.value.copy(painBefore = value)
    }

    fun updatePainAfter(value: Int) {
        _sessionFeedback.value = _sessionFeedback.value.copy(painAfter = value)
    }

    fun updateSessionNotes(notes: String) {
        _sessionFeedback.value = _sessionFeedback.value.copy(notes = notes)
    }

    fun updateSymptomChanges(changes: String) {
        _sessionFeedback.value = _sessionFeedback.value.copy(symptomChanges = changes)
    }

    fun completePTSession() {
        val logId = _activeSessionLogId.value ?: return
        val routineId = _detailRoutineId.value ?: return
        val feedback = _sessionFeedback.value

        viewModelScope.launch {
            val exercises = ptRepository.getExercisesForRoutine(routineId)
            ptRepository.completeSession(
                logId = logId,
                exercisesCompleted = exercises.size, // Full completion for now
                exercisesTotal = exercises.size,
                painBefore = feedback.painBefore,
                painAfter = feedback.painAfter,
                notes = feedback.notes,
                symptomChanges = feedback.symptomChanges
            )
            _activeSessionLogId.value = null
            _sessionFeedback.value = PTSessionFeedback()
        }
    }

    fun cancelPTSession() {
        _activeSessionLogId.value = null
        _sessionFeedback.value = PTSessionFeedback()
    }

    // ========== Add/Edit actions ==========
    fun initNewRoutine() {
        _editRoutineId.value = null
        _editForm.value = PTRoutineForm()
        _savedRoutineId.value = null
    }

    fun initEditRoutine(routineId: Long) {
        _editRoutineId.value = routineId
        _savedRoutineId.value = null
        viewModelScope.launch {
            val routine = ptRepository.getRoutineById(routineId) ?: return@launch
            val exercises = ptRepository.getExercisesForRoutine(routineId)
            _editForm.value = PTRoutineForm(
                name = routine.name,
                description = routine.description,
                condition = routine.condition,
                therapistName = routine.therapistName,
                frequency = routine.frequency,
                timesPerDay = routine.timesPerDay,
                isMustDo = routine.isMustDo,
                priority = routine.priority,
                precautions = routine.precautions,
                notes = routine.notes,
                selectedExerciseIds = exercises.map { it.exerciseId }.toSet(),
                exerciseConfigs = exercises.associateBy(
                    keySelector = { it.exerciseId },
                    valueTransform = {
                        PTExerciseConfig(
                            sets = it.prescribedSets,
                            reps = it.prescribedReps,
                            holdSeconds = it.prescribedHoldSeconds,
                            restSeconds = it.prescribedRestSeconds,
                            instructions = it.specialInstructions
                        )
                    }
                )
            )
        }
    }

    fun updateFormName(v: String) { _editForm.value = _editForm.value.copy(name = v) }
    fun updateFormDescription(v: String) { _editForm.value = _editForm.value.copy(description = v) }
    fun updateFormCondition(v: String) { _editForm.value = _editForm.value.copy(condition = v) }
    fun updateFormTherapist(v: String) { _editForm.value = _editForm.value.copy(therapistName = v) }
    fun updateFormFrequency(f: PTFrequency) { _editForm.value = _editForm.value.copy(frequency = f) }
    fun updateFormTimesPerDay(v: Int) { _editForm.value = _editForm.value.copy(timesPerDay = v.coerceIn(1, 10)) }
    fun updateFormMustDo(v: Boolean) { _editForm.value = _editForm.value.copy(isMustDo = v) }
    fun updateFormPriority(v: Int) { _editForm.value = _editForm.value.copy(priority = v.coerceIn(1, 10)) }
    fun updateFormPrecautions(v: String) { _editForm.value = _editForm.value.copy(precautions = v) }
    fun updateFormNotes(v: String) { _editForm.value = _editForm.value.copy(notes = v) }

    fun toggleExerciseSelected(exerciseId: Long) {
        val form = _editForm.value
        val newIds = form.selectedExerciseIds.toMutableSet()
        if (!newIds.add(exerciseId)) newIds.remove(exerciseId)
        _editForm.value = form.copy(selectedExerciseIds = newIds)
    }

    fun updateExerciseConfig(exerciseId: Long, config: PTExerciseConfig) {
        val form = _editForm.value
        _editForm.value = form.copy(
            exerciseConfigs = form.exerciseConfigs.toMutableMap().apply { this[exerciseId] = config }
        )
    }

    fun clearSavedRoutineId() { _savedRoutineId.value = null }

    fun saveRoutine() {
        val form = _editForm.value
        if (form.name.isBlank()) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val routine = PhysicalTherapyRoutine(
                    id = _editRoutineId.value ?: 0,
                    name = form.name.trim(),
                    description = form.description.trim(),
                    condition = form.condition.trim(),
                    therapistName = form.therapistName.trim(),
                    frequency = form.frequency,
                    timesPerDay = form.timesPerDay,
                    isMustDo = form.isMustDo,
                    priority = form.priority,
                    precautions = form.precautions.trim(),
                    notes = form.notes.trim()
                )
                val routineId = if (_editRoutineId.value != null) {
                    ptRepository.updateRoutine(routine)
                    routine.id
                } else {
                    ptRepository.createRoutine(routine)
                }
                val refs = form.selectedExerciseIds.mapIndexed { index, exerciseId ->
                    val cfg = form.exerciseConfigs[exerciseId] ?: PTExerciseConfig()
                    PTRoutineExerciseCrossRef(
                        routineId = routineId,
                        exerciseId = exerciseId,
                        orderIndex = index,
                        prescribedSets = cfg.sets,
                        prescribedReps = cfg.reps,
                        prescribedHoldSeconds = cfg.holdSeconds,
                        prescribedRestSeconds = cfg.restSeconds,
                        specialInstructions = cfg.instructions
                    )
                }
                ptRepository.setRoutineExercises(routineId, refs)
                _savedRoutineId.value = routineId
            } finally {
                _isSaving.value = false
            }
        }
    }
}

// ========== UI State Classes ==========

data class PTRoutineSummary(
    val routine: PhysicalTherapyRoutine,
    val completedToday: Int = 0,
    val isDueNow: Boolean = false,
    val lastCompletedAt: Long? = null
)

data class PTExerciseItem(
    val ref: PTRoutineExerciseCrossRef,
    val exercise: Exercise?
)

data class PTSessionFeedback(
    val painBefore: Int = 5,
    val painAfter: Int = 5,
    val notes: String = "",
    val symptomChanges: String = ""
)

data class PTExerciseConfig(
    val sets: Int = 3,
    val reps: String = "10-15",
    val holdSeconds: Int? = null,
    val restSeconds: Int = 30,
    val instructions: String = ""
)

data class PTRoutineForm(
    val name: String = "",
    val description: String = "",
    val condition: String = "",
    val therapistName: String = "",
    val frequency: PTFrequency = PTFrequency.DAILY,
    val timesPerDay: Int = 1,
    val isMustDo: Boolean = true,
    val priority: Int = 1,
    val precautions: String = "",
    val notes: String = "",
    val selectedExerciseIds: Set<Long> = emptySet(),
    val exerciseConfigs: Map<Long, PTExerciseConfig> = emptyMap()
)

data class PTListUiState(
    val mustDoRoutines: List<PTRoutineSummary> = emptyList(),
    val otherRoutines: List<PTRoutineSummary> = emptyList(),
    val archivedRoutines: List<PhysicalTherapyRoutine> = emptyList(),
    val showArchived: Boolean = false,
    val isLoading: Boolean = false
)

data class PTDetailUiState(
    val routine: PhysicalTherapyRoutine? = null,
    val exercises: List<PTExerciseItem> = emptyList(),
    val recentLogs: List<PTSessionLog> = emptyList(),
    val completedToday: Int = 0,
    val averagePainReduction: Float? = null,
    val activeSessionLogId: Long? = null,
    val sessionFeedback: PTSessionFeedback = PTSessionFeedback(),
    val isLoading: Boolean = false
)

data class PTEditUiState(
    val form: PTRoutineForm = PTRoutineForm(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedRoutineId: Long? = null,
    val availableExercises: List<Exercise> = emptyList(),
    val isLoading: Boolean = false
)




