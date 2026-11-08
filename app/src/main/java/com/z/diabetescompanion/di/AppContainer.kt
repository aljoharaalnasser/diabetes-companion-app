package com.z.diabetescompanion.di

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.z.diabetescompanion.R
import com.z.diabetescompanion.data.auth.AccountService
import com.z.diabetescompanion.data.auth.AuthRepository
import com.z.diabetescompanion.data.auth.FirebaseAuthRepository
import com.z.diabetescompanion.data.auth.UnconfiguredAuthRepository
import com.z.diabetescompanion.data.firestore.FirestoreCareLinkRepository
import com.z.diabetescompanion.data.firestore.FirestoreGlucoseRepository
import com.z.diabetescompanion.data.firestore.FirestoreMedicationRepository
import com.z.diabetescompanion.data.firestore.FirestoreReminderRepository
import com.z.diabetescompanion.data.firestore.FirestoreThresholdRepository
import com.z.diabetescompanion.data.firestore.FirestoreUserRepository
import com.z.diabetescompanion.data.prefs.UserPreferencesRepository
import com.z.diabetescompanion.data.reminders.ReminderScheduler
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container. Deliberately tiny for the skeleton; swap for
 * Hilt/Koin once the data layer grows further (Sprint 2 messaging, AI, alerts).
 */
class AppContainer(private val context: Context) {

    val isFirebaseConfigured: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Process-lifetime scope for work that must outlive any single screen. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val authRepository: AuthRepository by lazy {
        if (isFirebaseConfigured) {
            FirebaseAuthRepository(FirebaseAuth.getInstance())
        } else {
            Log.w(TAG, "Firebase not initialised - falling back to UnconfiguredAuthRepository")
            UnconfiguredAuthRepository()
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        val databaseId = context.getString(R.string.firestore_database_id).trim()
        if (databaseId.isEmpty()) FirebaseFirestore.getInstance()
        else FirebaseFirestore.getInstance(FirebaseApp.getInstance(), databaseId)
    }

    val userRepository: UserRepository by lazy { FirestoreUserRepository(firestore) }
    val glucoseRepository: GlucoseRepository by lazy { FirestoreGlucoseRepository(firestore) }
    val medicationRepository: MedicationRepository by lazy { FirestoreMedicationRepository(firestore) }
    val reminderRepository: ReminderRepository by lazy { FirestoreReminderRepository(firestore) }
    val thresholdRepository: ThresholdRepository by lazy { FirestoreThresholdRepository(firestore) }
    val careLinkRepository: CareLinkRepository by lazy { FirestoreCareLinkRepository(firestore) }

    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(context) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }

    val accountService: AccountService by lazy {
        AccountService(authRepository, userRepository, appScope)
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
