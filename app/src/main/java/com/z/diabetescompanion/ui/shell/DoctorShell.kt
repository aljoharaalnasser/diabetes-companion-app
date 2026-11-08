package com.z.diabetescompanion.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
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
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import com.z.diabetescompanion.ui.doctor.patients.PatientDetailScreen
import com.z.diabetescompanion.ui.doctor.patients.PatientListScreen
import com.z.diabetescompanion.ui.doctor.patients.ThresholdsScreen
import com.z.diabetescompanion.ui.doctor.requests.DoctorRequestsScreen
import com.z.diabetescompanion.ui.doctor.requests.RequestReviewScreen
import com.z.diabetescompanion.ui.messages.ChatScreen
import com.z.diabetescompanion.ui.messages.ConversationLauncher
import com.z.diabetescompanion.ui.messages.ConversationNavViewModel
import com.z.diabetescompanion.ui.messages.ConversationsScreen
import com.z.diabetescompanion.ui.patient.glucose.GlucoseHistoryScreen
import com.z.diabetescompanion.ui.patient.medications.MedicationsScreen
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen
import com.z.diabetescompanion.ui.summaries.WeeklySummaryScreen

private object D {
    const val PATIENTS = "doctor/patients"
    const val ALERTS = "doctor/alerts"
    const val REQUESTS = "doctor/requests"
    const val MESSAGES = "doctor/messages"
    const val MORE = "doctor/more"

    const val PATIENT = "doctor/patient/{uid}"
    const val PATIENT_HISTORY = "doctor/patient/{uid}/history"
    const val PATIENT_MEDICATIONS = "doctor/patient/{uid}/medications"
    const val PATIENT_THRESHOLDS = "doctor/patient/{uid}/thresholds"
    const val PATIENT_SUMMARY = "doctor/patient/{uid}/summary"
    const val ALERT = "doctor/alert/{patientUid}/{alertId}"
    const val REVIEW = "doctor/review/{id}"
    const val CHAT = "doctor/chat/{conversationId}"
    const val PROFILE = "doctor/profile"
    const val SETTINGS = "doctor/settings"
    const val PRIVACY = "doctor/privacy"

    fun patient(uid: String) = "doctor/patient/$uid"
    fun alert(patientUid: String, alertId: String) = "doctor/alert/$patientUid/$alertId"
    fun review(id: String) = "doctor/review/$id"
    fun chat(conversationId: String) = "doctor/chat/$conversationId"
}

/** Doctor navigation per the design brief: Patients, Alerts, Requests, Messages, More. */
@Composable
fun DoctorShell() {
    val tabs = listOf(
        ShellTab(D.PATIENTS, "Patients", Icons.Rounded.Groups),
        ShellTab(D.ALERTS, "Alerts", Icons.Rounded.NotificationsActive),
        ShellTab(D.REQUESTS, "Requests", Icons.Rounded.Inbox),
        ShellTab(D.MESSAGES, "Messages", Icons.AutoMirrored.Rounded.Chat),
        ShellTab(D.MORE, "More", Icons.Rounded.MoreHoriz),
    )
    val session = currentSession()
    val uidArg = listOf(navArgument("uid") { type = NavType.StringType })
    val navController = rememberNavController()

    val conversationNav: ConversationNavViewModel = viewModel {
        val c = container()
        ConversationNavViewModel(ConversationLauncher(c.careLinkRepository, c.messageRepository))
    }
    val chatDestination by conversationNav.destination.collectAsStateWithLifecycle()
    LaunchedEffect(chatDestination) {
        chatDestination?.let {
            navController.navigate(D.chat(it))
            conversationNav.consumeDestination()
        }
    }

    RoleShell(tabs = tabs, startRoute = D.PATIENTS, navController = navController) { nav ->
        composable(D.PATIENTS) { PatientListScreen(onOpenPatient = { nav.navigate(D.patient(it)) }) }
        composable(D.ALERTS) {
            CareTeamAlertsScreen(onOpenAlert = { patientUid, alertId -> nav.navigate(D.alert(patientUid, alertId)) })
        }
        composable(D.REQUESTS) {
            DoctorRequestsScreen(onOpenRequest = { nav.navigate(D.review(it)) })
        }
        composable(D.MESSAGES) {
            ConversationsScreen(onOpenConversation = { nav.navigate(D.chat(it)) })
        }
        composable(D.MORE) {
            MoreScreen(
                items = listOf(
                    MoreItem("Profile", "Your name as patients see it.", Icons.Rounded.PersonOutline) { nav.navigate(D.PROFILE) },
                    MoreItem("Settings", "Appearance, text size and notifications.", Icons.Rounded.Settings) { nav.navigate(D.SETTINGS) },
                    MoreItem("Privacy", "How patient data is protected.", Icons.Rounded.Shield) { nav.navigate(D.PRIVACY) },
                )
            )
        }

        composable(D.PATIENT, arguments = uidArg) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            PatientDetailScreen(
                patientUid = uid,
                onBack = { nav.popBackStack() },
                onOpenHistory = { nav.navigate("doctor/patient/$uid/history") },
                onOpenMedications = { nav.navigate("doctor/patient/$uid/medications") },
                onOpenThresholds = { nav.navigate("doctor/patient/$uid/thresholds") },
                onOpenSummary = { nav.navigate("doctor/patient/$uid/summary") },
                onMessagePatient = { name -> conversationNav.openAsDoctor(session.profile, uid, name) },
            )
        }
        composable(D.PATIENT_HISTORY, arguments = uidArg) { entry ->
            GlucoseHistoryScreen(
                patientUid = entry.arguments?.getString("uid").orEmpty(),
                title = "Glucose history",
                readOnly = true,
                onBack = { nav.popBackStack() },
            )
        }
        composable(D.PATIENT_MEDICATIONS, arguments = uidArg) { entry ->
            MedicationsScreen(
                patientUid = entry.arguments?.getString("uid").orEmpty(),
                title = "Medication history",
                readOnly = true,
                onBack = { nav.popBackStack() },
            )
        }
        composable(D.PATIENT_THRESHOLDS, arguments = uidArg) { entry ->
            ThresholdsScreen(
                patientUid = entry.arguments?.getString("uid").orEmpty(),
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(D.PATIENT_SUMMARY, arguments = uidArg) { entry ->
            WeeklySummaryScreen(
                patientUid = entry.arguments?.getString("uid").orEmpty(),
                title = "Weekly summary",
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            D.ALERT,
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
                onMessagePatient = { conversationNav.openAsDoctor(session.profile, patientUid, "") },
            )
        }
        composable(D.REVIEW, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            RequestReviewScreen(
                requestId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onSent = { nav.popBackStack() },
            )
        }
        composable(D.CHAT, arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }

        composable(D.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(D.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(D.PRIVACY) }) }
        composable(D.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = null) }
    }
}
