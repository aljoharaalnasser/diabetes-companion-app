package com.z.diabetescompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.z.diabetescompanion.data.reminders.ReminderReceiver
import com.z.diabetescompanion.ui.DiabetesCompanionApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.ensureChannel(this)
        val container = (application as DiabetesCompanionApp).container
        setContent {
            DiabetesCompanionApp(container = container)
        }
    }
}
