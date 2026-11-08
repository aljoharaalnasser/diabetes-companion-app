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
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.z.diabetescompanion.ui.common.PlaceholderScreen
import com.z.diabetescompanion.ui.doctor.patients.PatientDetailScreen
import com.z.diabetescompanion.ui.doctor.patients.PatientListScreen
import com.z.diabetescompanion.ui.doctor.patients.ThresholdsScreen
import com.z.diabetescompanion.ui.patient.glucose.GlucoseHistoryScreen
import com.z.diabetescompanion.ui.patient.medications.MedicationsScreen
import com.z.diabetescompanion.ui.patient.profile.ProfileScreen
import com.z.diabetescompanion.ui.settings.PrivacyScreen
import com.z.diabetescompanion.ui.settings.SettingsScreen

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
    const val PROFILE = "doctor/profile"
    const val SETTINGS = "doctor/settings"
    const val PRIVACY = "doctor/privacy"

    fun patient(uid: String) = "doctor/patient/$uid"
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
    val uidArg = listOf(navArgument("uid") { type = NavType.StringType })

    RoleShell(tabs = tabs, startRoute = D.PATIENTS) { nav ->
        composable(D.PATIENTS) { PatientListScreen(onOpenPatient = { nav.navigate(D.patient(it)) }) }
        composable(D.ALERTS) {
            PlaceholderScreen("Alerts", "Escalated alerts with acknowledgment, follow-up and resolution controls.", sprint = 2, icon = Icons.Rounded.NotificationsActive)
        }
        composable(D.REQUESTS) {
            PlaceholderScreen("Insulin requests", "Pending and responded requests, with doctor-only AI drafts for review.", sprint = 2, icon = Icons.Rounded.Inbox)
        }
        composable(D.MESSAGES) {
            PlaceholderScreen("Messages", "Conversations with patients and caregivers.", sprint = 2, icon = Icons.AutoMirrored.Rounded.Chat)
        }
        composable(D.MORE) {
            MoreScreen(
                items = listOf(
                    MoreItem("Profile", "Your name as patients see it.", Icons.Rounded.PersonOutline) { nav.navigate(D.PROFILE) },
                    MoreItem("Settings", "Text size and notifications.", Icons.Rounded.Settings) { nav.navigate(D.SETTINGS) },
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
        composable(D.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(D.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(D.PRIVACY) }) }
        composable(D.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = null) }
    }
}
