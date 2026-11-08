package com.z.diabetescompanion.data.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Thrown when a write doesn't reach the server in time (offline, or the database doesn't exist). */
class FirestoreTimeoutException : Exception("Timed out waiting for the server")

/**
 * Firestore queues writes forever while offline, which would leave a Save button spinning
 * indefinitely. Bound the wait so the UI can show a real message (NFR-15). The write itself
 * still completes later if connectivity returns.
 */
suspend fun <T> Task<T>.awaitOrTimeout(timeoutMs: Long = 15_000): T = try {
    withTimeout(timeoutMs) { await() }
} catch (e: TimeoutCancellationException) {
    throw FirestoreTimeoutException()
}

/** Live query as a Flow. Closes with the Firestore error so callers can surface it. */
fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/** User-facing copy for Firestore failures. */
fun Throwable.toFirestoreMessage(): String = when (this) {
    is FirestoreTimeoutException ->
        "The server didn't respond. Check your connection, and make sure the Firestore database exists for this project."
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have permission to do that. Check your sharing settings or sign in again."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Can't reach the server. Check your connection and try again."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "The database isn't set up yet. Create a Firestore database in the Firebase console."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "This query needs an index. Open Logcat for the Firebase console link."
        else -> localizedMessage ?: "Something went wrong. Please try again."
    }
    else -> localizedMessage?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}
