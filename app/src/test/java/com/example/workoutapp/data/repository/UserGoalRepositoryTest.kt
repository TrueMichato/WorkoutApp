package com.example.workoutapp.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.workoutapp.data.local.UserPreferencesDataStore
import com.example.workoutapp.data.local.dao.UserGoalDao
import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * In-memory fake mirroring the subset of [UserGoalDao] behavior needed here. Room DAOs are plain
 * interfaces (Room generates the SQLite-backed implementation at compile time via KSP), so faking
 * the interface directly - rather than standing up a real SQLite/Room instance, which requires
 * Robolectric or a device/emulator - keeps this an integration-level but fully deterministic JVM
 * unit test of the real [UserGoalRepository] wiring.
 */
private class FakeUserGoalDao(seed: UserGoal?) : UserGoalDao {
    val goalFlow = MutableStateFlow(seed)

    /** Convenience accessor for tests that assert on the raw stored row without going through a Flow. */
    val goal: UserGoal? get() = goalFlow.value

    override suspend fun insert(goal: UserGoal) { goalFlow.value = goal }
    override suspend fun update(goal: UserGoal) { goalFlow.value = goal }
    override suspend fun get(): UserGoal? = goalFlow.value
    override fun getFlow(): Flow<UserGoal?> = goalFlow

    override suspend fun updatePhase(phase: TrainingPhase, startDate: Long, endDate: Long?, timestamp: Long) {
        goalFlow.value = goalFlow.value?.copy(
            currentPhase = phase, phaseStartDate = startDate, phaseEndDate = endDate, updatedAt = timestamp
        )
    }

    override suspend fun updateCategoryWeights(weights: String, timestamp: Long) {
        goalFlow.value = goalFlow.value?.copy(categoryWeights = weights, updatedAt = timestamp)
    }

    override suspend fun insertCategoryStats(stats: CategoryStats): Unit =
        throw UnsupportedOperationException("Not needed for this test")
    override suspend fun insertAllCategoryStats(stats: List<CategoryStats>): Unit =
        throw UnsupportedOperationException("Not needed for this test")
    override suspend fun updateCategoryStats(stats: CategoryStats): Unit =
        throw UnsupportedOperationException("Not needed for this test")
    override suspend fun getCategoryStats(category: WorkoutCategory): CategoryStats? = null
    override fun getAllCategoryStats(): Flow<List<CategoryStats>> = MutableStateFlow(emptyList())
    override fun getCategoryStatsByNeglect(): Flow<List<CategoryStats>> = MutableStateFlow(emptyList())
    override suspend fun recordCategoryTraining(category: WorkoutCategory, timestamp: Long): Int = 0
    override suspend fun addCategoryMinutes(category: WorkoutCategory, minutes: Int, timestamp: Long): Int = 0
    override suspend fun recalculateDaysSinceLastTrained(now: Long) { /* no-op: unused here */ }
}

/**
 * Integration-level coverage for [UserGoalRepository]: both the "customized profile" signal
 * (a seeded default [UserGoal] row - the same neutral row [com.example.workoutapp.data.local.WorkoutDatabase]
 * inserts for every fresh install - must NOT be read as the user having customized their profile;
 * only the real Settings save flow via [UserGoalRepository.setTrainingPhase] /
 * [UserGoalRepository.setCategoryWeights] may flip that signal) and resilient category-weights
 * JSON decoding (unknown/malformed persisted data must be reported as an issue rather than silently
 * dropped or crashing, without overwriting the raw stored value).
 */
class UserGoalRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(seed: UserGoal?): Pair<UserGoalRepository, FakeUserGoalDao> {
        val dao = FakeUserGoalDao(seed)
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("prefs_${System.nanoTime()}.preferences_pb") }
        )
        return UserGoalRepository(dao, UserPreferencesDataStore(dataStore)) to dao
    }

    @Test
    fun seededDefaultGoalRow_aloneDoesNotMarkProfileCustomized() = runBlocking {
        // Mirrors WorkoutDatabase's onCreate seeding: a default UserGoal() row exists from the
        // very first launch, with no user action involved.
        val (repository, _) = newRepository(seed = UserGoal())

        assertTrue(
            "A seeded default row should be present, exactly like production's first-launch seed",
            repository.getUserGoalFlow().first() != null
        )
        assertFalse(
            "Row presence alone must never be read as the user having customized their profile - " +
                "this was the exact bug: hasSavedProfile was derived from row existence",
            repository.hasCustomizedProfileFlow().first()
        )
    }

    @Test
    fun noGoalRowAtAll_alsoReportsNeutral() = runBlocking {
        val (repository, _) = newRepository(seed = null)

        assertFalse(repository.hasCustomizedProfileFlow().first())
    }

    @Test
    fun setTrainingPhase_marksProfileCustomized() = runBlocking {
        val (repository, _) = newRepository(seed = UserGoal())
        assertFalse(repository.hasCustomizedProfileFlow().first())

        repository.setTrainingPhase(TrainingPhase.STRENGTH_FOCUS)

        assertTrue(
            "setTrainingPhase is a real user save action and must mark the profile customized",
            repository.hasCustomizedProfileFlow().first()
        )
    }

    @Test
    fun setCategoryWeights_alsoMarksProfileCustomized() = runBlocking {
        val (repository, _) = newRepository(seed = UserGoal())
        assertFalse(repository.hasCustomizedProfileFlow().first())

        repository.setCategoryWeights(mapOf(WorkoutCategory.STRENGTH to 2.0f))

        assertTrue(
            "setCategoryWeights is a real user save action and must mark the profile customized",
            repository.hasCustomizedProfileFlow().first()
        )
    }

    @Test
    fun categoryWeights_unknownForwardCategoryPreservesKnownWeightsAndReportsIssue() = runTest {
        val (repository, _) = newRepository(
            seed = UserGoal(categoryWeights = """{"STRENGTH":2.0,"FUTURE_CATEGORY":1.5}""")
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
        val (repository, dao) = newRepository(seed = UserGoal(categoryWeights = raw))

        val result = repository.getCategoryWeightsResult()

        assertTrue(result.value.isEmpty())
        assertTrue(result.hasIssues)
        assertEquals(raw, dao.goal?.categoryWeights)
        assertTrue(repository.getCategoryWeights().isEmpty())
    }
}
