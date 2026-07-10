package com.example.workoutapp.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichPrescriptionDataTest {

    @Test
    fun richPrescription_roundTripsThroughJson() {
        val original = RichPrescriptionData(
            rounds = 3,
            durationSeconds = 45,
            tempo = "31X1",
            effortTarget = "RPE 7-8"
        )

        val restored = original.toJson().toRichPrescriptionDataOrNull()

        assertEquals(original, restored)
    }

    @Test
    fun richPrescription_displaySummaryIncludesRelevantFields() {
        val summary = RichPrescriptionData(
            rounds = 2,
            durationSeconds = 30,
            tempo = "20X1",
            effortTarget = "Easy nasal breathing"
        ).displaySummary()

        assertTrue(summary.contains("2 rounds"))
        assertTrue(summary.contains("30s work"))
        assertTrue(summary.contains("Tempo 20X1"))
        assertTrue(summary.contains("Easy nasal breathing"))
    }

    @Test
    fun exercisePreset_resolvesBalancedRichPrescriptionFromJson() {
        val exercise = Exercise(
            id = 1,
            name = "Bike Sprint",
            trainingPhasePresets = Json.encodeToString(
                mapOf(
                    TrainingPhase.BALANCED.name to ExerciseProgrammingPreset(
                        setsText = "4",
                        repsText = "45s",
                        restSeconds = 30,
                        rounds = 3,
                        durationSeconds = 45,
                        tempo = "Fast cadence",
                        effortTarget = "RPE 8"
                    )
                )
            )
        )

        val preset = exercise.resolveBalancedProgrammingPreset()

        assertEquals("4", preset.setsText)
        assertEquals("45s", preset.repsText)
        assertEquals(30, preset.restSeconds)
        assertEquals(
            RichPrescriptionData(
                rounds = 3,
                durationSeconds = 45,
                tempo = "Fast cadence",
                effortTarget = "RPE 8"
            ),
            preset.toRichPrescriptionData()
        )
    }

    @Test
    fun exercisePreset_legacyJsonFallsBackToDefaultRichValues() {
        val exercise = Exercise(
            id = 2,
            name = "Push-up",
            trainingPhasePresets = """
                {"BALANCED":{"setsText":"3","repsText":"8-12","restSeconds":60}}
            """.trimIndent()
        )

        val preset = exercise.resolveBalancedProgrammingPreset()

        assertEquals(1, preset.rounds)
        assertEquals(null, preset.durationSeconds)
        assertEquals("", preset.tempo)
        assertEquals("", preset.effortTarget)
    }
}


