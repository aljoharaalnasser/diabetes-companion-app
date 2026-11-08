package com.z.diabetescompanion.ui.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.auth.AccountService
import com.z.diabetescompanion.data.auth.AuthRepository
import com.z.diabetescompanion.data.auth.AuthUser
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.prefs.UserPreferences
import com.z.diabetescompanion.data.prefs.UserPreferencesRepository
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.data.repository.UserRepository
import com.z.diabetescompanion.data.reminders.ReminderScheduler
import com.z.diabetescompanion.ui.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object SignedOut : SessionState
    data object Loading : SessionState
    /** Signed in but no Firestore profile yet (accounts created before roles existed). */
    data class NeedsProfile(val user: AuthUser) : SessionState
    data class Failed(val message: String) : SessionState
    data class Ready(val session: Session) : SessionState
}

/** Owns the session state that decides which top-level graph and which role shell is shown. */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val accountService: AccountService,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val isSignedInAtLaunch: Boolean = authRepository.currentUser != null

    /** Set when a sign-in was rejected for the wrong role; routes the user back to the form. */
    val signInError: StateFlow<String?> = accountService.signInError

    /** True while registering or verifying a sign-in role; navigation holds still until it clears. */
    val accountBusy: StateFlow<Boolean> = accountService.busy

    /** Bumped by [retry] to re-subscribe to the profile after a failure. */
    private val retryTick = MutableStateFlow(0)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences.stateIn(
        viewModelScope, SharingStarted.Eagerly, UserPreferences(),
    )

    private sealed interface ProfileLoad {
        data object Loading : ProfileLoad
        data class Loaded(val profile: UserProfile?) : ProfileLoad
        data class Error(val message: String) : ProfileLoad
    }

    val sessionState: StateFlow<SessionState> =
        combine(authRepository.authState, retryTick) { user, _ -> user }
            .flatMapLatest { user ->
                if (user == null) {
                    flowOf<Pair<AuthUser?, ProfileLoad>>(null to ProfileLoad.Loaded(null))
                } else {
                    userRepository.profile(user.uid)
                        .map<UserProfile?, ProfileLoad> { ProfileLoad.Loaded(it) }
                        .onStart { emit(ProfileLoad.Loading) }
                        .catch { emit(ProfileLoad.Error(it.toFirestoreMessage())) }
                        .map { user to it }
                }
            }
            .combine(accountService.busy) { (user, load), registering ->
                Triple(user, load, registering)
            }
            .combine(preferences) { (user, load, registering), prefs ->
                when {
                    user == null -> SessionState.SignedOut
                    // An account operation is mid-flight (profile write, role check): hold
                    // on Loading so the role shell never appears and then disappear again.
                    registering -> SessionState.Loading
                    load is ProfileLoad.Error -> SessionState.Failed(load.message)
                    load is ProfileLoad.Loading -> SessionState.Loading
                    load is ProfileLoad.Loaded && load.profile == null -> SessionState.NeedsProfile(user)
                    load is ProfileLoad.Loaded -> SessionState.Ready(
                        Session(
                            profile = load.profile!!,
                            effectiveRole = prefs.demoRole ?: load.profile.role,
                            preferences = prefs,
                        )
                    )
                    else -> SessionState.Loading
                }
            }
            .onEach { Log.d(TAG, "sessionState -> ${it::class.simpleName}") }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                if (isSignedInAtLaunch) SessionState.Loading else SessionState.SignedOut,
            )

    init {
        // Keep reminder alarms in sync with the signed-in patient's saved schedule (FR-07).
        viewModelScope.launch {
            authRepository.authState
                .flatMapLatest { user ->
                    if (user == null) flowOf(null)
                    else combine(reminderRepository.reminders(user.uid), preferences) { r, p -> r to p.notificationsEnabled }
                        .catch { /* permissions or offline: alarms stay as they were */ }
                }
                .collect { pair ->
                    if (pair != null) reminderScheduler.syncAll(pair.first, pair.second)
                }
        }
    }

    fun retry() {
        retryTick.value++
    }

    fun signOut() = authRepository.signOut()

    private companion object {
        const val TAG = "RootViewModel"
    }
}
