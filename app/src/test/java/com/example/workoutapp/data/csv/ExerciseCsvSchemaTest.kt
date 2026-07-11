package com.example.workoutapp.data.csv

import com.example.workoutapp.data.model.TrainingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExerciseCsvSchema] is the single source of truth the importer, the in-app help text, and
 * [ExerciseCsvTemplate] all read from. These tests guard the invariants that keep them in sync.
 */
class ExerciseCsvSchemaTest {

    @Test
    fun headers_containNoDuplicatesAndIncludeEveryTrainingPhase() {
        assertEquals(ExerciseCsvSchema.headers.size, ExerciseCsvSchema.headers.toSet().size)
        TrainingPhase.entries.forEach { phase ->
            ExerciseCsvSchema.phaseColumnSuffixes.forEach { suffix ->
                assertTrue(
                    "${ExerciseCsvSchema.phaseHeader(phase, suffix)} missing from schema headers",
                    ExerciseCsvSchema.phaseHeader(phase, suffix) in ExerciseCsvSchema.headers
                )
            }
        }
    }

    @Test
    fun requiredHeaders_areNameAndCategories() {
        assertEquals(listOf("name", "categories"), ExerciseCsvSchema.requiredHeaders)
    }

    @Test
    fun missingRequiredHeaders_detectsAbsentColumnsCaseInsensitively() {
        val missing = ExerciseCsvSchema.missingRequiredHeaders(listOf("description", "difficulty"))

        assertEquals(listOf("name", "categories"), missing)
    }

    @Test
    fun missingRequiredHeaders_isEmptyWhenBothPresent() {
        val missing = ExerciseCsvSchema.missingRequiredHeaders(listOf("name", "categories", "description"))

        assertTrue(missing.isEmpty())
    }

    @Test
    fun unknownHeaders_flagsColumnsOutsideTheSchema() {
        val unknown = ExerciseCsvSchema.unknownHeaders(listOf("name", "categories", "made_up_column"))

        assertEquals(listOf("made_up_column"), unknown)
    }

    @Test
    fun unknownHeaders_isEmptyForTheGeneratedTemplateHeaders() {
        val templateHeaders = ExerciseCsvParser.parseHeaders(ExerciseCsvTemplate.render())

        assertTrue(ExerciseCsvSchema.unknownHeaders(templateHeaders).isEmpty())
        assertTrue(ExerciseCsvSchema.missingRequiredHeaders(templateHeaders).isEmpty())
        assertEquals(ExerciseCsvSchema.headers, templateHeaders)
    }

    @Test
    fun helpSummary_mentionsMultiValueDelimitersAndBooleanSyntax() {
        val summary = ExerciseCsvSchema.helpSummary()

        assertTrue(summary.contains("name"))
        assertTrue(summary.contains("categories"))
        assertTrue(summary.contains(ExerciseCsvSchema.booleanSyntaxHelp))
        assertFalse(summary.isBlank())
    }
}
