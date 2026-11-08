package com.z.diabetescompanion.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.z.diabetescompanion.ui.common.PlaceholderScreen
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen

private object C {
    const val HOME = "caregiver/home"
    const val ALERTS = "caregiver/alerts"
    const val MESSAGES = "caregiver/messages"
    const val MORE = "caregiver/more"
    const val PROFILE = "caregiver/profile"
    const val SETTINGS = "caregiver/settings"
    const val PRIVACY = "caregiver/privacy"
}

/** Caregiver navigation: Home, Alerts, Messages, More. Feature screens land in Sprint 2 (S2-02). */
@Composable
fun CaregiverShell() {
    val tabs = listOf(
        ShellTab(C.HOME, "Home", Icons.Rounded.Home),
        ShellTab(C.ALERTS, "Alerts", Icons.Rounded.NotificationsActive),
        ShellTab(C.MESSAGES, "Messages", Icons.AutoMirrored.Rounded.Chat),
        ShellTab(C.MORE, "More", Icons.Rounded.MoreHoriz),
    )
    RoleShell(tabs = tabs, startRoute = C.HOME) { nav ->
        composable(C.HOME) {
            PlaceholderScreen("Linked patient", "Current glucose status and medication history for the patient who granted you access.", sprint = 2, icon = Icons.Rounded.Home)
        }
        composable(C.ALERTS) {
            PlaceholderScreen("Alerts", "Escalated alerts with Acknowledge, Contact Doctor and Record Resolution actions.", sprint = 2, icon = Icons.Rounded.NotificationsActive)
        }
        composable(C.MESSAGES) {
            PlaceholderScreen("Messages", "Conversation with the assigned doctor.", sprint = 2, icon = Icons.AutoMirrored.Rounded.Chat)
        }
        composable(C.MORE) {
            MoreScreen(
                items = listOf(
                    MoreItem("Profile", "Your name as patients see it.", Icons.Rounded.PersonOutline) { nav.navigate(C.PROFILE) },
                    MoreItem("Settings", "Text size and notifications.", Icons.Rounded.Settings) { nav.navigate(C.SETTINGS) },
                    MoreItem("Privacy", "How patient data is protected.", Icons.Rounded.Shield) { nav.navigate(C.PRIVACY) },
                )
            )
        }
        composable(C.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(C.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(C.PRIVACY) }) }
        composable(C.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = null) }
    }
}
