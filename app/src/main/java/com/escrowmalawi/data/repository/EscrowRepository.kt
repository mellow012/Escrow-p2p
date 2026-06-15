package com.escrowmalawi.data.repository

import com.escrowmalawi.blockchain.EthereumService
import org.web3j.crypto.Credentials
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escrow Module – EscrowRepository
 *
 * Orchestrates:
 *  • Locking funds on-chain (buyer pays into smart contract)
 *  • Releasing funds on-chain (buyer confirms → seller gets paid)
 *  • Refunding buyer on-chain (seller fails → buyer refunded)
 *  • Mirrors each result into Firestore via TradeRepository
 */
@Singleton
class EscrowRepository @Inject constructor(
    private val ethereumService: EthereumService,
    private val tradeRepository: TradeRepository
) {
    /**
     * Lock funds in the smart contract.
     * Called after the seller accepts the trade.
     *
     * @param credentials    Buyer's Ethereum credentials
     * @param tradeId        Firestore document ID
     * @param blockchainTradeId  keccak256 hash used as the on-chain key
     * @param sellerAddress  Seller's Ethereum wallet address
     * @param itemDescription Trade description
     * @param amountWei      Amount to lock, in Wei
     */
    suspend fun lockFunds(
        credentials: Credentials,
        tradeId: String,
        blockchainTradeId: String,
        sellerAddress: String,
        itemDescription: String,
        amountWei: BigInteger
    ): Result<String> {
        val result = ethereumService.createAndLockFunds(
            credentials, blockchainTradeId, sellerAddress, itemDescription, amountWei
        )
        return if (result.isSuccess) {
            val txHash = result.getOrThrow()
            tradeRepository.markFundsLocked(tradeId, txHash)
            Result.success(txHash)
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }

    /**
     * Release funds to the seller.
     * Buyer calls this after confirming delivery.
     */
    suspend fun releaseFunds(
        credentials: Credentials,
        tradeId: String,
        blockchainTradeId: String
    ): Result<Unit> {
        val result = ethereumService.releaseFunds(credentials, blockchainTradeId)
        return if (result.isSuccess) {
            val txHash = result.getOrThrow()
            tradeRepository.markCompleted(tradeId, txHash)
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }

    /**
     * Refund the buyer.
     * Called when seller fails to deliver.
     */
    suspend fun refundBuyer(
        credentials: Credentials,
        tradeId: String,
        blockchainTradeId: String
    ): Result<Unit> {
        val result = ethereumService.refundBuyer(credentials, blockchainTradeId)
        return if (result.isSuccess) {
            val txHash = result.getOrThrow()
            tradeRepository.markRefunded(tradeId, txHash)
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }

    /**
     * Read-only: returns how much Wei is locked for a given trade.
     */
    suspend fun getLockedAmount(blockchainTradeId: String): BigInteger =
        ethereumService.getLockedAmount(blockchainTradeId)

    /**
     * Helper to build credentials from a raw private key string.
     * In production, retrieve this from Android Keystore, not plain storage.
     */
    fun buildCredentials(privateKeyHex: String): Credentials =
        ethereumService.buildCredentials(privateKeyHex)
}
