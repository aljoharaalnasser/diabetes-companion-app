package com.z.diabetescompanion.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.z.diabetescompanion.data.model.Reminder
import java.util.Calendar

/**
 * Schedules one AlarmManager alarm per enabled reminder for its next occurrence.
 * [ReminderReceiver] posts the notification and re-schedules the following occurrence,
 * so a reminder keeps firing without a persistent service (FR-07).
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: Reminder) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        val triggerAt = nextTriggerMillis(reminder)
        val pending = pendingIntent(reminder)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(pendingIntent(reminderId))
    }

    /** Re-schedules everything; called on app start, after a boot, and when reminders change. */
    fun syncAll(reminders: List<Reminder>, notificationsEnabled: Boolean) {
        reminders.forEach { reminder ->
            if (notificationsEnabled) schedule(reminder) else cancel(reminder.id)
        }
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(reminder: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            ReminderReceiver.intent(context, reminder),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingIntent(reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "ReminderScheduler"

        /** Next wall-clock time (>= now + 1 min) matching the reminder's time and day set. */
        fun nextTriggerMillis(reminder: Reminder, now: Calendar = Calendar.getInstance()): Long {
            val candidate = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis <= now.timeInMillis + 60_000) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (!reminder.isDaily) {
                var guard = 0
                while (candidate.get(Calendar.DAY_OF_WEEK) !in reminder.days && guard++ < 7) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return candidate.timeInMillis
        }
    }
}
