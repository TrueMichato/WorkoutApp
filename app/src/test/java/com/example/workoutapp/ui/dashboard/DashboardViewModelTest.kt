package com.example.workoutapp.ui.dashboard

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardViewModelTest {

    @Test
    fun currentWeekBounds_usesMondayThroughSunday() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = utcCalendar(2026, Calendar.JULY, 9, 15, 30).timeInMillis

        val (start, end) = currentWeekBounds(now, utc)

        assertEquals(utcCalendar(2026, Calendar.JULY, 6, 0, 0).timeInMillis, start)
        assertEquals(utcCalendar(2026, Calendar.JULY, 13, 0, 0).timeInMillis, end)
    }

    @Test
    fun currentWeekBounds_keepsMondayAtStartOfSameWeek() {
        val utc = TimeZone.getTimeZone("UTC")
        val monday = utcCalendar(2026, Calendar.JULY, 6, 0, 0).timeInMillis

        val (start, end) = currentWeekBounds(monday, utc)

        assertEquals(monday, start)
        assertEquals(7L * 24 * 60 * 60 * 1_000, end - start)
    }

    private fun utcCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }
}
