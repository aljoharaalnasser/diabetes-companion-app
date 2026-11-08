package com.z.diabetescompanion.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import com.z.diabetescompanion.ui.admin.AdminAlertsScreen
import com.z.diabetescompanion.ui.admin.AdminAnalyticsScreen
import com.z.diabetescompanion.ui.admin.AdminOverviewScreen
import com.z.diabetescompanion.ui.admin.AdminUsersScreen
import com.z.diabetescompanion.ui.admin.AdminViewModel
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen

private object A {
    const val OVERVIEW = "admin/overview"
    const val USERS = "admin/users"
    const val ALERTS = "admin/alerts"
    const val ANALYTICS = "admin/analytics"
    const val MORE = "admin/more"
    const val PROFILE = "admin/profile"
    const val SETTINGS = "admin/settings"
    const val PRIVACY = "admin/privacy"
}

/**
 * Admin navigation (S3-01). One [AdminViewModel] is shared by all four tabs, so the
 * overview's counts and the lists below them are always the same snapshot of the data.
 */
@Composable
fun AdminShell() {
    val session = currentSession()
    val viewModel: AdminViewModel = viewModel {
        AdminViewModel(container().adminRepository, session.profile.displayName)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUid by viewModel.pendingUid.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    val tabs = listOf(
        ShellTab(A.OVERVIEW, "Overview", Icons.Rounded.Dashboard),
        ShellTab(A.USERS, "Users", Icons.Rounded.ManageAccounts),
        ShellTab(A.ALERTS, "Alerts", Icons.Rounded.NotificationsActive),
        ShellTab(A.ANALYTICS, "Analytics", Icons.Rounded.Insights),
        ShellTab(A.MORE, "More", Icons.Rounded.MoreHoriz),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        RoleShell(tabs = tabs, startRoute = A.OVERVIEW) { nav ->
            composable(A.OVERVIEW) { AdminOverviewScreen(state) }
            composable(A.USERS) {
                AdminUsersScreen(
                    state = state,
                    pendingUid = pendingUid,
                    onSetRole = viewModel::setRole,
                    onSetStatus = viewModel::setStatus,
                    onCompleteDeletion = viewModel::completeDeletionRequest,
                )
            }
            composable(A.ALERTS) { AdminAlertsScreen(state) }
            composable(A.ANALYTICS) { AdminAnalyticsScreen(state) }
            composable(A.MORE) {
                MoreScreen(
                    items = listOf(
                        MoreItem("Profile", "Your account details.", Icons.Rounded.PersonOutline) { nav.navigate(A.PROFILE) },
                        MoreItem("Settings", "Appearance, text size and notifications.", Icons.Rounded.Settings) { nav.navigate(A.SETTINGS) },
                        MoreItem("Privacy", "How data is protected.", Icons.Rounded.Shield) { nav.navigate(A.PRIVACY) },
                    )
                )
            }
            composable(A.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
            composable(A.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(A.PRIVACY) }) }
            composable(A.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = null) }
        }

        // Hosted above the shell so an account operation's result is visible on any tab.
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
