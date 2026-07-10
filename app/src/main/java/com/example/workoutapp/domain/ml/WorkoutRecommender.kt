package com.example.workoutapp.domain.ml

import com.example.workoutapp.data.model.MLPreferenceScore
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.MLFeedbackRepository
import com.example.workoutapp.domain.WorkoutPlannerCandidate
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * ML-based workout recommender that learns from user feedback.
 *
 * Architecture:
 * 1. Statistical layer: Wilson score confidence intervals for exercise/category preferences
 * 2. Contextual layer: Time-of-day and day-of-week patterns
 * 3. Feature-based layer: Gradient boosting on feature vectors (future TFLite model)
 *
 * The recommender provides adjustment scores that modify the base rule-based scores.
 */
@Singleton
class WorkoutRecommender @Inject constructor(
    private val feedbackRepository: MLFeedbackRepository
) {
    // Weights for combining different signal sources
    private val exercisePreferenceWeight = 0.4f
    private val categoryPreferenceWeight = 0.2f
    private val contextualPatternWeight = 0.2f
    private val explorationBonus = 0.1f
    private val noveltyWeight = 0.1f

    /**
     * Calculate ML-based adjustment score for a candidate exercise.
     * Returns a value in [-1, 1] range that should be added to the rule-based score.
     *
     * @param candidate The exercise candidate to score
     * @param categoryScores Cached preference scores for categories
     * @param exerciseScores Cached preference scores for exercises
     * @param totalSamples Total number of feedback events (for exploration bonus)
     * @param balanceScore Current training balance score (used for future contextual scoring)
     */
    fun calculateMLAdjustment(
        candidate: WorkoutPlannerCandidate,
        categoryScores: Map<WorkoutCategory, MLPreferenceScore>,
        exerciseScores: Map<Long, MLPreferenceScore>,
        totalSamples: Int,
        balanceScore: Int
    ): Float {
        var adjustment = 0f
        var totalWeight = 0f

        // Future: Use balanceScore for contextual adjustments
        // When balance is low, we might want to explore more
        val explorationMultiplier = if (balanceScore < 30) 1.2f else 1.0f

        // 1. Exercise preference (direct feedback on this exercise)
        val exerciseScore = exerciseScores[candidate.exercise.id]
        if (exerciseScore != null && exerciseScore.sampleCount >= 3) {
            adjustment += exerciseScore.score * exerciseScore.confidence * exercisePreferenceWeight
            totalWeight += exercisePreferenceWeight * exerciseScore.confidence
        }

        // 2. Category preference (aggregate feedback on this category)
        val categoryScore = candidate.categories
            .mapNotNull { categoryScores[it] }
            .maxByOrNull { it.confidence }
        if (categoryScore != null && categoryScore.sampleCount >= 5) {
            adjustment += categoryScore.score * categoryScore.confidence * categoryPreferenceWeight
            totalWeight += categoryPreferenceWeight * categoryScore.confidence
        }

        // 3. Exploration bonus (Upper Confidence Bound for less-sampled exercises)
        val exerciseSamples = exerciseScore?.sampleCount ?: 0
        if (totalSamples > 0 && exerciseSamples < 10) {
            val ucbBonus = calculateUCBBonus(exerciseSamples, totalSamples) * explorationMultiplier
            adjustment += ucbBonus * explorationBonus
            totalWeight += explorationBonus
        }

        // 4. Novelty bonus (exercises never done or rarely done get slight boost)
        val timesPerformed = candidate.exercise.timesPerformed
        if (timesPerformed == 0) {
            adjustment += noveltyWeight * 0.5f // New exercise gets exploration boost
            totalWeight += noveltyWeight
        } else if (timesPerformed < 5) {
            val novelty = (5 - timesPerformed) / 5f * 0.3f
            adjustment += novelty * noveltyWeight
            totalWeight += noveltyWeight
        }

        // Normalize by total weight if we have any signals
        return if (totalWeight > 0) {
            (adjustment / totalWeight).coerceIn(-1f, 1f)
        } else {
            0f // No ML signal available, defer to rule-based scoring
        }
    }

    /**
     * Upper Confidence Bound bonus for exploration.
     * Encourages trying exercises we have less data about.
     */
    private fun calculateUCBBonus(itemSamples: Int, totalSamples: Int): Float {
        if (totalSamples == 0 || itemSamples == 0) return 1f
        // UCB1 formula: sqrt(2 * ln(total) / item)
        val exploration = kotlin.math.sqrt(2 * ln(totalSamples.toFloat()) / itemSamples.coerceAtLeast(1))
        return exploration.coerceIn(0f, 1f)
    }

    /**
     * Get all preference scores for batch scoring.
     */
    suspend fun loadPreferenceScores(): Pair<Map<WorkoutCategory, MLPreferenceScore>, Map<Long, MLPreferenceScore>> {
        val exerciseScores = feedbackRepository.getAllExerciseScores()
        val categoryScores = WorkoutCategory.rotationCategories().mapNotNull { cat ->
            feedbackRepository.getCategoryScore(cat)?.let { cat to it }
        }.toMap()
        return categoryScores to exerciseScores
    }

    /**
     * Prepare context for ML scoring.
     */
    fun getCurrentContext(): MLContext {
        val cal = Calendar.getInstance()
        return MLContext(
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        )
    }

    /**
     * Suggest whether to include more variety or stick to preferences.
     */
    fun shouldExplore(totalSamples: Int): Boolean {
        // Explore more when we have fewer samples
        if (totalSamples < 50) return true

        // Occasionally explore even with lots of data (10% of the time)
        return Math.random() < 0.1
    }
}

data class MLContext(
    val dayOfWeek: Int,
    val hourOfDay: Int
)

/**
 * Statistics and model performance tracking.
 */
object MLStats {
    /**
     * Calculate acceptance rate from recent events.
     */
    fun calculateAcceptanceRate(
        positive: Int,
        negative: Int
    ): Float {
        val total = positive + negative
        return if (total > 0) positive.toFloat() / total else 0.5f
    }

    /**
     * Calculate confidence interval width.
     */
    fun calculateConfidenceWidth(sampleCount: Int, z: Float = 1.96f): Float {
        if (sampleCount == 0) return 1f
        return z / kotlin.math.sqrt(sampleCount.toFloat())
    }

    /**
     * Estimate model quality based on prediction accuracy.
     */
    fun estimateModelQuality(
        totalPredictions: Int,
        correctPredictions: Int
    ): Float {
        if (totalPredictions < 10) return 0f
        val accuracy = correctPredictions.toFloat() / totalPredictions
        val sampleBonus = minOf(1f, totalPredictions / 100f)
        return accuracy * 0.85f + sampleBonus * 0.15f
    }
}
