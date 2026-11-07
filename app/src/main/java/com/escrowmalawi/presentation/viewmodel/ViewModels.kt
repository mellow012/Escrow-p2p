package com.escrowmalawi.presentation.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.User
import com.escrowmalawi.data.repository.AuthRepository
import com.escrowmalawi.data.repository.EscrowRepository
import com.escrowmalawi.data.repository.StorageRepository
import com.escrowmalawi.data.repository.TradeRepository
import com.escrowmalawi.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ════════════════════════════════════════════════════════════════════════
//  UI STATES
// ════════════════════════════════════════════════════════════════════════

sealed class AuthUiState {
    object Idle    : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    object NeedsVerification : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

sealed class TradeUiState {
    object Idle    : TradeUiState()
    object Loading : TradeUiState()
    data class Success(val trade: Trade? = null) : TradeUiState()
    data class Error(val message: String) : TradeUiState()
}

sealed class EscrowUiState {
    object Idle    : EscrowUiState()
    object Loading : EscrowUiState()
    /** PayChangu checkout session created — app should open this URL. */
    data class CheckoutReady(val checkoutUrl: String) : EscrowUiState()
    data class Success(val message: String = "") : EscrowUiState()
    data class Error(val message: String) : EscrowUiState()
}

sealed class ReviewUiState {
    object Idle    : ReviewUiState()
    object Loading : ReviewUiState()
    object Success : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}

// ════════════════════════════════════════════════════════════════════════
//  AUTH VIEW MODEL
// ════════════════════════════════════════════════════════════════════════

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerUser  : RegisterUserUseCase,
    private val loginUser     : LoginUserUseCase,
    private val logoutUser    : LogoutUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val authRepository: AuthRepository,
    private val storageRepository: StorageRepository,
    private val walletManager: com.escrowmalawi.data.local.WalletManager
) : ViewModel() {

    private val _uiState    = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean get() = authRepository.isLoggedIn()

    init {
        if (isLoggedIn) loadCurrentUser()
    }

    fun register(name: String, email: String, phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = registerUser(name, email, phone, password)
            if (result.isSuccess) {
                _uiState.value = AuthUiState.NeedsVerification
            } else {
                _uiState.value = AuthUiState.Error(
                    friendlyError(result.exceptionOrNull()?.message ?: "Registration failed")
                )
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = loginUser(email, password)
            if (result.isSuccess) {
                if (authRepository.isEmailVerified()) {
                    loadCurrentUser()
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.NeedsVerification
                }
            } else {
                _uiState.value = AuthUiState.Error(
                    friendlyError(result.exceptionOrNull()?.message ?: "Login failed")
                )
            }
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.resendVerificationEmail()
            if (result.isSuccess) {
                _uiState.value = AuthUiState.Error("Verification email resent. Please check your inbox.")
            } else {
                _uiState.value = AuthUiState.Error("Failed to resend email: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun checkVerification() {
        viewModelScope.launch {
            if (authRepository.isEmailVerified()) {
                loadCurrentUser()
                _uiState.value = AuthUiState.Success
            } else {
                _uiState.value = AuthUiState.Error("Email is not verified yet. Please check your inbox.")
            }
        }
    }

    fun logout() {
        logoutUser()
        _currentUser.value = null
        _uiState.value = AuthUiState.Idle
    }

    fun updateProfile(
        context: android.content.Context,
        name: String,
        phone: String,
        imageUri: android.net.Uri?,
        walletAddress: String,
        privateKey: String, // Added this parameter
        onComplete: () -> Unit // Added this callback
    ) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val uid = authRepository.currentFirebaseUser()?.uid
                    ?: throw IllegalStateException("User not logged in")

                var imageUrl = _currentUser.value?.profilePictureUrl ?: ""

                if (imageUri != null) {
                    val uploadResult = storageRepository.uploadListingImage(context, "profile_$uid", imageUri)
                    if (uploadResult.isSuccess) {
                        imageUrl = uploadResult.getOrThrow()
                    } else {
                        _uiState.value = AuthUiState.Error("Image upload failed: ${uploadResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }

                val result = updateProfileUseCase(uid, name, phone, imageUrl, walletAddress)
                if (result.isSuccess) {
                    // Save the private key securely on the device
                    walletManager.savePrivateKey(uid, privateKey)

                    loadCurrentUser()
                    _uiState.value = AuthUiState.Success
                    onComplete() // Call callback
                } else {
                    _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "Update failed")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun resetState() { _uiState.value = AuthUiState.Idle }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _currentUser.value = authRepository.currentUserProfile()
        }
    }

    private fun friendlyError(raw: String): String = when {
        "email address is already in use" in raw -> "This email is already registered"
        "badly formatted"                 in raw -> "Invalid email address"
        "no user record"                  in raw -> "No account found for this email"
        "password is invalid"             in raw -> "Incorrect password"
        "NETWORK_ERROR"                   in raw ||
                "network error"                   in raw -> "No internet connection. Please try again."
        else                                     -> raw
    }
}

// ════════════════════════════════════════════════════════════════════════
//  TRADE VIEW MODEL
// ════════════════════════════════════════════════════════════════════════

@HiltViewModel
class TradeViewModel @Inject constructor(
    private val createTradeUseCase: CreateTradeUseCase,
    private val acceptTradeUseCase: AcceptTradeUseCase,
    private val rejectTradeUseCase: RejectTradeUseCase,
    private val tradeRepository   : TradeRepository,
    private val authRepository    : AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TradeUiState>(TradeUiState.Idle)
    val uiState: StateFlow<TradeUiState> = _uiState.asStateFlow()

    val buyerTrades: StateFlow<List<Trade>> = flow {
        val uid = authRepository.currentFirebaseUser()?.uid ?: return@flow
        emitAll(tradeRepository.buyerTradesFlow(uid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sellerTrades: StateFlow<List<Trade>> = flow {
        val uid = authRepository.currentFirebaseUser()?.uid ?: return@flow
        emitAll(tradeRepository.sellerTradesFlow(uid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Seller taps "Mark as Delivered" (starts the buyer's confirm window). */
    fun markDelivered(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Loading
            val result = tradeRepository.markDelivered(tradeId)
            _uiState.value = if (result.isSuccess) TradeUiState.Success(null)
            else TradeUiState.Error(result.exceptionOrNull()?.message ?: "Couldn't mark as delivered")
        }
    }

    fun createTrade(
        sellerUid: String, sellerName: String, sellerPhone: String,
        itemDescription: String, amount: Double, listingId: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Loading
            val result = createTradeUseCase(sellerUid, sellerName, sellerPhone, itemDescription, amount, listingId)
            _uiState.value = if (result.isSuccess) TradeUiState.Success(result.getOrNull())
            else TradeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to create trade")
        }
    }

    fun acceptTrade(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Loading
            val result = acceptTradeUseCase(tradeId)
            _uiState.value = if (result.isSuccess) TradeUiState.Success()
            else TradeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to accept")
        }
    }

    fun rejectTrade(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Loading
            val result = rejectTradeUseCase(tradeId)
            _uiState.value = if (result.isSuccess) TradeUiState.Success()
            else TradeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to reject")
        }
    }

    fun resetState() { _uiState.value = TradeUiState.Idle }
}

// ════════════════════════════════════════════════════════════════════════
//  ESCROW VIEW MODEL
// ════════════════════════════════════════════════════════════════════════

@HiltViewModel
class EscrowViewModel @Inject constructor(
    private val escrowRepository: EscrowRepository,
    private val authRepository  : AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EscrowUiState>(EscrowUiState.Idle)
    val uiState: StateFlow<EscrowUiState> = _uiState.asStateFlow()

    /** Buyer taps "Lock Funds" — creates a PayChangu checkout session. */
    fun initiateCheckout(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = EscrowUiState.Loading
            val buyer = authRepository.currentUserProfile()
            if (buyer == null) {
                _uiState.value = EscrowUiState.Error("You must be logged in to make a payment")
                return@launch
            }
            val result = escrowRepository.initiateCheckout(
                tradeId = tradeId,
                buyerEmail = buyer.email,
                buyerName = buyer.name
            )
            _uiState.value = if (result.isSuccess) EscrowUiState.CheckoutReady(result.getOrThrow())
            else EscrowUiState.Error(result.exceptionOrNull()?.message ?: "Failed to start payment")
        }
    }

    /** Buyer confirms receipt — simulated release, no real payout call. */
    fun releaseFunds(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = EscrowUiState.Loading
            val result = escrowRepository.releaseFunds(tradeId)
            _uiState.value = if (result.isSuccess) EscrowUiState.Success("Funds released to seller")
            else EscrowUiState.Error(result.exceptionOrNull()?.message ?: "Failed to release")
        }
    }

    /** Simulated refund, no real payout call. */
    fun refundBuyer(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = EscrowUiState.Loading
            val result = escrowRepository.refundBuyer(tradeId)
            _uiState.value = if (result.isSuccess) EscrowUiState.Success("Buyer refunded")
            else EscrowUiState.Error(result.exceptionOrNull()?.message ?: "Failed to refund")
        }
    }

    fun resetState() { _uiState.value = EscrowUiState.Idle }
}

// ════════════════════════════════════════════════════════════════════════
//  REVIEW VIEW MODEL
// ════════════════════════════════════════════════════════════════════════

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviewRepository: com.escrowmalawi.data.repository.ReviewRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Idle)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    fun submitReview(
        tradeId: String,
        ratedUid: String,
        rating: Int,
        comment: String
    ) {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            val rater = authRepository.currentUserProfile()
            if (rater == null) {
                _uiState.value = ReviewUiState.Error("You must be logged in to leave a review")
                return@launch
            }
            val result = reviewRepository.submitReview(
                tradeId = tradeId,
                raterUid = rater.uid,
                raterName = rater.name,
                ratedUid = ratedUid,
                rating = rating,
                comment = comment
            )
            _uiState.value = if (result.isSuccess) ReviewUiState.Success
            else ReviewUiState.Error(result.exceptionOrNull()?.message ?: "Failed to submit review")
        }
    }

    /** One-shot check used to decide whether to show the "Rate this trade" prompt. */
    suspend fun hasReviewed(tradeId: String, raterUid: String): Boolean =
        reviewRepository.hasReviewed(tradeId, raterUid).getOrDefault(false)

    fun reviewsForUserFlow(uid: String) = reviewRepository.reviewsForUserFlow(uid)

    fun resetState() { _uiState.value = ReviewUiState.Idle }
}

// ════════════════════════════════════════════════════════════════════════
//  NOTIFICATION VIEW MODEL
// ════════════════════════════════════════════════════════════════════════

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: com.escrowmalawi.data.repository.NotificationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val notifications: StateFlow<List<com.escrowmalawi.data.model.Notification>> = flow {
        val uid = authRepository.currentFirebaseUser()?.uid ?: return@flow
        emitAll(notificationRepository.getNotifications(uid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = notifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(notificationId)
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch {
            val uid = authRepository.currentFirebaseUser()?.uid ?: return@launch
            notificationRepository.deleteAllNotifications(uid)
        }
    }
}
