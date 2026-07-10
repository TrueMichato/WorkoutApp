package com.example.workoutapp.domain

import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.SessionStatus
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutSession
import org.junit.Assert.*
import org.junit.Test

class DashboardAnalyticsTest {

    // ── Balance score ────────────────────────────────────────────────

    @Test
    fun balanceScore_allCategoriesRecentlyTrained_returnsHighScore() {
        val stats = WorkoutCategory.rotationCategories().map {
            CategoryStats(category = it, daysSinceLastTrained = 1)
        }
        val score = DashboardAnalytics.balanceScore(stats)
        assertTrue("Score should be >= 80 when all trained recently, was $score", score >= 80)
    }

    @Test
    fun balanceScore_allCategoriesNeverTrained_returnsZero() {
        val stats = WorkoutCategory.rotationCategories().map {
            CategoryStats(category = it, daysSinceLastTrained = Int.MAX_VALUE)
        }
        val score = DashboardAnalytics.balanceScore(stats)
        assertEquals("Score should be 0 when nothing trained", 0, score)
    }

    @Test
    fun balanceScore_emptyStats_treatsAsMaxNeglect() {
        val score = DashboardAnalytics.balanceScore(emptyList())
        assertEquals(0, score)
    }

    @Test
    fun balanceScore_mixedRecency_returnsMidRange() {
        val categories = WorkoutCategory.rotationCategories()
        val stats = categories.mapIndexed { i, cat ->
            CategoryStats(category = cat, daysSinceLastTrained = if (i % 2 == 0) 2 else 100)
        }
        val score = DashboardAnalytics.balanceScore(stats)
        assertTrue("Mixed score should be between 20 and 80, was $score", score in 20..80)
    }

    // ── Category balances ────────────────────────────────────────────

    @Test
    fun categoryBalances_sortsByBalancePctAscending() {
        val stats = listOf(
            CategoryStats(category = WorkoutCategory.STRENGTH, avgSessionsPerWeek = 3f, daysSinceLastTrained = 1),
            CategoryStats(category = WorkoutCategory.FLEXIBILITY, avgSessionsPerWeek = 0f, daysSinceLastTrained = 30)
        )
        val balances = DashboardAnalytics.categoryBalances(stats)
        // The one with 0 sessions (lowest pct) should come first
        val flexIdx = balances.indexOfFirst { it.category == WorkoutCategory.FLEXIBILITY }
        val strIdx = balances.indexOfFirst { it.category == WorkoutCategory.STRENGTH }
        assertTrue("Flexibility should appear before Strength (more neglected)", flexIdx < strIdx)
    }

    @Test
    fun categoryBalances_allZeroSessions_allPctsAreZero() {
        val stats = WorkoutCategory.rotationCategories().map {
            CategoryStats(category = it, avgSessionsPerWeek = 0f, daysSinceLastTrained = 99)
        }
        val balances = DashboardAnalytics.categoryBalances(stats)
        assertTrue(balances.all { it.balancePct == 0f })
    }

    // ── Neglected exercises ──────────────────────────────────────────

    @Test
    fun neglectedExercises_filtersOnlyOlderThanThreshold() {
        val now = System.currentTimeMillis()
        val exercises = listOf(
            DashboardAnalytics.ExerciseSnapshot(1, "Squat", 10, now - 5 * 86400_000L),   // 5 days ago
            DashboardAnalytics.ExerciseSnapshot(2, "Deadlift", 8, now - 20 * 86400_000L), // 20 days ago
            DashboardAnalytics.ExerciseSnapshot(3, "Press", 3, now - 15 * 86400_000L)     // 15 days ago
        )
        val alerts = DashboardAnalytics.neglectedExercises(exercises, thresholdDays = 14)
        assertEquals("Should find 2 neglected exercises", 2, alerts.size)
        assertEquals("Deadlift", alerts[0].exerciseName) // Most neglected first
        assertEquals("Press", alerts[1].exerciseName)
    }

    @Test
    fun neglectedExercises_ignoresNeverPerformed() {
        val exercises = listOf(
            DashboardAnalytics.ExerciseSnapshot(1, "New Exercise", 0, null)
        )
        val alerts = DashboardAnalytics.neglectedExercises(exercises)
        assertTrue("Never-performed exercises should not generate alerts", alerts.isEmpty())
    }

    // ── Weekly trend ─────────────────────────────────────────────────

    @Test
    fun weeklyTrend_producesCorrectNumberOfWeeks() {
        val trend = DashboardAnalytics.weeklyTrend(emptyList(), weeks = 4)
        assertEquals(4, trend.size)
    }

    @Test
    fun weeklyTrend_countsSessionsInCorrectWeek() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            WorkoutSession(id = 1, name = "W1", status = SessionStatus.COMPLETED, completedAt = now - 2 * 86400_000L, actualDurationMinutes = 30),
            WorkoutSession(id = 2, name = "W2", status = SessionStatus.COMPLETED, completedAt = now - 9 * 86400_000L, actualDurationMinutes = 45)
        )
        val trend = DashboardAnalytics.weeklyTrend(sessions, weeks = 4)
        // The most recent week (index 3) should have 1 session
        val latestWeek = trend.last()
        assertEquals("Latest week should have 1 session", 1, latestWeek.sessionCount)
        assertEquals(30, latestWeek.totalMinutes)
    }

    // ── This-week summary ────────────────────────────────────────────

    @Test
    fun thisWeekSummary_countsCorrectly() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            WorkoutSession(id = 1, status = SessionStatus.COMPLETED, completedAt = now, actualDurationMinutes = 45),
            WorkoutSession(id = 2, status = SessionStatus.COMPLETED, completedAt = now, actualDurationMinutes = 30)
        )
        val trained = setOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY)
        val summary = DashboardAnalytics.thisWeekSummary(sessions, trained)
        assertEquals(2, summary.sessionsCompleted)
        assertEquals(75, summary.totalMinutes)
        assertEquals(2, summary.categoriesTrained.size)
        assertTrue(summary.categoriesMissed.isNotEmpty())
    }

    // ── Next recommendation ────────────────────────────────────────────

    @Test
    fun nextRecommendation_pendingPt_prioritizesPt() {
        val recommendation = DashboardAnalytics.nextRecommendation(
            pendingPTRoutineCount = 2,
            balanceScore = 90,
            categoryBalances = emptyList(),
            neglectedExercises = emptyList()
        )

        assertEquals(DashboardAnalytics.RecommendationAction.OPEN_PT, recommendation.action)
        assertTrue(recommendation.message.contains("physical therapy", ignoreCase = true))
    }

    @Test
    fun nextRecommendation_lowBalance_pointsToGenerator() {
        val balances = listOf(
            DashboardAnalytics.CategoryBalance(
                category = WorkoutCategory.MOBILITY,
                actualSessions = 0,
                targetSessionsPerWeek = 1.2f,
                daysSinceLast = 9,
                balancePct = 0.1f
            )
        )
        val recommendation = DashboardAnalytics.nextRecommendation(
            pendingPTRoutineCount = 0,
            balanceScore = 40,
            categoryBalances = balances,
            neglectedExercises = emptyList()
        )

        assertEquals(DashboardAnalytics.RecommendationAction.GENERATE_WORKOUT, recommendation.action)
        assertTrue(recommendation.message.contains("Mobility", ignoreCase = true))
    }
}
