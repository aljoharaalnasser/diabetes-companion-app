package com.z.diabetescompanion.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.z.diabetescompanion.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class TextSize(val label: String, val scale: Float, val storageKey: String) {
    Default("Default", 1.0f, "default"),
    Large("Large", 1.2f, "large"),
    ExtraLarge("Extra large", 1.4f, "extra_large");

    companion object {
        fun fromKey(key: String?): TextSize = entries.firstOrNull { it.storageKey == key } ?: Default
    }
}

data class UserPreferences(
    val textSize: TextSize = TextSize.Default,
    val notificationsEnabled: Boolean = true,
    /** Presentation-only override for the demo role switcher; never affects permissions. */
    val demoRole: UserRole? = null,
    val hasSeenWelcome: Boolean = false,
)

/** Device-local preferences (FR-22 essentials). Stored per device, not per account. */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val DEMO_ROLE = stringPreferencesKey("demo_role")
        val SEEN_WELCOME = booleanPreferencesKey("seen_welcome")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            textSize = TextSize.fromKey(prefs[Keys.TEXT_SIZE]),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            demoRole = UserRole.fromKey(prefs[Keys.DEMO_ROLE]),
            hasSeenWelcome = prefs[Keys.SEEN_WELCOME] ?: false,
        )
    }

    suspend fun setTextSize(size: TextSize) =
        context.dataStore.edit { it[Keys.TEXT_SIZE] = size.storageKey }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setDemoRole(role: UserRole?) = context.dataStore.edit {
        if (role == null) it.remove(Keys.DEMO_ROLE) else it[Keys.DEMO_ROLE] = role.storageKey
    }

    suspend fun setHasSeenWelcome(seen: Boolean) =
        context.dataStore.edit { it[Keys.SEEN_WELCOME] = seen }
}
