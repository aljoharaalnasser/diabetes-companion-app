import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// The Google Services plugin hard-fails the build when google-services.json is missing.
// Apply it only when the file is present so the project still compiles on a fresh checkout;
// the app shows a "Firebase not configured" banner at runtime until the file is added.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        "app/google-services.json not found - Firebase Auth will be disabled at runtime. " +
            "Download it from the Firebase console (Project settings > Your apps) and place it in app/."
    )
}

/**
 * Secrets come from a git-ignored .env at the repo root (falling back to the environment),
 * so no key is ever committed. Note: anything baked into BuildConfig ships inside the APK
 * and can be extracted, which is acceptable for an academic prototype but would need a
 * server-side proxy before any real deployment.
 */
val envProperties = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) envFile.inputStream().use { load(it) }
}

fun secret(key: String): String =
    (envProperties.getProperty(key) ?: System.getenv(key) ?: "").trim()

android {
    namespace = "com.z.diabetescompanion"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.z.diabetescompanion"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENAI_API_KEY", "\"${secret("OPENAI_API_KEY")}\"")
        buildConfigField("String", "OPENAI_MODEL", "\"${secret("OPENAI_MODEL").ifEmpty { "gpt-6-luna" }}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.play.services)

    // Tests
    testImplementation(libs.junit)
    // Real org.json for JVM tests of the carb JSON parser (android.jar only ships stubs).
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
