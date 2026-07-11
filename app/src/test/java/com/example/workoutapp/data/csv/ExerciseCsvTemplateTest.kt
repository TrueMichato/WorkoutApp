package com.example.workoutapp.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExerciseCsvTemplate] parses cleanly through [ExerciseCsvParser] and demonstrates the CSV
 * quirks (quoting, escaped quotes, and all three multi-value delimiters) importers must handle.
 */
class ExerciseCsvTemplateTest {

    private val records by lazy { ExerciseCsvParser.parse(ExerciseCsvTemplate.render()) }

    @Test
    fun render_producesExactlyThreeIllustrativeRows() {
        assertEquals(3, records.size)
    }

    @Test
    fun render_headerRowMatchesCanonicalSchemaExactly() {
        val headers = ExerciseCsvParser.parseHeaders(ExerciseCsvTemplate.render())

        assertEquals(ExerciseCsvSchema.headers, headers)
    }

    @Test
    fun render_firstRowIsASimpleStrengthExerciseWithPipeDelimiters() {
        val pushUp = records.first { it["name"] == "Push-Up" }

        assertEquals("Strength|Hypertrophy", pushUp["categories"])
        assertEquals("true", pushUp["is_compound"])
        assertEquals("false", pushUp["is_unilateral"])
        assertEquals("Chest|Triceps", pushUp["primary_muscles"])
    }

    @Test
    fun render_secondRowDemonstratesQuotingAltBooleansAndEveryDelimiter() {
        val farmersWalk = records.first { it["name"] == "Farmer's Walk" }

        // Comma-delimited categories required quoting when the row was written.
        assertEquals("Functional,Strength", farmersWalk["categories"])
        // Alternate boolean spellings.
        assertEquals("yes", farmersWalk["is_compound"])
        assertEquals("no", farmersWalk["is_unilateral"])
        // Semicolon and pipe delimiters.
        assertEquals("Forearms;Grip", farmersWalk["primary_muscles"])
        assertEquals("Abs|Glutes", farmersWalk["secondary_muscles"])
        // Embedded quotes survive the escape/unescape round trip.
        assertTrue(farmersWalk["instructions"]!!.contains("\"proud\""))
        // Equipment field needed comma-quoting too.
        assertEquals("Kettlebell, Sandbag", farmersWalk["equipment"])
    }

    @Test
    fun render_isValidUtf8Text() {
        val bytes = ExerciseCsvTemplate.render().toByteArray(Charsets.UTF_8)
        val decoded = String(bytes, Charsets.UTF_8)

        assertEquals(ExerciseCsvTemplate.render(), decoded)
    }

    @Test
    fun render_thirdRowDemonstratesAFamilyVariationLinkedByName() {
        val diamondPushUp = records.first { it["name"] == "Diamond Push-Up" }

        assertEquals("Push-Up", diamondPushUp["main_exercise"])
        assertEquals("Triceps emphasis", diamondPushUp["variation_focus"])
        // Its main exercise's row must also exist under that exact name so the reference resolves.
        assertTrue(records.any { it["name"] == "Push-Up" })
    }
}
