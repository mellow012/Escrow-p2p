package com.escrowmalawi.domain.usecase

import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.repository.AuthRepository
import com.escrowmalawi.data.repository.EscrowRepository
import com.escrowmalawi.data.repository.TradeRepository
import com.google.firebase.auth.FirebaseUser
import org.web3j.crypto.Credentials
import java.math.BigInteger
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
        amount: Double
    ): Result<Trade> {
        if (itemDescription.isBlank())
            return Result.failure(IllegalArgumentException("Item description is required"))
        if (amount <= 0)
            return Result.failure(IllegalArgumentException("Amount must be greater than 0"))

        val buyerProfile = authRepository.currentUserProfile()
            ?: return Result.failure(IllegalStateException("Not logged in"))

        val sellerProfile = authRepository.getUserProfile(sellerUid)
            ?: return Result.failure(IllegalArgumentException("Seller profile not found"))

        return tradeRepository.createTrade(
            buyerUid        = buyerProfile.uid,
            buyerName       = buyerProfile.name,
            buyerWallet     = buyerProfile.walletAddress,
            sellerUid       = sellerUid,
            sellerName      = sellerName,
            sellerPhone     = sellerPhone,
            sellerWallet    = sellerProfile.walletAddress,
            itemDescription = itemDescription,
            amount          = amount
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
//  ESCROW MODULE USE CASES
// ════════════════════════════════════════════════════════════════════════

class LockFundsUseCase @Inject constructor(
    private val escrowRepository: EscrowRepository
) {
    suspend operator fun invoke(
        credentials      : Credentials,
        tradeId          : String,
        blockchainTradeId: String,
        sellerAddress    : String,
        itemDescription  : String,
        amountWei        : BigInteger
    ): Result<String> = escrowRepository.lockFunds(
        credentials, tradeId, blockchainTradeId, sellerAddress, itemDescription, amountWei
    )
}

class ReleaseFundsUseCase @Inject constructor(
    private val escrowRepository: EscrowRepository
) {
    suspend operator fun invoke(
        credentials      : Credentials,
        tradeId          : String,
        blockchainTradeId: String
    ): Result<Unit> = escrowRepository.releaseFunds(credentials, tradeId, blockchainTradeId)
}

class RefundBuyerUseCase @Inject constructor(
    private val escrowRepository: EscrowRepository
) {
    suspend operator fun invoke(
        credentials      : Credentials,
        tradeId          : String,
        blockchainTradeId: String
    ): Result<Unit> = escrowRepository.refundBuyer(credentials, tradeId, blockchainTradeId)
}