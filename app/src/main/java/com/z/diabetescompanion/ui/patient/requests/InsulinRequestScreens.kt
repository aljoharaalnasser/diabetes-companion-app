package com.z.diabetescompanion.ui.patient.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.InsulinRequest
import com.z.diabetescompanion.data.model.RequestContext
import com.z.diabetescompanion.data.model.RequestStatus
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.InsulinRequestRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// ---------------------------------------------------------------------------
// New request (S2-06)
// ---------------------------------------------------------------------------

data class NewRequestUiState(
    val isLoading: Boolean = true,
    val question: String = "",
    val context: RequestContext = RequestContext(),
    val doctorUid: String = "",
    val doctorName: String = "",
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
) {
    val hasDoctor: Boolean get() = doctorUid.isNotBlank()
}

class NewInsulinRequestViewModel(
    private val patient: UserProfile,
    private val careLinkRepository: CareLinkRepository,
    private val glucoseRepository: GlucoseRepository,
    private val medicationRepository: MedicationRepository,
    private val requestRepository: InsulinRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewRequestUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val doctor = careLinkRepository.linksForPatient(patient.uid)
                    .catch { emit(emptyList()) }.first()
                    .firstOrNull { it.memberRole == UserRole.Doctor && it.status == CareLinkStatus.Active }
                val readings = glucoseRepository.readings(patient.uid, limit = 5)
                    .catch { emit(emptyList()) }.first()
                val doses = medicationRepository.logs(patient.uid, limit = 5)
                    .catch { emit(emptyList()) }.first()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        doctorUid = doctor?.memberUid.orEmpty(),
                        doctorName = doctor?.memberName.orEmpty(),
                        context = RequestContext(
                            age = patient.age,
                            diabetesType = patient.diabetesType,
                            carePlan = patient.carePlan,
                            relevantHistory = patient.relevantHistory,
                            latestValueMgdl = readings.firstOrNull()?.valueMgdl,
                            latestReadingAt = readings.firstOrNull()?.recordedAt,
                            recentReadings = readings.map { r ->
                                "${r.valueMgdl.toInt()} mg/dL at ${Format.dateTime(r.recordedAt)} (${r.source.label})"
                            },
                            recentDoses = doses.map { d ->
                                "${d.name} ${Format.dose(d.dose, d.unit)} at ${Format.dateTime(d.takenAt)}"
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toFirestoreMessage()) }
            }
        }
    }

    fun onQuestionChange(value: String) =
        _uiState.update { it.copy(question = value.take(500), error = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || !state.hasDoctor) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                requestRepository.create(
                    InsulinRequest(
                        id = UUID.randomUUID().toString(),
                        patientUid = patient.uid,
                        patientName = patient.displayName,
                        doctorUid = state.doctorUid,
                        doctorName = state.doctorName,
                        question = state.question.trim(),
                        context = state.context,
                        status = RequestStatus.Pending,
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, submitted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.toFirestoreMessage()) }
            }
        }
    }
}

