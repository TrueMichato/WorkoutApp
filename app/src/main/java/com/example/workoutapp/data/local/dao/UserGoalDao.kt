package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: UserGoal)

    @Update
    suspend fun update(goal: UserGoal)

    @Query("SELECT * FROM user_goals WHERE id = 1")
    suspend fun get(): UserGoal?

    @Query("SELECT * FROM user_goals WHERE id = 1")
    fun getFlow(): Flow<UserGoal?>

    @Query("UPDATE user_goals SET currentPhase = :phase, phaseStartDate = :startDate, phaseEndDate = :endDate, updatedAt = :timestamp WHERE id = 1")
    suspend fun updatePhase(phase: TrainingPhase, startDate: Long, endDate: Long?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_goals SET categoryWeights = :weights, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateCategoryWeights(weights: String, timestamp: Long = System.currentTimeMillis())

    // Category stats
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryStats(stats: CategoryStats)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCategoryStats(stats: List<CategoryStats>)

    @Update
    suspend fun updateCategoryStats(stats: CategoryStats)

    @Query("SELECT * FROM category_stats WHERE category = :category")
    suspend fun getCategoryStats(category: WorkoutCategory): CategoryStats?

    @Query("SELECT * FROM category_stats")
    fun getAllCategoryStats(): Flow<List<CategoryStats>>

    @Query("SELECT * FROM category_stats ORDER BY daysSinceLastTrained DESC")
    fun getCategoryStatsByNeglect(): Flow<List<CategoryStats>>

    @Query("UPDATE category_stats SET lastTrainedAt = :timestamp, totalSessions = totalSessions + 1, daysSinceLastTrained = 0, updatedAt = :timestamp WHERE category = :category")
    suspend fun recordCategoryTraining(category: WorkoutCategory, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE category_stats SET totalMinutes = totalMinutes + :minutes, updatedAt = :timestamp WHERE category = :category")
    suspend fun addCategoryMinutes(category: WorkoutCategory, minutes: Int, timestamp: Long = System.currentTimeMillis()): Int

    // Recalculate days since last trained (should be called periodically)
    @Query("""
        UPDATE category_stats 
        SET daysSinceLastTrained = CASE 
            WHEN lastTrainedAt IS NULL THEN 999999
            ELSE CAST((:now - lastTrainedAt) / 86400000 AS INTEGER)
        END,
        updatedAt = :now
    """)
    suspend fun recalculateDaysSinceLastTrained(now: Long = System.currentTimeMillis())
}
