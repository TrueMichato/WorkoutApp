package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.UserGoalDao
import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGoalRepositoryTest {

    @Test
    fun categoryWeights_unknownForwardCategoryPreservesKnownWeightsAndReportsIssue() = runTest {
        val repository = UserGoalRepository(
            FakeUserGoalDao(
                UserGoal(categoryWeights = """{"STRENGTH":2.0,"FUTURE_CATEGORY":1.5}""")
            )
        )

        val result = repository.getCategoryWeightsResult()

        assertEquals(mapOf(WorkoutCategory.STRENGTH to 2.0f), result.value)
        assertTrue(result.hasIssues)
        assertTrue(result.issues.single().message.contains("unknown category"))
        assertEquals(mapOf(WorkoutCategory.STRENGTH to 2.0f), repository.getCategoryWeights())
    }

    @Test
    fun categoryWeights_malformedJsonReportsIssueWithoutOverwritingStoredRawValue() = runTest {
        val raw = """{"STRENGTH":"""
        val dao = FakeUserGoalDao(UserGoal(categoryWeights = raw))
        val repository = UserGoalRepository(dao)

        val result = repository.getCategoryWeightsResult()

        assertTrue(result.value.isEmpty())
        assertTrue(result.hasIssues)
        assertEquals(raw, dao.goal.categoryWeights)
        assertTrue(repository.getCategoryWeights().isEmpty())
    }
}

private class FakeUserGoalDao(
    var goal: UserGoal
) : UserGoalDao {
    override suspend fun insert(goal: UserGoal) {
        this.goal = goal
    }

    override suspend fun update(goal: UserGoal) {
        this.goal = goal
    }

    override suspend fun get(): UserGoal = goal

    override fun getFlow(): Flow<UserGoal?> = flowOf(goal)

    override suspend fun updatePhase(phase: TrainingPhase, startDate: Long, endDate: Long?, timestamp: Long) {
        goal = goal.copy(currentPhase = phase, phaseStartDate = startDate, phaseEndDate = endDate, updatedAt = timestamp)
    }

    override suspend fun updateCategoryWeights(weights: String, timestamp: Long) {
        goal = goal.copy(categoryWeights = weights, updatedAt = timestamp)
    }

    override suspend fun insertCategoryStats(stats: CategoryStats) = Unit

    override suspend fun insertAllCategoryStats(stats: List<CategoryStats>) = Unit

    override suspend fun updateCategoryStats(stats: CategoryStats) = Unit

    override suspend fun getCategoryStats(category: WorkoutCategory): CategoryStats? = null

    override fun getAllCategoryStats(): Flow<List<CategoryStats>> = flowOf(emptyList())

    override fun getCategoryStatsByNeglect(): Flow<List<CategoryStats>> = flowOf(emptyList())

    override suspend fun recordCategoryTraining(category: WorkoutCategory, timestamp: Long) = Unit

    override suspend fun addCategoryMinutes(category: WorkoutCategory, minutes: Int, timestamp: Long) = Unit

    override suspend fun recalculateDaysSinceLastTrained(now: Long) = Unit
}
