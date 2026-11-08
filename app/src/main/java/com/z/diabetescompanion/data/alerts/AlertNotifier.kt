package com.z.diabetescompanion.data.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.z.diabetescompanion.MainActivity
import com.z.diabetescompanion.R
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertStage

/**
 * Local notifications for alert activity.
 *
 * Lock-screen text stays generic (NFR-09): no glucose value and no patient name appear until
 * the device is unlocked. Cross-device push to a caregiver or doctor would need Cloud
 * Messaging and a server; without one, the in-app alert state is authoritative and every
 * notification attempt is recorded on the alert itself, which is the charter's R3 mitigation.
 */
object AlertNotifier {

    const val CHANNEL_ID = "glucose_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Glucose alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Out-of-range readings and their escalation."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyStage(context: Context, alert: Alert) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val title = when (alert.stage) {
            AlertStage.Created -> "Glucose alert"
            AlertStage.CaregiverNotified -> "Your caregiver has been notified"
            AlertStage.DoctorNotified -> "Your doctor has been notified"
            AlertStage.Resolved -> "Alert resolved"
        }
        val text = when (alert.stage) {
            AlertStage.Resolved -> "Follow-up was recorded by ${alert.resolvedByName.ifBlank { "your care team" }}."
            else -> "Open the app to respond to your reading."
        }

        val openApp = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Diabetes Companion")
            .setContentText("You have a new alert update.")
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }
}
