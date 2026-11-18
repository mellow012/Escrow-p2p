package com.escrowmalawi.data.repository

import android.util.Log
import com.escrowmalawi.data.model.Verification
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Identity verification requests: verifications/{uid}. The web console approves or rejects them. */
@Singleton
class VerificationRepository @Inject constructor(
    firestore: FirebaseFirestore
) {
    private val verifications = firestore.collection("verifications")

    /** Live request for a user (null if they never submitted one). */
    fun statusFlow(uid: String): Flow<Verification?> = callbackFlow {
        val reg = verifications.document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e("VerificationRepository", "Firestore error: ${err.message}")
                return@addSnapshotListener
            }
            trySend(snap?.toObject(Verification::class.java))
        }
        awaitClose { reg.remove() }
    }

    suspend fun submit(uid: String, name: String, idUrl: String, selfieUrl: String): Result<Unit> {
        return try {
            verifications.document(uid).set(mapOf(
                "uid" to uid,
                "name" to name,
                "status" to "pending",
                "idImageUrl" to idUrl,
                "selfieUrl" to selfieUrl,
                "rejectReason" to "",
                "submittedAt" to Timestamp.now()
            )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
