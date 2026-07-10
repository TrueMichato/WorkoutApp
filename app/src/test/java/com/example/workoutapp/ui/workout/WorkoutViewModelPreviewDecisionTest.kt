package com.example.workoutapp.ui.workout

import com.example.workoutapp.data.local.dao.EquipmentDao
import com.example.workoutapp.data.local.dao.ExerciseDao
import com.example.workoutapp.data.local.dao.MLFeedbackDao
import com.example.workoutapp.data.local.dao.UserGoalDao
import com.example.workoutapp.data.local.dao.WorkoutPlanTemplateDao
import com.example.workoutapp.data.local.dao.WorkoutSessionDao
import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.CustomCategory
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseCategoryCrossRef
import com.example.workoutapp.data.model.ExerciseCustomCategoryCrossRef
import com.example.workoutapp.data.model.ExerciseEquipmentCrossRef
import com.example.workoutapp.data.model.ExerciseMuscleCrossRef
import com.example.workoutapp.data.model.FeedbackAction
import com.example.workoutapp.data.model.LocationEquipmentCrossRef
import com.example.workoutapp.data.model.MLFeedbackEvent
import com.example.workoutapp.data.model.MLPreferenceScore
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.SessionExercise
import com.example.workoutapp.data.model.SessionStatus
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WeightUnit
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutPlanTemplate
import com.example.workoutapp.data.model.WorkoutPlanTemplateExercise
import com.example.workoutapp.data.model.WorkoutPlanTemplateSummary
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.MLFeedbackRepository
import com.example.workoutapp.data.repository.UserGoalRepository
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import com.example.workoutapp.domain.PlannedExerciseSummary
import com.example.workoutapp.domain.WorkoutPlanner
import com.example.workoutapp.domain.ml.WorkoutRecommender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelPreviewDecisionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rejectPreviewExercise_unknownCategoryWeightReportsWarningAndDoesNotCrash() = runTest {
        val fixture = previewFixture("""{"STRENGTH":2.0,"FUTURE_CATEGORY":1.5}""")
        observeGeneratorUiState(fixture.viewModel)
        advanceUntilIdle()

        fixture.viewModel.rejectPreviewExercise(summaryForExerciseOne())
        advanceUntilIdle()

        assertNull(fixture.viewModel.generatorUiState.value.error)
        assertTrue(fixture.viewModel.generatorUiState.value.hasPreviewCustomizations)
        assertTrue(fixture.viewModel.generatorUiState.value.warning.orEmpty().contains("unknown category"))
        assertEquals(FeedbackAction.REJECTED, fixture.feedbackDao.events.single { it.exerciseId == 1L }.action)
    }

    @Test
    fun swapPreviewExercise_malformedCategoryWeightReportsWarningAndDoesNotCrash() = runTest {
        val fixture = previewFixture("""{"STRENGTH":""")
        observeGeneratorUiState(fixture.viewModel)
        advanceUntilIdle()

        fixture.viewModel.swapPreviewExercise(summaryForExerciseOne())
        advanceUntilIdle()

        assertNull(fixture.viewModel.generatorUiState.value.error)
        assertTrue(fixture.viewModel.generatorUiState.value.hasPreviewCustomizations)
        assertTrue(fixture.viewModel.generatorUiState.value.warning.orEmpty().contains("malformed"))
        assertEquals(FeedbackAction.SWAPPED, fixture.feedbackDao.events.single { it.exerciseId == 1L }.action)
    }

    private fun previewFixture(categoryWeights: String): PreviewFixture {
        val exerciseDao = FakeExerciseDao()
        val equipmentDao = FakeEquipmentDao()
        val userGoalDao = PreviewUserGoalDao(UserGoal(categoryWeights = categoryWeights))
        val feedbackDao = FakeMLFeedbackDao()
        val sessionDao = FakeWorkoutSessionDao()
        val templateDao = FakeWorkoutPlanTemplateDao()

        val exerciseRepository = ExerciseRepository(exerciseDao)
        val equipmentRepository = EquipmentRepository(equipmentDao)
        val userGoalRepository = UserGoalRepository(userGoalDao)
        val feedbackRepository = MLFeedbackRepository(feedbackDao)
        val sessionRepository = WorkoutSessionRepository(sessionDao, templateDao)
        val recommender = WorkoutRecommender(feedbackRepository)
        val planner = WorkoutPlanner(
            exerciseRepository = exerciseRepository,
            equipmentRepository = equipmentRepository,
            sessionRepository = sessionRepository,
            userGoalRepository = userGoalRepository,
            mlFeedbackRepository = feedbackRepository,
            workoutRecommender = recommender
        )

        return PreviewFixture(
            viewModel = WorkoutViewModel(
                sessionRepository = sessionRepository,
                exerciseRepository = exerciseRepository,
                equipmentRepository = equipmentRepository,
                userGoalRepository = userGoalRepository,
                mlFeedbackRepository = feedbackRepository,
                workoutPlanner = planner
            ),
            feedbackDao = feedbackDao
        )
    }

    private fun summaryForExerciseOne() = PlannedExerciseSummary(
        exerciseId = 1L,
        name = "Bench Press",
        categories = listOf(WorkoutCategory.STRENGTH),
        sets = 3,
        reps = "8-12",
        restSeconds = 90,
        estimatedMinutes = 5
    )

    private fun TestScope.observeGeneratorUiState(viewModel: WorkoutViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.generatorUiState.collect()
        }
    }
}

