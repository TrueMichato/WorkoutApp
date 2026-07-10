package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorSelectionCodecTest {

    @Test
    fun encodeDecode_roundTripsMultipleCategories() {
        val categories = setOf(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY, WorkoutCategory.ENDURANCE)

        val encoded = encodeGeneratorCategorySelection(categories)
        val decoded = decodeGeneratorCategorySelection(encoded)

        assertEquals(categories, decoded)
    }

    @Test
    fun decode_blankOrNullInputReturnsEmptySet() {
        assertTrue(decodeGeneratorCategorySelection(null).isEmpty())
        assertTrue(decodeGeneratorCategorySelection("").isEmpty())
        assertTrue(decodeGeneratorCategorySelection("   ").isEmpty())
    }

    @Test
    fun decode_malformedJsonFallsBackToEmptySetInsteadOfCrashing() {
        assertTrue(decodeGeneratorCategorySelection("not valid json").isEmpty())
    }

    @Test
    fun decode_unknownCategoryNamesAreIgnored() {
        val encoded = """["STRENGTH","NOT_A_REAL_CATEGORY"]"""

        val decoded = decodeGeneratorCategorySelection(encoded)

        assertEquals(setOf(WorkoutCategory.STRENGTH), decoded)
    }

    @Test
    fun encodeDecode_emptySetRoundTrips() {
        val encoded = encodeGeneratorCategorySelection(emptySet())

        assertTrue(decodeGeneratorCategorySelection(encoded).isEmpty())
    }
}
