package com.example.workoutapp.ui.workout

import androidx.lifecycle.SavedStateHandle
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.model.SessionStatus
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.model.encodeGeneratorCategorySelection
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.MLFeedbackRepository
import com.example.workoutapp.data.repository.UserGoalRepository
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import com.example.workoutapp.domain.SaveWorkoutPlanTemplateInput
import com.example.workoutapp.domain.WorkoutGenerationParams
import com.example.workoutapp.domain.WorkoutPlanDraft
import com.example.workoutapp.domain.WorkoutPlanner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real [WorkoutViewModel] integration coverage (constructing the actual class with mocked
 * repository/planner collaborators - never a pure-helper-only test) for the two PR #9 review
 * blockers plus SavedStateHandle restoration:
 *
 * 1. `hasSavedProfile` must reflect [UserGoalRepository.hasCustomizedProfileFlow], never mere
 *    `UserGoal` row presence (the seeded-default-row bug).
 * 2. A failed Save-as-Plan must produce a real, working "Try again" (never a dead retry).
 * 3. Generator selections seeded into [SavedStateHandle] (including corrupt values) must restore
 *    safely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelGeneratorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sessionRepository: WorkoutSessionRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var equipmentRepository: EquipmentRepository
    private lateinit var userGoalRepository: UserGoalRepository
    private lateinit var mlFeedbackRepository: MLFeedbackRepository
    private lateinit var workoutPlanner: WorkoutPlanner

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk(relaxed = true)
        exerciseRepository = mockk(relaxed = true)
        equipmentRepository = mockk(relaxed = true)
        userGoalRepository = mockk(relaxed = true)
        mlFeedbackRepository = mockk(relaxed = true)
        workoutPlanner = mockk(relaxed = true)

        // Exercised unconditionally by WorkoutViewModel's init block (observePlanEditorReferenceData).
        every { equipmentRepository.getAllLocations() } returns flowOf(emptyList())
        every { equipmentRepository.getDefaultLocationFlow() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): WorkoutViewModel =
        WorkoutViewModel(
            sessionRepository = sessionRepository,
            exerciseRepository = exerciseRepository,
            equipmentRepository = equipmentRepository,
            userGoalRepository = userGoalRepository,
            mlFeedbackRepository = mlFeedbackRepository,
            workoutPlanner = workoutPlanner,
            savedStateHandle = savedStateHandle
        )

    /** Keeps generatorUiState's WhileSubscribed(5_000) upstream flows actively collecting. */
    private fun kotlinx.coroutines.test.TestScope.subscribeToGeneratorUiState(viewModel: WorkoutViewModel) {
        viewModel.generatorUiState.onEach { }.launchIn(backgroundScope)
    }

    private val sampleDraft = WorkoutPlanDraft(
        session = WorkoutSession(name = "Sample Session"),
        exercises = emptyList(),
        focusCategories = emptyList(),
        estimatedDurationMinutes = 30,
        reasoning = emptyList(),
        exerciseSummaries = emptyList()
    )

    // ===================== Blocker #1: hasSavedProfile signal =====================

    @Test
    fun hasSavedProfile_isFalse_whenSeededRowExists_butNotCustomized() = runTest(testDispatcher) {
        // Mirrors production: WorkoutDatabase seeds a default UserGoal() row on first install,
        // but the user has never intentionally saved anything through Settings.
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)

        val viewModel = createViewModel()
        subscribeToGeneratorUiState(viewModel)

        assertFalse(
            "A seeded default UserGoal row must not be mistaken for a personalized profile",
            viewModel.generatorUiState.value.hasSavedProfile
        )
    }

    @Test
    fun hasSavedProfile_isTrue_whenCustomizedSignalIsSet() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(true)

        val viewModel = createViewModel()
        subscribeToGeneratorUiState(viewModel)

        assertTrue(viewModel.generatorUiState.value.hasSavedProfile)
    }

    // ===================== Blocker #2: Save-as-Plan retry =====================

    @Test
    fun savePreviewAsPlan_failure_setsRetryableAction_andRetryReinvokesSaveWithSameInput() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)
        coEvery { workoutPlanner.previewWorkout(any()) } returns sampleDraft
        coEvery {
            workoutPlanner.saveDraftAsTemplate(any(), any(), any())
        } throws RuntimeException("save failed") andThen 42L

        val viewModel = createViewModel()
        subscribeToGeneratorUiState(viewModel)

        viewModel.previewWorkout()
        viewModel.savePreviewAsPlan("My Plan", "desc")

        val afterFailure = viewModel.generatorUiState.value
        assertEquals("save failed", afterFailure.error)
        assertEquals(
            "A save failure must be retryable, not silently dropped",
            GeneratorAction.SAVE_PLAN,
            afterFailure.lastFailedAction
        )

        viewModel.retryLastGeneratorAction()

        coVerify(exactly = 2) {
            workoutPlanner.saveDraftAsTemplate(
                sampleDraft,
                SaveWorkoutPlanTemplateInput(name = "My Plan", description = "desc"),
                any()
            )
        }
        val afterRetry = viewModel.generatorUiState.value
        assertNull("A successful retry must clear the error", afterRetry.error)
        assertNull(afterRetry.lastFailedAction)
        assertTrue(afterRetry.planSaveMessage?.contains("My Plan") == true)
    }

    @Test
    fun retryLastGeneratorAction_isNoOp_whenNothingFailed() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)

        val viewModel = createViewModel()
        subscribeToGeneratorUiState(viewModel)

        // No prior failure recorded -> retry must never invoke anything (never a dead CTA that
        // fires an unrelated action, and never a crash).
        viewModel.retryLastGeneratorAction()

        coVerify(exactly = 0) { workoutPlanner.saveDraftAsTemplate(any(), any(), any()) }
        coVerify(exactly = 0) { workoutPlanner.previewWorkout(any()) }
        coVerify(exactly = 0) { workoutPlanner.generateAndSaveWorkout(any()) }
    }

    @Test
    fun savePreviewAsPlan_success_clearsAnyPriorFailedSaveRetryState() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)
        coEvery { workoutPlanner.previewWorkout(any()) } returns sampleDraft
        coEvery { workoutPlanner.saveDraftAsTemplate(any(), any(), any()) } returns 1L

        val viewModel = createViewModel()
        subscribeToGeneratorUiState(viewModel)

        viewModel.previewWorkout()
        viewModel.savePreviewAsPlan("Plan A")

        val state = viewModel.generatorUiState.value
        assertNull(state.error)
        assertNull(state.lastFailedAction)
    }

    // ===================== SavedStateHandle restoration =====================

    @Test
    fun generatorSelections_restoreFromSavedStateHandle() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)
        every { equipmentRepository.getAllLocations() } returns flowOf(
            listOf(EquipmentLocation(id = 7L, name = "Home Gym"))
        )

        val restoredHandle = SavedStateHandle(
            mapOf(
                WorkoutViewModel.KEY_GEN_LOCATION_ID to 7L,
                WorkoutViewModel.KEY_GEN_DURATION_MINUTES to 45,
                WorkoutViewModel.KEY_GEN_CATEGORIES_JSON to
                    encodeGeneratorCategorySelection(setOf(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY)),
                WorkoutViewModel.KEY_GEN_TIME_SLOT to TimeSlot.EVENING.name
            )
        )

        val viewModel = createViewModel(restoredHandle)
        subscribeToGeneratorUiState(viewModel)

        val state = viewModel.generatorUiState.value
        assertEquals(7L, state.selectedLocationId)
        assertEquals(45, state.durationMinutes)
        assertEquals(setOf(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY), state.selectedCategories.toSet())
        assertEquals(TimeSlot.EVENING, state.selectedTimeSlot)
    }

    @Test
    fun generatorSelections_corruptSavedStateHandle_fallsBackSafely_withoutCrashing() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)

        val corruptHandle = SavedStateHandle(
            mapOf(
                WorkoutViewModel.KEY_GEN_CATEGORIES_JSON to "{not valid json at all",
                WorkoutViewModel.KEY_GEN_TIME_SLOT to "NOT_A_REAL_TIME_SLOT"
            )
        )

        val viewModel = createViewModel(corruptHandle)
        subscribeToGeneratorUiState(viewModel)

        val state = viewModel.generatorUiState.value
        // Corrupt category JSON must decode to an empty selection rather than crash the ViewModel.
        assertTrue(state.selectedCategories.isEmpty())
        // Corrupt time slot enum name must fall back to the neutral default rather than crash.
        assertEquals(TimeSlot.ANYTIME, state.selectedTimeSlot)
    }

    @Test
    fun resetGeneratorSetup_clearsSavedStateHandleSelections() = runTest(testDispatcher) {
        every { userGoalRepository.getUserGoalFlow() } returns MutableStateFlow(UserGoal())
        every { userGoalRepository.hasCustomizedProfileFlow() } returns MutableStateFlow(false)
        every { equipmentRepository.getAllLocations() } returns flowOf(
            listOf(EquipmentLocation(id = 3L, name = "Gym"))
        )

        val handle = SavedStateHandle(
            mapOf(
                WorkoutViewModel.KEY_GEN_LOCATION_ID to 3L,
                WorkoutViewModel.KEY_GEN_DURATION_MINUTES to 30,
                WorkoutViewModel.KEY_GEN_CATEGORIES_JSON to
                    encodeGeneratorCategorySelection(setOf(WorkoutCategory.ENDURANCE)),
                WorkoutViewModel.KEY_GEN_TIME_SLOT to TimeSlot.MORNING.name
            )
        )

        val viewModel = createViewModel(handle)
        subscribeToGeneratorUiState(viewModel)
        // Sanity: selections did restore before we reset them.
        assertEquals(30, viewModel.generatorUiState.value.durationMinutes)

        viewModel.resetGeneratorSetup()

        assertNull(handle.get<Long>(WorkoutViewModel.KEY_GEN_LOCATION_ID))
        assertNull(handle.get<Int>(WorkoutViewModel.KEY_GEN_DURATION_MINUTES))
        assertNull(handle.get<String>(WorkoutViewModel.KEY_GEN_CATEGORIES_JSON))
        assertNull(handle.get<String>(WorkoutViewModel.KEY_GEN_TIME_SLOT))
    }
}
