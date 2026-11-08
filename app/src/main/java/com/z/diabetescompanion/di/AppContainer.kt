package com.z.diabetescompanion.di

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.z.diabetescompanion.BuildConfig
import com.z.diabetescompanion.R
import com.z.diabetescompanion.data.ai.AiClient
import com.z.diabetescompanion.data.ai.ChatbotService
import com.z.diabetescompanion.data.ai.InsulinDraftService
import com.z.diabetescompanion.data.ai.OpenAiClient
import com.z.diabetescompanion.data.ai.SummaryNarrativeService
import com.z.diabetescompanion.data.ai.UnconfiguredAiClient
import com.z.diabetescompanion.data.alerts.AlertEscalationScheduler
import com.z.diabetescompanion.data.alerts.AlertService
import com.z.diabetescompanion.data.auth.AccountService
import com.z.diabetescompanion.data.auth.AuthRepository
import com.z.diabetescompanion.data.auth.FirebaseAuthRepository
import com.z.diabetescompanion.data.auth.UnconfiguredAuthRepository
import com.z.diabetescompanion.data.firestore.FirestoreAlertRepository
import com.z.diabetescompanion.data.firestore.FirestoreCareLinkRepository
import com.z.diabetescompanion.data.firestore.FirestoreDoseSuggestionRepository
import com.z.diabetescompanion.data.firestore.FirestoreGlucoseRepository
import com.z.diabetescompanion.data.firestore.FirestoreIcrRepository
import com.z.diabetescompanion.data.firestore.FirestoreInsulinRequestRepository
import com.z.diabetescompanion.data.firestore.FirestoreMedicationRepository
import com.z.diabetescompanion.data.firestore.FirestoreMessageRepository
import com.z.diabetescompanion.data.firestore.FirestoreReminderRepository
import com.z.diabetescompanion.data.firestore.FirestoreThresholdRepository
import com.z.diabetescompanion.data.firestore.FirestoreUserRepository
import com.z.diabetescompanion.data.prefs.UserPreferencesRepository
import com.z.diabetescompanion.data.reminders.ReminderScheduler
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.DoseSuggestionRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.IcrRepository
import com.z.diabetescompanion.data.repository.InsulinRequestRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.MessageRepository
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container. Deliberately small for an academic prototype; swap for
 * Hilt or Koin if the graph grows much beyond this.
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

    // --- Sprint 1 records ---------------------------------------------------
    val userRepository: UserRepository by lazy { FirestoreUserRepository(firestore) }
    val glucoseRepository: GlucoseRepository by lazy { FirestoreGlucoseRepository(firestore) }
    val medicationRepository: MedicationRepository by lazy { FirestoreMedicationRepository(firestore) }
    val reminderRepository: ReminderRepository by lazy { FirestoreReminderRepository(firestore) }
    val thresholdRepository: ThresholdRepository by lazy { FirestoreThresholdRepository(firestore) }
    val careLinkRepository: CareLinkRepository by lazy { FirestoreCareLinkRepository(firestore) }

    // --- Sprint 2 connected care --------------------------------------------
    val alertRepository: AlertRepository by lazy { FirestoreAlertRepository(firestore) }
    val messageRepository: MessageRepository by lazy { FirestoreMessageRepository(firestore) }
    val insulinRequestRepository: InsulinRequestRepository by lazy {
        FirestoreInsulinRequestRepository(firestore)
    }

    // --- Meal insulin calculator (carbs from AI, dose from domain/InsulinCalculator) ---
    val icrRepository: IcrRepository by lazy { FirestoreIcrRepository(firestore) }
    val doseSuggestionRepository: DoseSuggestionRepository by lazy { FirestoreDoseSuggestionRepository(firestore) }

    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(context) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }
    val alertEscalationScheduler: AlertEscalationScheduler by lazy { AlertEscalationScheduler(context) }

    val alertService: AlertService by lazy {
        AlertService(context, alertRepository, thresholdRepository, alertEscalationScheduler)
    }

    val accountService: AccountService by lazy {
        AccountService(authRepository, userRepository, icrRepository, appScope)
    }

    // --- AI -----------------------------------------------------------------
    /** Key comes from the git-ignored .env via BuildConfig; absent key degrades gracefully. */
    val aiClient: AiClient by lazy {
        val key = BuildConfig.OPENAI_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "OPENAI_API_KEY is empty - AI features will explain that they need a key")
            UnconfiguredAiClient(BuildConfig.OPENAI_MODEL)
        } else {
            OpenAiClient(key, BuildConfig.OPENAI_MODEL)
        }
    }

    val chatbotService: ChatbotService by lazy { ChatbotService(aiClient) }
    val insulinDraftService: InsulinDraftService by lazy { InsulinDraftService(aiClient) }
    val summaryNarrativeService: SummaryNarrativeService by lazy { SummaryNarrativeService(aiClient) }

    private companion object {
        const val TAG = "AppContainer"
    }
}
