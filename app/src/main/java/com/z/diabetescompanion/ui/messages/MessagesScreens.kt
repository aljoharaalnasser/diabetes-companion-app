package com.z.diabetescompanion.ui.messages

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.Conversation
import com.z.diabetescompanion.data.model.Message
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.repository.MessageRepository
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

// ---------------------------------------------------------------------------
// Conversation list
// ---------------------------------------------------------------------------

data class ConversationsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val conversations: List<Conversation> = emptyList(),
)

class ConversationsViewModel(
    uid: String,
    messageRepository: MessageRepository,
) : ViewModel() {
    val uiState: StateFlow<ConversationsUiState> = messageRepository.conversationsFor(uid)
        .map { ConversationsUiState(isLoading = false, conversations = it) }
        .catch { emit(ConversationsUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationsUiState())
}

@Composable
fun ConversationsScreen(onOpenConversation: (String) -> Unit) {
    val session = currentSession()
    val viewModel: ConversationsViewModel = viewModel {
        ConversationsViewModel(session.uid, container().messageRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Messages") { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, Modifier.padding(padding))
            state.conversations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    title = "No conversations",
                    description = "Conversations with the care team appear here. A patient or caregiver starts one from an alert or from the doctor contact button.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.conversations.size) { index ->
                    ConversationRow(
                        conversation = state.conversations[index],
                        viewerUid = session.uid,
                        onClick = { onOpenConversation(state.conversations[index].id) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)); PrototypeNotice() }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, viewerUid: String, onClick: () -> Unit) {
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
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                conversation.otherPartyName(viewerUid).split(' ').filter { it.isNotBlank() }
                    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.otherPartyName(viewerUid), style = MaterialTheme.typography.titleSmall)
            Text(
                // Every row states whose records the thread concerns (FR-15).
                if (viewerUid == conversation.doctorUid)
                    "${conversation.memberRole.label} · about ${conversation.patientName}"
                else conversation.patientContext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (conversation.lastMessage.isNotBlank()) {
                Text(
                    conversation.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        conversation.lastMessageAt?.let {
            Text(
                Format.relative(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Chat thread
// ---------------------------------------------------------------------------

data class ChatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
)

class ChatViewModel(
    private val conversationId: String,
    private val viewer: UserProfile,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val sending = MutableStateFlow(false)
    private val localError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        messageRepository.conversation(conversationId),
        messageRepository.messages(conversationId),
        draft, sending, localError,
    ) { conversation, messages, draftText, isSending, error ->
        ChatUiState(
            isLoading = false,
            error = error,
            conversation = conversation,
            messages = messages,
            draft = draftText,
            isSending = isSending,
        )
    }
        .catch { emit(ChatUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onDraftChange(value: String) {
        localError.value = null
        draft.value = value.take(1000)
    }

    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty() || sending.value) return
        sending.value = true
        viewModelScope.launch {
            try {
                messageRepository.send(
                    conversationId,
                    Message(
                        id = UUID.randomUUID().toString(),
                        senderUid = viewer.uid,
                        senderName = viewer.displayName,
                        senderRole = viewer.role,
                        text = text,
                        sentAt = Date(),
                    ),
                )
                draft.value = ""
            } catch (e: Exception) {
                localError.value = e.toFirestoreMessage()
            } finally {
                sending.value = false
            }
        }
    }
}

@Composable
fun ChatScreen(conversationId: String, onBack: () -> Unit) {
    val session = currentSession()
    val viewModel: ChatViewModel = viewModel(key = "chat_$conversationId") {
        ChatViewModel(conversationId, session.profile, container().messageRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val title = state.conversation?.otherPartyName(session.uid) ?: "Conversation"

    ScreenScaffold(title = title, onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            state.conversation?.let { conversation ->
                Text(
                    text = if (session.uid == conversation.doctorUid)
                        "${conversation.memberRole.label} · about ${conversation.patientName}"
                    else conversation.patientContext,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            when {
                state.isLoading -> LoadingState()
                state.error != null && state.messages.isEmpty() -> ErrorState(state.error!!)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.AutoMirrored.Rounded.Chat,
                                title = "No messages yet",
                                description = "Write the first message. Replies are not immediate, and this is not an emergency channel.",
                            )
                        }
                    }
                    items(state.messages.size) { index ->
                        MessageBubble(state.messages[index], isMine = state.messages[index].senderUid == session.uid)
                    }
                }
            }

            InlineMessage(
                message = state.error.takeIf { state.messages.isNotEmpty() },
                tone = MessageTone.Error,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    AppTextField(
                        value = state.draft,
                        onValueChange = viewModel::onDraftChange,
                        label = "Message",
                        enabled = !state.isSending,
                        imeAction = ImeAction.Send,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = viewModel::send,
                        enabled = state.draft.isNotBlank() && !state.isSending,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (state.draft.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (isMine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!isMine) {
                Text(
                    "${message.senderName} · ${message.senderRole.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                Format.dateTime(message.sentAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
