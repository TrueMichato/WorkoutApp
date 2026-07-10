package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.UserGoalDao
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserGoalRepository @Inject constructor(
    private val userGoalDao: UserGoalDao
) {
    private val json = Json { encodeDefaults = true }

    suspend fun getUserGoal(): UserGoal = userGoalDao.get() ?: UserGoal()

    fun getUserGoalFlow(): Flow<UserGoal?> = userGoalDao.getFlow()

    suspend fun updateUserGoal(goal: UserGoal) = userGoalDao.update(goal)

    suspend fun setTrainingPhase(phase: TrainingPhase, endDate: Long? = null) {
        userGoalDao.updatePhase(phase, System.currentTimeMillis(), endDate)
    }

    suspend fun setCategoryWeights(weights: Map<WorkoutCategory, Float>) {
        val weightsMap = weights.mapKeys { it.key.name }
        val weightsJson = json.encodeToString(weightsMap)
        userGoalDao.updateCategoryWeights(weightsJson)
    }

    suspend fun getCategoryWeights(): Map<WorkoutCategory, Float> {
        val goal = getUserGoal()
        return try {
            val map = json.decodeFromString<Map<String, Float>>(goal.categoryWeights)
            map.mapKeys { WorkoutCategory.valueOf(it.key) }
        } catch (e: Exception) {
            // Return default weights from current phase
            goal.currentPhase.defaultWeights
        }
    }

    // Category stats
    fun getAllCategoryStats(): Flow<List<CategoryStats>> = userGoalDao.getAllCategoryStats()

    fun getCategoryStatsByNeglect(): Flow<List<CategoryStats>> = userGoalDao.getCategoryStatsByNeglect()

    suspend fun getCategoryStats(category: WorkoutCategory): CategoryStats? =
        userGoalDao.getCategoryStats(category)

    suspend fun recordCategoryTraining(category: WorkoutCategory) {
        check(userGoalDao.recordCategoryTraining(category) == 1) { "Category stats for $category are not initialized." }
    }

    suspend fun addCategoryTrainingMinutes(category: WorkoutCategory, minutes: Int) {
        check(userGoalDao.addCategoryMinutes(category, minutes) == 1) { "Category stats for $category are not initialized." }
    }

    suspend fun recalculateDaysSinceLastTrained() =
        userGoalDao.recalculateDaysSinceLastTrained()

    /**
     * Get categories that haven't been trained recently
     */
    suspend fun getNeglectedCategories(maxDays: Int = 7): List<CategoryStats> {
        val goal = getUserGoal()
        val allStats = mutableListOf<CategoryStats>()

        for (category in WorkoutCategory.rotationCategories()) {
            val stats = userGoalDao.getCategoryStats(category)
            if (stats != null && stats.daysSinceLastTrained > maxDays) {
                allStats.add(stats)
            }
        }

        return allStats.sortedByDescending { it.daysSinceLastTrained }
    }

    /**
     * Calculate priority score for a category based on:
     * - User weight preference
     * - Days since last trained
     * - Minimum rotation requirements
     */
    suspend fun calculateCategoryPriority(category: WorkoutCategory): Float {
        val goal = getUserGoal()
        val stats = userGoalDao.getCategoryStats(category) ?: return 0f
        val weights = getCategoryWeights()

        val userWeight = weights[category] ?: category.defaultWeight
        val daysSince = stats.daysSinceLastTrained

        // Base priority from user weight
        var priority = userWeight

        // Boost priority based on days since last trained
        // More days = higher priority (up to max days threshold)
        val daysFactor = (daysSince.toFloat() / goal.maxDaysWithoutCategory).coerceAtMost(2f)
        priority *= (1f + daysFactor)

        // If past the max days threshold, significantly boost priority
        if (daysSince >= goal.maxDaysWithoutCategory) {
            priority *= 2f
        }

        return priority
    }
}
