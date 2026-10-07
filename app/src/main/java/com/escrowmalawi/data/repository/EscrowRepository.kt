package com.escrowmalawi.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Escrow Module – EscrowRepository
 *
 * Fiat escrow via PayChangu, replacing the earlier Web3j/Ethereum on-chain
 * version. The actual "hold funds, then release" behaviour is enforced by
 * when we tell the backend to move money — PayChangu itself has no concept
 * of escrow, it just moves money from A to B. See /paychangu-backend for
 * the server side of this flow.
 *
 * - Locking funds = buyer completes a PayChangu checkout into the
 *   platform's own collection account. Confirmed asynchronously via the
 *   backend's webhook, which writes trade.txHash directly in Firestore —
 *   picked up automatically by TradeRepository's existing real-time
 *   listeners, no polling needed here.
 * - Releasing/refunding = simulated for this project's current scope: a
 *   direct Firestore status update via TradeRepository, no real payout
 *   call to PayChangu. See TradeRepository.markCompleted/markRefunded.
 */
@Singleton
class EscrowRepository @Inject constructor(
    @Named("paychanguBackendUrl") private val backendUrl: String,
    private val tradeRepository: TradeRepository
) {
    /**
     * Calls the backend to create a PayChangu Standard Checkout session for
     * this trade. Returns the checkout URL to open in a browser/Custom Tab.
     * The backend reads the authoritative amount from Firestore itself —
     * we don't send it from the client.
     */
    suspend fun initiateCheckout(
        tradeId: String,
        buyerEmail: String,
        buyerName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$backendUrl/api/paychangu/initiate")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("tradeId", tradeId)
                put("buyerEmail", buyerEmail)
                put("buyerName", buyerName)
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }

            if (responseCode !in 200..299) {
                val errorMsg = try {
                    JSONObject(responseText).optString("error", "Payment initiation failed")
                } catch (_: Exception) {
                    "Payment initiation failed (HTTP $responseCode)"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseText)
            val checkoutUrl = json.optString("checkoutUrl")
            if (checkoutUrl.isBlank()) {
                return@withContext Result.failure(Exception("Server did not return a checkout URL"))
            }

            Result.success(checkoutUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Simulated release — buyer confirmed receipt. No real payout call yet. */
    suspend fun releaseFunds(tradeId: String): Result<Unit> =
        tradeRepository.markCompleted(tradeId, txHash = "SIMULATED_RELEASE")

    /** Simulated refund — no real payout call yet. */
    suspend fun refundBuyer(tradeId: String): Result<Unit> =
        tradeRepository.markRefunded(tradeId, txHash = "SIMULATED_REFUND")
}
