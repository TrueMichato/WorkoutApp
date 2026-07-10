package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedJsonTest {

    @Test
    fun categoryWeights_recoversKnownValuesAndReportsUnknownEnum() {
        val result = decodeCategoryWeights("""{"STRENGTH":2.0,"FUTURE":1.5}""")

        assertEquals(2.0f, result.value[WorkoutCategory.STRENGTH])
        assertTrue(result.hasIssues)
        assertTrue(result.issues.single().message.contains("unknown category"))
    }

    @Test
    fun trainingPhasePresets_recoversKnownPhasesAndReportsUnknownPhase() {
        val result = decodeTrainingPhasePresets(
            "exercise programming presets",
            """
                {
                  "BALANCED":{"setsText":"3","repsText":"8-12","restSeconds":60},
                  "FUTURE_PHASE":{"setsText":"1","repsText":"1","restSeconds":1}
                }
            """.trimIndent()
        )

        assertEquals("3", result.value[TrainingPhase.BALANCED]?.setsText)
        assertTrue(result.hasIssues)
        assertTrue(result.issues.single().message.contains("unknown training phase"))
    }

    @Test
    fun exerciseResolver_unknownPhaseKeyUsesKnownBalancedPresetWithoutThrowing() {
        val exercise = Exercise(
            name = "Push-up",
            trainingPhasePresets = """
                {
                  "BALANCED":{"setsText":"5","repsText":"10","restSeconds":45},
                  "FUTURE_PHASE":{"setsText":"1","repsText":"1","restSeconds":1}
                }
            """.trimIndent()
        )

        val preset = exercise.resolveBalancedProgrammingPreset()

        assertEquals("5", preset.setsText)
        assertEquals("10", preset.repsText)
        assertEquals(45, preset.restSeconds)
        assertTrue(exercise.decodeStoredProgrammingPresets().hasIssues)
    }

    @Test
    fun exerciseResolver_malformedPresetJsonFallsBackWithoutThrowing() {
        val exercise = Exercise(
            name = "Push-up",
            defaultSets = 4,
            defaultReps = "6-8",
            defaultRestSeconds = 120,
            trainingPhasePresets = """{"BALANCED":"""
        )

        val preset = exercise.resolveBalancedProgrammingPreset()

        assertEquals("4", preset.setsText)
        assertEquals("6-8", preset.repsText)
        assertEquals(120, preset.restSeconds)
        assertTrue(exercise.decodeStoredProgrammingPresets().hasIssues)
    }

    @Test
    fun richPrescription_malformedJsonReportsIssueWithoutInventingDefaults() {
        val result = """{"rounds":""".decodeRichPrescriptionData()

        assertNull(result.value)
        assertTrue(result.hasIssues)
    }

    @Test
    fun richPrescription_blankJsonIsLegacyEmptyAndNotAnError() {
        val result = "".decodeRichPrescriptionData()

        assertNull(result.value)
        assertFalse(result.hasIssues)
    }
}
