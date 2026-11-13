package com.escrowmalawi.blockchain

import android.util.Log
import com.escrowmalawi.data.local.WalletManager
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.data.repository.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.web3j.crypto.Credentials
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class ChainAction { CREATE, ACCEPT, REJECT, PAYMENT, DELIVER, CONFIRM, REFUND, DISPUTE }

/**
 * Keeps the P2PEscrow contract in step with each trade.
 *
 * The contract needs a strict order (Created > Accepted > Funded > Delivered >
 * Completed/Refunded), so instead of sending one fire-and-forget tx per action
 * we work out, from the trade's Firestore state, which steps SHOULD be on-chain
 * (requiredSteps), compare with the steps already recorded (trade.chainDone) and
 * send whatever is missing, strictly in order, one at a time.
 *
 * Triggers: record() right after a Firestore step succeeds, and syncTrades()
 * from the live buyer/seller trade lists (this also catches changes made by the
 * backend automation, and anything an earlier failed attempt missed).
 *
 * Needs a new field on Trade:  val chainDone: List<String> = emptyList()
 */
@Singleton
class ChainRecorder @Inject constructor(
    private val ethereum: EthereumService,
    private val walletManager: WalletManager,
    private val authRepository: AuthRepository,
    firestore: FirebaseFirestore,
    @Named("demoBuyerKey") demoBuyerKey: String,
    @Named("demoSellerKey") demoSellerKey: String
) {
    private companion object {
        const val TAG = "ChainRecorder"
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 8_000L
        const val MAX_ERROR_LENGTH = 160
    }

    // DEMO MODE: shared test Buyer/Seller accounts sign every user's steps, so ANY
    // device can send ANY step. Test accounts with fake Sepolia ETH only.
    private val demoBuyerKey = demoBuyerKey.trim()
    private val demoSellerKey = demoSellerKey.trim()
    private val demoMode = this.demoBuyerKey.isNotBlank() && this.demoSellerKey.isNotBlank()
    private val buyerActions = setOf(ChainAction.CREATE, ChainAction.PAYMENT, ChainAction.CONFIRM)

    private val trades = firestore.collection("trades")
    private val disputes = firestore.collection("disputes")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val queued: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    /** A Firestore step just succeeded: bring the chain up to date for this trade. */
    @Suppress("UNUSED_PARAMETER")
    fun record(tradeId: String, action: ChainAction) = reconcile(tradeId)

    /** Called from the live trade lists. Skips trades that already have an unresolved chainError. */
    fun syncTrades(list: List<Trade>) {
        list.forEach { t ->
            if (t.chainError.isBlank() && pendingSteps(t).isNotEmpty()) reconcile(t.id)
        }
    }

    /** Kept so existing callers still compile. */
    fun syncBuyerPayments(buyerTrades: List<Trade>) = syncTrades(buyerTrades)

    // ── What should be on-chain, in order ────────────────────────────────

    private fun requiredSteps(t: Trade): List<ChainAction> {
        val steps = mutableListOf(ChainAction.CREATE)
        when (t.status) {
            TradeStatus.PENDING.name -> Unit
            TradeStatus.REJECTED.name -> steps += ChainAction.REJECT
            else -> { // ACTIVE, COMPLETED, REFUNDED, DISPUTED
                steps += ChainAction.ACCEPT
                // Once disputed, the moderator (server, Arbiter key) ends the trade on-chain,
                // so this app never sends CONFIRM or REFUND for a disputed trade.
                val disputed = t.status == TradeStatus.DISPUTED.name || t.disputeOpenedBy.isNotBlank()
                val completed = !disputed && t.status == TradeStatus.COMPLETED.name
                val paid = t.paidAt != null || completed || disputed
                if (paid) steps += ChainAction.PAYMENT
                if (paid && (t.deliveredAt != null || completed)) steps += ChainAction.DELIVER
                if (disputed) steps += ChainAction.DISPUTE
                if (completed) steps += ChainAction.CONFIRM
                if (!disputed && t.status == TradeStatus.REFUNDED.name && t.paidAt != null) steps += ChainAction.REFUND
            }
        }
        return steps
    }

    private fun pendingSteps(t: Trade) = requiredSteps(t).filter { it.name !in t.chainDone }

    // ── Reconcile loop ───────────────────────────────────────────────────

    private fun reconcile(tradeId: String) {
        if (!queued.add(tradeId)) return          // one waiting run per trade is enough
        scope.launch {
            val mutex = locks.getOrPut(tradeId) { Mutex() }
            mutex.withLock {
                queued.remove(tradeId)
                try {
                    runSteps(tradeId)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error for $tradeId", e)
                }
            }
        }
    }

    private suspend fun runSteps(tradeId: String) {
        val uid = authRepository.currentFirebaseUser()?.uid ?: return
        while (true) {
            val trade = trades.document(tradeId).get().await().toObject(Trade::class.java) ?: return
            val next = pendingSteps(trade).firstOrNull() ?: return
            if (!canSign(uid, trade, next)) return   // the other party's device will continue
            if (!sendStep(uid, trade, next)) return  // stop at the first failure; order matters
        }
    }

    /** Which side signs a step. A dispute is signed by whoever opened it. */
    private fun signsAsBuyer(trade: Trade, action: ChainAction): Boolean =
        if (action == ChainAction.DISPUTE) trade.disputeOpenedBy != "seller" else action in buyerActions

    private fun canSign(uid: String, trade: Trade, action: ChainAction): Boolean {
        if (demoMode) return true
        return if (signsAsBuyer(trade, action)) uid == trade.buyerUid else uid == trade.sellerUid
    }

    private suspend fun sendStep(uid: String, trade: Trade, action: ChainAction): Boolean {
        val credentials: Credentials
        val sellerAddress: String
        if (demoMode) {
            credentials = ethereum.credentialsFor(if (signsAsBuyer(trade, action)) demoBuyerKey else demoSellerKey)
            sellerAddress = ethereum.addressOf(demoSellerKey)
        } else {
            val privateKey = walletManager.getPrivateKey(uid)
            if (privateKey.isNullOrBlank()) {
                saveError(trade.id, action, "No wallet on this device")
                return false
            }
            credentials = ethereum.credentialsFor(privateKey)
            sellerAddress = trade.sellerWalletAddress
        }
        if (action == ChainAction.CREATE && sellerAddress.isBlank()) {
            saveError(trade.id, action, "Seller has no wallet yet")
            return false
        }

        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val result: Result<String> = when (action) {
                ChainAction.CREATE -> ethereum.createTrade(
                    credentials, trade.blockchainTradeId, sellerAddress, trade.amount.toLong()
                )
                ChainAction.ACCEPT -> ethereum.acceptTrade(credentials, trade.blockchainTradeId)
                ChainAction.REJECT -> ethereum.rejectTrade(credentials, trade.blockchainTradeId)
                ChainAction.PAYMENT -> ethereum.recordPayment(
                    credentials, trade.blockchainTradeId, trade.txHash.ifBlank { "PAYCHANGU" }
                )
                ChainAction.DELIVER -> ethereum.markDelivered(credentials, trade.blockchainTradeId)
                ChainAction.CONFIRM -> ethereum.confirmReceipt(credentials, trade.blockchainTradeId)
                ChainAction.REFUND -> ethereum.sellerRefund(credentials, trade.blockchainTradeId)
                ChainAction.DISPUTE -> ethereum.openDispute(credentials, trade.blockchainTradeId, evidenceFor(trade))
            }
            if (result.isSuccess) {
                saveSuccess(trade.id, action, result.getOrThrow())
                return true
            }
            lastError = result.exceptionOrNull()
            Log.w(TAG, "$action attempt ${attempt + 1} failed: ${lastError?.message}")
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }

        // Another phone may have sent this step while we were retrying: if so, carry on.
        val fresh = trades.document(trade.id).get().await().toObject(Trade::class.java)
        if (fresh != null && action.name in fresh.chainDone) return true

        saveError(trade.id, action, lastError?.message ?: "Unknown error")
        return false
    }

    /** Text hashed on-chain as the dispute evidence: the opener's statement and photo links. */
    private suspend fun evidenceFor(trade: Trade): String {
        val role = if (trade.disputeOpenedBy == "seller") "seller" else "buyer"
        val d = runCatching { disputes.document(trade.id).get().await() }.getOrNull()
        val statement = d?.getString("${role}Statement").orEmpty()
        val photos = (d?.get("${role}Evidence") as? List<*>)?.joinToString(",") { it.toString() }.orEmpty()
        return "${trade.id}|$role|$statement|$photos"
    }

    private suspend fun saveSuccess(tradeId: String, action: ChainAction, txHash: String) {
        val updates = mutableMapOf<String, Any>(
            "chainTxHash" to txHash,
            "chainError" to "",
            "chainDone" to FieldValue.arrayUnion(action.name)
        )
        if (action == ChainAction.PAYMENT) updates["chainPaid"] = true
        runCatching { trades.document(tradeId).update(updates).await() }
            .onFailure { Log.e(TAG, "Could not save chain result", it) }
    }

    private suspend fun saveError(tradeId: String, action: ChainAction, message: String) {
        val text = "${action.name}: $message".take(MAX_ERROR_LENGTH)
        runCatching { trades.document(tradeId).update("chainError", text).await() }
            .onFailure { Log.e(TAG, "Could not save chain error", it) }
    }
}