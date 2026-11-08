package com.z.diabetescompanion.data.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.z.diabetescompanion.DiabetesCompanionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when an alert's five-minute timer expires. Re-reads the alert so a response that
 * arrived in the meantime always wins, moves it one stage on, then schedules the next step.
 */
class AlertEscalationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ESCALATE) return
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID) ?: return
        val app = context.applicationContext as? DiabetesCompanionApp ?: return
        val patientUid = intent.getStringExtra(EXTRA_PATIENT_UID)?.takeIf { it.isNotBlank() }
            ?: app.container.authRepository.currentUser?.uid
            ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.alertService.escalateNow(patientUid, alertId)
            } catch (e: Exception) {
                Log.w(TAG, "Escalation failed for $alertId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ESCALATE = "com.z.diabetescompanion.ALERT_ESCALATE"
        const val EXTRA_ALERT_ID = "alertId"
        const val EXTRA_PATIENT_UID = "patientUid"
        private const val TAG = "AlertEscalationRx"
    }
}
