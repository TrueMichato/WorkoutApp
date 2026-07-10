package com.example.workoutapp.domain

import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.SessionExercise
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTrackingSummaryTest {

    @Test
    fun summarizeExercise_aggregatesRepsVolumeAndDuration() {
        val se = SessionExercise(id = 10, sessionId = 1, exerciseId = 100, orderIndex = 0, isCompleted = true)
        val exercise = Exercise(id = 100, name = "Bench Press")
        val logs = listOf(
            SetLog(id = 1, sessionExerciseId = 10, setNumber = 1, reps = 8, weight = 60f),
            SetLog(id = 2, sessionExerciseId = 10, setNumber = 2, reps = 6, weight = 70f, durationSeconds = 40)
        )

        val result = WorkoutTrackingSummary.summarizeExercise(se, exercise, logs)

        assertEquals(14, result.totalReps)
        // 8*60 + 6*70 = 480 + 420 = 900
        assertEquals(900f, result.totalVolume, 0.01f)
        assertEquals(40, result.totalDurationSeconds)
        assertEquals(2, result.setLogs.size)
        assertEquals("Bench Press", result.exercise?.name)
    }

    @Test
    fun summarizeSession_aggregatesAcrossExercises() {
        val session = WorkoutSession(id = 1, name = "Full Body")
        val se1 = SessionExercise(id = 10, sessionId = 1, exerciseId = 100, orderIndex = 0, isCompleted = true)
        val se2 = SessionExercise(id = 11, sessionId = 1, exerciseId = 101, orderIndex = 1, isSkipped = true)
        val e1 = Exercise(id = 100, name = "Squat")
        val e2 = Exercise(id = 101, name = "Plank")
        val logs = mapOf(
            10L to listOf(
                SetLog(id = 1, sessionExerciseId = 10, setNumber = 1, reps = 5, weight = 100f),
                SetLog(id = 2, sessionExerciseId = 10, setNumber = 2, reps = 5, weight = 100f)
            ),
            11L to emptyList()
        )

        val result = WorkoutTrackingSummary.summarizeSession(
            session, listOf(se1, se2), mapOf(100L to e1, 101L to e2), logs
        )

        assertEquals(2, result.totalLoggedSets)
        assertEquals(1, result.completedExerciseCount)
        assertEquals(1, result.skippedExerciseCount)
        // 5*100 + 5*100 = 1000
        assertEquals(1000f, result.totalVolume, 0.01f)
    }

    @Test
    fun summarizeSession_emptySetLogs_producesZeroVolume() {
        val session = WorkoutSession(id = 2, name = "Rest Day?")
        val se = SessionExercise(id = 20, sessionId = 2, exerciseId = 200, orderIndex = 0)

        val result = WorkoutTrackingSummary.summarizeSession(
            session, listOf(se), emptyMap(), emptyMap()
        )

        assertEquals(0, result.totalLoggedSets)
        assertEquals(0, result.completedExerciseCount)
        assertEquals(0f, result.totalVolume, 0.01f)
    }

    @Test
    fun formatVolume_handlesZeroAndLargeValues() {
        assertEquals("—", WorkoutTrackingSummary.formatVolume(0f))
        assertEquals("—", WorkoutTrackingSummary.formatVolume(-5f))
        assertEquals("500 kg", WorkoutTrackingSummary.formatVolume(500f))
        val large = WorkoutTrackingSummary.formatVolume(2500f)
        assert(large.contains("k kg")) { "Expected large volume to contain 'k kg' but was: $large" }
    }
}

