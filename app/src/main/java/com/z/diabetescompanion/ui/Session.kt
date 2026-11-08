package com.z.diabetescompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.z.diabetescompanion.DiabetesCompanionApp
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.prefs.UserPreferences
import com.z.diabetescompanion.di.AppContainer

/** Everything a signed-in screen needs to know about who is using it. */
data class Session(
    val profile: UserProfile,
    /** Role whose screens are shown: the demo override if set, else the account's role. */
    val effectiveRole: UserRole,
    val preferences: UserPreferences,
) {
    val uid: String get() = profile.uid
    val isDemoView: Boolean get() = effectiveRole != profile.role
}

val LocalSession = staticCompositionLocalOf<Session> { error("No session provided") }

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No container provided") }

/** Convenience for `viewModel { container().someRepository ... }` initializers. */
fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiabetesCompanionApp).container

@Composable
fun currentSession(): Session = LocalSession.current
