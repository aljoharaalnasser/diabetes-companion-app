package com.z.diabetescompanion.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.components.icon
import com.z.diabetescompanion.ui.currentSession

/**
 * Account management (FR-14, S3-01): search and filter every account, change a role,
 * suspend or reactivate an account, and record that a deletion request was carried out.
 *
 * Each of these writes is admin-only in the Security Rules, so the controls are not the
 * protection; they are the interface to a permission the server grants to this role alone.
 */
@Composable
fun AdminUsersScreen(
    state: AdminUiState,
    pendingUid: String?,
    onSetRole: (ManagedAccount, UserRole) -> Unit,
    onSetStatus: (ManagedAccount, AccountStatus) -> Unit,
    onCompleteDeletion: (DeletionRequest) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AccountFilter.All) }
    val session = currentSession()

    val visible = remember(state.accounts, query, filter) {
        val trimmed = query.trim()
        state.accounts.filter { filter.matches(it) }.filter { account ->
            trimmed.isBlank() ||
                account.profile.displayName.contains(trimmed, ignoreCase = true) ||
                account.profile.email.contains(trimmed, ignoreCase = true)
        }
    }
    val pendingDeletions = state.deletionRequests.filter { it.isPending }

    ScreenScaffold(title = "Users") { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search name or email") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AccountFilter.entries.forEach { option ->
                            FilterChip(
                                selected = option == filter,
                                onClick = { filter = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }

                if (pendingDeletions.isNotEmpty()) {
                    item { SectionHeader("Deletion requests") }
                    items(pendingDeletions, key = { "deletion_${it.uid}" }) { request ->
                        DeletionRequestCard(
                            request = request,
                            name = state.accounts.firstOrNull { it.profile.uid == request.uid }
                                ?.profile?.displayName ?: request.uid,
                            isBusy = pendingUid == request.uid,
                            onComplete = { onCompleteDeletion(request) },
                        )
                    }
                }

                item {
                    SectionHeader("${visible.size} of ${state.accounts.size} accounts")
                }

                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.ManageAccounts,
                            title = "No matching accounts",
                            description = "Clear the search or pick a different filter.",
                        )
                    }
                } else {
                    items(visible, key = { it.profile.uid }) { account ->
                        AccountCard(
                            account = account,
                            isSelf = account.profile.uid == session.uid,
                            isBusy = pendingUid == account.profile.uid,
                            onSetRole = { onSetRole(account, it) },
                            onSetStatus = { onSetStatus(account, it) },
                        )
                    }
                }

                item {
                    InlineMessage(
                        message = "Roles and suspension are enforced by the Security Rules. " +
                            "Suspension takes effect when the account next signs in or its " +
                            "profile next updates, and an account can never change its own role.",
                        tone = MessageTone.Info,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: ManagedAccount,
    isSelf: Boolean,
    isBusy: Boolean,
    onSetRole: (UserRole) -> Unit,
    onSetStatus: (AccountStatus) -> Unit,
) {
    val profile = account.profile
    var roleMenuOpen by remember { mutableStateOf(false) }
    var confirmRole by remember { mutableStateOf<UserRole?>(null) }
    var confirmSuspend by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (profile.isSuspended) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primaryContainer,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.isSuspended) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.displayName.ifBlank { "Unnamed account" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.email.ifBlank { profile.uid },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (profile.isSuspended) StatusBadge("Suspended", accent = true)
            }

            Spacer(Modifier.height(12.dp))
            DetailRow("Role", profile.role.label)
            if (profile.role == UserRole.Patient) {
                DetailRow("Care team", account.careTeamSummary)
            }
            profile.createdAt?.let { DetailRow("Registered", Format.dateFull(it)) }

            Spacer(Modifier.height(8.dp))
            if (isSelf) {
                Text(
                    "This is your own account. An administrator cannot change their own role " +
                        "or suspend themselves, so the platform can never be locked out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Saving…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TextButton(onClick = { roleMenuOpen = true }) {
                            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Change role")
                        }
                        DropdownMenu(
                            expanded = roleMenuOpen,
                            onDismissRequest = { roleMenuOpen = false },
                        ) {
                            UserRole.entries.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.label) },
                                    leadingIcon = {
                                        Icon(
                                            role.icon(),
                                            contentDescription = null,
                                            tint = if (role == profile.role) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingIcon = {
                                        if (role == profile.role) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = "Current role",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        roleMenuOpen = false
                                        if (role != profile.role) confirmRole = role
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = { confirmSuspend = true }) {
                        Icon(
                            if (profile.isSuspended) Icons.Rounded.Check else Icons.Rounded.PersonOff,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (profile.isSuspended) "Reactivate" else "Suspend")
                    }
                }
            }
        }
    }

    confirmRole?.let { role ->
        AlertDialog(
            onDismissRequest = { confirmRole = null },
            icon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
            title = { Text("Change role to ${role.label}?") },
            text = {
                Text(
                    "${profile.displayName} is a ${profile.role.label}. Changing the role changes " +
                        "which screens they see and what the server lets them read. Records " +
                        "already linked to this account stay where they are." +
                        if (role == UserRole.Admin) "\n\nAn administrator can manage every account on the platform." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRole = null; onSetRole(role) }) { Text("Change role") }
            },
            dismissButton = { TextButton(onClick = { confirmRole = null }) { Text("Cancel") } },
        )
    }

    if (confirmSuspend) {
        val reactivating = profile.isSuspended
        AlertDialog(
            onDismissRequest = { confirmSuspend = false },
            icon = { Icon(Icons.Rounded.PersonOff, contentDescription = null) },
            title = { Text(if (reactivating) "Reactivate this account?" else "Suspend this account?") },
            text = {
                Text(
                    if (reactivating) "${profile.displayName} will be able to sign in again."
                    else "${profile.displayName} will be signed out and refused at sign-in until " +
                        "an administrator reactivates them. Their records are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSuspend = false
                    onSetStatus(if (reactivating) AccountStatus.Active else AccountStatus.Suspended)
                }) { Text(if (reactivating) "Reactivate" else "Suspend") }
            },
            dismissButton = { TextButton(onClick = { confirmSuspend = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeletionRequestCard(
    request: DeletionRequest,
    name: String,
    isBusy: Boolean,
    onComplete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        request.requestedAt?.let { "Requested ${Format.dateFull(it)}" } ?: "Requested recently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge("Pending", accent = true)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The project team deletes the records, then records completion here. Target is " +
                    "30 days from the request, with backups expiring within a further 30 (NFR-10).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isBusy) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { confirm = true }) { Text("Mark as completed") }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Record this deletion as completed?") },
            text = {
                Text(
                    "Confirm only once the records have actually been deleted. The completion is " +
                        "stored with your name and the date as the evidence for the retention check."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; onComplete() }) { Text("Mark completed") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusBadge(label: String, accent: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (accent) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
