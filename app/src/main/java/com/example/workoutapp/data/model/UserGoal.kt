package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User's training goals and preferences for goal-adaptive scheduling
 */
@Entity(tableName = "user_goals")
data class UserGoal(
    @PrimaryKey
    val id: Long = 1, // Single row, singleton pattern

    // Current focus phase
    val currentPhase: TrainingPhase = TrainingPhase.BALANCED,
    val phaseStartDate: Long = System.currentTimeMillis(),
    val phaseEndDate: Long? = null, // Null = indefinite

    // Category weights (JSON map of category -> weight multiplier)
    // Weight of 1.0 = normal, 2.0 = double priority, 0.5 = half priority
    val categoryWeights: String = "{}",

    // Minimum rotation settings
    val minDaysBetweenSameCategory: Int = 1, // Prevents overtraining single category
    val maxDaysWithoutCategory: Int = 14, // Forces rotation even for low-priority categories
    val ensureWeeklyVariety: Boolean = true, // Guarantees each category touched at least once per week

    // Session preferences
    val preferredSessionDurationMinutes: Int = 60,
    val maxSessionsPerDay: Int = 2,
    val preferredRestDays: String = "[]", // JSON array of day-of-week (0=Sunday)

    // Progression settings
    val autoProgressionEnabled: Boolean = true,
    val progressionThreshold: Int = 3, // Successful sessions before suggesting progression

    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Training phase presets with default category weights
 */
enum class TrainingPhase(
    val displayName: String,
    val description: String,
    val defaultWeights: Map<WorkoutCategory, Float>
) {
    BALANCED(
        displayName = "Balanced",
        description = "Equal focus across all training types",
        defaultWeights = WorkoutCategory.entries.associateWith { 1.0f }
    ),
    STRENGTH_FOCUS(
        displayName = "Strength Focus",
        description = "Emphasis on maximal strength development",
        defaultWeights = mapOf(
            WorkoutCategory.STRENGTH to 2.0f,
            WorkoutCategory.HYPERTROPHY to 1.2f,
            WorkoutCategory.FUNCTIONAL to 1.0f,
            WorkoutCategory.MOBILITY to 0.8f
        ).withDefaults()
    ),
    HYPERTROPHY_FOCUS(
        displayName = "Hypertrophy Focus",
        description = "Emphasis on muscle building",
        defaultWeights = mapOf(
            WorkoutCategory.HYPERTROPHY to 2.0f,
            WorkoutCategory.STRENGTH to 1.0f,
            WorkoutCategory.FUNCTIONAL to 0.8f
        ).withDefaults()
    ),
    ENDURANCE_FOCUS(
        displayName = "Endurance Focus",
        description = "Building cardiovascular and muscular endurance",
        defaultWeights = mapOf(
            WorkoutCategory.ENDURANCE to 2.0f,
            WorkoutCategory.FUNCTIONAL to 1.2f,
            WorkoutCategory.MOBILITY to 1.0f
        ).withDefaults()
    ),
    SKILL_ACQUISITION(
        displayName = "Skill Acquisition",
        description = "Learning new movements and techniques",
        defaultWeights = mapOf(
            WorkoutCategory.SKILLS to 2.0f,
            WorkoutCategory.DEXTERITY to 1.5f,
            WorkoutCategory.MOBILITY to 1.2f,
            WorkoutCategory.STRENGTH to 0.8f
        ).withDefaults()
    ),
    RECOVERY(
        displayName = "Recovery Phase",
        description = "Light activity focusing on mobility and correctives",
        defaultWeights = mapOf(
            WorkoutCategory.MOBILITY to 2.0f,
            WorkoutCategory.FLEXIBILITY to 2.0f,
            WorkoutCategory.CORRECTIVES to 1.5f,
            WorkoutCategory.STRENGTH to 0.3f,
            WorkoutCategory.HYPERTROPHY to 0.3f,
            WorkoutCategory.ENDURANCE to 0.5f
        ).withDefaults()
    ),
    MARTIAL_ARTS_FOCUS(
        displayName = "Martial Arts Focus",
        description = "Combat sports and fight preparation",
        defaultWeights = mapOf(
            WorkoutCategory.MARTIAL_ARTS to 2.0f,
            WorkoutCategory.ENDURANCE to 1.5f,
            WorkoutCategory.DEXTERITY to 1.3f,
            WorkoutCategory.FUNCTIONAL to 1.2f,
            WorkoutCategory.FLEXIBILITY to 1.0f
        ).withDefaults()
    ),
    MOBILITY_REHAB(
        displayName = "Mobility & Rehab",
        description = "Focus on movement quality and injury prevention",
        defaultWeights = mapOf(
            WorkoutCategory.MOBILITY to 2.0f,
            WorkoutCategory.CORRECTIVES to 2.0f,
            WorkoutCategory.FLEXIBILITY to 1.5f,
            WorkoutCategory.FUNCTIONAL to 1.0f,
            WorkoutCategory.STRENGTH to 0.5f
        ).withDefaults()
    )
}

/**
 * Extension to fill in default weights for categories not explicitly set
 */
private fun Map<WorkoutCategory, Float>.withDefaults(): Map<WorkoutCategory, Float> {
    val result = WorkoutCategory.entries.associateWith { it.defaultWeight }.toMutableMap()
    this.forEach { (category, weight) -> result[category] = weight }
    return result
}

/**
 * Historical training statistics for a category
 */
@Entity(tableName = "category_stats")
data class CategoryStats(
    @PrimaryKey
    val category: WorkoutCategory,

    val totalSessions: Int = 0,
    val totalExercises: Int = 0,
    val totalMinutes: Int = 0,

    val lastTrainedAt: Long? = null,
    val daysSinceLastTrained: Int = Int.MAX_VALUE,

    // Rolling averages
    val avgSessionsPerWeek: Float = 0f,
    val avgMinutesPerSession: Float = 0f,

    // Streak tracking
    val currentStreak: Int = 0, // Consecutive weeks with at least one session
    val longestStreak: Int = 0,

    val updatedAt: Long = System.currentTimeMillis()
)

