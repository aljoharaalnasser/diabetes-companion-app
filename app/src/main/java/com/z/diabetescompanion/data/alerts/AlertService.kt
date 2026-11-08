package com.z.diabetescompanion.data.alerts

import android.content.Context
import android.util.Log
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertEvent
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.PatientResponse
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.AlertRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.util.Date

/**
 * Owns the alert lifecycle: raising one from an out-of-range reading, recording the patient's
 * answer, running the timed escalation, and resolving with a follow-up note (FR-08 to FR-10).
 *
 * Kept out of the ViewModels so the broadcast receiver and the UI drive exactly the same code.
 */
class AlertService(
    private val context: Context,
    private val alertRepository: AlertRepository,
    private val thresholdRepository: ThresholdRepository,
    private val scheduler: AlertEscalationScheduler,
) {

    /** Called right after a reading is saved. Returns the alert if one was raised. */
    suspend fun onReadingSaved(patient: UserProfile, reading: GlucoseReading): Alert? {
        val thresholds = thresholdRepository.thresholds(patient.uid).catch { emit(null) }.first()
        val alert = AlertRules.alertFor(reading, thresholds, patient) ?: return null
        return try {
            alertRepository.save(alert, isNew = true)
            scheduler.schedule(alert)
            AlertNotifier.notifyStage(context, alert)
            alert
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not raise alert for reading ${reading.id}", e)
            null
        }
    }

    /** Records one of the four patient actions (FR-08). */
    suspend fun respond(patientUid: String, alertId: String, response: PatientResponse): Alert? {
        val current = alertRepository.getAlert(patientUid, alertId) ?: return null
        if (current.isResolved) return current
        val updated = AlertRules.applyResponse(current, response)
        alertRepository.save(updated, isNew = false)
        // "I'm OK" and "Treating" stop the countdown; the others have already escalated.
        scheduler.schedule(updated)
        return updated
    }

    /**
     * Moves the alert on one stage because its timer expired, or because the demo control
     * asked for it. Re-reads first so a patient response that landed in between always wins.
     */
    suspend fun escalateNow(patientUid: String, alertId: String): Alert? {
        val current = alertRepository.getAlert(patientUid, alertId) ?: return null
        val escalated = AlertRules.applyEscalation(current) ?: run {
            scheduler.cancel(alertId)
            return current
        }
        val logged = escalated.copy(
            events = escalated.events + AlertEvent(
                type = "notification_attempt",
                label = "Notification attempt recorded",
                at = Date(),
                note = "In-app alert state updated for the care team",
            )
        )
        alertRepository.save(logged, isNew = false)
        scheduler.schedule(logged)
        AlertNotifier.notifyStage(context, logged)
        return logged
    }

    /** Caregiver or doctor closes the alert; the note is mandatory (FR-10). */
    suspend fun resolve(
        patientUid: String,
        alertId: String,
        resolver: UserProfile,
        note: String,
    ): Alert? {
        val current = alertRepository.getAlert(patientUid, alertId) ?: return null
        val resolved = AlertRules.applyResolution(current, resolver, note)
        alertRepository.save(resolved, isNew = false)
        scheduler.cancel(alertId)
        return resolved
    }

    private companion object {
        const val TAG = "AlertService"
    }
}
