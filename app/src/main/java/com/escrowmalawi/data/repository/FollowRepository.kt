package com.escrowmalawi.data.repository

import android.util.Log
import com.escrowmalawi.data.model.Follow
import com.escrowmalawi.data.model.NotificationType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follow a seller to see their products first on the home carousel and to get an alert
 * when they list something new or restock an item.
 */
@Singleton
class FollowRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val follows = firestore.collection("follows")
    private val notifications = firestore.collection("notifications")

    private companion object {
        const val TAG = "FollowRepository"
        const val MAX_FOLLOWERS_NOTIFIED = 500
        const val BATCH_SIZE = 400   // Firestore allows 500 writes per batch
    }

    /** Sellers this user follows, newest first (live). */
    fun myFollowsFlow(uid: String): Flow<List<Follow>> = callbackFlow {
        val reg = follows.whereEqualTo("followerUid", uid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "Follows query failed: ${err.message}")
                return@addSnapshotListener
            }
            val list = snap?.toObjects(Follow::class.java) ?: emptyList()
            trySend(list.sortedByDescending { it.createdAt.seconds })
        }
        awaitClose { reg.remove() }
    }

    suspend fun follow(followerUid: String, sellerUid: String, sellerName: String): Result<Unit> {
        if (followerUid == sellerUid) return Result.failure(IllegalArgumentException("You can't follow yourself"))
        return try {
            val id = "${followerUid}_$sellerUid"
            follows.document(id).set(
                Follow(id = id, followerUid = followerUid, sellerUid = sellerUid, sellerName = sellerName, createdAt = Timestamp.now())
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unfollow(followerUid: String, sellerUid: String): Result<Unit> = try {
        follows.document("${followerUid}_$sellerUid").delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun followerCount(sellerUid: String): Int = try {
        follows.whereEqualTo("sellerUid", sellerUid).count().get(AggregateSource.SERVER).await().count.toInt()
    } catch (e: Exception) {
        Log.e(TAG, "Follower count failed: ${e.message}")
        0
    }

    /**
     * Writes one notification per follower (skipping [exclude], e.g. people already alerted
     * another way). Returns how many were sent. Runs from the seller's device.
     */
    suspend fun notifyFollowers(
        sellerUid: String,
        title: String,
        message: String,
        listingId: String,
        exclude: Set<String> = emptySet()
    ): Result<Int> {
        return try {
            val followers = follows.whereEqualTo("sellerUid", sellerUid).limit(MAX_FOLLOWERS_NOTIFIED.toLong()).get().await()
                .documents.mapNotNull { it.getString("followerUid") }
                .filter { it != sellerUid && it !in exclude }
                .distinct()
            followers.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { uid ->
                    val id = UUID.randomUUID().toString()
                    batch.set(notifications.document(id), mapOf(
                        "id" to id,
                        "recipientUid" to uid,
                        "title" to title,
                        "message" to message,
                        "type" to NotificationType.SYSTEM.name,
                        "listingId" to listingId,
                        "isRead" to false,
                        "createdAt" to Timestamp.now()
                    ))
                }
                batch.commit().await()
            }
            Result.success(followers.size)
        } catch (e: Exception) {
            Log.e(TAG, "Notify followers failed: ${e.message}")
            Result.failure(e)
        }
    }
}
