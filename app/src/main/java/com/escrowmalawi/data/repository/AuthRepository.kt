package com.escrowmalawi.data.repository

import com.escrowmalawi.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
        
        return profile ?: User(
            uid = firebaseUser.uid, 
            name = firebaseUser.displayName ?: "User", 
            email = firebaseUser.email ?: "", 
            phone = ""
        )
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun updateProfile(
        uid: String,
        name: String,
        phone: String,
        profilePictureUrl: String,
        walletAddress: String
    ): Result<Unit> = try {
        firestore.collection("users").document(uid).update(
            mapOf(
                "name" to name,
                "phone" to phone,
                "profilePictureUrl" to profilePictureUrl,
                "walletAddress" to walletAddress
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}