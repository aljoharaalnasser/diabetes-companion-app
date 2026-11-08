package com.z.diabetescompanion.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Summarize
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
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import com.z.diabetescompanion.ui.messages.ChatScreen
import com.z.diabetescompanion.ui.messages.ConversationLauncher
import com.z.diabetescompanion.ui.messages.ConversationNavViewModel
import com.z.diabetescompanion.ui.messages.ConversationsScreen
import com.z.diabetescompanion.ui.patient.chatbot.ChatbotScreen
import com.z.diabetescompanion.ui.patient.glucose.AddGlucoseScreen
import com.z.diabetescompanion.ui.patient.glucose.GlucoseHistoryScreen
import com.z.diabetescompanion.ui.patient.home.PatientHomeScreen
import com.z.diabetescompanion.ui.patient.medications.AddMedicationScreen
import com.z.diabetescompanion.ui.patient.medications.MedicationsScreen
import com.z.diabetescompanion.ui.patient.medications.ReminderEditScreen
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.patient.requests.NewInsulinRequestScreen
import com.z.diabetescompanion.ui.patient.requests.PatientRequestDetailScreen
import com.z.diabetescompanion.ui.patient.requests.PatientRequestsScreen
import com.z.diabetescompanion.ui.patient.sharing.SharingScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen
import com.z.diabetescompanion.ui.summaries.WeeklySummaryScreen

private object P {
    const val HOME = "patient/home"
    const val HISTORY = "patient/history"
    const val MEDICATIONS = "patient/medications"
    const val MESSAGES = "patient/messages"
    const val MORE = "patient/more"

    const val ADD_GLUCOSE = "patient/glucose/add"
    const val ADD_MEDICATION = "patient/medications/add"
    const val REMINDERS = "patient/reminders"
    const val REMINDER_NEW = "patient/reminders/new"
    const val REMINDER_EDIT = "patient/reminders/{id}"
    const val PROFILE = "patient/profile"
    const val SHARING = "patient/sharing"
    const val SETTINGS = "patient/settings"
    const val PRIVACY = "patient/privacy"
    const val SUMMARIES = "patient/summaries"
    const val CHATBOT = "patient/chatbot"
    const val REQUESTS = "patient/requests"
    const val REQUEST_NEW = "patient/requests/new"
    const val REQUEST_DETAIL = "patient/requests/{id}"
    const val ALERT = "patient/alert/{alertId}"
    const val CHAT = "patient/chat/{conversationId}"

    fun alert(alertId: String) = "patient/alert/$alertId"
    fun chat(conversationId: String) = "patient/chat/$conversationId"
    fun request(id: String) = "patient/requests/$id"
}

