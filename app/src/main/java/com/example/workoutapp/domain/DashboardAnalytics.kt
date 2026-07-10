package com.example.workoutapp.domain

import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutSession
import kotlin.math.roundToInt

/**
 * Pure analytics computations for the Dashboard.
 * All functions are stateless — they take data in and return summaries out.
 */
object DashboardAnalytics {

    data class DashboardRecommendation(
        val title: String,
        val message: String,
        val actionLabel: String,
        val action: RecommendationAction
    )

    enum class RecommendationAction {
        OPEN_PT,
        GENERATE_WORKOUT,
        OPEN_EXERCISES,
        OPEN_WORKOUTS
    }

    // ── Balance score ────────────────────────────────────────────────────
    /**
     * Compute a 0-100 balance score.
     * 100 = all rotation categories have been trained recently
     *   (within their ideal window, derived from category weights).
     * 0 = every category is severely neglected.
     *
     * @param stats        per-category stats rows
     * @param weights      user-defined category weights (higher = train more)
     * @param idealDays    maximum "days since last trained" before a category
     *                     scores zero.  Scaled inversely by its weight.
     */
    fun balanceScore(
        stats: List<CategoryStats>,
        weights: Map<WorkoutCategory, Float> = emptyMap(),
        idealDays: Int = 10
    ): Int {
        val categories = WorkoutCategory.rotationCategories()
        if (categories.isEmpty()) return 100

        var weightedSum = 0.0
        var totalWeight = 0.0

        for (cat in categories) {
            val w = weights[cat] ?: cat.defaultWeight
            val daysSince = stats.firstOrNull { it.category == cat }?.daysSinceLastTrained
                ?: Int.MAX_VALUE
            val adjustedIdeal = (idealDays / w.coerceAtLeast(0.1f)).toInt().coerceAtLeast(3)
            val score = ((adjustedIdeal - daysSince).toDouble() / adjustedIdeal).coerceIn(0.0, 1.0)
            weightedSum += score * w
            totalWeight += w
        }

        return if (totalWeight > 0) (weightedSum / totalWeight * 100).roundToInt().coerceIn(0, 100) else 100
    }

    // ── Category balance bars ────────────────────────────────────────────
    /**
     * Compute per-category "balance bar" data.
     * Returns a list sorted by decreasing imbalance (most neglected first).
     */
    data class CategoryBalance(
        val category: WorkoutCategory,
        val actualSessions: Int,
        val targetSessionsPerWeek: Float,
        val daysSinceLast: Int,
        val balancePct: Float  // 0..1, 1 = perfectly on-target or better
    )

    fun categoryBalances(
        stats: List<CategoryStats>,
        weights: Map<WorkoutCategory, Float> = emptyMap(),
        weeklyBudget: Float = 5f  // total workouts per week the user aims for
    ): List<CategoryBalance> {
        val categories = WorkoutCategory.rotationCategories()
        val totalWeight = categories.sumOf { (weights[it] ?: it.defaultWeight).toDouble() }.toFloat()
            .coerceAtLeast(1f)

        return categories.map { cat ->
            val w = weights[cat] ?: cat.defaultWeight
            val target = (w / totalWeight) * weeklyBudget
            val s = stats.firstOrNull { it.category == cat }
            val actual = s?.avgSessionsPerWeek ?: 0f
            val pct = if (target > 0) (actual / target).coerceIn(0f, 1.5f) else 1f
            CategoryBalance(
                category = cat,
                actualSessions = s?.totalSessions ?: 0,
                targetSessionsPerWeek = target,
                daysSinceLast = s?.daysSinceLastTrained ?: Int.MAX_VALUE,
                balancePct = pct
            )
        }.sortedBy { it.balancePct }
    }

    // ── Neglected exercises ──────────────────────────────────────────────
    data class NeglectedExerciseAlert(
        val exerciseName: String,
        val exerciseId: Long,
        val daysSincePerformed: Int,
        val timesPerformed: Int
    )

    fun neglectedExercises(
        exercises: List<ExerciseSnapshot>,
        thresholdDays: Int = 14
    ): List<NeglectedExerciseAlert> {
        val now = System.currentTimeMillis()
        return exercises
            .filter { it.timesPerformed > 0 }  // only alert about exercises the user has done before
            .mapNotNull { ex ->
                val last = ex.lastPerformedAt ?: return@mapNotNull null
                val days = ((now - last) / (24 * 3600_000L)).toInt()
                if (days >= thresholdDays) {
                    NeglectedExerciseAlert(ex.name, ex.id, days, ex.timesPerformed)
                } else null
            }
            .sortedByDescending { it.daysSincePerformed }
    }

