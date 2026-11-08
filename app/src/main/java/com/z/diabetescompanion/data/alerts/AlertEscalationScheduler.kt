package com.z.diabetescompanion.data.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.domain.AlertRules
import java.util.Date

/**
 * Schedules the five-minute escalation steps from FR-09.
 *
 * The patient's device owns the timer, the same way it owns medication reminders. Without a
 * server this is the only place a timer can live; the resulting stage change is written to
 * Firestore, so the caregiver and doctor apps see it through their live listeners.
 */
class AlertEscalationScheduler(private val context: Context) {

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alert: Alert) {
        val dueAt = AlertRules.nextEscalationAt(alert) ?: run {
            cancel(alert.id)
            return
        }
        val triggerAt = maxOf(dueAt.time, System.currentTimeMillis() + 1_000)
        val pending = pendingIntent(alert.patientUid, alert.id)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "Escalation for ${alert.id} scheduled at ${Date(triggerAt)}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted; using inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(alertId: String) {
        alarmManager.cancel(pendingIntent("", alertId))
    }

    fun syncAll(alerts: List<Alert>) {
        alerts.forEach { alert ->
            if (AlertRules.nextStageOnTimeout(alert) == null) cancel(alert.id) else schedule(alert)
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(patientUid: String, alertId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alertId.hashCode(),
            Intent(context, AlertEscalationReceiver::class.java)
                .setAction(AlertEscalationReceiver.ACTION_ESCALATE)
                .putExtra(AlertEscalationReceiver.EXTRA_PATIENT_UID, patientUid)
                .putExtra(AlertEscalationReceiver.EXTRA_ALERT_ID, alertId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val TAG = "AlertEscalation"
    }
}
