package com.escrowmalawi.domain.usecase

import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.repository.AuthRepository
import com.escrowmalawi.data.repository.TradeRepository
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject

// ════════════════════════════════════════════════════════════════════════
//  USER MODULE USE CASES
// ════════════════════════════════════════════════════════════════════════

class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Result<FirebaseUser> {
        if (name.isBlank())
            return Result.failure(IllegalArgumentException("Name is required"))
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return Result.failure(IllegalArgumentException("Enter a valid email address"))
        if (password.length < 6)
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        return authRepository.register(name.trim(), email.trim(), phone.trim(), password)
    }
}

class LoginUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<FirebaseUser> {
        if (email.isBlank())
            return Result.failure(IllegalArgumentException("Email is required"))
        if (password.isBlank())
            return Result.failure(IllegalArgumentException("Password is required"))
        return authRepository.login(email.trim(), password)
    }
}

class LogoutUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() = authRepository.logout()
}

class UpdateProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        uid: String,
        name: String,
        phone: String,
        profilePictureUrl: String,
        walletAddress: String
    ): Result<Unit> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be empty"))
        if (phone.isBlank()) return Result.failure(IllegalArgumentException("Phone cannot be empty"))
        return authRepository.updateProfile(uid, name, phone, profilePictureUrl, walletAddress)
    }
}

// ════════════════════════════════════════════════════════════════════════
//  TRADE MODULE USE CASES
// ════════════════════════════════════════════════════════════════════════

class CreateTradeUseCase @Inject constructor(
    private val tradeRepository: TradeRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        sellerUid: String,
        sellerName: String,
        sellerPhone: String,
        itemDescription: String,
        amount: Double,
        listingId: String? = null
    ): Result<Trade> {
        if (itemDescription.isBlank())
            return Result.failure(IllegalArgumentException("Item description is required"))
        if (amount <= 0)
            return Result.failure(IllegalArgumentException("Amount must be greater than 0"))

        val buyerProfile = authRepository.currentUserProfile()
            ?: return Result.failure(IllegalStateException("Not logged in"))

        // The seller's profile doc may be missing (see AuthRepository.currentUserProfile);
        // that must not block the trade. Wallet is a legacy field, blank is fine.
        val sellerProfile = authRepository.getUserProfile(sellerUid)

        return tradeRepository.createTrade(
            buyerUid        = buyerProfile.uid,
            buyerName       = buyerProfile.name,
            buyerWallet     = buyerProfile.walletAddress,
            sellerUid       = sellerUid,
            sellerName      = sellerName,
            sellerPhone     = sellerPhone,
            sellerWallet    = sellerProfile?.walletAddress ?: "",
            itemDescription = itemDescription,
            amount          = amount,
            listingId       = listingId
        )
    }
}

class AcceptTradeUseCase @Inject constructor(
    private val tradeRepository: TradeRepository
) {
    suspend operator fun invoke(tradeId: String): Result<Unit> =
        tradeRepository.acceptTrade(tradeId)
}

class RejectTradeUseCase @Inject constructor(
    private val tradeRepository: TradeRepository
) {
    suspend operator fun invoke(tradeId: String): Result<Unit> =
        tradeRepository.rejectTrade(tradeId)
}

// ════════════════════════════════════════════════════════════════════════
//  ESCROW MODULE
// ════════════════════════════════════════════════════════════════════════
// No use-case wrappers here — EscrowViewModel calls EscrowRepository
// directly (same lightweight pattern as Notification/Review), since the
// PayChangu-backed flow has no extra validation logic worth wrapping.