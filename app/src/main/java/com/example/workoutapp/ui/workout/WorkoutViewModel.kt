package com.example.workoutapp.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.SessionExercise
import com.example.workoutapp.data.model.SessionStatus
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WeightUnit
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.RichPrescriptionData
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutCompletionSemantics
import com.example.workoutapp.data.model.WorkoutPlanTemplate
import com.example.workoutapp.data.model.WorkoutPlanTemplateExercise
import com.example.workoutapp.data.model.WorkoutPlanTemplateSummary
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.model.resolveBalancedProgrammingPreset
import com.example.workoutapp.data.model.toJson
import com.example.workoutapp.data.model.toRichPrescriptionDataOrNull
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.MLFeedbackRepository
import com.example.workoutapp.data.repository.UserGoalRepository
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import com.example.workoutapp.domain.DashboardAnalytics
import com.example.workoutapp.domain.PlannedExerciseSummary
import com.example.workoutapp.domain.SessionHistorySummary
import com.example.workoutapp.domain.WorkoutCompletionInput
import com.example.workoutapp.domain.WorkoutGenerationParams
import com.example.workoutapp.domain.WorkoutPlanDraft
import com.example.workoutapp.domain.WorkoutPlanner
import com.example.workoutapp.domain.WorkoutTrackingSummary
import com.example.workoutapp.domain.SaveWorkoutPlanTemplateInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val equipmentRepository: EquipmentRepository,
    private val userGoalRepository: UserGoalRepository,
    private val mlFeedbackRepository: MLFeedbackRepository,
    private val workoutPlanner: WorkoutPlanner
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }


    // --- Generator state ---
    private val _selectedLocationId = MutableStateFlow<Long?>(null)
    private val _durationMinutes = MutableStateFlow<Int?>(null)
    private val _selectedCategories = MutableStateFlow<Set<WorkoutCategory>>(emptySet())
    private val _selectedTimeSlot = MutableStateFlow(TimeSlot.ANYTIME)
    private val _excludedPreviewExerciseIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _previewFeedbackSessionId = MutableStateFlow<Long?>(null)
    private val _isGenerating = MutableStateFlow(false)
    private val _isPreviewing = MutableStateFlow(false)
    private val _generationError = MutableStateFlow<String?>(null)
    private val _generatedSessionId = MutableStateFlow<Long?>(null)
    private val _previewDraft = MutableStateFlow<WorkoutPlanDraft?>(null)
    private val _isSavingPlan = MutableStateFlow(false)
    private val _planSaveMessage = MutableStateFlow<String?>(null)

    // --- Active workout state ---
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    private val _completionInput = MutableStateFlow(WorkoutCompletionInput())
    private val _isCompletingWorkout = MutableStateFlow(false)
    private val _completedWorkoutId = MutableStateFlow<Long?>(null)
    private val _activeWorkoutError = MutableStateFlow<String?>(null)
    private val _setEntryDrafts = MutableStateFlow<Map<Long, SetEntryDraft>>(emptyMap())
    /** Bumped to force active-workout recomposition after set log / exercise status writes */
    private val _activeRefreshTick = MutableStateFlow(0)

    // --- History state ---
    private val _historyStatusFilter = MutableStateFlow<SessionStatus?>(null)

    // --- Plan editor state ---
    private val _planEditorUiState = MutableStateFlow(WorkoutPlanEditorUiState())
    val planEditorUiState: StateFlow<WorkoutPlanEditorUiState> = _planEditorUiState

    private var defaultPlanLocationId: Long? = null

    init {
        observePlanEditorReferenceData()
    }

    // ========== Overview ==========
    val overviewUiState: StateFlow<WorkoutOverviewUiState> = combine(
        sessionRepository.getInProgressSessionFlow(),
        sessionRepository.getSessionsForToday(),
        sessionRepository.getRecentCompletedSessions(limit = 5),
        sessionRepository.getPlanTemplates()
    ) { activeSession, todaySessions, recentSessions, savedPlans ->
        WorkoutOverviewUiState(
            activeSession = activeSession,
            todaysSessions = todaySessions.sortedWith(compareBy({ it.scheduledTimeSlot.sortOrder }, { it.plannedDate })),
            recentSessions = recentSessions,
            savedPlans = savedPlans
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutOverviewUiState(isLoading = true))

    // ========== Generator ==========
    private val generatorBaseState = combine(
        equipmentRepository.getAllLocations(),
        equipmentRepository.getDefaultLocationFlow(),
        userGoalRepository.getUserGoalFlow().map { it ?: UserGoal() },
        _selectedLocationId,
        _durationMinutes,
        _excludedPreviewExerciseIds
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val locations = args[0] as List<EquipmentLocation>
        val defaultLocation = args[1] as EquipmentLocation?
        val userGoal = args[2] as UserGoal
        val selectedLocationId = args[3] as Long?
        val durationMinutes = args[4] as Int?
        @Suppress("UNCHECKED_CAST")
        val excludedPreviewExerciseIds = args[5] as Set<Long>

        GeneratorBaseState(
            locations = locations,
            selectedLocationId = selectedLocationId,
            defaultLocationId = defaultLocation?.id ?: locations.firstOrNull()?.id,
            durationMinutes = durationMinutes ?: userGoal.preferredSessionDurationMinutes,
            currentPhase = userGoal.currentPhase,
            excludedPreviewExerciseIds = excludedPreviewExerciseIds
        )
    }

    private val generatorSelectionState = combine(
        generatorBaseState, _selectedCategories, _selectedTimeSlot
    ) { base, cats, slot -> GeneratorSelectionState(base, cats.toList(), slot) }

    private val generatorStatusState = combine(
        _isGenerating, _isPreviewing, _generationError, _generatedSessionId, _previewDraft, _isSavingPlan, _planSaveMessage
    ) { args: Array<Any?> ->
        GeneratorStatusState(
            isGenerating = args[0] as Boolean,
            isPreviewing = args[1] as Boolean,
            error = args[2] as String?,
            generatedSessionId = args[3] as Long?,
            previewDraft = args[4] as WorkoutPlanDraft?,
            isSavingPlan = args[5] as Boolean,
            planSaveMessage = args[6] as String?
        )
    }

    val generatorUiState: StateFlow<WorkoutGeneratorUiState> = combine(
        generatorSelectionState, generatorStatusState
    ) { sel, st ->
        WorkoutGeneratorUiState(
            locations = sel.base.locations,
            selectedLocationId = sel.base.selectedLocationId ?: sel.base.defaultLocationId,
            durationMinutes = sel.base.durationMinutes,
            selectedCategories = sel.selectedCategories,
            selectedTimeSlot = sel.selectedTimeSlot,
            currentPhase = sel.base.currentPhase,
            excludedExerciseIds = sel.base.excludedPreviewExerciseIds,
            isGenerating = st.isGenerating, isPreviewing = st.isPreviewing,
            error = st.error, generatedSessionId = st.generatedSessionId, previewDraft = st.previewDraft,
            hasPreviewCustomizations = sel.base.excludedPreviewExerciseIds.isNotEmpty(),
            isSavingPlan = st.isSavingPlan,
            planSaveMessage = st.planSaveMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutGeneratorUiState(isLoading = true))

    // ========== Active Workout (with set logs) ==========
    val activeWorkoutUiState: StateFlow<ActiveWorkoutUiState> = _activeSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) {
            flowOf(ActiveWorkoutUiState(isLoading = false))
        } else {
            combine(
                sessionRepository.getSessionByIdFlow(sessionId),
                sessionRepository.getExercisesForSession(sessionId),
                exerciseRepository.getAllExercisesIncludingArchived(),
                _completionInput, _isCompletingWorkout, _completedWorkoutId, _activeWorkoutError,
                _setEntryDrafts, _activeRefreshTick
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val session = values[0] as WorkoutSession?
                @Suppress("UNCHECKED_CAST")
                val sessionExercises = values[1] as List<SessionExercise>
                @Suppress("UNCHECKED_CAST")
                val exerciseLibrary = values[2] as List<Exercise>
                val completionInput = values[3] as WorkoutCompletionInput
                val isCompleting = values[4] as Boolean
                val completedId = values[5] as Long?
                val activeError = values[6] as String?
                @Suppress("UNCHECKED_CAST")
                val drafts = values[7] as Map<Long, SetEntryDraft>
                // values[8] is refreshTick – only used to trigger recomposition

                val exerciseById = exerciseLibrary.associateBy { it.id }
                val exercises = sessionExercises.map { se ->
                    val logs = sessionRepository.getSetLogsForExerciseSync(se.id)
                    SessionExerciseUi(
                        sessionExercise = se,
                        exercise = exerciseById[se.exerciseId],
                        setLogs = logs,
                        draft = drafts[se.id] ?: SetEntryDraft()
                    )
                }
                ActiveWorkoutUiState(
                    session = session, exercises = exercises,
                    completionInput = completionInput, isCompleting = isCompleting,
                    completedWorkoutId = completedId, error = activeError, isLoading = false
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState(isLoading = true))

    // ========== History (with summaries + filter) ==========
    val historyUiState: StateFlow<WorkoutHistoryUiState> = combine(
        sessionRepository.getAllSessions(),
        exerciseRepository.getAllExercisesIncludingArchived(),
        _historyStatusFilter
    ) { sessions, exerciseLibrary, statusFilter ->
        val exerciseById = exerciseLibrary.associateBy { it.id }
        val visible = sessions
            .filter { it.status != SessionStatus.PLANNED || it.completedAt != null || it.startedAt != null }
            .filter { statusFilter == null || it.status == statusFilter }
            .sortedWith(
                compareByDescending<WorkoutSession> { it.completedAt ?: it.startedAt ?: it.plannedDate }
                    .thenBy { it.scheduledTimeSlot.sortOrder }
            )
        val summaries = visible.map { session ->
            val exercises = sessionRepository.getExercisesForSessionSync(session.id)
            val logsByExercise = exercises.associate { it.id to sessionRepository.getSetLogsForExerciseSync(it.id) }
            WorkoutTrackingSummary.summarizeSession(session, exercises, exerciseById, logsByExercise)
        }
        WorkoutHistoryUiState(sessions = summaries, selectedStatus = statusFilter, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHistoryUiState(isLoading = true))

    // ========== Generator actions ==========
    fun selectLocation(locationId: Long) { _selectedLocationId.value = locationId; invalidateGeneratorPreview() }
    fun updateDuration(minutes: Int) { _durationMinutes.value = minutes; invalidateGeneratorPreview() }
    fun selectTimeSlot(timeSlot: TimeSlot) { _selectedTimeSlot.value = timeSlot; invalidateGeneratorPreview() }
    fun toggleCategory(category: WorkoutCategory) {
        _selectedCategories.value = _selectedCategories.value.toMutableSet().apply { if (!add(category)) remove(category) }
        invalidateGeneratorPreview()
    }
    fun clearCategories() { _selectedCategories.value = emptySet(); invalidateGeneratorPreview() }
    fun clearGeneratedSession() { _generatedSessionId.value = null }
    fun clearCompletedWorkout() { _completedWorkoutId.value = null }
    fun clearGeneratorError() { _generationError.value = null }
    fun clearActiveWorkoutError() { _activeWorkoutError.value = null }
    fun clearPlanSaveMessage() { _planSaveMessage.value = null }

    fun initializeNewPlanEditor() {
        _planEditorUiState.value = WorkoutPlanEditorUiState(
            selectedLocationId = defaultPlanLocationId,
            availableExercises = _planEditorUiState.value.availableExercises,
            locations = _planEditorUiState.value.locations
        )
    }

    fun loadPlanEditor(templateId: Long) {
        _planEditorUiState.update {
            it.copy(
                isLoading = true,
                error = null,
                saveSuccess = false,
                isDeleted = false,
                startedSessionId = null
            )
        }
        viewModelScope.launch {
            try {
                val template = sessionRepository.getPlanTemplateById(templateId)
                    ?: error("Workout plan not found.")
                val templateExercises = sessionRepository.getExercisesForPlanTemplate(templateId)
                val exerciseById = exerciseRepository.getAllExercisesIncludingArchived().first().associateBy { it.id }
                _planEditorUiState.update {
                    it.copy(
                        templateId = template.id,
                        name = template.name,
                        description = template.description,
                        notes = template.notes,
                        selectedLocationId = template.locationId,
                        durationMinutes = template.targetDurationMinutes,
                        selectedCategories = parsePlanCategories(template.targetCategories),
                        selectedTimeSlot = template.scheduledTimeSlot,
                        sourcePhase = template.sourcePhase,
                        selectedExercises = templateExercises.map { item ->
                            item.toEditorItem(exerciseById[item.exerciseId]?.name ?: "Exercise ${item.exerciseId}")
                        },
                        isLoading = false,
                        error = null,
                        saveSuccess = false,
                        isDeleted = false,
                        startedSessionId = null
                    )
                }
            } catch (e: Exception) {
                _planEditorUiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load workout plan.")
                }
            }
        }
    }

    fun updatePlanEditorName(value: String) {
        _planEditorUiState.update { it.copy(name = value, error = null, saveSuccess = false) }
    }

    fun updatePlanEditorDescription(value: String) {
        _planEditorUiState.update { it.copy(description = value, error = null, saveSuccess = false) }
    }

    fun updatePlanEditorNotes(value: String) {
        _planEditorUiState.update { it.copy(notes = value, error = null, saveSuccess = false) }
    }

    fun updatePlanEditorDuration(value: Int) {
        _planEditorUiState.update { it.copy(durationMinutes = value.coerceIn(10, 240), error = null, saveSuccess = false) }
    }

    fun updatePlanEditorLocation(locationId: Long?) {
        _planEditorUiState.update { it.copy(selectedLocationId = locationId, error = null, saveSuccess = false) }
    }

    fun updatePlanEditorTimeSlot(timeSlot: TimeSlot) {
        _planEditorUiState.update { it.copy(selectedTimeSlot = timeSlot, error = null, saveSuccess = false) }
    }

    fun updatePlanEditorPhase(phase: TrainingPhase) {
        _planEditorUiState.update { it.copy(sourcePhase = phase, error = null, saveSuccess = false) }
    }

    fun togglePlanEditorCategory(category: WorkoutCategory) {
        _planEditorUiState.update { state ->
            state.copy(
                selectedCategories = state.selectedCategories.toMutableSet().apply {
                    if (!add(category)) remove(category)
                },
                error = null,
                saveSuccess = false
            )
        }
    }

    fun addExerciseToPlanEditor(exerciseId: Long) {
        _planEditorUiState.update { state ->
            if (state.selectedExercises.any { it.exerciseId == exerciseId }) return@update state
            val exercise = state.availableExercises.firstOrNull { it.id == exerciseId } ?: return@update state
            state.copy(
                selectedExercises = state.selectedExercises + exercise.toEditorItem(),
                error = null,
                saveSuccess = false
            )
        }
    }

    fun removeExerciseFromPlanEditor(exerciseId: Long) {
        _planEditorUiState.update { state ->
            state.copy(
                selectedExercises = state.selectedExercises.filterNot { it.exerciseId == exerciseId },
                error = null,
                saveSuccess = false
            )
        }
    }

    fun moveExerciseUpInPlanEditor(exerciseId: Long) {
        _planEditorUiState.update { state ->
            val index = state.selectedExercises.indexOfFirst { it.exerciseId == exerciseId }
            if (index <= 0) return@update state
            val updated = state.selectedExercises.toMutableList().apply {
                add(index - 1, removeAt(index))
            }
            state.copy(selectedExercises = updated, error = null, saveSuccess = false)
        }
    }

    fun moveExerciseDownInPlanEditor(exerciseId: Long) {
        _planEditorUiState.update { state ->
            val index = state.selectedExercises.indexOfFirst { it.exerciseId == exerciseId }
            if (index == -1 || index >= state.selectedExercises.lastIndex) return@update state
            val updated = state.selectedExercises.toMutableList().apply {
                add(index + 1, removeAt(index))
            }
            state.copy(selectedExercises = updated, error = null, saveSuccess = false)
        }
    }

    fun updatePlanExerciseSets(exerciseId: Long, value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(1, 20) ?: return
        updatePlanEditorExercise(exerciseId) { copy(sets = parsed) }
    }

    fun updatePlanExerciseReps(exerciseId: Long, value: String) {
        updatePlanEditorExercise(exerciseId) { copy(reps = value) }
    }

    fun updatePlanExerciseRest(exerciseId: Long, value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(0, 600) ?: return
        updatePlanEditorExercise(exerciseId) { copy(restSeconds = parsed) }
    }

    fun updatePlanExerciseSection(exerciseId: Long, section: PlanExerciseSection) {
        updatePlanEditorExercise(exerciseId) { copy(section = section) }
    }

    fun updatePlanExerciseRounds(exerciseId: Long, value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(1, 20) ?: return
        updatePlanEditorExercise(exerciseId) { copy(rounds = parsed) }
    }

    fun updatePlanExerciseDuration(exerciseId: Long, value: String) {
        val parsed = value.toIntOrNull()?.coerceIn(1, 3600)
        updatePlanEditorExercise(exerciseId) { copy(durationSeconds = parsed) }
    }

    fun updatePlanExerciseTempo(exerciseId: Long, value: String) {
        updatePlanEditorExercise(exerciseId) { copy(tempo = value) }
    }

    fun updatePlanExerciseEffortTarget(exerciseId: Long, value: String) {
        updatePlanEditorExercise(exerciseId) { copy(effortTarget = value) }
    }

    fun updatePlanExerciseNotes(exerciseId: Long, value: String) {
        updatePlanEditorExercise(exerciseId) { copy(notes = value) }
    }

    fun savePlanEditor() {
        persistPlanEditor(playAfterSave = false)
    }

    fun playPlanFromEditor() {
        persistPlanEditor(playAfterSave = true)
    }

    fun deletePlanEditor() {
        val templateId = _planEditorUiState.value.templateId ?: return
        viewModelScope.launch {
            _planEditorUiState.update { it.copy(isSaving = true, error = null) }
            try {
                sessionRepository.deletePlanTemplate(templateId)
                _planEditorUiState.update { it.copy(isSaving = false, isDeleted = true) }
            } catch (e: Exception) {
                _planEditorUiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to delete workout plan.")
                }
            }
        }
    }

    fun clearPlanEditorConsumedState() {
        _planEditorUiState.update {
            it.copy(saveSuccess = false, isDeleted = false, startedSessionId = null)
        }
    }

    fun previewWorkout() {
        viewModelScope.launch {
            if (_previewFeedbackSessionId.value == null) {
                _previewFeedbackSessionId.value = -System.currentTimeMillis()
            }
            _isPreviewing.value = true; _generationError.value = null
            try { _previewDraft.value = workoutPlanner.previewWorkout(currentGenerationParams()) }
            catch (e: Exception) { _previewDraft.value = null; _generationError.value = e.message ?: "Preview failed." }
            finally { _isPreviewing.value = false }
        }
    }

    fun rejectPreviewExercise(summary: PlannedExerciseSummary) {
        if (summary.exerciseId in _excludedPreviewExerciseIds.value) return
        viewModelScope.launch {
            recordPreviewDecision(summary, isSwap = false)
            _excludedPreviewExerciseIds.value = _excludedPreviewExerciseIds.value + summary.exerciseId
            refreshPreviewKeepingReviewSession()
        }
    }

    fun swapPreviewExercise(summary: PlannedExerciseSummary) {
        if (summary.exerciseId in _excludedPreviewExerciseIds.value) return
        viewModelScope.launch {
            recordPreviewDecision(summary, isSwap = true)
            _excludedPreviewExerciseIds.value = _excludedPreviewExerciseIds.value + summary.exerciseId
            refreshPreviewKeepingReviewSession()
        }
    }

    fun resetPreviewChanges() {
        if (_excludedPreviewExerciseIds.value.isEmpty()) return
        viewModelScope.launch {
            _excludedPreviewExerciseIds.value = emptySet()
            _previewFeedbackSessionId.value = -System.currentTimeMillis()
            refreshPreviewKeepingReviewSession()
        }
    }

    fun generateWorkout() {
        viewModelScope.launch {
            _isGenerating.value = true; _generationError.value = null
            try {
                val result = workoutPlanner.generateAndSaveWorkout(currentGenerationParams())
                _previewDraft.value = result.draft; _generatedSessionId.value = result.sessionId
            } catch (e: Exception) { _generationError.value = e.message ?: "Generation failed." }
            finally { _isGenerating.value = false }
        }
    }

    fun savePreviewAsPlan(name: String, description: String = "") {
        val draft = _previewDraft.value ?: return
        viewModelScope.launch {
            _isSavingPlan.value = true
            _planSaveMessage.value = null
            try {
                workoutPlanner.saveDraftAsTemplate(
                    draft = draft,
                    input = SaveWorkoutPlanTemplateInput(name = name, description = description),
                    phase = generatorUiState.value.currentPhase
                )
                _planSaveMessage.value = "Saved \"${name.ifBlank { draft.session.name }}\" as a reusable plan."
            } catch (e: Exception) {
                _generationError.value = e.message ?: "Failed to save workout plan."
            } finally {
                _isSavingPlan.value = false
            }
        }
    }

    fun playPlan(templateId: Long) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            try {
                _generatedSessionId.value = workoutPlanner.playTemplate(templateId)
            } catch (e: Exception) {
                _generationError.value = e.message ?: "Failed to start saved plan."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // ========== Active workout actions ==========
    fun loadActiveWorkout(sessionId: Long) {
        if (_activeSessionId.value == sessionId) return
        _activeSessionId.value = sessionId
        _completedWorkoutId.value = null
        _activeWorkoutError.value = null
        _setEntryDrafts.value = emptyMap()
        viewModelScope.launch {
            sessionRepository.getSessionById(sessionId)?.let { session ->
                _completionInput.value = WorkoutCompletionInput(
                    perceivedDifficulty = session.perceivedDifficulty ?: 6,
                    energyLevel = session.energyLevel ?: 6,
                    satisfactionRating = session.satisfactionRating ?: 4,
                    notes = session.postSessionNotes
                )
            }
        }
    }

    fun startWorkout() {
        val sid = _activeSessionId.value ?: return
        viewModelScope.launch {
            val s = sessionRepository.getSessionById(sid) ?: return@launch
            if (s.status == SessionStatus.PLANNED) { sessionRepository.startSession(sid); refreshActive() }
        }
    }

    fun toggleExerciseCompleted(sessionExercise: SessionExercise, completed: Boolean) {
        viewModelScope.launch {
            try {
                _activeWorkoutError.value = null
                sessionRepository.markExerciseCompleted(sessionExercise.id, completed)
                refreshActive()
            } catch (e: Exception) {
                _activeWorkoutError.value = e.message ?: "Failed to update exercise status."
            }
        }
    }

    fun toggleExerciseSkipped(sessionExercise: SessionExercise, skipped: Boolean) {
        viewModelScope.launch {
            try {
                _activeWorkoutError.value = null
                sessionRepository.markExerciseSkipped(sessionExercise.id, skipped)
                refreshActive()
            } catch (e: Exception) {
                _activeWorkoutError.value = e.message ?: "Failed to update exercise status."
            }
        }
    }

    // --- Set entry draft helpers ---
    fun updateSetDraftReps(seId: Long, v: String) = updateDraft(seId) { copy(reps = v) }
    fun updateSetDraftWeight(seId: Long, v: String) = updateDraft(seId) { copy(weight = v) }
    fun updateSetDraftDuration(seId: Long, v: String) = updateDraft(seId) { copy(durationSeconds = v) }
    fun updateSetDraftRpe(seId: Long, v: String) = updateDraft(seId) { copy(rpe = v) }
    fun updateSetDraftNotes(seId: Long, v: String) = updateDraft(seId) { copy(notes = v) }

    fun saveSetLog(item: SessionExerciseUi) {
        viewModelScope.launch {
            try {
                _activeWorkoutError.value = null
                val draft = _setEntryDrafts.value[item.sessionExercise.id] ?: SetEntryDraft()
                val nextSet = (item.setLogs.maxOfOrNull { it.setNumber } ?: 0) + 1
                val richPrescription = item.sessionExercise.prescriptionJson.toRichPrescriptionDataOrNull()
                val log = SetLog(
                    sessionExerciseId = item.sessionExercise.id,
                    setNumber = nextSet,
                    reps = draft.reps.toIntOrNull() ?: extractFirstInt(item.sessionExercise.plannedReps),
                    weight = draft.weight.toFloatOrNull(),
                    weightUnit = WeightUnit.KG,
                    durationSeconds = draft.durationSeconds.toIntOrNull() ?: richPrescription?.durationSeconds,
                    rpe = draft.rpe.toIntOrNull()?.coerceIn(1, 10),
                    restTakenSeconds = item.sessionExercise.plannedRestSeconds,
                    notes = draft.notes
                )
                sessionRepository.logSet(log)
                _setEntryDrafts.value = _setEntryDrafts.value.toMutableMap().apply { remove(item.sessionExercise.id) }
                refreshActive()
            } catch (e: Exception) {
                _activeWorkoutError.value = e.message ?: "Failed to save set."
            }
        }
    }

    fun deleteSetLog(setLog: SetLog) {
        viewModelScope.launch {
            try {
                _activeWorkoutError.value = null
                sessionRepository.deleteSetLog(setLog)
                refreshActive()
            } catch (e: Exception) {
                _activeWorkoutError.value = e.message ?: "Failed to delete set."
            }
        }
    }

    // --- Completion feedback ---
    fun updatePerceivedDifficulty(value: Int) { _completionInput.value = _completionInput.value.copy(perceivedDifficulty = value) }
    fun updateEnergyLevel(value: Int) { _completionInput.value = _completionInput.value.copy(energyLevel = value) }
    fun updateSatisfaction(value: Int) { _completionInput.value = _completionInput.value.copy(satisfactionRating = value) }
    fun updateCompletionNotes(notes: String) { _completionInput.value = _completionInput.value.copy(notes = notes) }

    fun completeWorkout() {
        val sid = _activeSessionId.value ?: return
        viewModelScope.launch {
            _isCompletingWorkout.value = true
            _activeWorkoutError.value = null
            try {
                workoutPlanner.completeWorkout(sid, _completionInput.value)
                _completedWorkoutId.value = sid
            } catch (e: Exception) {
                _activeWorkoutError.value = e.message ?: "Failed to complete workout."
            } finally {
                _isCompletingWorkout.value = false
            }
        }
    }

    // --- History actions ---
    fun setHistoryStatusFilter(status: SessionStatus?) { _historyStatusFilter.value = status }

    // ========== Private helpers ==========
    private fun currentGenerationParams(): WorkoutGenerationParams {
        val s = generatorUiState.value
        return WorkoutGenerationParams(
            locationId = s.selectedLocationId,
            durationMinutes = s.durationMinutes,
            selectedCategories = s.selectedCategories,
            timeSlot = s.selectedTimeSlot,
            excludedExerciseIds = _excludedPreviewExerciseIds.value
        )
    }

    private suspend fun recordPreviewDecision(summary: PlannedExerciseSummary, isSwap: Boolean) {
        val previewSessionId = _previewFeedbackSessionId.value ?: -System.currentTimeMillis().also {
            _previewFeedbackSessionId.value = it
        }
        val exercise = exerciseRepository.getExerciseById(summary.exerciseId) ?: return
        val category = summary.categories.firstOrNull() ?: return
        val daysSinceExercise = exercise.lastPerformedAt
            ?.let { ((System.currentTimeMillis() - it) / (24L * 60L * 60L * 1000L)).toInt() }
            ?: Int.MAX_VALUE
        val daysSinceCategory = userGoalRepository.getCategoryStats(category)?.daysSinceLastTrained ?: Int.MAX_VALUE
        val weights = runCatching { userGoalRepository.getCategoryWeights() }.getOrDefault(emptyMap())
        
        val statsList = userGoalRepository.getAllCategoryStats().first()
        val balanceScore = DashboardAnalytics.balanceScore(statsList, weights)

        mlFeedbackRepository.recordSuggestion(
            exerciseId = summary.exerciseId,
            category = category,
            sessionId = previewSessionId,
            daysSinceExercise = daysSinceExercise,
            daysSinceCategory = daysSinceCategory,
            exerciseTimesPerformed = exercise.timesPerformed,
            difficulty = exercise.difficulty.level.coerceIn(1, 5),
            sessionDuration = generatorUiState.value.durationMinutes,
            balanceScore = balanceScore,
            timeSlot = generatorUiState.value.selectedTimeSlot
        )
        if (isSwap) mlFeedbackRepository.recordSwap(previewSessionId, summary.exerciseId)
        else mlFeedbackRepository.recordRejection(previewSessionId, summary.exerciseId)
    }

    private suspend fun refreshPreviewKeepingReviewSession() {
        _isPreviewing.value = true
        _generationError.value = null
        try {
            _previewDraft.value = workoutPlanner.previewWorkout(currentGenerationParams())
        } catch (e: Exception) {
            _previewDraft.value = null
            _generationError.value = e.message ?: "Preview failed."
        } finally {
            _isPreviewing.value = false
        }
    }

    private fun invalidateGeneratorPreview() {
        _previewDraft.value = null
        _generationError.value = null
        _excludedPreviewExerciseIds.value = emptySet()
        _previewFeedbackSessionId.value = null
    }
    private fun refreshActive() { _activeRefreshTick.value++ }

    private fun observePlanEditorReferenceData() {
        viewModelScope.launch {
            equipmentRepository.getAllLocations().collect { locations ->
                _planEditorUiState.update { state ->
                    state.copy(locations = locations)
                }
            }
        }
        viewModelScope.launch {
            equipmentRepository.getDefaultLocationFlow().collect { location ->
                defaultPlanLocationId = location?.id
                _planEditorUiState.update { state ->
                    if (state.templateId == null && state.selectedLocationId == null) {
                        state.copy(selectedLocationId = defaultPlanLocationId)
                    } else state
                }
            }
        }
        viewModelScope.launch {
            exerciseRepository.getAllExercisesIncludingArchived().collect { exercises ->
                val activeSorted = exercises.filterNot { it.isArchived }.sortedBy { it.name.lowercase() }
                val exerciseById = exercises.associateBy { it.id }
                _planEditorUiState.update { state ->
                    state.copy(
                        availableExercises = activeSorted,
                        selectedExercises = state.selectedExercises.map { item ->
                            item.copy(exerciseName = exerciseById[item.exerciseId]?.name ?: item.exerciseName)
                        }
                    )
                }
            }
        }
    }

    private fun updatePlanEditorExercise(
        exerciseId: Long,
        transform: PlanExerciseEditorItem.() -> PlanExerciseEditorItem
    ) {
        _planEditorUiState.update { state ->
            state.copy(
                selectedExercises = state.selectedExercises.map { item ->
                    if (item.exerciseId == exerciseId) item.transform() else item
                },
                error = null,
                saveSuccess = false
            )
        }
    }

    private fun persistPlanEditor(playAfterSave: Boolean) {
        val snapshot = _planEditorUiState.value
        if (snapshot.name.isBlank()) {
            _planEditorUiState.update { it.copy(error = "Plan name is required.") }
            return
        }
        if (snapshot.selectedExercises.isEmpty()) {
            _planEditorUiState.update { it.copy(error = "Add at least one exercise to the plan.") }
            return
        }

        viewModelScope.launch {
            _planEditorUiState.update { it.copy(isSaving = true, error = null) }
            try {
                val template = WorkoutPlanTemplate(
                    id = snapshot.templateId ?: 0,
                    name = snapshot.name.trim(),
                    description = snapshot.description.trim(),
                    notes = snapshot.notes.trim(),
                    locationId = snapshot.selectedLocationId,
                    targetDurationMinutes = snapshot.durationMinutes,
                    targetCategories = json.encodeToString(snapshot.selectedCategories.map { it.name }),
                    scheduledTimeSlot = snapshot.selectedTimeSlot,
                    sourcePhase = snapshot.sourcePhase,
                    createdAt = if (snapshot.templateId == null) System.currentTimeMillis() else snapshot.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                val templateExercises = snapshot.selectedExercises.mapIndexed { index, item ->
                    WorkoutPlanTemplateExercise(
                        templateId = snapshot.templateId ?: 0,
                        exerciseId = item.exerciseId,
                        orderIndex = index,
                        section = item.section,
                        plannedSets = item.sets,
                        plannedReps = item.reps.ifBlank { "8-12" },
                        plannedRestSeconds = item.restSeconds,
                        prescriptionJson = item.toRichPrescriptionData().toJson(),
                        coachingNotes = item.notes.trim()
                    )
                }
                val templateId = if (snapshot.templateId == null) {
                    sessionRepository.createPlanTemplate(template, templateExercises)
                } else {
                    sessionRepository.updatePlanTemplate(template, templateExercises)
                    snapshot.templateId
                }

                if (playAfterSave) {
                    val sessionId = sessionRepository.instantiateSessionFromTemplate(templateId)
                    _planEditorUiState.update {
                        it.copy(
                            templateId = templateId,
                            isSaving = false,
                            saveSuccess = true,
                            startedSessionId = sessionId,
                            createdAt = template.createdAt
                        )
                    }
                } else {
                    _planEditorUiState.update {
                        it.copy(
                            templateId = templateId,
                            isSaving = false,
                            saveSuccess = true,
                            startedSessionId = null,
                            createdAt = template.createdAt
                        )
                    }
                }
            } catch (e: Exception) {
                _planEditorUiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to save workout plan.")
                }
            }
        }
    }

    private fun updateDraft(seId: Long, transform: SetEntryDraft.() -> SetEntryDraft) {
        _setEntryDrafts.value = _setEntryDrafts.value.toMutableMap().apply {
            this[seId] = (this[seId] ?: SetEntryDraft()).transform()
        }
    }
    private fun extractFirstInt(value: String): Int? = Regex("\\d+").find(value)?.value?.toIntOrNull()

    private fun parsePlanCategories(value: String): Set<WorkoutCategory> =
        try {
            json.decodeFromString<List<String>>(value)
                .mapNotNull { raw -> WorkoutCategory.entries.firstOrNull { it.name == raw } }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
}

// ========== Internal state helpers ==========
private data class GeneratorBaseState(
    val locations: List<EquipmentLocation> = emptyList(), val selectedLocationId: Long? = null,
    val defaultLocationId: Long? = null, val durationMinutes: Int = 60, val currentPhase: TrainingPhase = TrainingPhase.BALANCED,
    val excludedPreviewExerciseIds: Set<Long> = emptySet()
)
private data class GeneratorSelectionState(val base: GeneratorBaseState, val selectedCategories: List<WorkoutCategory>, val selectedTimeSlot: TimeSlot)
private data class GeneratorStatusState(
    val isGenerating: Boolean = false,
    val isPreviewing: Boolean = false,
    val error: String? = null,
    val generatedSessionId: Long? = null,
    val previewDraft: WorkoutPlanDraft? = null,
    val isSavingPlan: Boolean = false,
    val planSaveMessage: String? = null
)

// ========== Public UI state classes ==========
data class SetEntryDraft(val reps: String = "", val weight: String = "", val durationSeconds: String = "", val rpe: String = "", val notes: String = "")

data class WorkoutOverviewUiState(
    val activeSession: WorkoutSession? = null,
    val todaysSessions: List<WorkoutSession> = emptyList(),
    val recentSessions: List<WorkoutSession> = emptyList(),
    val savedPlans: List<WorkoutPlanTemplateSummary> = emptyList(),
    val isLoading: Boolean = false
)

data class WorkoutGeneratorUiState(
    val locations: List<EquipmentLocation> = emptyList(), val selectedLocationId: Long? = null, val durationMinutes: Int = 60,
    val selectedCategories: List<WorkoutCategory> = emptyList(), val selectedTimeSlot: TimeSlot = TimeSlot.ANYTIME,
    val currentPhase: TrainingPhase = TrainingPhase.BALANCED, val excludedExerciseIds: Set<Long> = emptySet(),
    val isGenerating: Boolean = false, val isPreviewing: Boolean = false,
    val error: String? = null, val generatedSessionId: Long? = null, val previewDraft: WorkoutPlanDraft? = null,
    val hasPreviewCustomizations: Boolean = false,
    val isSavingPlan: Boolean = false,
    val planSaveMessage: String? = null,
    val isLoading: Boolean = false
)

data class SessionExerciseUi(
    val sessionExercise: SessionExercise,
    val exercise: Exercise?,
    val setLogs: List<SetLog> = emptyList(),
    val draft: SetEntryDraft = SetEntryDraft()
) {
    val completionState = WorkoutCompletionSemantics.exerciseState(sessionExercise, setLogs.size)
}

data class ActiveWorkoutUiState(
    val session: WorkoutSession? = null, val exercises: List<SessionExerciseUi> = emptyList(),
    val completionInput: WorkoutCompletionInput = WorkoutCompletionInput(),
    val isCompleting: Boolean = false, val completedWorkoutId: Long? = null,
    val error: String? = null, val isLoading: Boolean = false
)

data class WorkoutHistoryUiState(
    val sessions: List<SessionHistorySummary> = emptyList(),
    val selectedStatus: SessionStatus? = null,
    val isLoading: Boolean = false
)

data class PlanExerciseEditorItem(
    val exerciseId: Long,
    val exerciseName: String,
    val section: PlanExerciseSection,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val rounds: Int = 1,
    val durationSeconds: Int? = null,
    val tempo: String = "",
    val effortTarget: String = "",
    val notes: String = ""
)

data class WorkoutPlanEditorUiState(
    val templateId: Long? = null,
    val name: String = "",
    val description: String = "",
    val notes: String = "",
    val locations: List<EquipmentLocation> = emptyList(),
    val selectedLocationId: Long? = null,
    val durationMinutes: Int = 60,
    val selectedCategories: Set<WorkoutCategory> = emptySet(),
    val selectedTimeSlot: TimeSlot = TimeSlot.ANYTIME,
    val sourcePhase: TrainingPhase = TrainingPhase.BALANCED,
    val availableExercises: List<Exercise> = emptyList(),
    val selectedExercises: List<PlanExerciseEditorItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val isDeleted: Boolean = false,
    val startedSessionId: Long? = null,
    val error: String? = null
)

private fun Exercise.toEditorItem(): PlanExerciseEditorItem =
    resolveBalancedProgrammingPreset().let { preset ->
        PlanExerciseEditorItem(
            exerciseId = id,
            exerciseName = name,
            section = PlanExerciseSection.MAIN,
            sets = resolveEditorSetCount(preset.setsText, defaultSets),
            reps = preset.repsText.ifBlank { defaultReps },
            restSeconds = preset.restSeconds,
            rounds = preset.rounds,
            durationSeconds = preset.durationSeconds,
            tempo = preset.tempo,
            effortTarget = preset.effortTarget
        )
    }

private fun WorkoutPlanTemplateExercise.toEditorItem(exerciseName: String): PlanExerciseEditorItem =
    (prescriptionJson.toRichPrescriptionDataOrNull() ?: RichPrescriptionData()).let { prescription ->
    PlanExerciseEditorItem(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        section = section,
        sets = plannedSets,
        reps = plannedReps,
        restSeconds = plannedRestSeconds,
        rounds = prescription.rounds,
        durationSeconds = prescription.durationSeconds,
        tempo = prescription.tempo,
        effortTarget = prescription.effortTarget,
        notes = coachingNotes
    )
    }

private fun PlanExerciseEditorItem.toRichPrescriptionData(): RichPrescriptionData = RichPrescriptionData(
    rounds = rounds,
    durationSeconds = durationSeconds,
    tempo = tempo.trim(),
    effortTarget = effortTarget.trim()
)

private fun resolveEditorSetCount(setsText: String, fallback: Int): Int {
    val numbers = Regex("\\d+").findAll(setsText).mapNotNull { it.value.toIntOrNull() }.toList()
    return when {
        numbers.isEmpty() -> fallback
        numbers.size == 1 -> numbers.first().coerceIn(1, 20)
        else -> ((numbers.first() + numbers.last()) / 2).coerceIn(1, 20)
    }
}
