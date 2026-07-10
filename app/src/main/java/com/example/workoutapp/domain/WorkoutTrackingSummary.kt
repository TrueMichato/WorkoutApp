package com.example.workoutapp.domain

import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.SessionExercise
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.WorkoutSession
import kotlin.math.roundToInt

data class ExercisePerformanceSummary(
    val sessionExercise: SessionExercise,
    val exercise: Exercise?,
    val setLogs: List<SetLog>,
    val totalReps: Int,
    val totalVolume: Float,
    val totalDurationSeconds: Int
)

data class SessionHistorySummary(
    val session: WorkoutSession,
    val exercises: List<ExercisePerformanceSummary>,
    val totalLoggedSets: Int,
    val completedExerciseCount: Int,
    val skippedExerciseCount: Int,
    val totalVolume: Float,
    val totalDurationSeconds: Int
)

object WorkoutTrackingSummary {

    fun summarizeExercise(
        sessionExercise: SessionExercise,
        exercise: Exercise?,
        setLogs: List<SetLog>
    ): ExercisePerformanceSummary {
        val sorted = setLogs.sortedBy { it.setNumber }
        return ExercisePerformanceSummary(
            sessionExercise = sessionExercise,
            exercise = exercise,
            setLogs = sorted,
            totalReps = sorted.sumOf { it.reps ?: 0 },
            totalVolume = sorted.sumOf { ((it.reps ?: 0) * (it.weight ?: 0f)).toDouble() }.toFloat(),
            totalDurationSeconds = sorted.sumOf { it.durationSeconds ?: 0 }
        )
    }

    fun summarizeSession(
        session: WorkoutSession,
        sessionExercises: List<SessionExercise>,
        exerciseById: Map<Long, Exercise>,
        setLogsByExerciseId: Map<Long, List<SetLog>>
    ): SessionHistorySummary {
        val exerciseSummaries = sessionExercises.map { se ->
            summarizeExercise(
                sessionExercise = se,
                exercise = exerciseById[se.exerciseId],
                setLogs = setLogsByExerciseId[se.id].orEmpty()
            )
        }
        return SessionHistorySummary(
            session = session,
            exercises = exerciseSummaries,
            totalLoggedSets = exerciseSummaries.sumOf { it.setLogs.size },
            completedExerciseCount = exerciseSummaries.count { it.sessionExercise.isCompleted && !it.sessionExercise.isSkipped },
            skippedExerciseCount = exerciseSummaries.count { it.sessionExercise.isSkipped },
            totalVolume = exerciseSummaries.sumOf { it.totalVolume.toDouble() }.toFloat(),
            totalDurationSeconds = exerciseSummaries.sumOf { it.totalDurationSeconds }
        )
    }

    fun formatVolume(volume: Float): String {
        if (volume <= 0f) return "—"
        return if (volume >= 1000f) {
            "${(volume / 1000f * 10).roundToInt() / 10f}k kg"
        } else {
            "${volume.roundToInt()} kg"
        }
    }
}
