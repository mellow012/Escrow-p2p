package com.escrowmalawi.data.model

/** Plain data returned by the moderator API on the backend. Times are epoch milliseconds. */

data class ModArbiter(val address: String = "", val balanceEth: Double = 0.0, val error: String = "")

data class ModRecentTrade(
    val id: String, val itemDescription: String, val amount: Double, val status: String,
    val buyerName: String, val sellerName: String, val updatedAt: Long?
)

data class ModOverview(
    val statusCounts: Map<String, Int>,
    val completedVolume: Double,
    val users: Int,
    val activeListings: Int,
    val openDisputes: Int,
    val arbiter: ModArbiter,
    val recent: List<ModRecentTrade>
)

data class ModAiProposal(
    val summary: String, val buyerClaim: String, val sellerClaim: String,
    val keyFacts: List<String>, val evidenceNotes: List<String>,
    val proposedOutcome: String, val reasoning: String, val confidence: String
)

data class ModDispute(
    val tradeId: String,
    val status: String,                 // "open" | "resolved"
    val openedByRole: String,
    val createdAt: Long?, val resolvedAt: Long?, val aiAnalyzedAt: Long?,
    val buyerSubmittedAt: Long?, val sellerSubmittedAt: Long?,
    val buyerStatement: String, val sellerStatement: String,
    val buyerEvidence: List<String>, val sellerEvidence: List<String>,
    val aiProposal: ModAiProposal?, val aiError: String,
    val resolution: String, val resolveTxHash: String, val moderatorNote: String,
    val itemDescription: String, val amount: Double,
    val buyerName: String, val sellerName: String,
    val buyerPhoto: String? = null, val sellerPhoto: String? = null
)

data class ModUser(
    val uid: String, val name: String, val email: String, val phone: String,
    val isVerified: Boolean, val walletAddress: String,
    val avgRating: Double, val ratingCount: Int, val createdAt: Long?,
    val tradesBuying: Int, val tradesSelling: Int, val lastActive: Long?,
    val photoUrl: String? = null
)

data class ModUserMetrics(
    val totalUsers: Int, val joined7: Int, val joined30: Int, val active7: Int, val active30: Int,
    val everTraded: Int, val verified: Int, val withWallet: Int
)

data class ModUsersData(val metrics: ModUserMetrics, val users: List<ModUser>)

data class ModUserTrade(
    val id: String, val role: String, val itemDescription: String, val amount: Double, val status: String,
    val counterparty: String, val updatedAt: Long?, val chainTxHash: String, val hasDispute: Boolean
)

data class ModUserTrades(
    val trades: List<ModUserTrade>, val asBuyer: Int, val asSeller: Int, val spent: Double, val earned: Double
)

data class ModVerification(
    val uid: String, val name: String, val status: String, val idImageUrl: String, val selfieUrl: String,
    val rejectReason: String, val submittedAt: Long?, val reviewedAt: Long?
)