private data class PreviewFixture(
    val viewModel: WorkoutViewModel,
    val feedbackDao: FakeMLFeedbackDao
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class PreviewUserGoalDao(
    private var goal: UserGoal
) : UserGoalDao {
    private val stats = WorkoutCategory.rotationCategories().associateWith { category ->
        CategoryStats(category = category, daysSinceLastTrained = 1)
    }

    override suspend fun insert(goal: UserGoal) {
        this.goal = goal
    }

    override suspend fun update(goal: UserGoal) {
        this.goal = goal
    }

    override suspend fun get(): UserGoal = goal

    override fun getFlow(): Flow<UserGoal?> = flowOf(goal)

    override suspend fun updatePhase(phase: TrainingPhase, startDate: Long, endDate: Long?, timestamp: Long) {
        goal = goal.copy(currentPhase = phase, phaseStartDate = startDate, phaseEndDate = endDate, updatedAt = timestamp)
    }

    override suspend fun updateCategoryWeights(weights: String, timestamp: Long) {
        goal = goal.copy(categoryWeights = weights, updatedAt = timestamp)
    }

    override suspend fun insertCategoryStats(stats: CategoryStats) = Unit
    override suspend fun insertAllCategoryStats(stats: List<CategoryStats>) = Unit
    override suspend fun updateCategoryStats(stats: CategoryStats) = Unit
    override suspend fun getCategoryStats(category: WorkoutCategory): CategoryStats? = stats[category]
    override fun getAllCategoryStats(): Flow<List<CategoryStats>> = flowOf(stats.values.toList())
    override fun getCategoryStatsByNeglect(): Flow<List<CategoryStats>> = flowOf(stats.values.toList())
    override suspend fun recordCategoryTraining(category: WorkoutCategory, timestamp: Long) = Unit
    override suspend fun addCategoryMinutes(category: WorkoutCategory, minutes: Int, timestamp: Long) = Unit
    override suspend fun recalculateDaysSinceLastTrained(now: Long) = Unit
}

private class FakeExerciseDao : ExerciseDao {
    private val exercises = MutableStateFlow(
        listOf(
            Exercise(id = 1L, name = "Bench Press", difficulty = Difficulty.INTERMEDIATE),
            Exercise(id = 2L, name = "Incline Press", difficulty = Difficulty.INTERMEDIATE)
        )
    )

    override suspend fun insert(exercise: Exercise): Long = exercise.id
    override suspend fun insertAll(exercises: List<Exercise>): List<Long> = exercises.map { it.id }
    override suspend fun update(exercise: Exercise): Int = 1
    override suspend fun delete(exercise: Exercise) = Unit
    override suspend fun deleteById(exerciseId: Long) = Unit
    override suspend fun getById(id: Long): Exercise? = exercises.value.firstOrNull { it.id == id }
    override fun getByIdFlow(id: Long): Flow<Exercise?> = flowOf(exercises.value.firstOrNull { it.id == id })
    override fun getAllActive(): Flow<List<Exercise>> = exercises
    override fun getAll(): Flow<List<Exercise>> = exercises
    override suspend fun getAllSync(): List<Exercise> = exercises.value
    override fun getFavorites(): Flow<List<Exercise>> = flowOf(emptyList())
    override fun getRecentlyPerformed(limit: Int): Flow<List<Exercise>> = flowOf(emptyList())
    override fun getMostPerformed(limit: Int): Flow<List<Exercise>> = flowOf(emptyList())
    override fun getNeglected(beforeTimestamp: Long, limit: Int): Flow<List<Exercise>> = flowOf(emptyList())
    override fun search(query: String): Flow<List<Exercise>> = flowOf(emptyList())
    override fun getByDifficulty(difficulty: Difficulty): Flow<List<Exercise>> = flowOf(emptyList())
    override suspend fun markPerformed(exerciseId: Long, timestamp: Long) = Unit
    override suspend fun setFavorite(exerciseId: Long, isFavorite: Boolean, timestamp: Long) = Unit
    override suspend fun setArchived(exerciseId: Long, isArchived: Boolean, timestamp: Long) = Unit
    override suspend fun insertCategoryRef(ref: ExerciseCategoryCrossRef) = Unit
    override suspend fun insertCategoryRefs(refs: List<ExerciseCategoryCrossRef>) = Unit
    override suspend fun clearCategoriesForExercise(exerciseId: Long) = Unit
    override suspend fun getCategoriesForExercise(exerciseId: Long): List<WorkoutCategory> = listOf(WorkoutCategory.STRENGTH)
    override suspend fun getExerciseIdsForCategory(category: WorkoutCategory): List<Long> = emptyList()
    override fun getExercisesByCategory(category: WorkoutCategory): Flow<List<Exercise>> = flowOf(emptyList())
    override suspend fun insertEquipmentRef(ref: ExerciseEquipmentCrossRef) = Unit
    override suspend fun insertEquipmentRefs(refs: List<ExerciseEquipmentCrossRef>) = Unit
    override suspend fun clearEquipmentForExercise(exerciseId: Long) = Unit
    override suspend fun getRequiredEquipmentIds(exerciseId: Long): List<Long> = emptyList()
    override fun getExercisesByEquipment(equipmentId: Long): Flow<List<Exercise>> = flowOf(emptyList())
    override suspend fun insertMuscleRef(ref: ExerciseMuscleCrossRef) = Unit
    override suspend fun insertMuscleRefs(refs: List<ExerciseMuscleCrossRef>) = Unit
    override suspend fun clearMusclesForExercise(exerciseId: Long) = Unit
    override suspend fun getPrimaryMuscles(exerciseId: Long): List<MuscleGroup> = emptyList()
    override suspend fun getSecondaryMuscles(exerciseId: Long): List<MuscleGroup> = emptyList()
    override fun getExercisesByMuscle(muscleGroup: MuscleGroup): Flow<List<Exercise>> = flowOf(emptyList())
    override suspend fun insertCustomCategory(category: CustomCategory): Long = category.id
    override fun getAllCustomCategories(): Flow<List<CustomCategory>> = flowOf(emptyList())
    override suspend fun deleteCustomCategory(category: CustomCategory) = Unit
    override suspend fun insertCustomCategoryRef(ref: ExerciseCustomCategoryCrossRef) = Unit
    override suspend fun clearCustomCategoriesForExercise(exerciseId: Long) = Unit
    override fun getActiveCount(): Flow<Int> = flowOf(exercises.value.size)
    override suspend fun getFavoriteCount(): Int = 0
}

private class FakeEquipmentDao : EquipmentDao {
    override suspend fun insert(equipment: Equipment): Long = equipment.id
    override suspend fun insertAll(equipment: List<Equipment>): List<Long> = equipment.map { it.id }
    override suspend fun update(equipment: Equipment) = Unit
    override suspend fun delete(equipment: Equipment) = Unit
    override suspend fun getById(id: Long): Equipment? = null
    override fun getAll(): Flow<List<Equipment>> = flowOf(emptyList())
    override fun getPortable(): Flow<List<Equipment>> = flowOf(emptyList())
    override fun getCustom(): Flow<List<Equipment>> = flowOf(emptyList())
    override fun search(query: String): Flow<List<Equipment>> = flowOf(emptyList())
    override suspend fun getCount(): Int = 0
    override suspend fun insertLocation(location: EquipmentLocation): Long = location.id
    override suspend fun insertLocations(locations: List<EquipmentLocation>): List<Long> = locations.map { it.id }
    override suspend fun updateLocation(location: EquipmentLocation) = Unit
    override suspend fun deleteLocation(location: EquipmentLocation) = Unit
    override suspend fun getLocationById(id: Long): EquipmentLocation? = null
    override fun getAllLocations(): Flow<List<EquipmentLocation>> = flowOf(emptyList())
    override suspend fun getDefaultLocation(): EquipmentLocation? = null
    override fun getDefaultLocationFlow(): Flow<EquipmentLocation?> = flowOf(null)
    override suspend fun clearDefaultLocation() = Unit
    override suspend fun setDefaultLocation(locationId: Long) = Unit
    override suspend fun insertLocationEquipment(ref: LocationEquipmentCrossRef) = Unit
    override suspend fun insertLocationEquipmentAll(refs: List<LocationEquipmentCrossRef>) = Unit
    override suspend fun deleteLocationEquipment(ref: LocationEquipmentCrossRef) = Unit
    override suspend fun clearEquipmentForLocation(locationId: Long) = Unit
    override suspend fun removeEquipmentFromLocation(locationId: Long, equipmentId: Long) = Unit
    override fun getEquipmentForLocation(locationId: Long): Flow<List<Equipment>> = flowOf(emptyList())
    override suspend fun getEquipmentIdsForLocation(locationId: Long): List<Long> = emptyList()
    override suspend fun getEquipmentCountForLocation(locationId: Long): Int = 0
    override suspend fun isEquipmentAtLocation(locationId: Long, equipmentId: Long): Boolean = false
}

private class FakeMLFeedbackDao : MLFeedbackDao {
    val events = mutableListOf<MLFeedbackEvent>()

    override suspend fun insertEvent(event: MLFeedbackEvent): Long {
        val id = events.size + 1L
        events += event.copy(id = id)
        return id
    }

    override suspend fun insertEvents(events: List<MLFeedbackEvent>) {
        events.forEach { insertEvent(it) }
    }

    override suspend fun updateEvent(event: MLFeedbackEvent) = Unit
    override suspend fun getEventById(id: Long): MLFeedbackEvent? = events.firstOrNull { it.id == id }
    override fun getRecentEvents(limit: Int): Flow<List<MLFeedbackEvent>> = flowOf(events.take(limit))
    override fun getEventsForExercise(exerciseId: Long): Flow<List<MLFeedbackEvent>> = flowOf(events.filter { it.exerciseId == exerciseId })
    override fun getEventsForCategory(category: WorkoutCategory): Flow<List<MLFeedbackEvent>> = flowOf(events.filter { it.suggestedCategory == category })
    override suspend fun getEventsForSession(sessionId: Long): List<MLFeedbackEvent> = events.filter { it.suggestedInSessionId == sessionId }
    override suspend fun updateEventOutcome(sessionId: Long, exerciseId: Long, action: FeedbackAction, difficulty: Int?, rating: Int?) {
        val index = events.indexOfFirst { it.suggestedInSessionId == sessionId && it.exerciseId == exerciseId }
        if (index >= 0) {
            events[index] = events[index].copy(action = action, perceivedDifficulty = difficulty, satisfactionRating = rating)
        }
    }

    override suspend fun getPositiveCountForExercise(exerciseId: Long): Int = 0
    override suspend fun getNegativeCountForExercise(exerciseId: Long): Int = 0
    override suspend fun getPositiveCountForCategory(category: WorkoutCategory): Int = 0
    override suspend fun getNegativeCountForCategory(category: WorkoutCategory): Int = 0
    override suspend fun getTotalEventCount(): Int = events.size
    override suspend fun getRecentEventsSync(limit: Int): List<MLFeedbackEvent> = events.take(limit)
    override suspend fun getAcceptanceRateByTimeOfDay(exerciseId: Long, startHour: Int, endHour: Int): Float? = null
    override suspend fun getAcceptanceRateByDayOfWeek(exerciseId: Long, dayOfWeek: Int): Float? = null
    override suspend fun insertPreferenceScore(score: MLPreferenceScore) = Unit
    override suspend fun insertPreferenceScores(scores: List<MLPreferenceScore>) = Unit
    override suspend fun getPreferenceScore(key: String): MLPreferenceScore? = null
    override suspend fun getAllExerciseScores(): List<MLPreferenceScore> = emptyList()
    override suspend fun getAllCategoryScores(): List<MLPreferenceScore> = emptyList()
    override suspend fun clearPreferenceScores() = Unit
    override suspend fun deleteEventsOlderThan(timestamp: Long) = Unit
    override suspend fun clearAllEvents() = Unit
}

private class FakeWorkoutSessionDao : WorkoutSessionDao {
    override suspend fun insert(session: WorkoutSession): Long = session.id
    override suspend fun update(session: WorkoutSession) = Unit
    override suspend fun delete(session: WorkoutSession) = Unit
    override suspend fun deleteById(sessionId: Long) = Unit
    override suspend fun getById(id: Long): WorkoutSession? = null
    override fun getByIdFlow(id: Long): Flow<WorkoutSession?> = flowOf(null)
    override fun getAll(): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override fun getByStatus(status: SessionStatus): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override suspend fun getInProgressSession(): WorkoutSession? = null
    override fun getInProgressSessionFlow(): Flow<WorkoutSession?> = flowOf(null)
    override fun getSessionsForDay(startOfDay: Long, endOfDay: Long): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override fun getSessionsInRange(startDate: Long, endDate: Long): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override fun getCompletedInRange(startDate: Long, endDate: Long): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override fun getRecentCompleted(limit: Int): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override fun getUpcoming(now: Long, limit: Int): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override suspend fun updateStatus(sessionId: Long, status: SessionStatus, timestamp: Long) = Unit
    override suspend fun startSession(sessionId: Long, timestamp: Long) = Unit
    override suspend fun completeSession(sessionId: Long, duration: Int, timestamp: Long) = Unit
    override suspend fun insertSessionExercise(exercise: SessionExercise): Long = exercise.id
    override suspend fun insertSessionExercises(exercises: List<SessionExercise>): List<Long> = exercises.map { it.id }
    override suspend fun updateSessionExercise(exercise: SessionExercise) = Unit
    override suspend fun deleteSessionExercise(exercise: SessionExercise) = Unit
    override fun getExercisesForSession(sessionId: Long): Flow<List<SessionExercise>> = flowOf(emptyList())
    override suspend fun getExercisesForSessionSync(sessionId: Long): List<SessionExercise> = emptyList()
    override suspend fun setExerciseCompleted(id: Long, isCompleted: Boolean) = Unit
    override suspend fun setExerciseSkipped(id: Long, isSkipped: Boolean) = Unit
    override suspend fun insertSetLog(log: SetLog): Long = log.id
    override suspend fun insertSetLogs(logs: List<SetLog>): List<Long> = logs.map { it.id }
    override suspend fun updateSetLog(log: SetLog) = Unit
    override suspend fun deleteSetLog(log: SetLog) = Unit
    override fun getSetLogsForExercise(sessionExerciseId: Long): Flow<List<SetLog>> = flowOf(emptyList())
    override suspend fun getSetLogsForExerciseSync(sessionExerciseId: Long): List<SetLog> = emptyList()
    override fun getCompletedSessionCount(): Flow<Int> = flowOf(0)
    override fun getTotalTrainingMinutes(): Flow<Int?> = flowOf(0)
    override suspend fun getSessionCountInRange(startDate: Long, endDate: Long): Int = 0
}

private class FakeWorkoutPlanTemplateDao : WorkoutPlanTemplateDao {
    override suspend fun insertTemplate(template: WorkoutPlanTemplate): Long = template.id
    override suspend fun updateTemplate(template: WorkoutPlanTemplate) = Unit
    override suspend fun deleteTemplate(template: WorkoutPlanTemplate) = Unit
    override suspend fun deleteTemplateById(templateId: Long) = Unit
    override suspend fun getTemplateById(templateId: Long): WorkoutPlanTemplate? = null
    override fun getTemplateSummaries(): Flow<List<WorkoutPlanTemplateSummary>> = flowOf(emptyList())
    override suspend fun getExercisesForTemplate(templateId: Long): List<WorkoutPlanTemplateExercise> = emptyList()
    override suspend fun insertTemplateExercises(exercises: List<WorkoutPlanTemplateExercise>): List<Long> = exercises.map { it.id }
    override suspend fun clearExercisesForTemplate(templateId: Long) = Unit
}
