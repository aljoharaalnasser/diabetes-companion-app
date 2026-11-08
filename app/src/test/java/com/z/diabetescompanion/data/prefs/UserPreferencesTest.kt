package com.z.diabetescompanion.data.prefs

import org.junit.Assert.*
import org.junit.Test

/** US-20 decision table and recovery from absent or obsolete stored preference values. */
class UserPreferencesTest {
    @Test fun defaultsAllowBothNotificationCategories() {
        val prefs = UserPreferences()
        assertTrue(prefs.remindersAllowed)
        assertTrue(prefs.alertsAllowed)
    }

    @Test fun masterSwitchOverridesEveryCategoryCombination() {
        for (reminders in listOf(false, true)) {
            for (alerts in listOf(false, true)) {
                val prefs = UserPreferences(
                    notificationsEnabled = false,
                    reminderNotifications = reminders,
                    alertNotifications = alerts,
                )
                assertFalse(prefs.remindersAllowed)
                assertFalse(prefs.alertsAllowed)
            }
        }
    }

    @Test fun categoriesCanBeControlledIndependently() {
        for (reminders in listOf(false, true)) {
            for (alerts in listOf(false, true)) {
                val prefs = UserPreferences(
                    notificationsEnabled = true,
                    reminderNotifications = reminders,
                    alertNotifications = alerts,
                )
                assertEquals(reminders, prefs.remindersAllowed)
                assertEquals(alerts, prefs.alertsAllowed)
            }
        }
    }

    @Test fun missingOrObsoleteTextSizeFallsBackToDefault() {
        assertEquals(TextSize.Default, TextSize.fromKey(null))
        assertEquals(TextSize.Default, TextSize.fromKey("obsolete"))
    }

    @Test fun supportedTextSizesRoundTripAndIncrease() {
        val sizes = TextSize.entries
        sizes.forEach { assertEquals(it, TextSize.fromKey(it.storageKey)) }
        assertEquals(1f, sizes.first().scale, 0f)
        sizes.zipWithNext().forEach { (a, b) -> assertTrue(b.scale > a.scale) }
    }

    @Test fun missingOrObsoleteThemeFollowsSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.System, ThemeMode.fromKey("obsolete"))
    }

    @Test fun supportedThemesRoundTrip() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.storageKey)) }
    }
}
