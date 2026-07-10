package com.example.workoutapp.data.model

enum class ExerciseCompletionState {
    NOT_STARTED,
    LOGGED,
    COMPLETED,
    SKIPPED
}

object WorkoutCompletionSemantics {
    fun exerciseState(
        sessionExercise: SessionExercise,
        loggedSetCount: Int
    ): ExerciseCompletionState = when {
        sessionExercise.isSkipped -> ExerciseCompletionState.SKIPPED
        sessionExercise.isCompleted -> ExerciseCompletionState.COMPLETED
        loggedSetCount > 0 -> ExerciseCompletionState.LOGGED
        else -> ExerciseCompletionState.NOT_STARTED
    }

    fun completedExercises(exercises: List<SessionExercise>): List<SessionExercise> =
        exercises.filter { it.isCompleted && !it.isSkipped }

    fun skippedExercises(exercises: List<SessionExercise>): List<SessionExercise> =
        exercises.filter { it.isSkipped }

    fun unfinishedExercises(exercises: List<SessionExercise>): List<SessionExercise> =
        exercises.filterNot { it.isCompleted || it.isSkipped }

    fun finalSessionStatus(exercises: List<SessionExercise>): SessionStatus {
        val completed = completedExercises(exercises)
        return if (exercises.isNotEmpty() && completed.size == exercises.size) {
            SessionStatus.COMPLETED
        } else {
            SessionStatus.PARTIAL
        }
    }

    fun isFinalized(status: SessionStatus): Boolean =
        status == SessionStatus.COMPLETED || status == SessionStatus.PARTIAL
}
