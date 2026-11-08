package com.z.diabetescompanion.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.z.diabetescompanion.ui.common.PlaceholderScreen
import com.z.diabetescompanion.ui.currentSession
import com.z.diabetescompanion.ui.patient.glucose.AddGlucoseScreen
import com.z.diabetescompanion.ui.patient.glucose.GlucoseHistoryScreen
import com.z.diabetescompanion.ui.patient.home.PatientHomeScreen
import com.z.diabetescompanion.ui.patient.medications.AddMedicationScreen
import com.z.diabetescompanion.ui.patient.medications.MedicationsScreen
import com.z.diabetescompanion.ui.patient.medications.ReminderEditScreen
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.patient.sharing.SharingScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen

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
    const val INSULIN_REQUEST = "patient/insulin_request"
    const val CHATBOT = "patient/chatbot"
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

    RoleShell(tabs = tabs, startRoute = P.HOME) { nav ->
        composable(P.HOME) {
            PatientHomeScreen(
                onAddGlucose = { nav.navigate(P.ADD_GLUCOSE) },
                onLogMedication = { nav.navigate(P.ADD_MEDICATION) },
                onOpenHistory = { nav.navigate(P.HISTORY) { launchSingleTop = true } },
                onOpenReminders = { nav.navigate(P.REMINDERS) },
                onAskDoctor = { nav.navigate(P.INSULIN_REQUEST) },
                onOpenChatbot = { nav.navigate(P.CHATBOT) },
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
            PlaceholderScreen(
                title = "Messages",
                description = "Text conversations with your assigned doctor, linked to your records.",
                sprint = 2,
                icon = Icons.AutoMirrored.Rounded.Chat,
            )
        }
        composable(P.MORE) {
            MoreScreen(
                items = listOf(
                    MoreItem("Weekly summaries", "Glucose and medication overview by week.", Icons.Rounded.Summarize) { nav.navigate(P.SUMMARIES) },
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
                onOpenHistory = {
                    nav.navigate(P.HISTORY) { popUpTo(P.HOME); launchSingleTop = true }
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
        composable(P.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(P.SHARING) { SharingScreen(onBack = { nav.popBackStack() }) }
        composable(P.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(P.PRIVACY) })
        }
        composable(P.PRIVACY) {
            PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = { nav.navigate(P.SHARING) })
        }
        composable(P.SUMMARIES) {
            PlaceholderScreen(
                title = "Weekly summaries",
                description = "Date range, glucose chart, high/low events and a plain-language overview.",
                sprint = 2,
                icon = Icons.Rounded.Insights,
                onBack = { nav.popBackStack() },
            )
        }
        composable(P.INSULIN_REQUEST) {
            PlaceholderScreen(
                title = "Ask the doctor about your insulin dose",
                description = "Send your question with your recent readings and doses for your doctor to review. Responses are not immediate and this is not an emergency channel.",
                sprint = 2,
                icon = Icons.Rounded.MedicalServices,
                onBack = { nav.popBackStack() },
            )
        }
        composable(P.CHATBOT) {
            PlaceholderScreen(
                title = "Diabetes Companion chat",
                description = "General guidance only. No insulin-dose recommendations.",
                sprint = 2,
                icon = Icons.AutoMirrored.Rounded.Chat,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
