package com.example.workoutapp.ui.workout

import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory

/**
 * Concise, honest defaults summary shown at the top of the workout generator screen so a
 * first-time or routine user can hit "Generate" without opening the advanced controls.
 *
 * Pure/no Android or DI dependencies so it can be unit tested directly.
 */
data class GeneratorSetupSummary(
    val detailLine: String,
    val defaultsExplanation: String
)

/**
 * Builds [GeneratorSetupSummary] from the generator's current selections.
 *
 * When [hasSavedProfile] is false (no [com.example.workoutapp.data.model.UserGoal] row has ever
 * been saved), the duration/phase shown are the app's neutral, conservative fallbacks rather than
 * anything personalized - the explanation must say so plainly instead of implying the generator
 * already knows the user's preferences.
 */
fun buildGeneratorSetupSummary(
    hasSavedProfile: Boolean,
    durationMinutes: Int,
    locationName: String?,
    phase: TrainingPhase,
    selectedCategories: List<WorkoutCategory>
): GeneratorSetupSummary {
    val locationPart = locationName?.takeIf { it.isNotBlank() }?.let { " at $it" } ?: ""
    val detailLine = "$durationMinutes min$locationPart \u2022 ${phase.displayName}"

    val focusPart = if (selectedCategories.isEmpty()) {
        "Auto-balanced across your rotation, protecting undertrained categories."
    } else {
        "Focused on ${selectedCategories.joinToString(", ") { it.displayName }}."
    }

    val defaultsExplanation = if (hasSavedProfile) {
        "Based on your saved goal and preferences. $focusPart"
    } else {
        "No saved goal yet, so these are neutral starting defaults, not personalized. $focusPart"
    }

    return GeneratorSetupSummary(detailLine = detailLine, defaultsExplanation = defaultsExplanation)
}
