package com.example.workoutapp.domain

import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.toRichPrescriptionDataOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlannerEngineTest {

    private val goal = UserGoal(
        currentPhase = TrainingPhase.BALANCED,
        preferredSessionDurationMinutes = 45,
        progressionThreshold = 3,
        autoProgressionEnabled = true,
        maxDaysWithoutCategory = 7
    )

    @Test
    fun buildDraft_prioritizesSelectedAndNeglectedCategories() {
        val params = WorkoutGenerationParams(
            durationMinutes = 45,
            selectedCategories = listOf(WorkoutCategory.MOBILITY),
            timeSlot = TimeSlot.EVENING
        )
        val candidates = listOf(
            candidate(
                id = 1,
                name = "Hip Flow",
                categories = listOf(WorkoutCategory.MOBILITY),
                timesPerformed = 0
            ),
            candidate(
                id = 2,
                name = "Thoracic Opener",
                categories = listOf(WorkoutCategory.MOBILITY, WorkoutCategory.CORRECTIVES),
                timesPerformed = 1
            ),
            candidate(
                id = 3,
                name = "Bench Press",
                categories = listOf(WorkoutCategory.STRENGTH),
                timesPerformed = 10
            )
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = goal,
            candidates = candidates,
            categoryPriority = mapOf(
                WorkoutCategory.MOBILITY to 4.5f,
                WorkoutCategory.CORRECTIVES to 2f,
                WorkoutCategory.STRENGTH to 0.8f
            ),
            categoryNeglectDays = mapOf(
                WorkoutCategory.MOBILITY to 10,
                WorkoutCategory.CORRECTIVES to 8,
                WorkoutCategory.STRENGTH to 1
            ),
            now = 1_000_000L
        )

        assertEquals(listOf(WorkoutCategory.MOBILITY), draft.focusCategories)
        assertTrue(draft.exercises.isNotEmpty())
        assertTrue(draft.exerciseSummaries.first().categories.contains(WorkoutCategory.MOBILITY))
        assertTrue(draft.reasoning.any { it.contains("undertrained", ignoreCase = true) })
        assertTrue(draft.exerciseSummaries.isNotEmpty())
    }

    @Test
    fun buildDraft_addsProgressionForWellKnownStrengthMovement() {
        val params = WorkoutGenerationParams(
            durationMinutes = 30,
            selectedCategories = listOf(WorkoutCategory.STRENGTH)
        )
        val candidates = listOf(
            candidate(
                id = 10,
                name = "Squat",
                categories = listOf(WorkoutCategory.STRENGTH),
                defaultSets = 3,
                timesPerformed = 5
            )
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = goal,
            candidates = candidates,
            categoryPriority = mapOf(WorkoutCategory.STRENGTH to 4f),
            categoryNeglectDays = mapOf(WorkoutCategory.STRENGTH to 9),
            now = 1_000_000L
        )

        assertEquals(1, draft.exercises.size)
        assertEquals(4, draft.exercises.first().sets)
        assertEquals(TimeSlot.ANYTIME, draft.session.scheduledTimeSlot)
        assertEquals("Squat", draft.exerciseSummaries.first().name)
    }

    @Test
    fun buildDraft_autoMode_pullsInSeverelyNeglectedCategory_forFairness() {
        val params = WorkoutGenerationParams(durationMinutes = 60)
        val candidates = listOf(
            candidate(1, "Bench Press", listOf(WorkoutCategory.STRENGTH), timesPerformed = 2),
            candidate(2, "Intervals", listOf(WorkoutCategory.ENDURANCE), timesPerformed = 2),
            candidate(3, "Hip Flow", listOf(WorkoutCategory.MOBILITY), timesPerformed = 0)
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = goal.copy(ensureWeeklyVariety = true, maxDaysWithoutCategory = 7),
            candidates = candidates,
            categoryPriority = mapOf(
                WorkoutCategory.STRENGTH to 5f,
                WorkoutCategory.ENDURANCE to 4f,
                WorkoutCategory.MOBILITY to 0.5f
            ),
            categoryNeglectDays = mapOf(
                WorkoutCategory.STRENGTH to 1,
                WorkoutCategory.ENDURANCE to 2,
                WorkoutCategory.MOBILITY to 11
            ),
            now = 1_000_000L
        )

        assertTrue(draft.focusCategories.contains(WorkoutCategory.MOBILITY))
        assertTrue(draft.reasoning.any { it.contains("undertrained", ignoreCase = true) })
    }

    @Test
    fun buildDraft_shortSession_limitsFocusCount() {
        val params = WorkoutGenerationParams(durationMinutes = 20)
        val candidates = listOf(
            candidate(1, "Bench Press", listOf(WorkoutCategory.STRENGTH), timesPerformed = 2),
            candidate(2, "Intervals", listOf(WorkoutCategory.ENDURANCE), timesPerformed = 2),
            candidate(3, "Hip Flow", listOf(WorkoutCategory.MOBILITY), timesPerformed = 0)
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = goal.copy(ensureWeeklyVariety = false),
            candidates = candidates,
            categoryPriority = mapOf(
                WorkoutCategory.STRENGTH to 4f,
                WorkoutCategory.ENDURANCE to 3f,
                WorkoutCategory.MOBILITY to 2f
            ),
            categoryNeglectDays = mapOf(
                WorkoutCategory.STRENGTH to 3,
                WorkoutCategory.ENDURANCE to 2,
                WorkoutCategory.MOBILITY to 1
            ),
            now = 1_000_000L
        )

        assertEquals(1, draft.focusCategories.size)
        assertEquals(WorkoutCategory.STRENGTH, draft.focusCategories.first())
    }

    @Test
    fun buildDraft_penalizes_recentlyTrainedCategory_usingSpacingRule() {
        val params = WorkoutGenerationParams(
            durationMinutes = 20,
            selectedCategories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY)
        )
        val spacedGoal = goal.copy(minDaysBetweenSameCategory = 3)
        val candidates = listOf(
            candidate(1, "Bench Press", listOf(WorkoutCategory.STRENGTH), timesPerformed = 0),
            candidate(2, "Hip Flow", listOf(WorkoutCategory.MOBILITY), timesPerformed = 0)
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = spacedGoal,
            candidates = candidates,
            categoryPriority = mapOf(
                WorkoutCategory.STRENGTH to 5f,
                WorkoutCategory.MOBILITY to 3f
            ),
            categoryNeglectDays = mapOf(
                WorkoutCategory.STRENGTH to 0,
                WorkoutCategory.MOBILITY to 5
            ),
            now = 1_000_000L
        )

        assertEquals(2L, draft.exerciseSummaries.first().exerciseId)
    }

    @Test
    fun buildDraft_respectsExcludedExerciseIds_forPreviewReview() {
        val params = WorkoutGenerationParams(
            durationMinutes = 30,
            selectedCategories = listOf(WorkoutCategory.STRENGTH),
            excludedExerciseIds = setOf(1L)
        )
        val candidates = listOf(
            candidate(1, "Bench Press", listOf(WorkoutCategory.STRENGTH), timesPerformed = 0),
            candidate(2, "Incline Dumbbell Press", listOf(WorkoutCategory.STRENGTH), timesPerformed = 0)
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = goal,
            candidates = candidates,
            categoryPriority = mapOf(WorkoutCategory.STRENGTH to 4f),
            categoryNeglectDays = mapOf(WorkoutCategory.STRENGTH to 8),
            now = 1_000_000L
        )

        assertTrue(draft.exerciseSummaries.none { it.exerciseId == 1L })
        assertTrue(draft.exerciseSummaries.any { it.exerciseId == 2L })
    }

    @Test
    fun buildDraft_usesTrainingPhasePreset_whenAvailable() {
        val presetJson = Json.encodeToString(
            mapOf(
                TrainingPhase.HYPERTROPHY_FOCUS.name to ExerciseProgrammingPreset(
                    setsText = "4-5",
                    repsText = "10-15",
                    restSeconds = 60,
                    rounds = 3,
                    durationSeconds = 40,
                    tempo = "31X1",
                    effortTarget = "RPE 8"
                )
            )
        )
        val params = WorkoutGenerationParams(
            durationMinutes = 30,
            selectedCategories = listOf(WorkoutCategory.HYPERTROPHY)
        )
        val candidates = listOf(
            candidate(
                id = 20,
                name = "Push-up",
                categories = listOf(WorkoutCategory.HYPERTROPHY),
                timesPerformed = 0,
                trainingPhasePresets = presetJson
            )
        )

        val draft = WorkoutPlannerEngine.buildDraft(
            params = params,
            goal = goal.copy(currentPhase = TrainingPhase.HYPERTROPHY_FOCUS, autoProgressionEnabled = false),
            candidates = candidates,
            categoryPriority = mapOf(WorkoutCategory.HYPERTROPHY to 4f),
            categoryNeglectDays = mapOf(WorkoutCategory.HYPERTROPHY to 8),
            now = 1_000_000L
        )

        assertEquals(4, draft.exercises.first().sets)
        assertEquals("10-15", draft.exercises.first().reps)
        assertEquals(60, draft.exercises.first().restSeconds)
        assertEquals(3, draft.exercises.first().prescriptionJson.toRichPrescriptionDataOrNull()?.rounds)
        assertEquals(40, draft.exercises.first().prescriptionJson.toRichPrescriptionDataOrNull()?.durationSeconds)
        assertEquals("31X1", draft.exercises.first().prescriptionJson.toRichPrescriptionDataOrNull()?.tempo)
        assertEquals("RPE 8", draft.exercises.first().prescriptionJson.toRichPrescriptionDataOrNull()?.effortTarget)
    }

    private fun candidate(
        id: Long,
        name: String,
        categories: List<WorkoutCategory>,
        defaultSets: Int = 3,
        timesPerformed: Int,
        lastPerformedAt: Long? = null,
        trainingPhasePresets: String = "{}"
    ): WorkoutPlannerCandidate {
        return WorkoutPlannerCandidate(
            exercise = com.example.workoutapp.data.model.Exercise(
                id = id,
                name = name,
                difficulty = Difficulty.INTERMEDIATE,
                defaultSets = defaultSets,
                timesPerformed = timesPerformed,
                lastPerformedAt = lastPerformedAt,
                estimatedDurationSeconds = 150,
                trainingPhasePresets = trainingPhasePresets
            ),
            categories = categories,
            requiredEquipmentIds = emptySet()
        )
    }
}