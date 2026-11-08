package com.z.diabetescompanion.ui.doctor.requests

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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.ai.AiResult
import com.z.diabetescompanion.data.ai.InsulinDraftService
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.AiDraft
import com.z.diabetescompanion.data.model.DoctorResponse
import com.z.diabetescompanion.data.model.InsulinRequest
import com.z.diabetescompanion.data.model.RequestContext
import com.z.diabetescompanion.data.model.RequestStatus
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.repository.InsulinRequestRepository
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

// ---------------------------------------------------------------------------
// Inbox
// ---------------------------------------------------------------------------

class DoctorRequestsViewModel(
    doctorUid: String,
    requestRepository: InsulinRequestRepository,
) : ViewModel() {
    val requests: StateFlow<List<InsulinRequest>?> = requestRepository.requestsForDoctor(doctorUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun DoctorRequestsScreen(onOpenRequest: (String) -> Unit) {
    val session = currentSession()
    val viewModel: DoctorRequestsViewModel = viewModel {
        DoctorRequestsViewModel(session.uid, container().insulinRequestRepository)
    }
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    var showResponded by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "Insulin requests") { padding ->
        val all = requests
        when {
            all == null -> LoadingState(Modifier.padding(padding))
            all.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    title = "No requests",
                    description = "When a patient who shares with you asks about their insulin dose, the request arrives here.",
                )
            }
            else -> {
                val pending = all.filter { it.status == RequestStatus.Pending }
                val visible = if (showResponded) all else pending
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !showResponded,
                                onClick = { showResponded = false },
                                label = { Text("Pending (${pending.size})") },
                            )
                            FilterChip(
                                selected = showResponded,
                                onClick = { showResponded = true },
                                label = { Text("All (${all.size})") },
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.CheckCircle,
                                title = "Nothing pending",
                                description = "Every request has been answered.",
                            )
                        }
                    }
                    items(visible.size) { index ->
                        RequestRow(visible[index]) { onOpenRequest(visible[index].id) }
                    }
                    item { Spacer(Modifier.height(8.dp)); PrototypeNotice() }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(request: InsulinRequest, onClick: () -> Unit) {
    val pending = request.status == RequestStatus.Pending
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
                if (pending) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (pending) Icons.Rounded.Schedule else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (pending) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(request.patientName, style = MaterialTheme.typography.titleSmall)
            Text(
                request.question.ifBlank { "Insulin advice request" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                buildString {
                    append(request.status.label)
                    request.createdAt?.let { append(" · ").append(Format.relative(it)) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (pending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Doctor-only review (S2-07, S2-08)
// ---------------------------------------------------------------------------

data class ReviewUiState(
    val draftText: String = "",
    val notes: String = "",
    val isGenerating: Boolean = false,
    val isSending: Boolean = false,
    val generationError: String? = null,
    val sendError: String? = null,
    val usedDraft: Boolean = false,
    val wroteOwn: Boolean = false,
    val draftDiscarded: Boolean = false,
    val showConfirm: Boolean = false,
    val sent: Boolean = false,
) {
    val canSend: Boolean get() = draftText.isNotBlank() && !isSending
}

class RequestReviewViewModel(
    private val requestId: String,
    private val doctor: UserProfile,
    private val requestRepository: InsulinRequestRepository,
    private val draftService: InsulinDraftService,
) : ViewModel() {

    val request: StateFlow<InsulinRequest?> = requestRepository.request(requestId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val storedDraft: StateFlow<AiDraft?> = requestRepository.draft(requestId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState = _uiState.asStateFlow()

    val aiConfigured: Boolean get() = draftService.isConfigured

    /** Loads a stored draft into the editor once, so the doctor never loses their edits. */
    fun adoptStoredDraft(draft: AiDraft) {
        if (_uiState.value.draftText.isNotBlank() || _uiState.value.draftDiscarded) return
        if (!draft.failed) {
            _uiState.update { it.copy(draftText = draft.text, usedDraft = true) }
        }
    }

    fun generateDraft(question: String, context: RequestContext) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(isGenerating = true, generationError = null, draftDiscarded = false) }
        viewModelScope.launch {
            when (val result = draftService.draft(question, context)) {
                is AiResult.Success -> {
                    val draft = AiDraft(text = result.text, model = result.model)
                    runCatching { requestRepository.saveDraft(requestId, draft) }
                    _uiState.update {
                        it.copy(isGenerating = false, draftText = result.text, usedDraft = true, wroteOwn = false)
                    }
                }
                is AiResult.Error -> {
                    runCatching {
                        requestRepository.saveDraft(
                            requestId,
                            AiDraft(text = "", model = draftService.modelName, failed = true, failureMessage = result.message),
                        )
                    }
                    _uiState.update { it.copy(isGenerating = false, generationError = result.message) }
                }
            }
        }
    }

    fun onDraftChange(value: String) = _uiState.update { it.copy(draftText = value, sendError = null) }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value.take(500)) }

    /** Discard clears the draft but keeps the record that one existed. */
    fun discardDraft() = _uiState.update {
        it.copy(draftText = "", usedDraft = false, draftDiscarded = true, generationError = null)
    }

    fun writeOwn() = _uiState.update {
        it.copy(draftText = "", usedDraft = false, wroteOwn = true, draftDiscarded = true)
    }

    fun showConfirm(show: Boolean) = _uiState.update { it.copy(showConfirm = show) }

    /** Sending is always an explicit, separate action (FR-19). */
    fun send(request: InsulinRequest) {
        val state = _uiState.value
        if (!state.canSend) return
        val body = buildString {
            append(state.draftText.trim())
            if (state.notes.isNotBlank()) {
                append("\n\n")
                append(state.notes.trim())
            }
        }
        _uiState.update { it.copy(isSending = true, sendError = null, showConfirm = false) }
        viewModelScope.launch {
            try {
                requestRepository.submitResponse(
                    requestId,
                    request.copy(
                        status = RequestStatus.Responded,
                        response = DoctorResponse(
                            text = body,
                            doctorUid = doctor.uid,
                            doctorName = doctor.displayName,
                            usedDraft = state.usedDraft,
                            submittedAt = Date(),
                        ),
                    ),
                )
                _uiState.update { it.copy(isSending = false, sent = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.toFirestoreMessage()) }
            }
        }
    }
}

@Composable
fun RequestReviewScreen(requestId: String, onBack: () -> Unit, onSent: () -> Unit) {
    val session = currentSession()
    val viewModel: RequestReviewViewModel = viewModel(key = "review_$requestId") {
        val c = container()
        RequestReviewViewModel(requestId, session.profile, c.insulinRequestRepository, c.insulinDraftService)
    }
    val request by viewModel.request.collectAsStateWithLifecycle()
    val storedDraft by viewModel.storedDraft.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    storedDraft?.let { LaunchedEffect(it.generatedAt, it.text) { viewModel.adoptStoredDraft(it) } }
    if (state.sent) LaunchedEffect(Unit) { onSent() }

    ScreenScaffold(title = "Review request", onBack = onBack) { padding ->
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
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionHeader("${current.patientName} asked")
            Spacer(Modifier.height(8.dp))
            PlainCard { Text(current.question.ifBlank { "(no question text)" }, style = MaterialTheme.typography.bodyMedium) }

            Spacer(Modifier.height(18.dp))
            SectionHeader("Patient records")
            Spacer(Modifier.height(8.dp))
            RecordsCard(current.context)

            if (current.context.missingData.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                MissingDataCard(current.context.missingData)
            }

            Spacer(Modifier.height(18.dp))

            if (current.status == RequestStatus.Responded) {
                SectionHeader("Response sent")
                Spacer(Modifier.height(8.dp))
                PlainCard {
                    Column {
                        Text(current.response?.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sent by ${current.response?.doctorName}" +
                                (current.response?.submittedAt?.let { " · ${Format.dateTime(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            DraftSection(
                state = state,
                aiConfigured = viewModel.aiConfigured,
                onGenerate = { viewModel.generateDraft(current.question, current.context) },
                onDraftChange = viewModel::onDraftChange,
                onDiscard = viewModel::discardDraft,
                onWriteOwn = viewModel::writeOwn,
            )

            Spacer(Modifier.height(16.dp))
            SectionHeader("Additional notes")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Appended to the end of your reply (optional)") },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            InlineMessage(message = state.sendError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 10.dp))
            PrimaryButton(
                text = "Send reviewed response",
                onClick = { viewModel.showConfirm(true) },
                loading = state.isSending,
                enabled = state.canSend,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "The patient sees only what you send. They never see the draft.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showConfirm) {
        val current = request
        AlertDialog(
            onDismissRequest = { viewModel.showConfirm(false) },
            title = { Text("Send this response?") },
            text = {
                Text(
                    "It will be sent to ${current?.patientName.orEmpty()} under your name, with the time recorded. " +
                        "You cannot edit it afterwards."
                )
            },
            confirmButton = {
                TextButton(onClick = { current?.let(viewModel::send) }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { viewModel.showConfirm(false) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DraftSection(
    state: ReviewUiState,
    aiConfigured: Boolean,
    onGenerate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onDiscard: () -> Unit,
    onWriteOwn: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "AI-generated draft — doctor review required",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Visible only to you. Edit it, replace it, or write your own; nothing reaches the patient until you send it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        when {
            !aiConfigured -> InlineMessage(
                message = "AI drafting is not configured, so write the response yourself below. Add OPENAI_API_KEY to .env to enable drafts.",
                tone = MessageTone.Warning,
            )
            state.generationError != null -> InlineMessage(
                message = "${state.generationError} You can still write the response yourself below.",
                tone = MessageTone.Warning,
            )
        }

        if (state.draftText.isBlank() && !state.wroteOwn) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onGenerate,
                enabled = aiConfigured && !state.isGenerating,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Preparing draft", Modifier.padding(start = 10.dp))
                } else {
                    Icon(
                        if (state.generationError != null) Icons.Rounded.Refresh else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                    )
                    Text(
                        if (state.generationError != null) "Try the draft again" else "Prepare AI draft",
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onWriteOwn,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Write my own response") }
        }

        if (state.draftText.isNotBlank() || state.wroteOwn) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.draftText,
                onValueChange = onDraftChange,
                label = {
                    Text(if (state.usedDraft) "Draft (editable)" else "Your response")
                },
                minLines = 6,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDiscard,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) { Text("Discard") }
                OutlinedButton(
                    onClick = onGenerate,
                    enabled = aiConfigured && !state.isGenerating,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) { Text("Regenerate") }
            }
        }
    }
}

@Composable
private fun PlainCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun RecordsCard(context: RequestContext) {
    PlainCard {
        Column {
            Text("Age: ${context.age?.toString() ?: "not recorded"}", style = MaterialTheme.typography.bodyMedium)
            Text("Type: ${context.diabetesType.ifBlank { "not recorded" }}", style = MaterialTheme.typography.bodyMedium)
            Text("History: ${context.relevantHistory.ifBlank { "not recorded" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Care plan: ${context.carePlan.ifBlank { "not recorded" }}", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(10.dp))
            Text("Recent readings", style = MaterialTheme.typography.labelLarge)
            if (context.recentReadings.isEmpty()) {
                Text("None recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                context.recentReadings.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Recent doses", style = MaterialTheme.typography.labelLarge)
            if (context.recentDoses.isEmpty()) {
                Text("None recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                context.recentDoses.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Gaps are shown before any draft so they are never glossed over (FR-18, NFR-15). */
@Composable
private fun MissingDataCard(missing: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Missing information",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            missing.forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
