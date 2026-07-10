package com.example.workoutapp.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCsvParserTest {

    @Test
    fun parse_handlesQuotedCommasAndNormalizedHeaders() {
        val csv = """
            name,categories,description,default_sets,hypertrophy_focus_reps
            "Push-up","Strength|Hypertrophy","Hands under shoulders, keep full-body tension",3,"10-15"
        """.trimIndent()

        val records = ExerciseCsvParser.parse(csv)

        assertEquals(1, records.size)
        assertEquals("Push-up", records.first()["name"])
        assertEquals("Strength|Hypertrophy", records.first()["categories"])
        assertEquals("Hands under shoulders, keep full-body tension", records.first()["description"])
        assertEquals("10-15", records.first()["hypertrophy_focus_reps"])
    }

    @Test
    fun parse_skipsBlankRows() {
        val csv = """
            name,categories

            Squat,Strength

            Hip Airplane,Mobility
        """.trimIndent()

        val records = ExerciseCsvParser.parse(csv)

        assertEquals(2, records.size)
        assertEquals("Squat", records[0]["name"])
        assertEquals("Hip Airplane", records[1]["name"])
    }

    @Test
    fun parseLine_supportsEscapedQuotes() {
        val line = "\"Farmer\"\"s Walk\",Functional,\"Carry the bells and stay tall\""

        val cells = ExerciseCsvParser.parseLine(line)

        assertEquals(3, cells.size)
        assertEquals("Farmer\"s Walk", cells[0])
        assertTrue(cells[2].contains("stay tall"))
    }
}



