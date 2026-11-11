package com.escrowmalawi.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Real calls to the deployed P2PEscrow contract (Sepolia).
 *
 * The contract holds no money. It records each trade's lifecycle and enforces
 * who may do what and when. Every call is signed on the device by the acting
 * user's own wallet (key from WalletManager) and pays its own gas.
 *
 * Gas is ESTIMATED LIVE from the node (never hardcoded): Sepolia charges far
 * more for new storage than a local test chain, so fixed limits fail.
 */
@Singleton
class EthereumService @Inject constructor(
    @Named("rpcUrl") private val rpcUrl: String,
    @Named("contractAddress") private val contractAddress: String
) {
    companion object {
        const val SEPOLIA_CHAIN_ID = 11155111L
        private const val RECEIPT_POLL_MS = 3000L
        private const val RECEIPT_ATTEMPTS = 40          // ~2 minutes
        private const val GAS_LIMIT_MARGIN_PERCENT = 130 // estimate x 1.3
        private const val GAS_PRICE_MARGIN_PERCENT = 120 // node price x 1.2
    }

    private val web3j: Web3j by lazy { Web3j.build(HttpService(rpcUrl)) }

    // Demo mode signs many trades from the same two accounts. Sending must be
    // one-at-a-time so two transactions never grab the same nonce.
    private val sendLock = Mutex()

    fun addressOf(privateKeyHex: String): String = credentialsFor(privateKeyHex).address

    fun credentialsFor(privateKeyHex: String): Credentials = Credentials.create(privateKeyHex)

    // ── Contract actions (each returns the confirmed transaction hash) ───

    suspend fun createTrade(c: Credentials, tradeId: String, seller: String, amountMwk: Long): Result<String> =
        send(c, fn("createTrade", Bytes32(idBytes(tradeId)), Address(seller), Uint256(BigInteger.valueOf(amountMwk))))

    suspend fun acceptTrade(c: Credentials, tradeId: String): Result<String> =
        send(c, fn("acceptTrade", Bytes32(idBytes(tradeId))))

    suspend fun rejectTrade(c: Credentials, tradeId: String): Result<String> =
        send(c, fn("rejectTrade", Bytes32(idBytes(tradeId))))

    /** [paymentRef] is the PayChangu reference; only its keccak256 hash goes on-chain. */
    suspend fun recordPayment(c: Credentials, tradeId: String, paymentRef: String): Result<String> =
        send(c, fn("recordPayment", Bytes32(idBytes(tradeId)), Bytes32(Hash.sha3(paymentRef.toByteArray(Charsets.UTF_8)))))

    suspend fun markDelivered(c: Credentials, tradeId: String): Result<String> =
        send(c, fn("markDelivered", Bytes32(idBytes(tradeId))))

    suspend fun confirmReceipt(c: Credentials, tradeId: String): Result<String> =
        send(c, fn("confirmReceipt", Bytes32(idBytes(tradeId))))

    suspend fun sellerRefund(c: Credentials, tradeId: String): Result<String> =
        send(c, fn("sellerRefund", Bytes32(idBytes(tradeId))))

    // ── Wallet balance (for the "gas balance" row in Profile) ────────────

    suspend fun getBalanceEth(address: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val resp = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()
            if (resp.hasError()) throw IllegalStateException(resp.error.message ?: "Balance lookup failed")
            Convert.fromWei(BigDecimal(resp.balance), Convert.Unit.ETHER)
                .setScale(5, RoundingMode.DOWN).toPlainString()
        }
    }

    // ── Internals ────────────────────────────────────────────────────────

    private fun fn(name: String, vararg inputs: Type<*>): Function =
        Function(name, inputs.toList(), emptyList<TypeReference<*>>())

    /** Firestore stores the id as 0x + keccak256(docId) = exactly 32 bytes. */
    private fun idBytes(tradeId: String): ByteArray {
        val bytes = Numeric.hexStringToByteArray(tradeId)
        require(bytes.size == 32) { "Trade has no valid on-chain id" }
        return bytes
    }

    private fun requireConfigured() {
        check(rpcUrl.isNotBlank()) { "RPC_URL is missing. Add RPC_URL=... to local.properties and rebuild." }
        check(contractAddress.isNotBlank()) { "Contract address is not configured." }
    }

    private suspend fun send(credentials: Credentials, function: Function): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hash = sendLock.withLock { submit(credentials, function) }
                awaitReceipt(hash)   // waiting for the block does not hold the lock
                hash
            }
        }

    /** Estimates gas, signs and broadcasts. Returns the transaction hash (not yet mined). */
    private fun submit(credentials: Credentials, function: Function): String {
        requireConfigured()
        val data = FunctionEncoder.encode(function)

        // 1. Estimate gas live. A revert here (wrong state / wrong caller) costs nothing.
        val estimate = web3j.ethEstimateGas(
            Transaction.createFunctionCallTransaction(
                credentials.address, null, null, null, contractAddress, data
            )
        ).send()
        if (estimate.hasError()) {
            throw IllegalStateException(estimate.error.message ?: "Gas estimation failed")
        }
        val gasLimit = estimate.amountUsed
            .multiply(BigInteger.valueOf(GAS_LIMIT_MARGIN_PERCENT.toLong()))
            .divide(BigInteger.valueOf(100))

        // 2. Price from the node, with a small margin.
        val gasPrice = web3j.ethGasPrice().send().gasPrice
            .multiply(BigInteger.valueOf(GAS_PRICE_MARGIN_PERCENT.toLong()))
            .divide(BigInteger.valueOf(100))

        // 3. Sign (EIP-155, Sepolia) and send.
        val txManager = RawTransactionManager(web3j, credentials, SEPOLIA_CHAIN_ID)
        val sent = txManager.sendTransaction(gasPrice, gasLimit, contractAddress, data, BigInteger.ZERO)
        if (sent.hasError()) {
            throw IllegalStateException(sent.error.message ?: "Transaction rejected")
        }
        return sent.transactionHash
    }

    /** Waits for the block and makes sure the transaction did not revert. */
    private fun awaitReceipt(hash: String) {
        val receipt = PollingTransactionReceiptProcessor(web3j, RECEIPT_POLL_MS, RECEIPT_ATTEMPTS)
            .waitForTransactionReceipt(hash)
        if (!receipt.isStatusOK) throw IllegalStateException("Transaction reverted on-chain")
    }
}
