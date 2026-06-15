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
    val amount: Double = 0.0,           // Amount in MWK (display); ETH handled on-chain
    val status: String = TradeStatus.PENDING.name,
    val blockchainTradeId: String = "", // keccak256 hash used on-chain
    val txHash: String = "",            // Ethereum transaction hash when funds locked
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
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
    SYSTEM
}

data class Notification(
    val id: String = "",
    val recipientUid: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.SYSTEM.name,
    val tradeId: String? = null,
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
    val price       : Double        = 0.0,         // MWK
    val category    : String        = ListingCategory.OTHER.name,
    val imageUrls   : List<String>  = emptyList(),  // Multiple images
    val status      : String        = ListingStatus.ACTIVE.name,
    val createdAt   : Timestamp     = Timestamp.now(),
    val updatedAt   : Timestamp     = Timestamp.now()
)