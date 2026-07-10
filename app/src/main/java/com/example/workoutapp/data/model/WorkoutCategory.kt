package com.example.workoutapp.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Predefined workout categories covering all major training types.
 * Users can also create custom categories.
 */
enum class WorkoutCategory(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val defaultWeight: Float = 1.0f // Default priority weight for goal-adaptive scheduling
) {
    STRENGTH(
        displayName = "Strength",
        description = "Heavy resistance training focused on maximal force production",
        icon = Icons.Default.FitnessCenter,
        defaultWeight = 1.0f
    ),
    HYPERTROPHY(
        displayName = "Hypertrophy",
        description = "Muscle building with moderate weights and higher volume",
        icon = Icons.Default.Man,
        defaultWeight = 1.0f
    ),
    ENDURANCE(
        displayName = "Endurance",
        description = "Cardiovascular and muscular endurance training",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        defaultWeight = 1.0f
    ),
    DEXTERITY(
        displayName = "Dexterity",
        description = "Coordination, agility, and fine motor control",
        icon = Icons.Default.Sports,
        defaultWeight = 0.8f
    ),
    MOBILITY(
        displayName = "Mobility",
        description = "Joint range of motion and movement quality",
        icon = Icons.Default.Accessibility,
        defaultWeight = 0.9f
    ),
    FLEXIBILITY(
        displayName = "Flexibility",
        description = "Stretching and lengthening muscle tissue",
        icon = Icons.Default.SelfImprovement,
        defaultWeight = 0.8f
    ),
    FUNCTIONAL(
        displayName = "Functional",
        description = "Real-world movement patterns and practical strength",
        icon = Icons.Default.Hiking,
        defaultWeight = 1.0f
    ),
    CORRECTIVES(
        displayName = "Correctives",
        description = "Exercises to address imbalances and movement dysfunctions",
        icon = Icons.Default.Balance,
        defaultWeight = 0.9f
    ),
    SKILLS(
        displayName = "Skills",
        description = "Sport-specific or movement skills practice",
        icon = Icons.Default.EmojiEvents,
        defaultWeight = 0.7f
    ),
    MARTIAL_ARTS(
        displayName = "Martial Arts",
        description = "Combat sports and self-defense training",
        icon = Icons.Default.SportsMartialArts,
        defaultWeight = 0.8f
    ),
    ODDBALLS(
        displayName = "Oddballs",
        description = "Unconventional or unique exercises that don't fit other categories",
        icon = Icons.Default.Psychology,
        defaultWeight = 0.5f
    ),
    CUSTOM(
        displayName = "Custom",
        description = "User-defined category",
        icon = Icons.Default.Add,
        defaultWeight = 0.5f
    );

    companion object {
        /**
         * Get categories suitable for regular workout rotation (excludes CUSTOM placeholder)
         */
        fun rotationCategories(): List<WorkoutCategory> = entries.filter { it != CUSTOM }
    }
}
