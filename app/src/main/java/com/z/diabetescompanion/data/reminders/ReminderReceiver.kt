package com.z.diabetescompanion.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.z.diabetescompanion.MainActivity
import com.z.diabetescompanion.R
import com.z.diabetescompanion.data.model.Reminder
import java.util.Locale

/** Fires for one reminder occurrence: posts the notification, then schedules the next one. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val reminder = intent.toReminder() ?: return

        ensureChannel(context)
        if (hasNotificationPermission(context)) {
            val time = String.format(Locale.getDefault(), "%02d:%02d", reminder.hour, reminder.minute)
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Lock-screen copy stays generic (NFR-09): no medication name until unlocked.
            val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Medication reminder")
                .setContentText("Open Diabetes Companion for details.")
                .build()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Time for ${reminder.medicationName}")
                .setContentText("Scheduled for $time. Tap to log it.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        }

        ReminderScheduler(context).schedule(reminder)
    }

    companion object {
        const val ACTION_FIRE = "com.z.diabetescompanion.REMINDER_FIRE"
        const val CHANNEL_ID = "medication_reminders"

        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_DAYS = "days"

        fun intent(context: Context, reminder: Reminder): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_ID, reminder.id)
                .putExtra(EXTRA_NAME, reminder.medicationName)
                .putExtra(EXTRA_HOUR, reminder.hour)
                .putExtra(EXTRA_MINUTE, reminder.minute)
                .putExtra(EXTRA_DAYS, reminder.days.toIntArray())

        private fun Intent.toReminder(): Reminder? {
            val id = getStringExtra(EXTRA_ID) ?: return null
            return Reminder(
                id = id,
                medicationName = getStringExtra(EXTRA_NAME).orEmpty(),
                hour = getIntExtra(EXTRA_HOUR, 8),
                minute = getIntExtra(EXTRA_MINUTE, 0),
                days = getIntArrayExtra(EXTRA_DAYS)?.toList() ?: emptyList(),
                enabled = true,
            )
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Medication reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders for medication and insulin you've scheduled."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }

        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
