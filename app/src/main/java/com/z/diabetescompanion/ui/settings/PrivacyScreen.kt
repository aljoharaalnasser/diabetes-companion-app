package com.z.diabetescompanion.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Privacy information (NFR-08, NFR-10, FR-21): who can see the patient's data, how it is
 * protected, the retention promise and a deletion request. Patients also get a link to
 * the sharing controls.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onOpenSharing: (() -> Unit)?,
) {
    val session = currentSession()
    val isPatient = session.profile.role == UserRole.Patient
    val viewModel: PrivacyViewModel = viewModel { PrivacyViewModel(session.uid, container().careLinkRepository) }
    val links by viewModel.links.collectAsStateWithLifecycle()
    val deletion by viewModel.deletionRequest.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "Privacy", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (isPatient) {
                InfoCard(Icons.Rounded.Groups, "Who can see your records") {
                    val doctor = links.firstOrNull { it.memberRole == UserRole.Doctor && it.status == CareLinkStatus.Active }
                    val caregiver = links.firstOrNull { it.memberRole == UserRole.Caregiver && it.status == CareLinkStatus.Active }
                    Text("Assigned doctor: ${doctor?.memberName ?: "none"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Designated caregiver: ${caregiver?.memberName ?: "none"}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Sharing is off until you grant it, and you can revoke it at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (onOpenSharing != null) LinkButton(text = "Manage sharing", onClick = onOpenSharing)
                }
                Spacer(Modifier.height(16.dp))
            }

            InfoCard(Icons.Rounded.Lock, "Encryption") {
                Text(
                    "Data travels over TLS and is stored encrypted at rest by Firebase. Notifications on the lock screen never show medication names or readings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            InfoCard(Icons.Rounded.VerifiedUser, "Role-based access") {
                Text(
                    "Server-side rules check your role and your active sharing links on every request. A revoked caregiver is blocked on their very next request.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            InfoCard(Icons.Rounded.Schedule, "Retention") {
                Text(
                    "Identifiable project records are deleted within 90 days after the final project evaluation. Earlier deletion requests are completed within 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (isPatient) {
                Spacer(Modifier.height(16.dp))
                InfoCard(Icons.Rounded.DeleteForever, "Request data deletion") {
                    val request = deletion
                    if (request != null) {
                        val date = request.requestedAt?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(it) } ?: "just now"
                        InlineMessage(message = "Deletion requested on $date. Status: ${request.status}.", tone = MessageTone.Info)
                    } else {
                        Text(
                            "Asks the project team to delete your account and all records. You'll keep access until the request is processed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Request deletion of my data", onClick = { confirmDelete = true })
                    }
                }
            }

            InlineMessage(message = error, tone = MessageTone.Error, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(20.dp))
            PrototypeNotice()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Request deletion?") },
            text = { Text("The project team will delete your account and records within 30 days. This can't be undone once processed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.requestDeletion() }) { Text("Request deletion") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

class PrivacyViewModel(
    private val uid: String,
    private val careLinkRepository: CareLinkRepository,
) : ViewModel() {
    val error = MutableStateFlow<String?>(null)

    val links = careLinkRepository.linksForPatient(uid)
        .catch { error.value = it.toFirestoreMessage(); emit(emptyList<CareLink>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deletionRequest = careLinkRepository.deletionRequest(uid)
        .catch { emit(null as DeletionRequest?) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun requestDeletion() {
        viewModelScope.launch {
            try {
                careLinkRepository.requestDeletion(uid)
            } catch (e: Exception) {
                error.value = e.toFirestoreMessage()
            }
        }
    }
}
