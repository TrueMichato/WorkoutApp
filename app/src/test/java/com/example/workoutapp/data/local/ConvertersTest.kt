package com.example.workoutapp.data.local

import com.example.workoutapp.data.model.PersistedDataDecodeException
import com.example.workoutapp.data.model.WorkoutCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun stringList_malformedJsonThrowsInsteadOfReturningEmpty() {
        assertThrows(PersistedDataDecodeException::class.java) {
            converters.toStringList("[\"kept\"")
        }
    }

    @Test
    fun categoryList_unknownEnumThrowsInsteadOfDroppingAllCategories() {
        val raw = """["STRENGTH","FUTURE_CATEGORY"]"""

        val error = assertThrows(PersistedDataDecodeException::class.java) {
            converters.toCategoryList(raw)
        }

        assertEquals("workout category list", error.fieldName)
    }

    @Test
    fun categoryList_lowercaseLegacyNamesDecodeCompatibly() {
        val raw = """["strength","mobility"]"""

        assertEquals(
            listOf(WorkoutCategory.STRENGTH, WorkoutCategory.MOBILITY),
            converters.toCategoryList(raw)
        )
    }
}
