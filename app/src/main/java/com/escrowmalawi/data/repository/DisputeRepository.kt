package com.escrowmalawi.data.repository

import android.util.Log
import com.escrowmalawi.blockchain.ChainAction
import com.escrowmalawi.blockchain.ChainRecorder
import com.escrowmalawi.data.model.Dispute
import com.escrowmalawi.data.model.NotificationType
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispute Module. Statements and photo links live in Firestore (disputes/{tradeId});
 * the contract only gets a hash of the opener's evidence (see ChainRecorder).
 * The moderator console on the backend adds the AI analysis and makes the decision.
 */
@Singleton
class DisputeRepository @Inject constructor(
    firestore: FirebaseFirestore,
    private val chainRecorder: ChainRecorder
) {
    private val firestoreDb = firestore
    private val disputes = firestore.collection("disputes")
    private val trades = firestore.collection("trades")
    private val notifications = firestore.collection("notifications")

    /** Live dispute for a trade (null until one is opened). */
    fun disputeFlow(tradeId: String): Flow<Dispute?> = callbackFlow {
        val reg = disputes.document(tradeId).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e("DisputeRepository", "Firestore error: ${err.message}")
                return@addSnapshotListener
            }
            trySend(snap?.toObject(Dispute::class.java))
        }
        awaitClose { reg.remove() }
    }

    /** Buyer or seller freezes a PAID, ACTIVE trade and gives their side. */
    suspend fun openDispute(
        tradeId: String,
        uid: String,
        statement: String,
        evidence: List<String>
    ): Result<Unit> {
        return try {
            firestoreDb.runTransaction { txn ->
                val tradeRef = trades.document(tradeId)
                val trade = txn.get(tradeRef).toObject(Trade::class.java)
                    ?: throw IllegalStateException("Trade not found")

                val role = roleOf(trade, uid)
                if (trade.status != TradeStatus.ACTIVE.name || trade.txHash.isBlank()) {
                    throw IllegalStateException("A dispute can only be opened after payment, while the trade is active")
                }

                val isBuyer = role == "buyer"
                txn.set(disputes.document(tradeId), mapOf(
                    "tradeId" to tradeId,
                    "status" to "open",
                    "openedByRole" to role,
                    "buyerUid" to trade.buyerUid,
                    "sellerUid" to trade.sellerUid,
                    "buyerStatement" to if (isBuyer) statement else "",
                    "sellerStatement" to if (isBuyer) "" else statement,
                    "buyerEvidence" to if (isBuyer) evidence else emptyList<String>(),
                    "sellerEvidence" to if (isBuyer) emptyList<String>() else evidence,
                    "createdAt" to Timestamp.now()
                ))
                txn.update(tradeRef, mapOf(
                    "status" to TradeStatus.DISPUTED.name,
                    "disputeOpenedBy" to role,
                    "updatedAt" to Timestamp.now()
                ))

                // Tell the other party they can add their side
                val otherUid = if (isBuyer) trade.sellerUid else trade.buyerUid
                val notifId = UUID.randomUUID().toString()
                txn.set(notifications.document(notifId), mapOf(
                    "id" to notifId,
                    "recipientUid" to otherUid,
                    "title" to "Dispute opened",
                    "message" to "A dispute was opened on ${trade.itemDescription}. Open the trade to give your side before the moderator decides.",
                    "type" to NotificationType.SYSTEM.name,
                    "tradeId" to tradeId,
                    "isRead" to false,
                    "createdAt" to Timestamp.now()
                ))
                null
            }.await()
            chainRecorder.record(tradeId, ChainAction.DISPUTE)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The other party adds (or updates) their statement and photos while the dispute is open. */
    suspend fun submitStatement(
        tradeId: String,
        uid: String,
        statement: String,
        evidence: List<String>
    ): Result<Unit> {
        return try {
            val trade = trades.document(tradeId).get().await().toObject(Trade::class.java)
                ?: throw IllegalStateException("Trade not found")
            val role = roleOf(trade, uid)
            val dispute = disputes.document(tradeId).get().await().toObject(Dispute::class.java)
                ?: throw IllegalStateException("Dispute not found")
            if (dispute.status != "open") throw IllegalStateException("This dispute is already resolved")

            disputes.document(tradeId).update(mapOf(
                "${role}Statement" to statement,
                "${role}Evidence" to evidence,
                "${role}SubmittedAt" to Timestamp.now()
            )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun roleOf(trade: Trade, uid: String): String = when (uid) {
        trade.buyerUid -> "buyer"
        trade.sellerUid -> "seller"
        else -> throw IllegalStateException("You are not a party to this trade")
    }
}
