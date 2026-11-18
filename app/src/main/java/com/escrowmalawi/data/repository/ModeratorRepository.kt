package com.escrowmalawi.data.repository

import com.escrowmalawi.data.model.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Talks to the backend's admin routes (under /api/admin) with the signed-in user's Firebase ID token.
 * The server only accepts the token if the account's email is in its MODERATOR_EMAILS list,
 * so nothing here can be abused by a normal user.
 */
@Singleton
class ModeratorRepository @Inject constructor(
    @Named("paychanguBackendUrl") private val baseUrl: String
) {
    private suspend fun idToken(): String? =
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        slow: Boolean = false
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = idToken() ?: return@withContext Result.failure(Exception("You are signed out"))
            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = if (slow) 75_000 else 25_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()

            if (code !in 200..299) {
                val message = json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: if (code == 401) "Not authorised as a moderator" else "Server error ($code)"
                return@withContext Result.failure(Exception(message))
            }
            Result.success(json ?: JSONObject())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isModerator(): Boolean =
        request("GET", "/api/admin/me").getOrNull()?.optBoolean("moderator", false) ?: false

    suspend fun overview(): Result<ModOverview> = request("GET", "/api/admin/overview").mapCatching { j ->
        val counts = j.optJSONObject("statusCounts")
        val statusCounts: Map<String, Int> =
            counts?.let { c -> c.keys().asSequence().associateWith { key -> c.optInt(key) } } ?: emptyMap()
        val a = j.optJSONObject("arbiter")
        ModOverview(
            statusCounts = statusCounts,
            completedVolume = j.dbl("completedVolume"),
            users = j.intVal("users"),
            activeListings = j.intVal("activeListings"),
            openDisputes = j.intVal("openDisputes"),
            arbiter = ModArbiter(a?.str("address").orEmpty(), a?.dbl("balanceEth") ?: 0.0, a?.str("error").orEmpty()),
            recent = j.optJSONArray("recent").objects { r ->
                ModRecentTrade(
                    r.str("id"), r.str("itemDescription").trim(), r.dbl("amount"), r.str("status"),
                    r.str("buyerName").trim(), r.str("sellerName").trim(), r.lng("updatedAt")
                )
            }
        )
    }

    suspend fun disputes(): Result<List<ModDispute>> = request("GET", "/api/admin/disputes").mapCatching { j ->
        j.optJSONArray("items").objects { d ->
            val t = d.optJSONObject("trade")
            ModDispute(
                tradeId = d.str("tradeId"), status = d.str("status"), openedByRole = d.str("openedByRole"),
                createdAt = d.lng("createdAt"), resolvedAt = d.lng("resolvedAt"), aiAnalyzedAt = d.lng("aiAnalyzedAt"),
                buyerSubmittedAt = d.lng("buyerSubmittedAt"), sellerSubmittedAt = d.lng("sellerSubmittedAt"),
                buyerStatement = d.str("buyerStatement"), sellerStatement = d.str("sellerStatement"),
                buyerEvidence = d.optJSONArray("buyerEvidence").strings(),
                sellerEvidence = d.optJSONArray("sellerEvidence").strings(),
                aiProposal = d.optJSONObject("aiProposal")?.let(::parseProposal),
                aiError = d.str("aiError"),
                resolution = d.str("resolution"), resolveTxHash = d.str("resolveTxHash"), moderatorNote = d.str("moderatorNote"),
                itemDescription = t?.str("itemDescription").orEmpty().trim(), amount = t?.dbl("amount") ?: 0.0,
                buyerName = t?.str("buyerName").orEmpty().trim(), sellerName = t?.str("sellerName").orEmpty().trim()
            )
        }
    }

    suspend fun analyze(tradeId: String): Result<Unit> =
        request("POST", "/api/admin/disputes/$tradeId/analyze", JSONObject(), slow = true).map { }

    /** Sends resolveDispute on-chain; returns the transaction hash. Can take up to a minute. */
    suspend fun resolve(tradeId: String, outcome: String, note: String): Result<String> =
        request(
            "POST", "/api/admin/disputes/$tradeId/resolve",
            JSONObject().put("outcome", outcome).put("note", note), slow = true
        ).map { it.str("txHash") }

    suspend fun users(): Result<ModUsersData> = request("GET", "/api/admin/users").mapCatching { j ->
        val m = j.getJSONObject("metrics")
        ModUsersData(
            metrics = ModUserMetrics(
                m.intVal("totalUsers"), m.intVal("joined7"), m.intVal("joined30"), m.intVal("active7"),
                m.intVal("active30"), m.intVal("everTraded"), m.intVal("verified"), m.intVal("withWallet")
            ),
            users = j.optJSONArray("users").objects { u ->
                ModUser(
                    u.str("uid"), u.str("name").trim(), u.str("email"), u.str("phone"),
                    u.optBoolean("isVerified", false), u.str("walletAddress"),
                    u.dbl("avgRating"), u.intVal("ratingCount"), u.lng("createdAt"),
                    u.intVal("tradesBuying"), u.intVal("tradesSelling"), u.lng("lastActive")
                )
            }
        )
    }

    suspend fun userTrades(uid: String): Result<ModUserTrades> =
        request("GET", "/api/admin/users/$uid/trades").mapCatching { j ->
            val totals = j.getJSONObject("totals")
            ModUserTrades(
                trades = j.optJSONArray("trades").objects { t ->
                    ModUserTrade(
                        t.str("id"), t.str("role"), t.str("itemDescription"), t.dbl("amount"), t.str("status"),
                        t.str("counterparty"), t.lng("updatedAt"), t.str("chainTxHash"), t.optBoolean("hasDispute", false)
                    )
                },
                asBuyer = totals.intVal("asBuyer"), asSeller = totals.intVal("asSeller"),
                spent = totals.dbl("spent"), earned = totals.dbl("earned")
            )
        }

    suspend fun setVerified(uid: String, verified: Boolean): Result<Unit> =
        request("POST", "/api/admin/users/$uid/verify", JSONObject().put("verified", verified)).map { }

    suspend fun verifications(): Result<List<ModVerification>> =
        request("GET", "/api/admin/verifications").mapCatching { j ->
            j.optJSONArray("items").objects { v ->
                ModVerification(
                    v.str("uid"), v.str("name").trim(), v.str("status"), v.str("idImageUrl"), v.str("selfieUrl"),
                    v.str("rejectReason"), v.lng("submittedAt"), v.lng("reviewedAt")
                )
            }
        }

    suspend fun reviewVerification(uid: String, approve: Boolean, reason: String): Result<Unit> =
        request(
            "POST", "/api/admin/verifications/$uid",
            JSONObject().put("action", if (approve) "approve" else "reject").put("reason", reason)
        ).map { }

    // org.json returns "null" text for JSON null, so check first
    private fun parseProposal(p: JSONObject) = ModAiProposal(
        summary = p.str("summary"), buyerClaim = p.str("buyerClaim"), sellerClaim = p.str("sellerClaim"),
        keyFacts = p.optJSONArray("keyFacts").strings(), evidenceNotes = p.optJSONArray("evidenceNotes").strings(),
        proposedOutcome = p.str("proposedOutcome"), reasoning = p.str("reasoning"), confidence = p.str("confidence")
    )

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
    private fun JSONObject.lng(k: String): Long? = if (isNull(k)) null else optLong(k)
    private fun JSONObject.dbl(k: String): Double = if (isNull(k)) 0.0 else optDouble(k, 0.0)
    private fun JSONObject.intVal(k: String): Int = if (isNull(k)) 0 else optInt(k, 0)

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }

    private fun <T> JSONArray?.objects(block: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(block) }
}