package com.escrowmalawi.data.repository


import com.escrowmalawi.data.model.Review
import com.escrowmalawi.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Review Module – ReviewRepository
 *
 * Handles submitting trade ratings/reviews stored in Firestore /reviews collection,
 * and keeps a running average (avgRating/ratingCount) cached on the reviewed
 * user's /users document so profile screens don't need to aggregate on read.
 *
 * Review doc IDs are deterministic ("{tradeId}_{raterUid}") so a user can only
 * ever leave one review per trade — resubmission overwrites nothing since we
 * check existence first.
 */
@Singleton
class ReviewRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val reviewsCollection = firestore.collection("reviews")
    private val usersCollection = firestore.collection("users")

    private fun reviewId(tradeId: String, raterUid: String) = "${tradeId}_$raterUid"

    // ── Has this user already rated this trade? ──────────────────────────
    suspend fun hasReviewed(tradeId: String, raterUid: String): Result<Boolean> {
        return try {
            val snap = reviewsCollection.document(reviewId(tradeId, raterUid)).get().await()
            Result.success(snap.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Submit a review for a completed trade ─────────────────────────────
    suspend fun submitReview(
        tradeId: String,
        raterUid: String,
        raterName: String,
        ratedUid: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        if (rating !in 1..5) {
            return Result.failure(IllegalArgumentException("Rating must be between 1 and 5"))
        }
        val docId = reviewId(tradeId, raterUid)
        return try {
            val reviewRef = reviewsCollection.document(docId)
            val userRef = usersCollection.document(ratedUid)

            firestore.runTransaction { txn ->
                if (txn.get(reviewRef).exists()) {
                    throw IllegalStateException("You've already reviewed this trade")
                }

                val userSnap = txn.get(userRef)
                val currentAvg = userSnap.getDouble("avgRating") ?: 0.0
                val currentCount = (userSnap.getLong("ratingCount") ?: 0L).toInt()

                val newCount = currentCount + 1
                val newAvg = ((currentAvg * currentCount) + rating) / newCount

                val review = Review(
                    id = docId,
                    tradeId = tradeId,
                    raterUid = raterUid,
                    raterName = raterName,
                    ratedUid = ratedUid,
                    rating = rating,
                    comment = comment.trim(),
                    createdAt = Timestamp.now()
                )
                txn.set(reviewRef, review)
                txn.update(userRef, mapOf(
                    "avgRating" to newAvg,
                    "ratingCount" to newCount
                ))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Real-time reviews received by a user (newest first) ───────────────
    fun reviewsForUserFlow(uid: String): Flow<List<Review>> = callbackFlow {
        val listener = reviewsCollection
            .whereEqualTo("ratedUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("ReviewRepository", "Firestore Error: ${err.message}")
                    return@addSnapshotListener
                }
                val reviews = snap?.toObjects(Review::class.java) ?: emptyList()
                // Sort client-side to avoid needing a composite index
                trySend(reviews.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
        awaitClose { listener.remove() }
    }
}