package com.z.diabetescompanion.ui.patient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.Reminder
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.reminders.ReminderScheduler
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.Trend
import com.z.diabetescompanion.domain.TrendCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Date
import java.util.concurrent.TimeUnit

data class UpcomingReminder(val reminder: Reminder, val nextAt: Date)

data class PatientHomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val latest: GlucoseReading? = null,
    val status: GlucoseStatus = GlucoseStatus.NoThresholds,
    val trend: Trend = Trend.Unavailable,
    val thresholds: Thresholds? = null,
    /** Last 7 days, for the small chart. */
    val chartReadings: List<GlucoseReading> = emptyList(),
    val upcomingReminders: List<UpcomingReminder> = emptyList(),
    val readingCount: Int = 0,
    /** Open alerts, newest first, surfaced on the dashboard (FR-11 integration). */
    val activeAlerts: List<Alert> = emptyList(),
)

class PatientHomeViewModel(
    uid: String,
    glucoseRepository: GlucoseRepository,
    thresholdRepository: ThresholdRepository,
    reminderRepository: ReminderRepository,
    alertRepository: AlertRepository,
) : ViewModel() {

    private data class Loaded(
        val readings: List<GlucoseReading>,
        val thresholds: Thresholds?,
        val reminders: List<Reminder>,
        val alerts: List<Alert>,
    )

    val uiState: StateFlow<PatientHomeUiState> = combine(
        glucoseRepository.readings(uid, limit = 200),
        thresholdRepository.thresholds(uid),
        reminderRepository.reminders(uid),
        // A failure here must not take the dashboard down with it, so alerts degrade to empty.
        alertRepository.alertsForPatient(uid, limit = 20).catch { emit(emptyList()) },
    ) { readings, thresholds, reminders, alerts -> Loaded(readings, thresholds, reminders, alerts) }
        .map { it.toUiState() }
        .catch { emit(PatientHomeUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientHomeUiState())

    private fun Loaded.toUiState(): PatientHomeUiState {
        val latest = readings.firstOrNull()
        val weekAgo = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        val now = java.util.Calendar.getInstance()
        val upcoming = reminders
            .filter { it.enabled }
            .map { UpcomingReminder(it, Date(ReminderScheduler.nextTriggerMillis(it, now))) }
            .sortedBy { it.nextAt }
            .take(3)
        return PatientHomeUiState(
            isLoading = false,
            latest = latest,
            status = latest?.let { GlucoseStatus.of(it.valueMgdl, thresholds) } ?: GlucoseStatus.NoThresholds,
            trend = TrendCalculator.of(readings),
            thresholds = thresholds,
            chartReadings = readings.filter { it.recordedAt.after(weekAgo) },
            activeAlerts = alerts.filterNot { it.isResolved },
            upcomingReminders = upcoming,
            readingCount = readings.size,
        )
    }
}
