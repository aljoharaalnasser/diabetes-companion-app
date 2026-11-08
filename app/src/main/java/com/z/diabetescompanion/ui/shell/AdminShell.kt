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
import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.z.diabetescompanion.ui.common.PlaceholderScreen
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

/** Admin navigation. Screens arrive in Sprint 3 (S3-01) on top of Sprint 2 aggregate queries. */
@Composable
fun AdminShell() {
    val tabs = listOf(
        ShellTab(A.OVERVIEW, "Overview", Icons.Rounded.Dashboard),
        ShellTab(A.USERS, "Users", Icons.Rounded.ManageAccounts),
        ShellTab(A.ALERTS, "Alerts", Icons.Rounded.NotificationsActive),
        ShellTab(A.ANALYTICS, "Analytics", Icons.Rounded.Insights),
        ShellTab(A.MORE, "More", Icons.Rounded.MoreHoriz),
    )
    RoleShell(tabs = tabs, startRoute = A.OVERVIEW) { nav ->
        composable(A.OVERVIEW) {
            PlaceholderScreen("Overview", "Total patients, active cases and alert counts.", sprint = 3, icon = Icons.Rounded.Dashboard)
        }
        composable(A.USERS) {
            PlaceholderScreen("Users", "User list and account-management details.", sprint = 3, icon = Icons.Rounded.ManageAccounts)
        }
        composable(A.ALERTS) {
            PlaceholderScreen("Operational alerts", "Alert review and resolution-status monitoring.", sprint = 3, icon = Icons.Rounded.NotificationsActive)
        }
        composable(A.ANALYTICS) {
            PlaceholderScreen("Analytics", "Aggregate analytics across the platform.", sprint = 3, icon = Icons.Rounded.Insights)
        }
        composable(A.MORE) {
            MoreScreen(
                items = listOf(
                    MoreItem("Profile", "Your account details.", Icons.Rounded.PersonOutline) { nav.navigate(A.PROFILE) },
                    MoreItem("Settings", "Text size and notifications.", Icons.Rounded.Settings) { nav.navigate(A.SETTINGS) },
                    MoreItem("Privacy", "How data is protected.", Icons.Rounded.Shield) { nav.navigate(A.PRIVACY) },
                )
            )
        }
        composable(A.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(A.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onOpenPrivacy = { nav.navigate(A.PRIVACY) }) }
        composable(A.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }, onOpenSharing = null) }
    }
}
