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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.z.diabetescompanion.ui.alerts.AlertDetailScreen
import com.z.diabetescompanion.ui.alerts.CareTeamAlertsScreen
import com.z.diabetescompanion.ui.caregiver.CaregiverHomeScreen
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import com.z.diabetescompanion.ui.messages.ChatScreen
import com.z.diabetescompanion.ui.messages.ConversationLauncher
import com.z.diabetescompanion.ui.messages.ConversationNavViewModel
import com.z.diabetescompanion.ui.messages.ConversationsScreen
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen

private object C {
    const val HOME = "caregiver/home"
    const val ALERTS = "caregiver/alerts"
    const val MESSAGES = "caregiver/messages"
    const val MORE = "caregiver/more"
    const val ALERT = "caregiver/alert/{patientUid}/{alertId}"
    const val CHAT = "caregiver/chat/{conversationId}"
    const val PROFILE = "caregiver/profile"
    const val SETTINGS = "caregiver/settings"
    const val PRIVACY = "caregiver/privacy"

    fun alert(patientUid: String, alertId: String) = "caregiver/alert/$patientUid/$alertId"
    fun chat(conversationId: String) = "caregiver/chat/$conversationId"
}

/** Caregiver navigation per the design brief: Home, Alerts, Messages, More (FR-13). */
@Composable
fun CaregiverShell() {
    val tabs = listOf(
        ShellTab(C.HOME, "Home", Icons.Rounded.Home),
        ShellTab(C.ALERTS, "Alerts", Icons.Rounded.NotificationsActive),
        ShellTab(C.MESSAGES, "Messages", Icons.AutoMirrored.Rounded.Chat),
        ShellTab(C.MORE, "More", Icons.Rounded.MoreHoriz),
    )
    val session = currentSession()
    val navController = rememberNavController()

    val conversationNav: ConversationNavViewModel = viewModel {
        val c = container()
        ConversationNavViewModel(ConversationLauncher(c.careLinkRepository, c.messageRepository))
    }
    val chatDestination by conversationNav.destination.collectAsStateWithLifecycle()
    LaunchedEffect(chatDestination) {
        chatDestination?.let {
            navController.navigate(C.chat(it))
            conversationNav.consumeDestination()
        }
    }

    RoleShell(tabs = tabs, startRoute = C.HOME, navController = navController) { nav ->
        composable(C.HOME) {
            CaregiverHomeScreen(
                onOpenAlert = { patientUid, alertId -> nav.navigate(C.alert(patientUid, alertId)) },
                onMessageDoctor = { linked ->
                    conversationNav.openAsCaregiver(
                        session.profile, linked.link.patientUid, linked.link.patientName,
                    )
                },
            )
        }
        composable(C.ALERTS) {
            CareTeamAlertsScreen(onOpenAlert = { patientUid, alertId -> nav.navigate(C.alert(patientUid, alertId)) })
        }
        composable(C.MESSAGES) {
            ConversationsScreen(onOpenConversation = { nav.navigate(C.chat(it)) })
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

        composable(
            C.ALERT,
            arguments = listOf(
                navArgument("patientUid") { type = NavType.StringType },
                navArgument("alertId") { type = NavType.StringType },
            ),
        ) { entry ->
            val patientUid = entry.arguments?.getString("patientUid").orEmpty()
            AlertDetailScreen(
                patientUid = patientUid,
                alertId = entry.arguments?.getString("alertId").orEmpty(),
                onBack = { nav.popBackStack() },
                onContactDoctor = {},
                onMessagePatient = {
                    conversationNav.openAsCaregiver(session.profile, patientUid, "")
                },
            )
        }
        composable(C.CHAT, arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }

        composable(C.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(C.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(C.PRIVACY) }) }
        composable(C.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = null) }
    }
}
