package com.escrowmalawi.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.escrowmalawi.MainActivity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows a status-bar notification whenever a NEW document appears in
 * notifications/ for the signed-in user (trade accepted, dispute opened,
 * restock request, back in stock, ...).
 *
 * This works while the app process is alive (open or in the background). Alerts
 * for things that happen while the app is fully closed need Firebase Cloud
 * Messaging (push), which is a separate step.
 */
@Singleton
class NotificationWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore
) {
    private companion object {
        const val TAG = "NotificationWatcher"
        const val CHANNEL_ID = "escrow_updates"
    }

    private var registration: ListenerRegistration? = null
    private var watchingUid: String? = null
    private var firstSnapshot = true
    private val seen = HashSet<String>()

    @Synchronized
    fun ensureWatching(uid: String) {
        if (watchingUid == uid && registration != null) return
        stop()
        watchingUid = uid
        firstSnapshot = true
        seen.clear()
        createChannel()

        registration = firestore.collection("notifications")
            .whereEqualTo("recipientUid", uid)   // single filter: no composite index needed
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Listener failed: ${err.message}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                // Everything already there when we start listening is "old": don't alert for it.
                if (firstSnapshot) {
                    snap.documents.forEach { seen += it.id }
                    firstSnapshot = false
                    return@addSnapshotListener
                }
                snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { change ->
                        val doc = change.document
                        if (!seen.add(doc.id)) return@forEach
                        if (doc.getBoolean("isRead") == true) return@forEach
                        show(doc.id, doc.getString("title").orEmpty(), doc.getString("message").orEmpty())
                    }
            }
    }

    @Synchronized
    fun stop() {
        registration?.remove()
        registration = null
        watchingUid = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Trade updates", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Trade requests, acceptances, disputes and restock alerts"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun show(id: String, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title.ifBlank { "Escrow update" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }
}
