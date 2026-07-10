package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.MLFeedbackDao
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class MLFeedbackRepository @Inject constructor(
    private val mlFeedbackDao: MLFeedbackDao
) {
    // ── Event Recording ──────────────────────────────────────────────

    /**
     * Record a feedback event when an exercise is suggested.
     */
    suspend fun recordSuggestion(
        exerciseId: Long,
        category: WorkoutCategory,
        sessionId: Long,
        daysSinceExercise: Int,
        daysSinceCategory: Int,
        exerciseTimesPerformed: Int,
        difficulty: Int,
        sessionDuration: Int,
        balanceScore: Int,
        timeSlot: TimeSlot
    ): Long {
        val cal = Calendar.getInstance()
        val event = MLFeedbackEvent(
            exerciseId = exerciseId,
            suggestedCategory = category,
            suggestedInSessionId = sessionId,
            action = FeedbackAction.ACCEPTED, // Default to accepted, update if rejected
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            timeSlot = timeSlot,
            daysSinceExercisePerformed = daysSinceExercise,
            daysSinceCategoryTrained = daysSinceCategory,
            exerciseTimesPerformed = exerciseTimesPerformed,
            difficultyLevel = difficulty,
            sessionDurationMinutes = sessionDuration,
            currentBalanceScore = balanceScore
        )
        return mlFeedbackDao.insertEvent(event)
    }

    /**
     * Update event when user rejects/swaps a suggestion.
     */
    suspend fun recordRejection(sessionId: Long, exerciseId: Long) {
        mlFeedbackDao.updateEventOutcome(sessionId, exerciseId, FeedbackAction.REJECTED, null, null)
    }

    /**
     * Update event when user swaps an exercise.
     */
    suspend fun recordSwap(sessionId: Long, exerciseId: Long) {
        mlFeedbackDao.updateEventOutcome(sessionId, exerciseId, FeedbackAction.SWAPPED, null, null)
    }

    /**
     * Update events when workout is completed.
     */
    suspend fun recordWorkoutCompletion(
        sessionId: Long,
        completedExerciseIds: Set<Long>,
        skippedExerciseIds: Set<Long>,
        notCompletedExerciseIds: Set<Long> = emptySet(),
        perceivedDifficulty: Int?,
        satisfactionRating: Int?
    ) {
        val events = mlFeedbackDao.getEventsForSession(sessionId)
        events.forEach { event ->
            val newAction = when (event.exerciseId) {
                in completedExerciseIds -> FeedbackAction.COMPLETED
                in skippedExerciseIds -> FeedbackAction.SKIPPED
                in notCompletedExerciseIds -> FeedbackAction.NOT_COMPLETED
                else -> event.action // Keep existing action
            }
            mlFeedbackDao.updateEventOutcome(
                sessionId = sessionId,
                exerciseId = event.exerciseId,
                action = newAction,
                difficulty = perceivedDifficulty,
                rating = satisfactionRating
            )
        }
    }

    // ── Queries ──────────────────────────────────────────────────────

    fun getRecentEvents(limit: Int = 100): Flow<List<MLFeedbackEvent>> =
        mlFeedbackDao.getRecentEvents(limit)

    fun getEventsForExercise(exerciseId: Long): Flow<List<MLFeedbackEvent>> =
        mlFeedbackDao.getEventsForExercise(exerciseId)

    suspend fun getEventsForSession(sessionId: Long): List<MLFeedbackEvent> =
        mlFeedbackDao.getEventsForSession(sessionId)

    suspend fun getTotalEventCount(): Int = mlFeedbackDao.getTotalEventCount()

    // ── Preference Scores ────────────────────────────────────────────

    suspend fun calculateExercisePreference(exerciseId: Long): Float {
        val positive = mlFeedbackDao.getPositiveCountForExercise(exerciseId)
        val negative = mlFeedbackDao.getNegativeCountForExercise(exerciseId)
        val total = positive + negative
        if (total == 0) return 0f

        // Wilson score lower bound for confidence interval
        val p = positive.toFloat() / total
        val z = 1.96f // 95% confidence
        val denominator = 1 + z * z / total
        val center = p + z * z / (2 * total)
        val spread = z * kotlin.math.sqrt((p * (1 - p) + z * z / (4 * total)) / total)

        // Return score in -1 to 1 range
        return ((center - spread) / denominator * 2 - 1).coerceIn(-1f, 1f)
    }

    suspend fun calculateCategoryPreference(category: WorkoutCategory): Float {
        val positive = mlFeedbackDao.getPositiveCountForCategory(category)
        val negative = mlFeedbackDao.getNegativeCountForCategory(category)
        val total = positive + negative
        if (total == 0) return 0f

        val p = positive.toFloat() / total
        val z = 1.96f
        val denominator = 1 + z * z / total
        val center = p + z * z / (2 * total)
        val spread = z * kotlin.math.sqrt((p * (1 - p) + z * z / (4 * total)) / total)

        return ((center - spread) / denominator * 2 - 1).coerceIn(-1f, 1f)
    }

    suspend fun cachePreferenceScores(exerciseIds: List<Long>) {
        val scores = mutableListOf<MLPreferenceScore>()

        // Cache exercise scores
        exerciseIds.forEach { exerciseId ->
            val positive = mlFeedbackDao.getPositiveCountForExercise(exerciseId)
            val negative = mlFeedbackDao.getNegativeCountForExercise(exerciseId)
            val total = positive + negative
            if (total > 0) {
                scores.add(MLPreferenceScore(
                    key = "exercise:$exerciseId",
                    score = calculateExercisePreference(exerciseId),
                    confidence = minOf(1f, total / 20f), // Full confidence after 20 samples
                    sampleCount = total
                ))
            }
        }

        // Cache category scores
        WorkoutCategory.rotationCategories().forEach { category ->
            val positive = mlFeedbackDao.getPositiveCountForCategory(category)
            val negative = mlFeedbackDao.getNegativeCountForCategory(category)
            val total = positive + negative
            if (total > 0) {
                scores.add(MLPreferenceScore(
                    key = "category:${category.name}",
                    score = calculateCategoryPreference(category),
                    confidence = minOf(1f, total / 30f),
                    sampleCount = total
                ))
            }
        }

        mlFeedbackDao.insertPreferenceScores(scores)
    }

    suspend fun getExerciseScore(exerciseId: Long): MLPreferenceScore? =
        mlFeedbackDao.getPreferenceScore("exercise:$exerciseId")

    suspend fun getCategoryScore(category: WorkoutCategory): MLPreferenceScore? =
        mlFeedbackDao.getPreferenceScore("category:${category.name}")

    suspend fun getAllExerciseScores(): Map<Long, MLPreferenceScore> {
        return mlFeedbackDao.getAllExerciseScores()
            .mapNotNull { score ->
                val id = score.key.removePrefix("exercise:").toLongOrNull()
                id?.let { it to score }
            }
            .toMap()
    }

    // ── Feature Extraction ───────────────────────────────────────────

    fun buildFeatureVector(
        dayOfWeek: Int,
        hourOfDay: Int,
        daysSinceExercise: Int,
        daysSinceCategory: Int,
        exerciseTimesPerformed: Int,
        difficultyLevel: Int,
        sessionDurationMinutes: Int,
        balanceScore: Int
    ): MLFeatureVector {
        return MLFeatureVector(
            dayOfWeekSin = sin(2 * PI * dayOfWeek / 7).toFloat(),
            dayOfWeekCos = cos(2 * PI * dayOfWeek / 7).toFloat(),
            hourOfDaySin = sin(2 * PI * hourOfDay / 24).toFloat(),
            hourOfDayCos = cos(2 * PI * hourOfDay / 24).toFloat(),
            daysSinceExercise = (daysSinceExercise.coerceAtMost(30) / 30f),
            daysSinceCategory = (daysSinceCategory.coerceAtMost(14) / 14f),
            exerciseFrequency = (exerciseTimesPerformed.coerceAtMost(100) / 100f),
            difficultyLevel = ((difficultyLevel - 1) / 4f).coerceIn(0f, 1f),
            sessionDuration = (sessionDurationMinutes.coerceIn(15, 120) - 15) / 105f,
            balanceScore = balanceScore / 100f
        )
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    suspend fun cleanupOldEvents(daysToKeep: Int = 90) {
        val threshold = System.currentTimeMillis() - daysToKeep.toLong() * 24 * 60 * 60 * 1000
        mlFeedbackDao.deleteEventsOlderThan(threshold)
    }
}
