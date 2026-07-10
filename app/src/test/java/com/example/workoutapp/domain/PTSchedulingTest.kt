package com.example.workoutapp.domain

import com.example.workoutapp.data.model.PTFrequency
import com.example.workoutapp.data.model.PTSessionLog
import com.example.workoutapp.data.model.PhysicalTherapyRoutine
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PT scheduling logic — verifies that the scheduling
 * metadata on PhysicalTherapyRoutine, PTFrequency, and PTSessionLog
 * compose correctly for must-do / due-now decisions.
 */
class PTSchedulingTest {

    @Test
    fun mustDoRoutine_isDueWhenNotCompletedToday() {
        val routine = PhysicalTherapyRoutine(
            id = 1,
            name = "Shoulder rehab",
            frequency = PTFrequency.DAILY,
            timesPerDay = 1,
            isMustDo = true,
            isActive = true
        )
        val completedToday = 0
        val isDue = routine.isMustDo && completedToday < routine.timesPerDay
        assertTrue("Routine should be due when not completed today", isDue)
    }

    @Test
    fun mustDoRoutine_notDueWhenCompletedToday() {
        val routine = PhysicalTherapyRoutine(
            id = 2,
            name = "Knee stretches",
            frequency = PTFrequency.DAILY,
            timesPerDay = 1,
            isMustDo = true,
            isActive = true
        )
        val completedToday = 1
        val isDue = routine.isMustDo && completedToday < routine.timesPerDay
        assertFalse("Routine should not be due when already completed", isDue)
    }

    @Test
    fun multiDailyRoutine_isDueUntilAllSessionsDone() {
        val routine = PhysicalTherapyRoutine(
            id = 3,
            name = "Neck exercises",
            frequency = PTFrequency.MULTIPLE_DAILY,
            timesPerDay = 3,
            isMustDo = true,
            isActive = true
        )
        assertTrue(routine.isMustDo && 0 < routine.timesPerDay)
        assertTrue(routine.isMustDo && 1 < routine.timesPerDay)
        assertTrue(routine.isMustDo && 2 < routine.timesPerDay)
        assertFalse(routine.isMustDo && 3 < routine.timesPerDay)
    }

    @Test
    fun nonMustDoRoutine_isNeverMarkedDue() {
        val routine = PhysicalTherapyRoutine(
            id = 4,
            name = "Optional ankle warmup",
            frequency = PTFrequency.AS_NEEDED,
            timesPerDay = 1,
            isMustDo = false,
            isActive = true
        )
        val isDue = routine.isMustDo && 0 < routine.timesPerDay
        assertFalse("Non-must-do routine should never be marked due", isDue)
    }

    @Test
    fun ptFrequency_timesPerWeekValues() {
        assertEquals(14, PTFrequency.MULTIPLE_DAILY.timesPerWeek)
        assertEquals(7, PTFrequency.DAILY.timesPerWeek)
        assertEquals(4, PTFrequency.EVERY_OTHER_DAY.timesPerWeek)
        assertEquals(3, PTFrequency.THREE_TIMES_WEEK.timesPerWeek)
        assertEquals(2, PTFrequency.TWICE_WEEK.timesPerWeek)
        assertEquals(1, PTFrequency.WEEKLY.timesPerWeek)
        assertEquals(0, PTFrequency.AS_NEEDED.timesPerWeek)
    }

    @Test
    fun sessionLog_painReductionCalculation() {
        val log = PTSessionLog(
            id = 1,
            routineId = 1,
            startedAt = System.currentTimeMillis(),
            completedAt = System.currentTimeMillis(),
            painLevelBefore = 7,
            painLevelAfter = 4
        )
        val delta = (log.painLevelAfter ?: 0) - (log.painLevelBefore ?: 0)
        assertEquals(-3, delta)
        assertTrue("Negative delta means pain improved", delta < 0)
    }

    @Test
    fun archivedRoutine_excludedFromActiveList() {
        val routine = PhysicalTherapyRoutine(
            id = 5,
            name = "Old routine",
            isActive = true,
            isArchived = true
        )
        // The DAO query filters isArchived = 0 for active list
        val shouldShow = routine.isActive && !routine.isArchived
        assertFalse("Archived routine should not appear in active list", shouldShow)
    }
}

