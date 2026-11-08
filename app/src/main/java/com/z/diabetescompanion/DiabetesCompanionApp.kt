package com.z.diabetescompanion

import android.app.Application
import com.z.diabetescompanion.di.AppContainer

class DiabetesCompanionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Firebase itself is initialised by FirebaseInitProvider (from google-services.json);
        // the container detects whether that succeeded.
        container = AppContainer(this)
    }
}
