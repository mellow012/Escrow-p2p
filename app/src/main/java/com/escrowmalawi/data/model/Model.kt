package com.escrowmalawi.data.model


import com.google.firebase.Timestamp
import java.util.UUID

// ── User ─────────────────────────────────────────────────────────────────────
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String="",
    val phone: String = "",
    val profilePictureUrl: String = "",
    val isVerified: Boolean = false,
    val walletAddress: String = "",     // Ethereum wallet address (optional at registration)
    val avgRating: Double = 0.0,        // Cached average of reviews received (0 if none yet)
    val ratingCount: Int = 0,           // Cached count of reviews received
    val createdAt: Timestamp = Timestamp.now()
)

// ── Trade ────────────────────────────────────────────────────────────────────
enum class TradeStatus {
    PENDING,    // Buyer created; awaiting seller acceptance
    ACTIVE,     // Seller accepted; funds locked in escrow
    COMPLETED,  // Buyer confirmed delivery; funds released
    REFUNDED,   // Seller failed; buyer refunded
    REJECTED    // Seller rejected the trade request
}

data class Trade(
    val id: String = "",
    val buyerUid: String = "",
    val buyerName: String = "",
    val sellerUid: String = "",
    val sellerName: String = "",
    val sellerPhone: String = "",
    val sellerWalletAddress: String = "", // Added
    val buyerWalletAddress: String = "",  // Added
    val itemDescription: String = "",
    val listingId: String = "",         // Listing this trade is for (blank for legacy/manual trades)
    val quantity: Int = 1,              // Units reserved from the listing's stock
    val amount: Double = 0.0,           // Amount in MWK (display); ETH handled on-chain
    val status: String = TradeStatus.PENDING.name,
    val blockchainTradeId: String = "", // keccak256 hash used on-chain
    val txHash: String = "",            // Ethereum transaction hash when funds locked
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    // Timestamps that drive the escrow automation rules (null until that step happens)
    val acceptedAt: Timestamp? = null,  // seller accepted
    val paidAt: Timestamp? = null,      // payment confirmed (set by the backend)
    val deliveredAt: Timestamp? = null  // seller marked the item delivered
)

// ── EscrowState ──────────────────────────────────────────────────────────────
data class EscrowState(
    val tradeId: String = "",
    val lockedAmountWei: String = "0",  // Use String for BigInteger safety
    val isReleased: Boolean = false,
    val isRefunded: Boolean = false,
    val txHash: String = ""
)

// ── Notification ─────────────────────────────────────────────
enum class NotificationType {
    TRADE_REQUEST,
    TRADE_ACCEPTED,
    FUNDS_LOCKED,
    TRADE_COMPLETED,
    TRADE_REFUNDED,
    TRADE_REJECTED,
    TRADE_DELIVERED,
    SYSTEM
}

data class Notification(
    val id: String = "",
    val recipientUid: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.SYSTEM.name,
    val tradeId: String? = null,
    val listingId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)

// ── Listing ───────────────────────────────────────────────────────────────────
enum class ListingStatus { ACTIVE, SOLD, REMOVED }

enum class ListingCategory(val label: String) {
    ELECTRONICS   ("Electronics"),
    CLOTHING      ("Clothing"),
    FOOD          ("Food & Groceries"),
    SERVICES      ("Services"),
    VEHICLES      ("Vehicles"),
    PROPERTY      ("Property"),
    AGRICULTURE   ("Agriculture"),
    OTHER         ("Other")
}

data class Listing(
    val id          : String        = "",
    val sellerUid   : String        = "",
    val sellerName  : String        = "",
    val sellerEmail : String        = "",
    val sellerPhone : String        = "",
    val title       : String        = "",
    val description : String        = "",
    val price       : Double        = 0.0,         // MWK, per unit
    val quantity    : Int           = 1,           // Units in stock (older listings default to 1)
    val category    : String        = ListingCategory.OTHER.name,
    val imageUrls   : List<String>  = emptyList(),  // Multiple images
    val status      : String        = ListingStatus.ACTIVE.name,
    val createdAt   : Timestamp     = Timestamp.now(),
    val updatedAt   : Timestamp     = Timestamp.now()
)

// ── Review ───────────────────────────────────────────────────────────────────
data class Review(
    val id: String = "",
    val tradeId: String = "",
    val raterUid: String = "",      // who left the review
    val raterName: String = "",
    val ratedUid: String = "",      // who the review is about
    val rating: Int = 5,            // 1..5
    val comment: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

// ── ListingEnquiry ────────────────────────────────────────────────────────────
data class ListingEnquiry(
    val id: String = "",
    val listingId: String = "",
    val userUid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhone: String = "",
    val message: String = "",
    val status: String = "PENDING", // PENDING, NOTIFIED, COMPLETED
    val createdAt: Timestamp = Timestamp.now()
)

/** True when a listing can't be bought any more (out of stock or not active). */
fun Listing.isSoldOut(): Boolean = status != ListingStatus.ACTIVE.name || quantity <= 0
