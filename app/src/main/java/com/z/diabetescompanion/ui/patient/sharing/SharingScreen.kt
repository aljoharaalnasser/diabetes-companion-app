package com.z.diabetescompanion.ui.patient.sharing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.UserRepository
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirectoryState(
    val role: UserRole,
    val isLoading: Boolean = true,
    val people: List<UserProfile> = emptyList(),
    val error: String? = null,
)

/**
 * Consent controls (S1-02 / FR-21): grant or revoke the assigned doctor and the
 * designated caregiver. One active link per role at a time keeps the model simple.
 */
class SharingViewModel(
    private val patient: UserProfile,
    private val careLinkRepository: CareLinkRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val links = careLinkRepository.linksForPatient(patient.uid)
        .catch { _error.value = it.toFirestoreMessage(); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _directory = MutableStateFlow<DirectoryState?>(null)
    val directory = _directory.asStateFlow()

    fun openDirectory(role: UserRole) {
        _directory.value = DirectoryState(role)
        viewModelScope.launch {
            try {
                val people = userRepository.listByRole(role)
                _directory.update { it?.copy(isLoading = false, people = people) }
            } catch (e: Exception) {
                _directory.update { it?.copy(isLoading = false, error = e.toFirestoreMessage()) }
            }
        }
    }

    fun closeDirectory() { _directory.value = null }

    fun grant(member: UserProfile) {
        _directory.value = null
        viewModelScope.launch {
            try {
                careLinkRepository.grant(
                    CareLink(
                        id = CareLink.idFor(patient.uid, member.uid),
                        patientUid = patient.uid,
                        patientName = patient.displayName,
                        memberUid = member.uid,
                        memberName = member.displayName,
                        memberRole = member.role,
                        status = CareLinkStatus.Active,
                    )
                )
            } catch (e: Exception) {
                _error.value = e.toFirestoreMessage()
            }
        }
    }

    fun revoke(link: CareLink) {
        viewModelScope.launch {
            try {
                careLinkRepository.revoke(link.id)
            } catch (e: Exception) {
                _error.value = e.toFirestoreMessage()
            }
        }
    }
}

@Composable
fun SharingScreen(onBack: () -> Unit) {
    val session = currentSession()
    val viewModel: SharingViewModel = viewModel {
        val c = container()
        SharingViewModel(session.profile, c.careLinkRepository, c.userRepository)
    }
    val links by viewModel.links.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val directory by viewModel.directory.collectAsStateWithLifecycle()
    var pendingRevoke by remember { mutableStateOf<CareLink?>(null) }

    ScreenScaffold(title = "Care-team sharing", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "Choose who can see your glucose readings, medication history and profile. Nothing is shared until you grant access, and you can revoke it at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            RoleCard(
                role = UserRole.Doctor,
                icon = Icons.Rounded.MedicalServices,
                title = "Assigned doctor",
                description = "Can view your records and set your target and critical glucose thresholds.",
                links = links,
                onGrant = { viewModel.openDirectory(UserRole.Doctor) },
                onRevoke = { pendingRevoke = it },
            )
            Spacer(Modifier.height(16.dp))
            RoleCard(
                role = UserRole.Caregiver,
                icon = Icons.Rounded.Groups,
                title = "Designated caregiver",
                description = "Can view your records and will receive escalated alerts (from Sprint 2).",
                links = links,
                onGrant = { viewModel.openDirectory(UserRole.Caregiver) },
                onRevoke = { pendingRevoke = it },
            )

            InlineMessage(message = error, tone = MessageTone.Error, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(20.dp))
            PrototypeNotice()
            Spacer(Modifier.height(24.dp))
        }
    }

    directory?.let { dir ->
        AlertDialog(
            onDismissRequest = viewModel::closeDirectory,
            title = { Text("Choose a ${dir.role.label.lowercase()}") },
            text = {
                when {
                    dir.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading directory")
                    }
                    dir.error != null -> Text(dir.error)
                    dir.people.isEmpty() -> Text("No ${dir.role.label.lowercase()} accounts exist yet. Ask them to register first.")
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(dir.people.size) { index ->
                            val person = dir.people[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.grant(person) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(person.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeDirectory) { Text("Cancel") } },
        )
    }

    pendingRevoke?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text("Revoke access?") },
            text = { Text("${link.memberName} will lose access to your records immediately. Their next request to the server will be blocked.") },
            confirmButton = { TextButton(onClick = { viewModel.revoke(link); pendingRevoke = null }) { Text("Revoke access") } },
            dismissButton = { TextButton(onClick = { pendingRevoke = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoleCard(
    role: UserRole,
    icon: ImageVector,
    title: String,
    description: String,
    links: List<CareLink>,
    onGrant: () -> Unit,
    onRevoke: (CareLink) -> Unit,
) {
    val active = links.firstOrNull { it.memberRole == role && it.status == CareLinkStatus.Active }
    val revoked = links.filter { it.memberRole == role && it.status == CareLinkStatus.Revoked }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            if (active != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(active.memberName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Sharing on" + (active.grantedAt?.let { " since ${Format.dateFull(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Revoke access", onClick = { onRevoke(active) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Not shared", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Grant access", onClick = onGrant)
            }
            if (revoked.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Previously revoked: ${revoked.joinToString { it.memberName }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

