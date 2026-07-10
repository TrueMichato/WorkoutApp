package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records user feedback on workout suggestions for ML learning.
 * Each event captures whether the user accepted/rejected a suggestion
 * and contextual features at the time of the suggestion.
 */
@Entity(tableName = "ml_feedback_events")
data class MLFeedbackEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // What was suggested
    val exerciseId: Long,
    val suggestedCategory: WorkoutCategory,
    val suggestedInSessionId: Long,

    // User action
    val action: FeedbackAction,

    // Context at time of suggestion
    val dayOfWeek: Int,                    // 1=Monday, 7=Sunday
    val hourOfDay: Int,                    // 0-23
    val timeSlot: TimeSlot,
    val daysSinceExercisePerformed: Int,   // How long since this exercise was done
    val daysSinceCategoryTrained: Int,     // How long since this category was done
    val exerciseTimesPerformed: Int,       // Total times user has done this exercise
    val difficultyLevel: Int,              // 1-5 difficulty of the exercise
    val sessionDurationMinutes: Int,       // Target duration of the session
    val currentBalanceScore: Int,          // 0-100 balance score at time of suggestion

    // Post-workout feedback (filled in after session completion)
    val perceivedDifficulty: Int? = null,  // 1-10 from session completion
    val satisfactionRating: Int? = null,   // 1-5 from session completion

    // Timestamps
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * User's action on a suggestion
 */
enum class FeedbackAction {
    ACCEPTED,           // User kept the exercise in the workout
    REJECTED,           // User removed the exercise from the suggestion
    COMPLETED,          // User completed the exercise in the workout
    SKIPPED,            // User skipped the exercise during the workout
    NOT_COMPLETED,      // User finished the workout without completing this exercise
    SWAPPED             // User replaced with a different exercise
}

/**
 * Aggregated preference score for an exercise or category.
 * Used to cache ML-derived scores for quick access.
 */
@Entity(tableName = "ml_preference_scores")
data class MLPreferenceScore(
    @PrimaryKey
    val key: String,                       // "exercise:123" or "category:STRENGTH"

    val score: Float,                      // -1.0 to 1.0 preference score
    val confidence: Float,                 // 0.0 to 1.0 confidence in the score
    val sampleCount: Int,                  // Number of feedback events used

    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Feature vector for ML model input.
 * All features normalized to 0-1 range.
 */
data class MLFeatureVector(
    val dayOfWeekSin: Float,              // sin(2π * dayOfWeek / 7)
    val dayOfWeekCos: Float,              // cos(2π * dayOfWeek / 7)
    val hourOfDaySin: Float,              // sin(2π * hour / 24)
    val hourOfDayCos: Float,              // cos(2π * hour / 24)
    val daysSinceExercise: Float,         // normalized 0-1
    val daysSinceCategory: Float,         // normalized 0-1
    val exerciseFrequency: Float,         // normalized 0-1
    val difficultyLevel: Float,           // normalized 0-1
    val sessionDuration: Float,           // normalized 0-1
    val balanceScore: Float               // normalized 0-1
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        dayOfWeekSin, dayOfWeekCos,
        hourOfDaySin, hourOfDayCos,
        daysSinceExercise, daysSinceCategory,
        exerciseFrequency, difficultyLevel,
        sessionDuration, balanceScore
    )

    companion object {
        const val FEATURE_COUNT = 10
    }
}
