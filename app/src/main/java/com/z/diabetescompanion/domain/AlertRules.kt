package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertEvent
import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.PatientResponse
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The escalation policy from FR-09, kept as pure functions so every stage is unit-testable
 * without Firebase or Android. The charter's timings are prototype values, not clinical advice.
 *
 *  - Warning, unanswered .............. caregiver after 5 minutes, then doctor 5 minutes later
 *  - Critical reading .................. caregiver immediately, then doctor after 5 minutes
 *  - "Need Help" ....................... caregiver and doctor immediately
 *  - "I'm OK" / "Treating" ............. acknowledged, timers stop, alert stays open
 */
object AlertRules {

    val ESCALATION_STEP_MS: Long = TimeUnit.MINUTES.toMillis(5)

    /** Null when the reading needs no alert, i.e. in range or no thresholds configured. */
    fun alertFor(
        reading: GlucoseReading,
        thresholds: Thresholds?,
        patient: UserProfile,
        now: Date = Date(),
    ): Alert? {
        val status = GlucoseStatus.of(reading.valueMgdl, thresholds)
        if (!status.isOutOfRange) return null

        val severity = when (status) {
            GlucoseStatus.CriticalLow, GlucoseStatus.CriticalHigh -> AlertSeverity.Critical
            else -> AlertSeverity.Warning
        }
        // A critical reading skips the first wait and reaches the caregiver at once.
        val stage = if (severity == AlertSeverity.Critical) AlertStage.CaregiverNotified else AlertStage.Created

        val events = buildList {
            add(AlertEvent("created", "Alert created", now, note = status.label))
            if (severity == AlertSeverity.Critical) {
                add(AlertEvent("caregiver_notified", "Caregiver notified", now, note = "Critical reading"))
            }
        }

        return Alert(
            id = Alert.idForReading(reading.id),
            patientUid = patient.uid,
            patientName = patient.displayName,
            valueMgdl = reading.valueMgdl,
            recordedAt = reading.recordedAt,
            severity = severity,
            stage = stage,
            statusLabel = status.label,
            caregiverNotifiedAt = if (severity == AlertSeverity.Critical) now else null,
            events = events,
        )
    }

    /** The stage an alert should move to when its timer fires, or null if it should not move. */
    fun nextStageOnTimeout(alert: Alert): AlertStage? = when {
        alert.isResolved -> null
        alert.patientResponse?.stopsEscalation == true -> null
        alert.stage == AlertStage.Created -> AlertStage.CaregiverNotified
        alert.stage == AlertStage.CaregiverNotified -> AlertStage.DoctorNotified
        else -> null
    }

    /** When the next automatic escalation is due, or null when nothing is pending. */
    fun nextEscalationAt(alert: Alert, now: Date = Date()): Date? {
        if (nextStageOnTimeout(alert) == null) return null
        val from = when (alert.stage) {
            AlertStage.Created -> alert.createdAt ?: alert.recordedAt
            AlertStage.CaregiverNotified -> alert.caregiverNotifiedAt ?: alert.createdAt ?: alert.recordedAt
            else -> return null
        }
        return Date(from.time + ESCALATION_STEP_MS)
    }

    /** Seconds until the next escalation, floored at zero; null when nothing is pending. */
    fun secondsUntilEscalation(alert: Alert, now: Date = Date()): Long? {
        val at = nextEscalationAt(alert, now) ?: return null
        return ((at.time - now.time) / 1000).coerceAtLeast(0)
    }

    /** Applies a patient's answer, including the immediate routes for Need Help. */
    fun applyResponse(alert: Alert, response: PatientResponse, now: Date = Date()): Alert {
        val events = alert.events + AlertEvent(
            type = "acknowledged",
            label = "Patient responded",
            at = now,
            actorName = alert.patientName,
            note = response.label,
        )
        return when (response) {
            PatientResponse.NeedHelp -> alert.copy(
                patientResponse = response,
                respondedAt = now,
                stage = AlertStage.DoctorNotified,
                caregiverNotifiedAt = alert.caregiverNotifiedAt ?: now,
                doctorNotifiedAt = alert.doctorNotifiedAt ?: now,
                events = events + AlertEvent("caregiver_notified", "Caregiver notified", now, note = "Need Help") +
                    AlertEvent("doctor_notified", "Doctor notified", now, note = "Need Help"),
            )
            PatientResponse.ContactDoctor -> alert.copy(
                patientResponse = response,
                respondedAt = now,
                stage = maxOf(alert.stage, AlertStage.DoctorNotified, compareBy { it.order }),
                doctorNotifiedAt = alert.doctorNotifiedAt ?: now,
                events = events + AlertEvent("doctor_notified", "Doctor notified", now, note = "Contact Doctor"),
            )
            PatientResponse.ImOk, PatientResponse.Treating -> alert.copy(
                patientResponse = response,
                respondedAt = now,
                events = events,
            )
        }
    }

    /** Moves an alert one stage forward because its timer expired. */
    fun applyEscalation(alert: Alert, now: Date = Date()): Alert? {
        val next = nextStageOnTimeout(alert) ?: return null
        val label = if (next == AlertStage.CaregiverNotified) "Caregiver notified" else "Doctor notified"
        return alert.copy(
            stage = next,
            caregiverNotifiedAt = if (next == AlertStage.CaregiverNotified) now else alert.caregiverNotifiedAt,
            doctorNotifiedAt = if (next == AlertStage.DoctorNotified) now else alert.doctorNotifiedAt,
            events = alert.events + AlertEvent(
                type = next.storageKey,
                label = label,
                at = now,
                note = "No response after 5 minutes",
            ),
        )
    }

    /** Resolution always carries a note and records who closed it (FR-10). */
    fun applyResolution(
        alert: Alert,
        resolver: UserProfile,
        note: String,
        now: Date = Date(),
    ): Alert = alert.copy(
        stage = AlertStage.Resolved,
        resolvedByUid = resolver.uid,
        resolvedByName = resolver.displayName,
        resolvedByRole = resolver.role,
        resolutionNote = note.trim(),
        resolvedAt = now,
        events = alert.events + AlertEvent(
            type = "resolved",
            label = "Resolved",
            at = now,
            actorName = resolver.displayName,
            note = note.trim(),
        ),
    )

    fun resolutionNoteError(note: String): String? = when {
        note.isBlank() -> "A follow-up note is required to resolve an alert."
        note.trim().length < 4 -> "Please write a slightly longer note."
        else -> null
    }
}
