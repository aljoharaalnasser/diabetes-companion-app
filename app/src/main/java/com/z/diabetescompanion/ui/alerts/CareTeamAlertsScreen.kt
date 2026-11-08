package com.z.diabetescompanion.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.ui.components.AlertListItem
import com.z.diabetescompanion.ui.components.EmergencyNotice
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CareTeamAlertsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val alerts: List<Alert> = emptyList(),
    val showResolved: Boolean = false,
) {
    val open: List<Alert> get() = alerts.filterNot { it.isResolved }
    val visible: List<Alert> get() = if (showResolved) alerts else open
}

/**
 * Alerts across every patient who currently shares with this caregiver or doctor.
 * Revoking consent drops the patient from the link query, so their alerts disappear
 * immediately and the next server read is refused as well (NFR-11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CareTeamAlertsViewModel(
    memberUid: String,
    careLinkRepository: CareLinkRepository,
    alertRepository: AlertRepository,
) : ViewModel() {

    private val showResolved = MutableStateFlow(false)

    private val alerts = careLinkRepository.activeLinksForMember(memberUid)
        .map { links -> links.map { it.patientUid }.distinct() }
        .flatMapLatest { uids -> alertRepository.alertsForPatients(uids) }

    val uiState: StateFlow<CareTeamAlertsUiState> = combine(alerts, showResolved) { list, resolved ->
        CareTeamAlertsUiState(isLoading = false, alerts = list, showResolved = resolved)
    }
        .catch { emit(CareTeamAlertsUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareTeamAlertsUiState())

    fun setShowResolved(value: Boolean) { showResolved.value = value }
}

@Composable
fun CareTeamAlertsScreen(onOpenAlert: (patientUid: String, alertId: String) -> Unit) {
    val session = currentSession()
    val viewModel: CareTeamAlertsViewModel = viewModel {
        val c = container()
        CareTeamAlertsViewModel(session.uid, c.careLinkRepository, c.alertRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Alerts") { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, Modifier.padding(padding))
            state.alerts.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "No alerts",
                    description = "Alerts appear here when a patient who shares with you records a reading outside their target range.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column {
                        FilterChip(
                            selected = state.showResolved,
                            onClick = { viewModel.setShowResolved(!state.showResolved) },
                            label = { Text(if (state.showResolved) "Showing all" else "Open only") },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.open.size} open · ${state.alerts.size} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.visible.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Nothing open",
                            description = "Every alert has been resolved. Switch the filter to see the history.",
                        )
                    }
                }
                items(state.visible.size) { index ->
                    val alert = state.visible[index]
                    AlertListItem(
                        alert = alert,
                        showPatientName = true,
                        onClick = { onOpenAlert(alert.patientUid, alert.id) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    EmergencyNotice()
                    Spacer(Modifier.height(10.dp))
                    PrototypeNotice()
                }
            }
        }
    }
}
