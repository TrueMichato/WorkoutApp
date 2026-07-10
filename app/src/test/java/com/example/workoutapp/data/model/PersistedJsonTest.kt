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
