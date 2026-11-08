package com.z.diabetescompanion

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.z.diabetescompanion.data.prefs.TextSize
import com.z.diabetescompanion.data.prefs.ThemeMode
import com.z.diabetescompanion.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Sprint3PreferencesIntegrationTest {
    private fun isolatedContext(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(base.filesDir, "sprint3-integration").apply { mkdirs() }
        }
    }

    @Test fun IT_S3_105_appearancePersistsAcrossRepositoryInstances() {
        runBlocking {
            val context = isolatedContext()
            val first = UserPreferencesRepository(context)
            first.setTextSize(TextSize.ExtraLarge)
            first.setThemeMode(ThemeMode.Dark)
            val restored = UserPreferencesRepository(context).preferences.first()
            assertEquals(TextSize.ExtraLarge, restored.textSize)
            assertEquals(ThemeMode.Dark, restored.themeMode)
        }
    }

    @Test fun IT_S3_106_notificationPreferencesPersistAndRemainIndependent() {
        runBlocking {
            val context = isolatedContext()
            val first = UserPreferencesRepository(context)
            first.setNotificationsEnabled(true)
            first.setReminderNotifications(false)
            first.setAlertNotifications(true)
            val restored = UserPreferencesRepository(context).preferences.first()
            assertFalse(restored.remindersAllowed)
            assertTrue(restored.alertsAllowed)
            first.setNotificationsEnabled(false)
            val disabled = UserPreferencesRepository(context).preferences.first()
            assertFalse(disabled.remindersAllowed)
            assertFalse(disabled.alertsAllowed)
        }
    }
}
