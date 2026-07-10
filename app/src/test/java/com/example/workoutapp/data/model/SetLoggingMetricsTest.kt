package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetLoggingMetricsTest {

    @Test
    fun parseTimedPrescriptionSeconds_parsesVariousDurationFormats() {
        assertEquals(30, parseTimedPrescriptionSeconds("30s"))
        assertEquals(45, parseTimedPrescriptionSeconds("45 sec"))
        assertEquals(60, parseTimedPrescriptionSeconds("60 seconds"))
        assertEquals(20, parseTimedPrescriptionSeconds("  20s  "))
        assertEquals(10, parseTimedPrescriptionSeconds("10 SECONDS"))
    }

    @Test
    fun parseTimedPrescriptionSeconds_returnsNullForRepBasedText() {
        assertNull(parseTimedPrescriptionSeconds("8-12"))
        assertNull(parseTimedPrescriptionSeconds("AMRAP"))
        assertNull(parseTimedPrescriptionSeconds(""))
        assertNull(parseTimedPrescriptionSeconds("12"))
    }

    @Test
    fun resolveSetMetricVisibility_repBasedPrescriptionShowsRepsAndWeight() {
        val metrics = resolveSetMetricVisibility(plannedReps = "8-12", richPrescription = null)

        assertTrue(metrics.showReps)
        assertTrue(metrics.showWeight)
        assertFalse(metrics.showDuration)
        assertTrue(metrics.showRpe)
        assertTrue(metrics.showNotes)
    }

    @Test
    fun resolveSetMetricVisibility_durationTextHidesRepsButKeepsWeight() {
        // Duration and load can coexist (e.g. a weighted farmer's walk or a loaded plank hold).
        // The model has no per-exercise load-capability signal, so weight stays visible for
        // timed prescriptions rather than being silently hidden/dropped.
        val metrics = resolveSetMetricVisibility(plannedReps = "30s", richPrescription = null)

        assertFalse(metrics.showReps)
        assertTrue(metrics.showWeight)
        assertTrue(metrics.showDuration)
    }

    @Test
    fun resolveSetMetricVisibility_explicitRichPrescriptionDurationHidesRepsButKeepsWeight() {
        val metrics = resolveSetMetricVisibility(
            plannedReps = "AMRAP",
            richPrescription = RichPrescriptionData(durationSeconds = 40)
        )

        assertFalse(metrics.showReps)
        assertTrue(metrics.showWeight)
        assertTrue(metrics.showDuration)
    }

    @Test
    fun resolveSetMetricVisibility_richPrescriptionWithoutDurationDoesNotForceTimed() {
        val metrics = resolveSetMetricVisibility(
            plannedReps = "8-12",
            richPrescription = RichPrescriptionData(rounds = 3, tempo = "31X1")
        )

        assertTrue(metrics.showReps)
        assertTrue(metrics.showWeight)
        assertFalse(metrics.showDuration)
    }
}
