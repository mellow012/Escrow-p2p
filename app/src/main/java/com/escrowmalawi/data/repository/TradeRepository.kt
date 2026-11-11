package com.escrowmalawi.data.repository

import com.escrowmalawi.blockchain.ChainAction
import com.escrowmalawi.blockchain.ChainRecorder
import com.escrowmalawi.data.model.ListingStatus
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.data.model.NotificationType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.web3j.crypto.Hash
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trade Module – TradeRepository
 *
 * Handles creating, fetching, accepting and rejecting trades
 * stored in Firestore /trades collection.
 */
@Singleton
class TradeRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chainRecorder: ChainRecorder
) {
    private val tradesCollection = firestore.collection("trades")
    private val notificationsCollection = firestore.collection("notifications")
    private val listingsCollection = firestore.collection("listings")

    // ── Create a trade (buyer initiates) ─────────────────────────────────
    suspend fun createTrade(
        buyerUid: String,
        buyerName: String,
        buyerWallet: String,
        sellerUid: String,
        sellerName: String,
        sellerPhone: String,
        sellerWallet: String,
        itemDescription: String,
        amount: Double,
        listingId: String = ""
    ): Result<Trade> {
        return try {
            // Soft check so buyers get an immediate "sold out" instead of a dead trade.
            // The authoritative, race-safe check happens when the seller accepts.
            if (listingId.isNotBlank()) {
                val listingSnap = listingsCollection.document(listingId).get().await()
                if (!listingSnap.exists()) {
                    return Result.failure(IllegalStateException("This listing is no longer available"))
                }
                val stock = (listingSnap.getLong("quantity") ?: 1L).toInt()
                val status = listingSnap.getString("status") ?: ListingStatus.ACTIVE.name
                if (status != ListingStatus.ACTIVE.name || stock <= 0) {
                    return Result.failure(IllegalStateException("Sold out — this item is no longer available"))
                }
            }

            val docId = UUID.randomUUID().toString()

            // Create a deterministic on-chain ID from the Firestore doc ID
            val blockchainTradeId = "0x" + org.web3j.crypto.Hash.sha3String(docId).removePrefix("0x")

            val trade = Trade(
                id = docId,
                buyerUid = buyerUid,
                buyerName = buyerName,
                buyerWalletAddress = buyerWallet,
                sellerUid = sellerUid,
                sellerName = sellerName,
                sellerPhone = sellerPhone,
                sellerWalletAddress = sellerWallet,
                itemDescription = itemDescription,
                listingId = listingId,
                quantity = 1,
                amount = amount,
                status = TradeStatus.PENDING.name,
                blockchainTradeId = blockchainTradeId,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )

            firestore.runBatch { batch ->
                batch.set(tradesCollection.document(docId), trade)

                // Notify Seller
                val notifId = UUID.randomUUID().toString()
                batch.set(notificationsCollection.document(notifId), mapOf(
                    "id" to notifId,
                    "recipientUid" to sellerUid,
                    "title" to "New Trade Request",
                    "message" to "$buyerName wants to buy $itemDescription for MWK ${"%,.0f".format(amount)}",
                    "type" to NotificationType.TRADE_REQUEST.name,
                    "tradeId" to docId,
                    "isRead" to false,
                    "createdAt" to Timestamp.now()
                ))
            }.await()

            chainRecorder.record(docId, ChainAction.CREATE)
            Result.success(trade)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Seller accepts trade → status becomes ACTIVE (reserves stock) ────
    suspend fun acceptTrade(tradeId: String): Result<Unit> {
        return try {
            firestore.runTransaction { txn ->
                val tradeRef = tradesCollection.document(tradeId)
                val trade = txn.get(tradeRef).toObject(Trade::class.java)
                    ?: throw IllegalStateException("Trade not found")

                // Guards against a double-tap reserving stock twice
                if (trade.status != TradeStatus.PENDING.name) {
                    throw IllegalStateException("This trade has already been handled")
                }

                // All reads must come before any writes in a Firestore transaction
                val listingRef = trade.listingId.takeIf { it.isNotBlank() }
                    ?.let { listingsCollection.document(it) }
                val listingSnap = listingRef?.let { txn.get(it) }

                if (listingRef != null && listingSnap != null && listingSnap.exists()) {
                    val stock = (listingSnap.getLong("quantity") ?: 1L).toInt()
                    val needed = trade.quantity.coerceAtLeast(1)
                    if (stock < needed) {
                        throw IllegalStateException(
                            "Sold out — there's not enough stock left to accept this trade"
                        )
                    }
                    val remaining = stock - needed
                    val updates = mutableMapOf<String, Any>(
                        "quantity" to remaining,
                        "updatedAt" to Timestamp.now()
                    )
                    if (remaining == 0) updates["status"] = ListingStatus.SOLD.name
                    txn.update(listingRef, updates)
                }

                txn.update(tradeRef, mapOf(
                    "status" to TradeStatus.ACTIVE.name,
                    "acceptedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                ))

                // Notify Buyer
                val notifId = UUID.randomUUID().toString()
                txn.set(notificationsCollection.document(notifId), mapOf(
                    "id" to notifId,
                    "recipientUid" to trade.buyerUid,
                    "title" to "Trade Accepted",
                    "message" to "${trade.sellerName} accepted your trade request for ${trade.itemDescription}.",
                    "type" to NotificationType.TRADE_ACCEPTED.name,
                    "tradeId" to tradeId,
                    "isRead" to false,
                    "createdAt" to Timestamp.now()
                ))
                null
            }.await()
            chainRecorder.record(tradeId, ChainAction.ACCEPT)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Seller marks the item delivered → starts the buyer's confirm window ──
    suspend fun markDelivered(tradeId: String): Result<Unit> {
        return try {
            firestore.runTransaction { txn ->
                val tradeRef = tradesCollection.document(tradeId)
                val trade = txn.get(tradeRef).toObject(Trade::class.java)
                    ?: throw IllegalStateException("Trade not found")
                if (trade.status != TradeStatus.ACTIVE.name) {
                    throw IllegalStateException("This trade is no longer active")
                }
                if (trade.txHash.isBlank()) {
                    throw IllegalStateException("The buyer hasn't paid yet")
                }
                if (trade.deliveredAt != null) {
                    throw IllegalStateException("Already marked as delivered")
                }

                txn.update(tradeRef, mapOf(
                    "deliveredAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                ))

                val notifId = UUID.randomUUID().toString()
                txn.set(notificationsCollection.document(notifId), mapOf(
                    "id" to notifId,
                    "recipientUid" to trade.buyerUid,
                    "title" to "Item Delivered?",
                    "message" to "${trade.sellerName} marked ${trade.itemDescription} as delivered. " +
                            "Confirm receipt, or the funds are released to the seller automatically after the confirm window.",
                    "type" to NotificationType.TRADE_DELIVERED.name,
                    "tradeId" to tradeId,
                    "isRead" to false,
                    "createdAt" to Timestamp.now()
                ))
                null
            }.await()
            chainRecorder.record(tradeId, ChainAction.DELIVER)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Seller rejects trade ─────────────────────────────────────────────
    suspend fun rejectTrade(tradeId: String): Result<Unit> {
        return try {
            val trade = getTrade(tradeId).getOrNull()

            firestore.runBatch { batch ->
                batch.update(tradesCollection.document(tradeId), mapOf(
                    "status" to TradeStatus.REJECTED.name,
                    "updatedAt" to Timestamp.now()
                ))

                if (trade != null) {
                    // Notify Buyer
                    val notifId = UUID.randomUUID().toString()
                    batch.set(notificationsCollection.document(notifId), mapOf(
                        "id" to notifId,
                        "recipientUid" to trade.buyerUid,
                        "title" to "Trade Rejected",
                        "message" to "${trade.sellerName} rejected your trade request for ${trade.itemDescription}.",
                        "type" to NotificationType.TRADE_REJECTED.name,
                        "tradeId" to tradeId,
                        "isRead" to false,
                        "createdAt" to Timestamp.now()
                    ))
                }
            }.await()
            chainRecorder.record(tradeId, ChainAction.REJECT)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Mark trade funds locked (buyer pays into escrow) ────────────────
    suspend fun markFundsLocked(tradeId: String, txHash: String): Result<Unit> {
        return try {
            val trade = getTrade(tradeId).getOrNull()

            firestore.runBatch { batch ->
                batch.update(tradesCollection.document(tradeId), mapOf(
                    "txHash" to txHash,
                    "updatedAt" to Timestamp.now()
                ))

                if (trade != null) {
                    // Notify Seller
                    val notifId = UUID.randomUUID().toString()
                    batch.set(notificationsCollection.document(notifId), mapOf(
                        "id" to notifId,
                        "recipientUid" to trade.sellerUid,
                        "title" to "Funds Locked",
                        "message" to "${trade.buyerName} has locked MWK ${"%,.0f".format(trade.amount)} in escrow for ${trade.itemDescription}. You can now proceed with delivery.",
                        "type" to NotificationType.FUNDS_LOCKED.name,
                        "tradeId" to tradeId,
                        "isRead" to false,
                        "createdAt" to Timestamp.now()
                    ))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Mark trade completed (funds released on-chain → mirror in Firestore)
    suspend fun markCompleted(tradeId: String, txHash: String): Result<Unit> {
        return try {
            val trade = getTrade(tradeId).getOrNull()

            firestore.runBatch { batch ->
                batch.update(tradesCollection.document(tradeId), mapOf(
                    "status" to TradeStatus.COMPLETED.name,
                    "txHash" to txHash,
                    "updatedAt" to Timestamp.now()
                ))

                if (trade != null) {
                    // Notify Seller
                    val notifId = UUID.randomUUID().toString()
                    batch.set(notificationsCollection.document(notifId), mapOf(
                        "id" to notifId,
                        "recipientUid" to trade.sellerUid,
                        "title" to "Payment Received",
                        "message" to "${trade.buyerName} confirmed receipt. Funds for ${trade.itemDescription} have been released to your wallet.",
                        "type" to NotificationType.TRADE_COMPLETED.name,
                        "tradeId" to tradeId,
                        "isRead" to false,
                        "createdAt" to Timestamp.now()
                    ))
                }
            }.await()
            chainRecorder.record(tradeId, ChainAction.CONFIRM)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Mark trade refunded (returns any reserved stock to the listing) ──
    suspend fun markRefunded(tradeId: String, txHash: String): Result<Unit> {
        return try {
            firestore.runTransaction { txn ->
                val tradeRef = tradesCollection.document(tradeId)
                val trade = txn.get(tradeRef).toObject(Trade::class.java)

                // Stock is only reserved once a trade is ACTIVE (seller accepted)
                val listingRef = trade
                    ?.takeIf { it.status == TradeStatus.ACTIVE.name }
                    ?.listingId?.takeIf { it.isNotBlank() }
                    ?.let { listingsCollection.document(it) }
                val listingSnap = listingRef?.let { txn.get(it) }

                txn.update(tradeRef, mapOf(
                    "status" to TradeStatus.REFUNDED.name,
                    "txHash" to txHash,
                    "updatedAt" to Timestamp.now()
                ))

                if (trade != null && listingRef != null && listingSnap != null && listingSnap.exists()) {
                    val stock = (listingSnap.getLong("quantity") ?: 0L).toInt()
                    val updates = mutableMapOf<String, Any>(
                        "quantity" to stock + trade.quantity.coerceAtLeast(1),
                        "updatedAt" to Timestamp.now()
                    )
                    // Bring a sold-out listing back to the marketplace (never resurrect REMOVED ones)
                    if (listingSnap.getString("status") == ListingStatus.SOLD.name) {
                        updates["status"] = ListingStatus.ACTIVE.name
                    }
                    txn.update(listingRef, updates)
                }

                if (trade != null) {
                    // Notify Seller that buyer was refunded
                    val notifId = UUID.randomUUID().toString()
                    txn.set(notificationsCollection.document(notifId), mapOf(
                        "id" to notifId,
                        "recipientUid" to trade.sellerUid,
                        "title" to "Trade Refunded",
                        "message" to "The trade for ${trade.itemDescription} was refunded to ${trade.buyerName}.",
                        "type" to NotificationType.TRADE_REFUNDED.name,
                        "tradeId" to tradeId,
                        "isRead" to false,
                        "createdAt" to Timestamp.now()
                    ))
                }
                null
            }.await()
            chainRecorder.record(tradeId, ChainAction.REFUND)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Single trade (one-shot) ──────────────────────────────────────────
    suspend fun getTrade(tradeId: String): Result<Trade> {
        return try {
            val snap = tradesCollection.document(tradeId).get().await()
            val trade = snap.toObject(Trade::class.java) ?: error("Trade not found")
            Result.success(trade)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Real-time trades for a buyer ─────────────────────────────────────
    fun buyerTradesFlow(buyerUid: String): Flow<List<Trade>> = callbackFlow {
        val listener = tradesCollection
            .whereEqualTo("buyerUid", buyerUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Log the error but don't crash the flow
                    android.util.Log.e("TradeRepository", "Firestore Error: ${err.message}")
                    return@addSnapshotListener
                }
                val trades = snap?.toObjects(Trade::class.java) ?: emptyList()
                chainRecorder.syncBuyerPayments(trades)
                // Sort client-side to avoid needing a composite index
                trySend(trades.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
        awaitClose { listener.remove() }
    }

    // ── Real-time trades for a seller ────────────────────────────────────
    fun sellerTradesFlow(sellerUid: String): Flow<List<Trade>> = callbackFlow {
        val listener = tradesCollection
            .whereEqualTo("sellerUid", sellerUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("TradeRepository", "Firestore Error: ${err.message}")
                    return@addSnapshotListener
                }
                val trades = snap?.toObjects(Trade::class.java) ?: emptyList()
                trySend(trades.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
        awaitClose { listener.remove() }
    }
}