@Composable
fun NewInsulinRequestScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val session = currentSession()
    val viewModel: NewInsulinRequestViewModel = viewModel {
        val c = container()
        NewInsulinRequestViewModel(
            session.profile, c.careLinkRepository, c.glucoseRepository,
            c.medicationRepository, c.insulinRequestRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.submitted) LaunchedEffect(Unit) { onSubmitted() }

    ScreenScaffold(title = "Ask about your insulin", onBack = onBack) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (!state.hasDoctor) {
                InlineMessage(
                    message = "You have not shared your records with a doctor yet. Open More, then Care-team sharing, to choose one before asking.",
                    tone = MessageTone.Warning,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "Your question goes to ${state.doctorName}, who will review your records before replying.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            AppTextField(
                value = state.question,
                onValueChange = viewModel::onQuestionChange,
                label = "Your question (optional)",
                placeholder = "What would you like to ask your doctor?",
                enabled = !state.isSubmitting,
                imeAction = ImeAction.Done,
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader("What your doctor will see")
            Spacer(Modifier.height(10.dp))
            ContextPreview(state.context)

            Spacer(Modifier.height(20.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Replies are not immediate. This is not an emergency channel: in an emergency, " +
                        "contact local emergency services yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            InlineMessage(message = state.error, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
            PrimaryButton(
                text = "Send to my doctor",
                onClick = viewModel::submit,
                loading = state.isSubmitting,
                enabled = state.hasDoctor,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContextPreview(context: RequestContext) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ContextRow("Age", context.age?.toString() ?: "not recorded")
            ContextRow("Diabetes type", context.diabetesType.ifBlank { "not recorded" })
            ContextRow("Care plan", context.carePlan.ifBlank { "not recorded" })
            ContextRow(
                "Latest reading",
                context.latestValueMgdl?.let {
                    "${it.toInt()} mg/dL" + (context.latestReadingAt?.let { d -> " · ${Format.dateTime(d)}" } ?: "")
                } ?: "no readings",
            )
            ContextRow("Recent readings", "${context.recentReadings.size} attached")
            ContextRow("Recent doses", "${context.recentDoses.size} attached")

            if (context.missingData.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Missing: ${context.missingData.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ContextRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(118.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// Request history and the answer (S2-06)
// ---------------------------------------------------------------------------

class PatientRequestsViewModel(
    patientUid: String,
    requestRepository: InsulinRequestRepository,
) : ViewModel() {
    val requests: StateFlow<List<InsulinRequest>?> = requestRepository.requestsForPatient(patientUid)
        .map { it }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PatientRequestsScreen(
    onBack: () -> Unit,
    onNewRequest: () -> Unit,
    onOpenRequest: (String) -> Unit,
) {
    val session = currentSession()
    val viewModel: PatientRequestsViewModel = viewModel {
        PatientRequestsViewModel(session.uid, container().insulinRequestRepository)
    }
    val requests by viewModel.requests.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Insulin requests", onBack = onBack) { padding ->
        val list = requests
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.MedicalServices,
                    title = "No requests yet",
                    description = "Ask your doctor about your insulin dose and the request will appear here with its status.",
                    action = { PrimaryButton(text = "Ask the doctor", onClick = onNewRequest) },
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { PrimaryButton(text = "New request", onClick = onNewRequest) }
                items(list.size) { index ->
                    RequestRow(list[index]) { onOpenRequest(list[index].id) }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(request: InsulinRequest, onClick: () -> Unit) {
    val answered = request.status == RequestStatus.Responded
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(
                if (answered) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (answered) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                contentDescription = null,
                tint = if (answered) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                request.question.ifBlank { "Insulin advice request" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            Text(
                buildString {
                    append(request.status.label)
                    request.createdAt?.let { append(" · ").append(Format.relative(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
fun PatientRequestDetailScreen(requestId: String, onBack: () -> Unit) {
    val viewModel: RequestDetailViewModel = viewModel(key = "req_$requestId") {
        RequestDetailViewModel(requestId, container().insulinRequestRepository)
    }
    val request by viewModel.request.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Your request", onBack = onBack) { padding ->
        val current = request
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionHeader("Your question")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    current.question.ifBlank { "(no question text)" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            val response = current.response
            if (response == null) {
                SectionHeader("Status")
                Spacer(Modifier.height(8.dp))
                InlineMessage(
                    message = "Pending doctor review. ${current.doctorName} will reply after reviewing your records. Responses are not immediate.",
                    tone = MessageTone.Info,
                )
            } else {
                SectionHeader("Your doctor's reply")
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(response.text, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Sent by ${response.doctorName}" +
                                (response.submittedAt?.let { " · ${Format.dateTime(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Records attached")
            Spacer(Modifier.height(8.dp))
            ContextPreview(current.context)
            Spacer(Modifier.height(24.dp))
        }
    }
}

class RequestDetailViewModel(
    requestId: String,
    requestRepository: InsulinRequestRepository,
) : ViewModel() {
    val request: StateFlow<InsulinRequest?> = requestRepository.request(requestId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
