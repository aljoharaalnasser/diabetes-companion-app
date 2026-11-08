package com.z.diabetescompanion.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.z.diabetescompanion.DiabetesCompanionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Alarms don't survive a reboot; re-create them from the signed-in patient's reminders. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? DiabetesCompanionApp ?: return
        val uid = app.container.authRepository.currentUser?.uid ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.container.preferencesRepository.preferences.first()
                val reminders = app.container.reminderRepository.listOnce(uid)
                app.container.reminderScheduler.syncAll(reminders, prefs.notificationsEnabled)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Failed to restore reminders", e)
            } finally {
                pending.finish()
            }
        }
    }
}
