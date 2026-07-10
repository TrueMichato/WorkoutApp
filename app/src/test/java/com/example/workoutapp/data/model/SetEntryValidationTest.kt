package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEntryValidationTest {

    private val repBasedMetrics = SetMetricVisibility(showReps = true, showWeight = true, showDuration = false)
    private val timedMetrics = SetMetricVisibility(showReps = false, showWeight = false, showDuration = true)

    @Test
    fun validate_blankFieldsAreValidAndParseToNull() {
        val result = validateSetEntryDraft(SetEntryDraft(), repBasedMetrics)

        assertTrue(result.isValid)
        assertNull(result.reps)
        assertNull(result.weight)
        assertNull(result.rpe)
    }

    @Test
    fun validate_validRepBasedInputParsesCorrectly() {
        val draft = SetEntryDraft(reps = "10", weight = "62.5", rpe = "8", notes = "felt strong")
        val result = validateSetEntryDraft(draft, repBasedMetrics)

        assertTrue(result.isValid)
        assertEquals(10, result.reps)
        assertEquals(62.5f, result.weight)
        assertEquals(8, result.rpe)
        assertEquals("felt strong", result.notes)
    }

    @Test
    fun validate_negativeRepsIsRejectedWithActionableError() {
        val result = validateSetEntryDraft(SetEntryDraft(reps = "-5"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.reps)
        assertTrue(result.errors.reps!!.contains("negative"))
    }

    @Test
    fun validate_negativeWeightIsRejected() {
        val result = validateSetEntryDraft(SetEntryDraft(weight = "-2.5"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.weight)
        assertTrue(result.errors.weight!!.contains("negative"))
    }

    @Test
    fun validate_nonNumericRepsIsRejected() {
        val result = validateSetEntryDraft(SetEntryDraft(reps = "abc"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.reps)
        assertEquals("Enter a whole number for reps", result.errors.reps)
    }

    @Test
    fun validate_repsAboveMaxIsRejected() {
        val result = validateSetEntryDraft(SetEntryDraft(reps = "500"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.reps)
    }

    @Test
    fun validate_rpeOutsideRangeIsRejected() {
        val tooLow = validateSetEntryDraft(SetEntryDraft(rpe = "0"), repBasedMetrics)
        val tooHigh = validateSetEntryDraft(SetEntryDraft(rpe = "11"), repBasedMetrics)

        assertFalse(tooLow.isValid)
        assertFalse(tooHigh.isValid)
    }

    @Test
    fun validate_zeroDurationIsRejectedButPositiveDurationIsValid() {
        val zero = validateSetEntryDraft(SetEntryDraft(durationSeconds = "0"), timedMetrics)
        val positive = validateSetEntryDraft(SetEntryDraft(durationSeconds = "30"), timedMetrics)

        assertFalse(zero.isValid)
        assertTrue(positive.isValid)
        assertEquals(30, positive.durationSeconds)
    }

    @Test
    fun validate_hiddenFieldsAreIgnoredEvenIfPopulatedWithGarbage() {
        // A timed exercise hides reps/weight; even nonsensical text in those drafted fields
        // (e.g. leftover from switching exercises) must not produce errors or block saving.
        val draft = SetEntryDraft(reps = "not-a-number", weight = "-99", durationSeconds = "45")
        val result = validateSetEntryDraft(draft, timedMetrics)

        assertTrue(result.isValid)
        assertNull(result.reps)
        assertNull(result.weight)
        assertEquals(45, result.durationSeconds)
    }

    @Test
    fun fieldErrors_hasErrorsReflectsAnyPopulatedError() {
        val noErrors = SetEntryFieldErrors()
        val withError = SetEntryFieldErrors(reps = "bad")

        assertFalse(noErrors.hasErrors)
        assertTrue(withError.hasErrors)
    }
}
