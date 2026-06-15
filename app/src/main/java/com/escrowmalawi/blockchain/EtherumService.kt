package com.escrowmalawi.blockchain

import android.util.Log
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.tx.gas.StaticGasProvider
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Smart Contract Module – EthereumService
 *
 * Wraps Web3j to interact with the deployed EscrowMalawi.sol contract.
 *
 * Functions:
 *   • createAndLockFunds  – buyer locks ETH into escrow
 *   • releaseFunds        – buyer confirms delivery → seller receives ETH
 *   • refundBuyer         – either party triggers refund
 *   • getLockedAmount     – read-only query of locked ETH
 *
 * NOTE: Replace CONTRACT_ADDRESS and RPC_URL with your actual deployment values.
 *       Store the private key securely (Android Keystore in production).
 */
@Singleton
class EthereumService @Inject constructor(
    @Named("rpcUrl") private val rpcUrl: String,
    @Named("contractAddress") private val contractAddress: String
) {
    companion object {
        private const val TAG = "EthereumService"

        // Gas settings (adjust for Sepolia testnet or Polygon)
        private val GAS_PRICE = BigInteger.valueOf(20_000_000_000L)   // 20 Gwei
        private val GAS_LIMIT = BigInteger.valueOf(300_000L)
    }

    private val web3j: Web3j by lazy {
        Web3j.build(HttpService(rpcUrl))
    }

    // ── Build a credentials object from a private key ────────────────────
    fun buildCredentials(privateKeyHex: String): Credentials =
        Credentials.create(privateKeyHex)

    // ── Create and lock funds (Mocked) ───────────────────────────────────
    suspend fun createAndLockFunds(
        credentials: Credentials,
        blockchainTradeId: String,
        sellerAddress: String,
        itemDescription: String,
        amountWei: BigInteger
    ): Result<String> {
        return try {
            // SIMULATED DELAY
            kotlinx.coroutines.delay(2000)
            val mockTxHash = "0xmock_success_" + java.util.UUID.randomUUID().toString().replace("-", "")
            Log.d(TAG, "MOCK createAndLockFunds tx: $mockTxHash")
            Result.success(mockTxHash)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Release funds (Mocked) ──────────────────────────────────────────
    suspend fun releaseFunds(
        credentials: Credentials,
        blockchainTradeId: String
    ): Result<String> {
        return try {
            kotlinx.coroutines.delay(2000)
            val mockTxHash = "0xmock_release_" + java.util.UUID.randomUUID().toString().replace("-", "")
            Result.success(mockTxHash)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Refund buyer (Mocked) ────────────────────────────────────────────
    suspend fun refundBuyer(
        credentials: Credentials,
        blockchainTradeId: String
    ): Result<String> {
        return try {
            kotlinx.coroutines.delay(2000)
            val mockTxHash = "0xmock_refund_" + java.util.UUID.randomUUID().toString().replace("-", "")
            Result.success(mockTxHash)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Read locked amount (Mocked) ──────────────────────────────────────
    suspend fun getLockedAmount(blockchainTradeId: String): BigInteger {
        // Just return a dummy value so the UI shows something is locked
        return BigInteger("1000000000000000000") // 1.0 ETH
    }

    // ── Internal: send a signed transaction ─────────────────────────────
    private fun sendTransaction(
        credentials: Credentials,
        function: Function,
        valueWei: BigInteger
    ): String {
        val gasProvider = StaticGasProvider(GAS_PRICE, GAS_LIMIT)
        val txManager = RawTransactionManager(web3j, credentials)
        val encodedFunction = FunctionEncoder.encode(function)

        val receipt = txManager.sendTransaction(
            GAS_PRICE,
            GAS_LIMIT,
            contractAddress,
            encodedFunction,
            valueWei
        )
        return receipt.transactionHash
    }

    // ── Utility: convert 0x-prefixed hex string to 32-byte array ─────────
    private fun hexToBytes32(hex: String): ByteArray {
        val clean = hex.removePrefix("0x")
        val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.copyOf(32) // pad to 32 bytes
    }
}
