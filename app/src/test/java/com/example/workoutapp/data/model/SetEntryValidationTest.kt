package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEntryValidationTest {

    private val repBasedMetrics = SetMetricVisibility(showReps = true, showWeight = true, showDuration = false)
    private val timedMetrics = SetMetricVisibility(showReps = false, showWeight = true, showDuration = true)

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
    fun validate_hiddenRepsAreIgnoredEvenIfPopulatedWithGarbageOnATimedExercise() {
        // A timed exercise hides reps only; even nonsensical text left over in that field
        // (e.g. from switching exercises) must not produce an error or block saving.
        val draft = SetEntryDraft(reps = "not-a-number", weight = "40", durationSeconds = "45")
        val result = validateSetEntryDraft(draft, timedMetrics)

        assertTrue(result.isValid)
        assertNull(result.reps)
        assertEquals(40f, result.weight)
        assertEquals(45, result.durationSeconds)
    }

    @Test
    fun validate_weightIsAvailableAndValidatedOnTimedExercises() {
        // Duration and load can coexist (e.g. a weighted farmer's walk or loaded plank hold),
        // so weight must still be captured and validated for timed prescriptions, not silently
        // dropped just because the exercise is duration-based.
        val negative = validateSetEntryDraft(SetEntryDraft(weight = "-20", durationSeconds = "30"), timedMetrics)
        val valid = validateSetEntryDraft(SetEntryDraft(weight = "20", durationSeconds = "30"), timedMetrics)

        assertFalse(negative.isValid)
        assertNull(negative.weight)
        assertTrue(negative.errors.weight!!.contains("negative"))

        assertTrue(valid.isValid)
        assertEquals(20f, valid.weight)
        assertEquals(30, valid.durationSeconds)
    }

    @Test
    fun validate_nanWeightIsRejected() {
        val result = validateSetEntryDraft(SetEntryDraft(weight = "NaN"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.weight)
        assertEquals("Enter a valid weight", result.errors.weight)
    }

    @Test
    fun validate_positiveInfinityWeightIsRejected() {
        val result = validateSetEntryDraft(SetEntryDraft(weight = "Infinity"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.weight)
        assertEquals("Enter a valid weight", result.errors.weight)
    }

    @Test
    fun validate_negativeInfinityWeightIsRejected() {
        val result = validateSetEntryDraft(SetEntryDraft(weight = "-Infinity"), repBasedMetrics)

        assertFalse(result.isValid)
        assertNull(result.weight)
        assertEquals("Enter a valid weight", result.errors.weight)
    }

    @Test
    fun fieldErrors_hasErrorsReflectsAnyPopulatedError() {
        val noErrors = SetEntryFieldErrors()
        val withError = SetEntryFieldErrors(reps = "bad")

        assertFalse(noErrors.hasErrors)
        assertTrue(withError.hasErrors)
    }
}