    /** Minimal snapshot of Exercise to avoid coupling to Room entity in tests */
    data class ExerciseSnapshot(
        val id: Long,
        val name: String,
        val timesPerformed: Int,
        val lastPerformedAt: Long?
    )

    // ── Weekly volume trend ──────────────────────────────────────────────
    data class WeeklyTrend(
        val weekLabel: String,     // e.g. "Mar 3"
        val sessionCount: Int,
        val totalMinutes: Int
    )

    fun weeklyTrend(
        sessions: List<WorkoutSession>,
        weeks: Int = 8
    ): List<WeeklyTrend> {
        val now = System.currentTimeMillis()
        val msPerWeek = 7 * 24 * 3600_000L

        return (0 until weeks).reversed().map { weeksAgo ->
            val weekEnd = now - weeksAgo * msPerWeek
            val weekStart = weekEnd - msPerWeek
            val inRange = sessions.filter { s ->
                val t = s.completedAt ?: s.startedAt ?: s.plannedDate
                t in weekStart until weekEnd
            }
            // Build a label like "Mar 3"
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = weekStart }
            val month = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
                .format(cal.time)
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            WeeklyTrend(
                weekLabel = "$month $day",
                sessionCount = inRange.size,
                totalMinutes = inRange.sumOf { it.actualDurationMinutes ?: 0 }
            )
        }
    }

    // ── This-week summary ────────────────────────────────────────────────
    data class ThisWeekSummary(
        val sessionsCompleted: Int,
        val totalMinutes: Int,
        val categoriesTrained: Set<WorkoutCategory>,
        val categoriesMissed: Set<WorkoutCategory>
    )

    /**
     * Quick summary of the current week (Mon-Sun).
     */
    fun thisWeekSummary(
        sessions: List<WorkoutSession>,
        trainedCategoriesThisWeek: Set<WorkoutCategory>
    ): ThisWeekSummary {
        val completed = sessions.filter { it.completedAt != null }
        return ThisWeekSummary(
            sessionsCompleted = completed.size,
            totalMinutes = completed.sumOf { it.actualDurationMinutes ?: 0 },
            categoriesTrained = trainedCategoriesThisWeek,
            categoriesMissed = WorkoutCategory.rotationCategories().toSet() - trainedCategoriesThisWeek
        )
    }

    // ── Next recommendation ───────────────────────────────────────────────
    fun nextRecommendation(
        pendingPTRoutineCount: Int,
        balanceScore: Int,
        categoryBalances: List<CategoryBalance>,
        neglectedExercises: List<NeglectedExerciseAlert>
    ): DashboardRecommendation {
        if (pendingPTRoutineCount > 0) {
            return DashboardRecommendation(
                title = "Take care of PT first",
                message = "$pendingPTRoutineCount physical therapy routine${if (pendingPTRoutineCount == 1) " is" else "s are"} still pending today.",
                actionLabel = "Open PT",
                action = RecommendationAction.OPEN_PT
            )
        }

        val weakestCategory = categoryBalances
            .filter { it.daysSinceLast < Int.MAX_VALUE }
            .minByOrNull { it.balancePct }
            ?: categoryBalances.firstOrNull()
        if (balanceScore in 0..64 && weakestCategory != null) {
            return DashboardRecommendation(
                title = "Rebalance your training",
                message = "${weakestCategory.category.displayName} is the most undertrained area right now${if (weakestCategory.daysSinceLast >= 999) " and hasn't been touched yet" else ", last hit ${weakestCategory.daysSinceLast} day(s) ago"}.",
                actionLabel = "Generate Workout",
                action = RecommendationAction.GENERATE_WORKOUT
            )
        }

        val neglected = neglectedExercises.firstOrNull()
        if (neglected != null) {
            return DashboardRecommendation(
                title = "Bring back an old movement",
                message = "${neglected.exerciseName} hasn't shown up in ${neglected.daysSincePerformed} days. Rotating it back in could keep your library fresh.",
                actionLabel = "Browse Exercises",
                action = RecommendationAction.OPEN_EXERCISES
            )
        }

        return DashboardRecommendation(
            title = "You're in a good groove",
            message = if (balanceScore >= 80) {
                "Your recent training looks balanced. Keep momentum with another smart session."
            } else {
                "You’ve got room for another productive session based on your current rotation."
            },
            actionLabel = "Open Workouts",
            action = RecommendationAction.OPEN_WORKOUTS
        )
    }
}
