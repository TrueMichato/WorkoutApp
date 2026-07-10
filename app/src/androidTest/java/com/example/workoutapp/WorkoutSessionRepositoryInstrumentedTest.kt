package com.example.workoutapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.FeedbackAction
import com.example.workoutapp.data.model.MLFeedbackEvent
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.RichPrescriptionData
import com.example.workoutapp.data.model.SessionStatus
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutPlanTemplate
import com.example.workoutapp.data.model.WorkoutPlanTemplateExercise
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.model.toJson
import com.example.workoutapp.data.model.toRichPrescriptionDataOrNull
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.SessionExerciseConfig
import com.example.workoutapp.data.repository.WorkoutSessionCompletionInput
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutSessionRepositoryInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: WorkoutDatabase

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    @Inject
    lateinit var workoutSessionRepository: WorkoutSessionRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
        }
    }

    @Test
    fun instantiateSessionFromTemplate_preservesRichPrescriptionFields() = runBlocking {
        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = "Template Test Row",
                difficulty = Difficulty.BEGINNER,
                defaultSets = 2,
                defaultReps = "10",
                defaultRestSeconds = 45
            ),
            categories = listOf(WorkoutCategory.MOBILITY),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )
        val richPrescription = RichPrescriptionData(
            rounds = 4,
            durationSeconds = 50,
            tempo = "20X1",
            effortTarget = "Smooth nasal breathing"
        )
        val templateId = workoutSessionRepository.createPlanTemplate(
            template = WorkoutPlanTemplate(
                name = "Recovery Circuit",
                description = "Move and breathe",
                targetDurationMinutes = 25,
                scheduledTimeSlot = TimeSlot.MORNING,
                sourcePhase = TrainingPhase.RECOVERY
            ),
            exercises = listOf(
                WorkoutPlanTemplateExercise(
                    templateId = 0,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    section = PlanExerciseSection.COOLDOWN,
                    plannedSets = 2,
                    plannedReps = "50s",
                    plannedRestSeconds = 20,
                    prescriptionJson = richPrescription.toJson(),
                    coachingNotes = "Stay relaxed"
                )
            )
        )

        val sessionId = workoutSessionRepository.instantiateSessionFromTemplate(templateId)
        val sessionExercises = workoutSessionRepository.getExercisesForSession(sessionId).first()
        val sessionExercise = sessionExercises.single()

        assertEquals(PlanExerciseSection.COOLDOWN, sessionExercise.section)
        assertEquals("50s", sessionExercise.plannedReps)
        assertEquals(20, sessionExercise.plannedRestSeconds)
        assertEquals("Stay relaxed", sessionExercise.notes)
        assertNotNull(sessionExercise.prescriptionJson.toRichPrescriptionDataOrNull())
        assertEquals(richPrescription, sessionExercise.prescriptionJson.toRichPrescriptionDataOrNull())
    }

    @Test
    fun createSessionWithExercises_rollsBackSessionWhenExerciseInsertFails() = runBlocking {
        val result = runCatching {
            workoutSessionRepository.createSessionWithExercises(
                session = WorkoutSession(name = "Invalid session"),
                exerciseConfigs = listOf(SessionExerciseConfig(exerciseId = Long.MAX_VALUE))
            )
        }

        assertTrue(result.isFailure)
        assertTrue(workoutSessionRepository.getAllSessions().first().isEmpty())
    }

    @Test
    fun createExerciseWithRelations_persistsAllRelations() = runBlocking {
        val equipmentId = database.equipmentDao().insert(Equipment(name = "Test bands"))

        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Banded row"),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = listOf(equipmentId),
            primaryMuscles = listOf(MuscleGroup.UPPER_BACK),
            secondaryMuscles = listOf(MuscleGroup.BICEPS)
        )

        assertNotNull(exerciseRepository.getExerciseById(exerciseId))
        assertEquals(listOf(WorkoutCategory.STRENGTH), exerciseRepository.getExerciseCategories(exerciseId))
        assertEquals(listOf(equipmentId), exerciseRepository.getRequiredEquipmentIds(exerciseId))
        assertEquals(listOf(MuscleGroup.UPPER_BACK), exerciseRepository.getPrimaryMuscles(exerciseId))
        assertEquals(listOf(MuscleGroup.BICEPS), exerciseRepository.getSecondaryMuscles(exerciseId))
    }

    @Test
    fun logSet_doesNotCompleteExercise() = runBlocking {
        val exerciseId = createExercise("Set-only squat", WorkoutCategory.STRENGTH)
        val sessionId = workoutSessionRepository.createSessionWithExercises(
            WorkoutSession(name = "Set logging"),
            listOf(SessionExerciseConfig(exerciseId = exerciseId, sets = 3))
        )
        val sessionExercise = workoutSessionRepository.getExercisesForSessionSync(sessionId).single()

        workoutSessionRepository.logSet(
            SetLog(sessionExerciseId = sessionExercise.id, setNumber = 1, reps = 5, weight = 80f)
        )

        val updated = workoutSessionRepository.getExercisesForSessionSync(sessionId).single()
        assertEquals(false, updated.isCompleted)
        assertEquals(false, updated.isSkipped)
        assertEquals(1, workoutSessionRepository.getSetLogsForExerciseSync(sessionExercise.id).size)
    }

    @Test
    fun completeWorkout_partialWorkout_countsOnlyExplicitDoneExercises() = runBlocking {
        seedCategoryStats(WorkoutCategory.STRENGTH, WorkoutCategory.ENDURANCE)
        val completedExerciseId = createExercise("Completed deadlift", WorkoutCategory.STRENGTH)
        val loggedOnlyExerciseId = createExercise("Logged-only row", WorkoutCategory.ENDURANCE)
        val sessionId = workoutSessionRepository.createSessionWithExercises(
            WorkoutSession(name = "Partial session", status = SessionStatus.IN_PROGRESS, startedAt = 1_000L),
            listOf(
                SessionExerciseConfig(exerciseId = completedExerciseId),
                SessionExerciseConfig(exerciseId = loggedOnlyExerciseId)
            )
        )
        val exercises = workoutSessionRepository.getExercisesForSessionSync(sessionId)
        workoutSessionRepository.markExerciseCompleted(exercises[0].id, true)
        workoutSessionRepository.logSet(SetLog(sessionExerciseId = exercises[1].id, setNumber = 1, reps = 8))

        val result = workoutSessionRepository.completeWorkout(sessionId, completionInput(), now = 121_000L)

        assertEquals(SessionStatus.PARTIAL, result.status)
        assertEquals(SessionStatus.PARTIAL, workoutSessionRepository.getSessionById(sessionId)?.status)
        assertEquals(1, database.exerciseDao().getById(completedExerciseId)?.timesPerformed)
        assertEquals(0, database.exerciseDao().getById(loggedOnlyExerciseId)?.timesPerformed)
        assertEquals(1, database.userGoalDao().getCategoryStats(WorkoutCategory.STRENGTH)?.totalSessions)
        assertEquals(0, database.userGoalDao().getCategoryStats(WorkoutCategory.ENDURANCE)?.totalSessions)
    }

    @Test
    fun completeWorkout_explicitCompleteAndSkip_updatesFeedbackTruthfully() = runBlocking {
        seedCategoryStats(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY)
        val completedExerciseId = createExercise("Done press", WorkoutCategory.STRENGTH)
        val skippedExerciseId = createExercise("Skipped stretch", WorkoutCategory.MOBILITY)
        val sessionId = workoutSessionRepository.createSessionWithExercises(
            WorkoutSession(name = "Done and skip", status = SessionStatus.IN_PROGRESS),
            listOf(
                SessionExerciseConfig(exerciseId = completedExerciseId),
                SessionExerciseConfig(exerciseId = skippedExerciseId)
            )
        )
        val exercises = workoutSessionRepository.getExercisesForSessionSync(sessionId)
        insertFeedback(sessionId, completedExerciseId, WorkoutCategory.STRENGTH)
        insertFeedback(sessionId, skippedExerciseId, WorkoutCategory.MOBILITY)
        workoutSessionRepository.markExerciseCompleted(exercises[0].id, true)
        workoutSessionRepository.markExerciseSkipped(exercises[1].id, true)

        val result = workoutSessionRepository.completeWorkout(sessionId, completionInput(), now = 5_000L)

        assertEquals(SessionStatus.PARTIAL, result.status)
        val feedbackByExercise = database.mlFeedbackDao().getEventsForSession(sessionId).associateBy { it.exerciseId }
        assertEquals(FeedbackAction.COMPLETED, feedbackByExercise.getValue(completedExerciseId).action)
        assertEquals(FeedbackAction.SKIPPED, feedbackByExercise.getValue(skippedExerciseId).action)
    }

    @Test
    fun completeWorkout_duplicateCompletionIsIdempotent() = runBlocking {
        seedCategoryStats(WorkoutCategory.STRENGTH)
        val exerciseId = createExercise("Idempotent squat", WorkoutCategory.STRENGTH)
        val sessionId = workoutSessionRepository.createSessionWithExercises(
            WorkoutSession(name = "Idempotent", status = SessionStatus.IN_PROGRESS),
            listOf(SessionExerciseConfig(exerciseId = exerciseId))
        )
        val sessionExercise = workoutSessionRepository.getExercisesForSessionSync(sessionId).single()
        insertFeedback(sessionId, exerciseId, WorkoutCategory.STRENGTH)
        workoutSessionRepository.markExerciseCompleted(sessionExercise.id, true)

        val first = workoutSessionRepository.completeWorkout(sessionId, completionInput(), now = 10_000L)
        val second = workoutSessionRepository.completeWorkout(sessionId, completionInput(), now = 20_000L)

        assertEquals(SessionStatus.COMPLETED, first.status)
        assertEquals(true, second.alreadyFinalized)
        assertEquals(1, database.exerciseDao().getById(exerciseId)?.timesPerformed)
        assertEquals(1, database.userGoalDao().getCategoryStats(WorkoutCategory.STRENGTH)?.totalSessions)
        assertEquals(FeedbackAction.COMPLETED, database.mlFeedbackDao().getEventsForSession(sessionId).single().action)
    }

    @Test
    fun completeWorkout_rollsBackWhenCategoryStatsUpdateFails() = runBlocking {
        val exerciseId = createExercise("Missing stats lunge", WorkoutCategory.STRENGTH)
        val sessionId = workoutSessionRepository.createSessionWithExercises(
            WorkoutSession(name = "Rollback", status = SessionStatus.IN_PROGRESS),
            listOf(SessionExerciseConfig(exerciseId = exerciseId))
        )
        val sessionExercise = workoutSessionRepository.getExercisesForSessionSync(sessionId).single()
        insertFeedback(sessionId, exerciseId, WorkoutCategory.STRENGTH)
        workoutSessionRepository.markExerciseCompleted(sessionExercise.id, true)

        val result = runCatching {
            workoutSessionRepository.completeWorkout(sessionId, completionInput(), now = 30_000L)
        }

        assertTrue(result.isFailure)
        assertEquals(SessionStatus.IN_PROGRESS, workoutSessionRepository.getSessionById(sessionId)?.status)
        assertEquals(0, database.exerciseDao().getById(exerciseId)?.timesPerformed)
        assertEquals(FeedbackAction.ACCEPTED, database.mlFeedbackDao().getEventsForSession(sessionId).single().action)
    }

    private suspend fun createExercise(name: String, category: WorkoutCategory): Long =
        exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = name,
                difficulty = Difficulty.BEGINNER,
                defaultSets = 3,
                defaultReps = "8",
                defaultRestSeconds = 60
            ),
            categories = listOf(category),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )

    private suspend fun seedCategoryStats(vararg categories: WorkoutCategory) {
        database.userGoalDao().insertAllCategoryStats(categories.map { CategoryStats(category = it, daysSinceLastTrained = 10) })
    }

    private suspend fun insertFeedback(sessionId: Long, exerciseId: Long, category: WorkoutCategory) {
        database.mlFeedbackDao().insertEvent(
            MLFeedbackEvent(
                exerciseId = exerciseId,
                suggestedCategory = category,
                suggestedInSessionId = sessionId,
                action = FeedbackAction.ACCEPTED,
                dayOfWeek = 1,
                hourOfDay = 12,
                timeSlot = TimeSlot.ANYTIME,
                daysSinceExercisePerformed = 7,
                daysSinceCategoryTrained = 7,
                exerciseTimesPerformed = 0,
                difficultyLevel = 1,
                sessionDurationMinutes = 30,
                currentBalanceScore = 50
            )
        )
    }

    private fun completionInput() = WorkoutSessionCompletionInput(
        perceivedDifficulty = 6,
        energyLevel = 7,
        satisfactionRating = 4,
        notes = "Done"
    )
}
