package com.escrowmalawi.data.repository

import com.escrowmalawi.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    // ── Register ─────────────────────────────────────────────────────────
    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: error("Auth returned null user")

            // Send verification email
            firebaseUser.sendEmailVerification().await()

            // Fire-and-forget — no .await() so we never block on Firestore confirmation
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(
                    User(
                        uid       = firebaseUser.uid,
                        name      = name.trim(),
                        email     = email.trim(),
                        phone     = phone.trim(),
                        createdAt = Timestamp.now()
                    )
                )

            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Check Verification ──────────────────────────────────────────────
    suspend fun isEmailVerified(): Boolean {
        auth.currentUser?.reload()?.await()
        return auth.currentUser?.isEmailVerified ?: false
    }

    // ── Resend Verification ─────────────────────────────────────────────
    suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            auth.currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Login ────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            Result.success(result.user ?: error("Sign-in returned null user"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Logout ───────────────────────────────────────────────────────────
    fun logout() = auth.signOut()

    // ── Helpers ──────────────────────────────────────────────────────────
    fun currentFirebaseUser(): FirebaseUser? = auth.currentUser

    suspend fun getUserProfile(uid: String): User? {
        return try {
            val cached = try {
                firestore.collection("users").document(uid)
                    .get(Source.CACHE).await()
                    .takeIf { it.exists() }
                    ?.toObject(User::class.java)
            } catch (_: Exception) { null }

            cached ?: try {
                firestore.collection("users").document(uid)
                    .get(Source.SERVER).await()
                    .toObject(User::class.java)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun currentUserProfile(): User? {
        val firebaseUser = auth.currentUser ?: return null
        val profile = getUserProfile(firebaseUser.uid)
        if (profile != null) return profile

        // No profile document found. Registration writes it fire-and-forget, so
        // it can go missing (e.g. app closed or offline right after sign-up).
        // Rebuild it so other users can trade with this account.
        val fallback = User(
            uid = firebaseUser.uid,
            name = firebaseUser.displayName
                ?: firebaseUser.email?.substringBefore("@")
                ?: "User",
            email = firebaseUser.email ?: "",
            phone = firebaseUser.phoneNumber ?: ""
        )
        ensureProfileDocExists(fallback)
        return fallback
    }

    /**
     * Creates the profile document ONLY if the server positively confirms it
     * doesn't exist. If the check fails (offline etc.) we write nothing, so a
     * real profile can never be overwritten with fallback data.
     */
    private suspend fun ensureProfileDocExists(fallback: User) {
        try {
            val ref = firestore.collection("users").document(fallback.uid)
            val snap = ref.get(Source.SERVER).await()
            if (!snap.exists()) {
                ref.set(fallback.copy(createdAt = Timestamp.now())).await()
            }
        } catch (_: Exception) {
            // Couldn't confirm either way: try again next time the profile loads.
        }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Real-time ratings lookup for a set of users, keyed by uid → (avgRating, ratingCount).
     * Used to show seller trust scores on listing rows (Home, Marketplace) without
     * fetching each seller's full profile individually.
     *
     * Firestore's whereIn caps at 30 values, so uids are chunked and the resulting
     * flows combined into a single up-to-date map.
     */
    fun usersRatingsFlow(uids: List<String>): kotlinx.coroutines.flow.Flow<Map<String, Pair<Double, Int>>> {
        val distinctUids = uids.distinct().filter { it.isNotBlank() }
        if (distinctUids.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyMap())

        val chunkFlows = distinctUids.chunked(30).map { chunk ->
            kotlinx.coroutines.flow.callbackFlow {
                val listener = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .addSnapshotListener { snap, err ->
                        if (err != null) {
                            android.util.Log.e("AuthRepository", "Ratings listen failed: ${err.message}")
                            return@addSnapshotListener
                        }
                        val map = snap?.documents?.associate { doc ->
                            doc.id to Pair(doc.getDouble("avgRating") ?: 0.0, (doc.getLong("ratingCount") ?: 0L).toInt())
                        } ?: emptyMap()
                        trySend(map)
                    }
                awaitClose { listener.remove() }
            }
        }

        return kotlinx.coroutines.flow.combine(chunkFlows) { maps -> maps.reduce { acc, m -> acc + m } }
    }

    suspend fun updateProfile(
        uid: String,
        name: String,
        phone: String,
        profilePictureUrl: String,
        walletAddress: String
    ): Result<Unit> = try {
        // set+merge (not update) so it also works if the profile doc is missing
        firestore.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "name" to name,
                "phone" to phone,
                "profilePictureUrl" to profilePictureUrl,
                "walletAddress" to walletAddress
            ),
            SetOptions.merge()
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}