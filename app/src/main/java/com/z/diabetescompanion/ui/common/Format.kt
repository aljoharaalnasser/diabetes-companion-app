package com.z.diabetescompanion.ui.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {
    private val time get() = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dayLong get() = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    private val dayShort get() = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val dateTime get() = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    private val dateFull get() = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun time(date: Date): String = time.format(date)
    fun dateTime(date: Date): String = dateTime.format(date)
    fun dateFull(date: Date): String = dateFull.format(date)
    fun today(): String = dayLong.format(Date())

    /** "Today", "Yesterday", or "Mon, 3 Sep". */
    fun dayLabel(date: Date, now: Date = Date()): String {
        val a = Calendar.getInstance().apply { time = date }
        val b = Calendar.getInstance().apply { time = now }
        val sameYear = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
        val dayDiff = b.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> "Today"
            sameYear && dayDiff == 1 -> "Yesterday"
            else -> dayShort.format(date)
        }
    }

    /** "Just now", "12 min ago", "3h ago", else date + time. */
    fun relative(date: Date, now: Date = Date()): String {
        val diff = now.time - date.time
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
            diff < TimeUnit.HOURS.toMillis(24) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            else -> dateTime(date)
        }
    }

    fun clock(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        }
        return time.format(cal.time)
    }

    fun days(days: List<Int>): String {
        if (days.isEmpty() || days.size == 7) return "Every day"
        val names = mapOf(
            Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
        )
        return days.sorted().mapNotNull { names[it] }.joinToString(", ")
    }

    fun dose(dose: Double, unit: String): String {
        val number = if (dose % 1.0 == 0.0) dose.toInt().toString() else String.format(Locale.US, "%.1f", dose)
        return "$number $unit"
    }
}
