package com.escrowmalawi.data.repository

import android.util.Log
import com.escrowmalawi.data.model.Notification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val notificationsCollection = firestore.collection("notifications")

    suspend fun sendNotification(notification: Notification): Result<Unit> {
        return try {
            val id = UUID.randomUUID().toString()
            val finalNotification = notification.copy(id = id)
            notificationsCollection.document(id).set(finalNotification).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNotifications(recipientUid: String): Flow<List<Notification>> = callbackFlow {
        // No orderBy here: whereEqualTo + orderBy needs a composite index, and without it
        // the query fails and the list stays empty. We sort on the device instead.
        val listener = notificationsCollection
            .whereEqualTo("recipientUid", recipientUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("NotificationRepository", "Notifications query failed: ${err.message}")
                    return@addSnapshotListener
                }
                val notifications = snap?.toObjects(Notification::class.java) ?: emptyList()
                trySend(notifications.sortedByDescending { it.createdAt.seconds })
            }
        awaitClose { listener.remove() }
    }

    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            notificationsCollection.document(notificationId).update("isRead", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            notificationsCollection.document(notificationId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllNotifications(recipientUid: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val snapshots = notificationsCollection
                .whereEqualTo("recipientUid", recipientUid)
                .get()
                .await()

            for (doc in snapshots.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}