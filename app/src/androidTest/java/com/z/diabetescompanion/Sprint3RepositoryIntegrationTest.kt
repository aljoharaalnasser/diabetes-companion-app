package com.z.diabetescompanion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.z.diabetescompanion.data.firestore.FirestoreAdminRepository
import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.domain.AdminMetrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Runs only against demo-diabetes-sprint3 over adb reverse, never the default Firebase app. */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class Sprint3RepositoryIntegrationTest {
    private lateinit var app: FirebaseApp
    private lateinit var db: FirebaseFirestore
    private lateinit var repository: FirestoreAdminRepository
    private val args get() = InstrumentationRegistry.getArguments()
    private val patientUid get() = requireNotNull(args.getString("s3PatientUid"))
    private val password get() = requireNotNull(args.getString("s3EmulatorPassword"))

    @Before fun connectToLocalEmulators() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:sprint3test")
            .setProjectId("demo-diabetes-sprint3")
            .setApiKey("emulator-only-not-a-live-key")
            .build()
        app = FirebaseApp.initializeApp(context, options, "s3-test-" + UUID.randomUUID())
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)
        db = FirebaseFirestore.getInstance(app)
        db.useEmulator("127.0.0.1", 8080)
        runBlocking {
            withTimeout(20_000) {
                auth.signInWithEmailAndPassword("s3-admin@example.invalid", password).await()
            }
        }
        repository = FirestoreAdminRepository(db)
    }

    @After fun disconnect() {
        if (::db.isInitialized) runBlocking { db.terminate().await() }
        if (::app.isInitialized) app.delete()
    }

    @Test fun IT_S3_101_adminRepositoryQueriesMapKnownFixtureAndCounts() {
        runBlocking {
            withTimeout(20_000) {
                val users = repository.allUsers().first()
                val links = repository.allCareLinks().first()
                val alerts = repository.allAlerts().first()
                val deletions = repository.allDeletionRequests().first()
                val metrics = AdminMetrics.from(users, links, alerts, deletions)
                assertEquals(5, metrics.totalAccounts)
                assertEquals(2, metrics.totalPatients)
                assertEquals(1, metrics.activeCases)
                assertEquals(2, metrics.totalAlerts)
                assertEquals(1, metrics.openAlerts)
                assertEquals(1, metrics.resolvedAlerts)
                assertEquals(1, metrics.pendingDeletionRequests)
                assertTrue(users.zipWithNext().all { (a, b) -> a.displayName <= b.displayName })
            }
        }
    }

    @Test fun IT_S3_102_roleAndSuspensionWritesPersistThroughRepository() {
        runBlocking {
            withTimeout(20_000) {
                repository.setRole(patientUid, UserRole.Doctor)
                repository.setAccountStatus(patientUid, AccountStatus.Suspended)
                val updated = repository.allUsers().first { users ->
                    users.any { it.uid == patientUid && it.role == UserRole.Doctor && it.isSuspended }
                }.first { it.uid == patientUid }
                assertEquals(UserRole.Doctor, updated.role)
                assertTrue(updated.isSuspended)
                repository.setAccountStatus(patientUid, AccountStatus.Active)
                repository.setRole(patientUid, UserRole.Patient)
            }
        }
    }

    @Test fun IT_S3_103_deletionCompletionPersistsWithAuditFields() {
        runBlocking {
            withTimeout(20_000) {
                repository.completeDeletionRequest(patientUid, "Fictional Administrator")
                val request = repository.allDeletionRequests().first { requests ->
                    requests.any { it.uid == patientUid && !it.isPending }
                }.first { it.uid == patientUid }
                assertFalse(request.isPending)
                assertEquals("Fictional Administrator", request.completedByName)
                assertNotNull(request.completedAt)
            }
        }
    }

    @Test fun IT_S3_104_patientCannotCallAdminRoleMutation() {
        runBlocking {
            withTimeout(20_000) {
                FirebaseAuth.getInstance(app).signInWithEmailAndPassword("s3-patient@example.invalid", password).await()
                try {
                    repository.setRole(requireNotNull(args.getString("s3SecondPatientUid")), UserRole.Admin)
                    fail("Patient was allowed an administrator operation")
                } catch (error: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
                }
            }
        }
    }
}
