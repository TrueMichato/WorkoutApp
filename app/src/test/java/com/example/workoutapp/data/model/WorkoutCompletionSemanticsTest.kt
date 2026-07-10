package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCompletionSemanticsTest {

    @Test
    fun exerciseState_oneLoggedSetIsLoggedNotCompleted() {
        val exercise = SessionExercise(
            id = 1,
            sessionId = 10,
            exerciseId = 100,
            orderIndex = 0,
            plannedSets = 3
        )

        val state = WorkoutCompletionSemantics.exerciseState(exercise, loggedSetCount = 1)

        assertEquals(ExerciseCompletionState.LOGGED, state)
        assertFalse(exercise.isCompleted)
    }

    @Test
    fun finalSessionStatus_allExplicitlyDoneIsCompleted() {
        val exercises = listOf(
            sessionExercise(id = 1, completed = true),
            sessionExercise(id = 2, completed = true)
        )

        assertEquals(SessionStatus.COMPLETED, WorkoutCompletionSemantics.finalSessionStatus(exercises))
    }

    @Test
    fun finalSessionStatus_skippedOrUnfinishedIsPartial() {
        val exercises = listOf(
            sessionExercise(id = 1, completed = true),
            sessionExercise(id = 2, skipped = true),
            sessionExercise(id = 3)
        )

        assertEquals(SessionStatus.PARTIAL, WorkoutCompletionSemantics.finalSessionStatus(exercises))
        assertEquals(1, WorkoutCompletionSemantics.completedExercises(exercises).size)
        assertEquals(1, WorkoutCompletionSemantics.skippedExercises(exercises).size)
        assertEquals(1, WorkoutCompletionSemantics.unfinishedExercises(exercises).size)
    }

    @Test
    fun isFinalized_onlyCompletedAndPartialAreCompletionTerminal() {
        assertTrue(WorkoutCompletionSemantics.isFinalized(SessionStatus.COMPLETED))
        assertTrue(WorkoutCompletionSemantics.isFinalized(SessionStatus.PARTIAL))
        assertFalse(WorkoutCompletionSemantics.isFinalized(SessionStatus.IN_PROGRESS))
        assertFalse(WorkoutCompletionSemantics.isFinalized(SessionStatus.PLANNED))
    }

    private fun sessionExercise(
        id: Long,
        completed: Boolean = false,
        skipped: Boolean = false
    ) = SessionExercise(
        id = id,
        sessionId = 10,
        exerciseId = 100 + id,
        orderIndex = id.toInt(),
        isCompleted = completed,
        isSkipped = skipped
    )
}
