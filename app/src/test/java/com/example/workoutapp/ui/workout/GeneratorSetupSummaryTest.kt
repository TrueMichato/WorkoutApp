package com.example.workoutapp.ui.workout

import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorSetupSummaryTest {

    @Test
    fun noSavedProfile_explainsDefaultsAreNeutralNotPersonalized() {
        val summary = buildGeneratorSetupSummary(
            hasSavedProfile = false,
            durationMinutes = 60,
            locationName = null,
            phase = TrainingPhase.BALANCED,
            selectedCategories = emptyList()
        )

        assertTrue(summary.defaultsExplanation.contains("No saved goal yet"))
        assertTrue(summary.defaultsExplanation.contains("not personalized"))
        assertEquals("60 min \u2022 Balanced", summary.detailLine)
    }

    @Test
    fun savedProfile_creditsSavedGoalInsteadOfClaimingNewPersonalization() {
        val summary = buildGeneratorSetupSummary(
            hasSavedProfile = true,
            durationMinutes = 45,
            locationName = "Home Gym",
            phase = TrainingPhase.STRENGTH_FOCUS,
            selectedCategories = emptyList()
        )

        assertTrue(summary.defaultsExplanation.startsWith("Based on your saved goal"))
        assertEquals("45 min at Home Gym \u2022 Strength Focus", summary.detailLine)
    }

    @Test
    fun noSelectedCategories_mentionsAutoBalancingAndProtection() {
        val summary = buildGeneratorSetupSummary(
            hasSavedProfile = true,
            durationMinutes = 60,
            locationName = null,
            phase = TrainingPhase.BALANCED,
            selectedCategories = emptyList()
        )

        assertTrue(summary.defaultsExplanation.contains("Auto-balanced across your rotation"))
    }

    @Test
    fun selectedCategories_areListedByDisplayName() {
        val summary = buildGeneratorSetupSummary(
            hasSavedProfile = true,
            durationMinutes = 60,
            locationName = null,
            phase = TrainingPhase.BALANCED,
            selectedCategories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY)
        )

        assertTrue(
            summary.defaultsExplanation.contains(
                "Focused on ${WorkoutCategory.STRENGTH.displayName}, ${WorkoutCategory.MOBILITY.displayName}."
            )
        )
    }

    @Test
    fun blankLocationName_isTreatedAsNoLocation() {
        val summary = buildGeneratorSetupSummary(
            hasSavedProfile = true,
            durationMinutes = 30,
            locationName = "   ",
            phase = TrainingPhase.BALANCED,
            selectedCategories = emptyList()
        )

        assertEquals("30 min \u2022 Balanced", summary.detailLine)
    }
}
