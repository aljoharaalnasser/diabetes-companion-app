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

/** Labels are kept to one short word so the chooser still fits at the largest size. */
enum class TextSize(val label: String, val scale: Float, val storageKey: String) {
    Default("Default", 1.0f, "default"),
    Large("Large", 1.2f, "large"),
    ExtraLarge("Largest", 1.4f, "extra_large");

    companion object {
        fun fromKey(key: String?): TextSize = entries.firstOrNull { it.storageKey == key } ?: Default
    }
}

/** Light/dark choice (S3-02). "System" follows the device setting, which is the default. */
enum class ThemeMode(val label: String, val storageKey: String) {
    System("System", "system"),
    Light("Light", "light"),
    Dark("Dark", "dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.storageKey == key } ?: System
    }
}

data class UserPreferences(
    val textSize: TextSize = TextSize.Default,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Master switch. Off silences both notification kinds below. */
    val notificationsEnabled: Boolean = true,
    val reminderNotifications: Boolean = true,
    val alertNotifications: Boolean = true,
    /** Presentation-only override for the demo role switcher; never affects permissions. */
    val demoRole: UserRole? = null,
    val hasSeenWelcome: Boolean = false,
) {
    /** Medication reminder alarms (FR-07). */
    val remindersAllowed: Boolean get() = notificationsEnabled && reminderNotifications

    /** Alert and escalation notifications (FR-09). */
    val alertsAllowed: Boolean get() = notificationsEnabled && alertNotifications
}

/** Device-local preferences (FR-22 essentials). Stored per device, not per account. */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val REMINDER_NOTIFICATIONS = booleanPreferencesKey("reminder_notifications")
        val ALERT_NOTIFICATIONS = booleanPreferencesKey("alert_notifications")
        val DEMO_ROLE = stringPreferencesKey("demo_role")
        val SEEN_WELCOME = booleanPreferencesKey("seen_welcome")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            textSize = TextSize.fromKey(prefs[Keys.TEXT_SIZE]),
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            reminderNotifications = prefs[Keys.REMINDER_NOTIFICATIONS] ?: true,
            alertNotifications = prefs[Keys.ALERT_NOTIFICATIONS] ?: true,
            demoRole = UserRole.fromKey(prefs[Keys.DEMO_ROLE]),
            hasSeenWelcome = prefs[Keys.SEEN_WELCOME] ?: false,
        )
    }

    suspend fun setTextSize(size: TextSize) =
        context.dataStore.edit { it[Keys.TEXT_SIZE] = size.storageKey }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.storageKey }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setReminderNotifications(enabled: Boolean) =
        context.dataStore.edit { it[Keys.REMINDER_NOTIFICATIONS] = enabled }

    suspend fun setAlertNotifications(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_NOTIFICATIONS] = enabled }

    suspend fun setDemoRole(role: UserRole?) = context.dataStore.edit {
        if (role == null) it.remove(Keys.DEMO_ROLE) else it[Keys.DEMO_ROLE] = role.storageKey
    }

    suspend fun setHasSeenWelcome(seen: Boolean) =
        context.dataStore.edit { it[Keys.SEEN_WELCOME] = seen }
}