/** Patient navigation per the design brief: Home, History, Medications, Messages, More. */
@Composable
fun PatientShell() {
    val tabs = listOf(
        ShellTab(P.HOME, "Home", Icons.Rounded.Home),
        ShellTab(P.HISTORY, "History", Icons.AutoMirrored.Rounded.ShowChart),
        ShellTab(P.MEDICATIONS, "Medications", Icons.Rounded.Medication),
        ShellTab(P.MESSAGES, "Messages", Icons.AutoMirrored.Rounded.Chat),
        ShellTab(P.MORE, "More", Icons.Rounded.MoreHoriz),
    )
    val session = currentSession()
    val navController = rememberNavController()

    // "Contact Doctor" has to resolve the assigned doctor before it can navigate.
    val conversationNav: ConversationNavViewModel = viewModel {
        val c = container()
        ConversationNavViewModel(ConversationLauncher(c.careLinkRepository, c.messageRepository))
    }
    val chatDestination by conversationNav.destination.collectAsStateWithLifecycle()
    LaunchedEffect(chatDestination) {
        chatDestination?.let {
            navController.navigate(P.chat(it))
            conversationNav.consumeDestination()
        }
    }

    RoleShell(tabs = tabs, startRoute = P.HOME, navController = navController) { nav ->
        composable(P.HOME) {
            PatientHomeScreen(
                onAddGlucose = { nav.navigate(P.ADD_GLUCOSE) },
                onLogMedication = { nav.navigate(P.ADD_MEDICATION) },
                onOpenHistory = { nav.navigate(P.HISTORY) { launchSingleTop = true } },
                onOpenReminders = { nav.navigate(P.REMINDERS) },
                onAskDoctor = { nav.navigate(P.REQUESTS) },
                onOpenChatbot = { nav.navigate(P.CHATBOT) },
                onOpenAlert = { nav.navigate(P.alert(it)) },
            )
        }
        composable(P.HISTORY) {
            GlucoseHistoryScreen(patientUid = session.uid, onAdd = { nav.navigate(P.ADD_GLUCOSE) })
        }
        composable(P.MEDICATIONS) {
            MedicationsScreen(
                patientUid = session.uid,
                onAddLog = { nav.navigate(P.ADD_MEDICATION) },
                onAddReminder = { nav.navigate(P.REMINDER_NEW) },
                onEditReminder = { nav.navigate("patient/reminders/${it.id}") },
            )
        }
        composable(P.MESSAGES) {
            ConversationsScreen(onOpenConversation = { nav.navigate(P.chat(it)) })
        }
        composable(P.MORE) {
            MoreScreen(
                items = listOf(
                    MoreItem("Weekly summaries", "Glucose and medication overview by week.", Icons.Rounded.Summarize) { nav.navigate(P.SUMMARIES) },
                    MoreItem("Insulin requests", "Ask your doctor and read their reply.", Icons.Rounded.MedicalServices) { nav.navigate(P.REQUESTS) },
                    MoreItem("Care-team sharing", "Grant or revoke access for your doctor and caregiver.", Icons.Rounded.Groups) { nav.navigate(P.SHARING) },
                    MoreItem("Profile", "Age, diabetes type, history and care plan.", Icons.Rounded.PersonOutline) { nav.navigate(P.PROFILE) },
                    MoreItem("Settings", "Text size, notifications and privacy.", Icons.Rounded.Settings) { nav.navigate(P.SETTINGS) },
                    MoreItem("Privacy", "How your data is protected and how to request deletion.", Icons.Rounded.Shield) { nav.navigate(P.PRIVACY) },
                )
            )
        }

        composable(P.ADD_GLUCOSE) {
            AddGlucoseScreen(
                onBack = { nav.popBackStack() },
                onOpenHistory = { nav.navigate(P.HISTORY) { popUpTo(P.HOME); launchSingleTop = true } },
                onOpenAlert = { alertId ->
                    nav.navigate(P.alert(alertId)) { popUpTo(P.HOME) }
                },
            )
        }
        composable(P.ADD_MEDICATION) {
            AddMedicationScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
        }
        composable(P.REMINDERS) {
            MedicationsScreen(
                patientUid = session.uid,
                title = "Reminders",
                initialTab = 1,
                onBack = { nav.popBackStack() },
                onAddLog = { nav.navigate(P.ADD_MEDICATION) },
                onAddReminder = { nav.navigate(P.REMINDER_NEW) },
                onEditReminder = { nav.navigate("patient/reminders/${it.id}") },
            )
        }
        composable(P.REMINDER_NEW) {
            ReminderEditScreen(reminderId = null, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
        }
        composable(P.REMINDER_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ReminderEditScreen(
                reminderId = entry.arguments?.getString("id"),
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }

        composable(P.ALERT, arguments = listOf(navArgument("alertId") { type = NavType.StringType })) { entry ->
            AlertDetailScreen(
                patientUid = session.uid,
                alertId = entry.arguments?.getString("alertId").orEmpty(),
                onBack = { nav.popBackStack() },
                onContactDoctor = { conversationNav.openWithDoctor(session.profile) },
            )
        }
        composable(P.CHAT, arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }

        composable(P.CHATBOT) {
            ChatbotScreen(
                onBack = { nav.popBackStack() },
                onAskDoctor = { nav.navigate(P.REQUEST_NEW) },
            )
        }
        composable(P.REQUESTS) {
            PatientRequestsScreen(
                onBack = { nav.popBackStack() },
                onNewRequest = { nav.navigate(P.REQUEST_NEW) },
                onOpenRequest = { nav.navigate(P.request(it)) },
            )
        }
        composable(P.REQUEST_NEW) {
            NewInsulinRequestScreen(onBack = { nav.popBackStack() }, onSubmitted = { nav.popBackStack() })
        }
        composable(P.REQUEST_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            PatientRequestDetailScreen(
                requestId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }

        composable(P.SUMMARIES) {
            WeeklySummaryScreen(patientUid = session.uid, onBack = { nav.popBackStack() })
        }
        composable(P.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(P.SHARING) { SharingScreen(onBack = { nav.popBackStack() }) }
        composable(P.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(P.PRIVACY) })
        }
        composable(P.PRIVACY) {
            PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = { nav.navigate(P.SHARING) })
        }
    }
}
