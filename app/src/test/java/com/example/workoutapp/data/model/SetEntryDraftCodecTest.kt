package com.example.workoutapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEntryDraftCodecTest {

    @Test
    fun encodeDecode_roundTripsMultipleDrafts() {
        val drafts = mapOf(
            1L to SetEntryDraft(reps = "10", weight = "60", rpe = "8"),
            2L to SetEntryDraft(durationSeconds = "30", notes = "keep core tight")
        )

        val encoded = encodeSetEntryDrafts(drafts)
        val decoded = decodeSetEntryDrafts(encoded)

        assertEquals(drafts, decoded)
    }

    @Test
    fun decode_blankOrNullInputReturnsEmptyMap() {
        assertTrue(decodeSetEntryDrafts(null).isEmpty())
        assertTrue(decodeSetEntryDrafts("").isEmpty())
        assertTrue(decodeSetEntryDrafts("   ").isEmpty())
    }

    @Test
    fun decode_malformedJsonFallsBackToEmptyMapInsteadOfCrashing() {
        assertTrue(decodeSetEntryDrafts("not valid json").isEmpty())
    }

    @Test
    fun encodeDecode_emptyMapRoundTrips() {
        val encoded = encodeSetEntryDrafts(emptyMap())

        assertTrue(decodeSetEntryDrafts(encoded).isEmpty())
    }
}
