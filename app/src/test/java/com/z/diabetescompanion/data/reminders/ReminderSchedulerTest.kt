package com.z.diabetescompanion.data.reminders

import com.z.diabetescompanion.data.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderSchedulerTest {

    private fun at(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            // Week of 2026-09-13 (Sunday) .. 2026-09-19 (Saturday)
            set(2026, Calendar.SEPTEMBER, 13 + (dayOfWeek - Calendar.SUNDAY), hour, minute, 0)
        }

    private fun Long.cal() = Calendar.getInstance().apply { timeInMillis = this@cal }

    @Test
    fun `daily reminder later today fires today`() {
        val now = at(Calendar.TUESDAY, 8, 0)
        val next = ReminderScheduler.nextTriggerMillis(Reminder("r", "Metformin", hour = 21, minute = 0), now).cal()
        assertEquals(Calendar.TUESDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(21, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `daily reminder already passed today fires tomorrow`() {
        val now = at(Calendar.TUESDAY, 22, 0)
        val next = ReminderScheduler.nextTriggerMillis(Reminder("r", "Metformin", hour = 21, minute = 0), now).cal()
        assertEquals(Calendar.WEDNESDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `reminder within the next minute is pushed to the following day`() {
        val now = at(Calendar.TUESDAY, 20, 59)
        val next = ReminderScheduler.nextTriggerMillis(Reminder("r", "Metformin", hour = 21, minute = 0), now).cal()
        assertEquals(Calendar.WEDNESDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `weekday-only reminder skips to the next allowed day`() {
        val now = at(Calendar.FRIDAY, 22, 0)
        val reminder = Reminder("r", "Insulin", hour = 9, minute = 30, days = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY))
        val next = ReminderScheduler.nextTriggerMillis(reminder, now).cal()
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, next.get(Calendar.MINUTE))
    }
}
