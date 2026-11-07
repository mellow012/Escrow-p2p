package com.escrowmalawi.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.data.model.*
import com.escrowmalawi.data.repository.*
import com.escrowmalawi.data.util.withNetworkTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ListingUiState {
    object Idle     : ListingUiState()
    object Loading  : ListingUiState()
    object Success  : ListingUiState()
    data class UploadProgress(val percent: Int) : ListingUiState()
    data class Error(val message: String) : ListingUiState()
}

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val listingRepository: ListingRepository,
    private val storageRepository : StorageRepository,
    private val authRepository    : AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListingUiState>(ListingUiState.Idle)
    val uiState: StateFlow<ListingUiState> = _uiState.asStateFlow()

    // ── Search / filter ───────────────────────────────────────────────────
    private val _searchQuery      = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ListingCategory?>(null)
    val selectedCategory: StateFlow<ListingCategory?> = _selectedCategory.asStateFlow()

    // ── Listings (live from Firestore) ────────────────────────────────────
    private val _allListings = listingRepository.activeListingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listings: StateFlow<List<Listing>> = combine(
        _allListings, _searchQuery, _selectedCategory
    ) { all, query, category ->
        all.filter { l ->
            val matchesQuery    = query.isBlank() ||
                    l.title.contains(query, ignoreCase = true) ||
                    l.description.contains(query, ignoreCase = true) ||
                    l.sellerName.contains(query, ignoreCase = true)
            val matchesCategory = category == null || l.category == category.name
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myListings: StateFlow<List<Listing>> = flow {
        val uid = authRepository.currentFirebaseUser()?.uid ?: return@flow
        emitAll(listingRepository.myListingsFlow(uid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Seller ratings for whichever listings are currently visible ────────
    // uid -> (avgRating, ratingCount). Recomputed whenever the visible seller
    // set changes; used to show trust scores on listing rows.
    val sellerRatings: StateFlow<Map<String, Pair<Double, Int>>> = listings
        .map { it.map { listing -> listing.sellerUid }.distinct() }
        .distinctUntilChanged()
        .flatMapLatest { uids -> authRepository.usersRatingsFlow(uids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSearchQuery(query: String)           { _searchQuery.value = query }
    fun setCategory(category: ListingCategory?) { _selectedCategory.value = category }

    // ── Create listing ────────────────────────────────────────────────────
    /**
     * 1. Upload each image to Cloudinary → get a secure https:// URL
     * 2. Save the listing doc to Firestore with those URLs
     *
     * Firestore stores tiny strings, not raw base64 bytes — no 1MB limit hit.
     * Network steps are wrapped in timeouts so the UI never hangs forever.
     */
    fun createListing(
        context    : Context,
        title      : String,
        description: String,
        price      : Double,
        quantity   : Int,
        category   : ListingCategory,
        imageUris  : List<Uri>
    ) {
        viewModelScope.launch {
            if (title.isBlank()) { _uiState.value = ListingUiState.Error("Title is required"); return@launch }
            if (price <= 0)      { _uiState.value = ListingUiState.Error("Enter a valid price"); return@launch }
            if (quantity <= 0)   { _uiState.value = ListingUiState.Error("Quantity must be at least 1"); return@launch }

            _uiState.value = ListingUiState.Loading

            // 0. Load the seller profile (with timeout)
            val profile = try {
                withNetworkTimeout(20_000) { authRepository.currentUserProfile() }
            } catch (e: Exception) {
                android.util.Log.e("MarketplaceVM", "Profile load failed", e)
                _uiState.value = ListingUiState.Error(e.localizedMessage ?: "Network error")
                return@launch
            }

            if (profile == null) {
                android.util.Log.e("MarketplaceVM", "Auth user missing. Current user: ${authRepository.currentFirebaseUser()}")
                _uiState.value = ListingUiState.Error("Auth error: No active session found.")
                return@launch
            }

            // 1. Upload images to Cloudinary one by one (StorageRepository has its own 30s timeout)
            val imageUrls = mutableListOf<String>()
            for ((index, uri) in imageUris.withIndex()) {
                val percent = ((index.toFloat() / imageUris.size) * 100).toInt()
                _uiState.value = ListingUiState.UploadProgress(percent)
                android.util.Log.d("MarketplaceVM", "Uploading image ${index + 1}/${imageUris.size} ($percent%)")

                val result = storageRepository.uploadListingImage(context, profile.uid, uri)
                if (result.isFailure) {
                    _uiState.value = ListingUiState.Error(
                        "Image ${index + 1} upload failed: ${result.exceptionOrNull()?.localizedMessage}"
                    )
                    return@launch
                }
                imageUrls.add(result.getOrThrow())
            }

            // 2. Save listing to Firestore (with timeout)
            android.util.Log.d("MarketplaceVM", "Saving listing with ${imageUrls.size} image URLs")
            val result = try {
                withNetworkTimeout {
                    listingRepository.createListing(
                        sellerUid   = profile.uid,
                        sellerName  = profile.name,
                        sellerEmail = profile.email,
                        sellerPhone = profile.phone,
                        title       = title,
                        description = description,
                        price       = price,
                        quantity    = quantity,
                        category    = category,
                        imageUrls   = imageUrls
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

            _uiState.value = if (result.isSuccess) {
                ListingUiState.Success
            } else {
                val err = result.exceptionOrNull()
                android.util.Log.e("MarketplaceVM", "Firestore save failed", err)
                ListingUiState.Error("Save failed: ${err?.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun removeListing(listingId: String) {
        viewModelScope.launch {
            // Cloudinary deletion via unsigned preset isn't supported server-side
            // (requires API Secret). Images will persist until manually cleaned up.
            listingRepository.removeListing(listingId)
        }
    }

    fun resetState() { _uiState.value = ListingUiState.Idle }

    fun seedData(context: android.content.Context) {
        viewModelScope.launch {
            val user = authRepository.currentUserProfile() ?: return@launch
            _uiState.value = ListingUiState.Loading
            val result = listingRepository.seedDatabase(user.uid, user.name)
            _uiState.value = if (result.isSuccess) ListingUiState.Success
            else ListingUiState.Error(result.exceptionOrNull()?.message ?: "Seeding failed")
        }
    }

    fun sellerListingsFlow(sellerUid: String): Flow<List<Listing>> =
        listingRepository.myListingsFlow(sellerUid)

    fun sellerRatingFlow(sellerUid: String): Flow<Pair<Double, Int>> =
        authRepository.userRatingFlow(sellerUid)

    fun listingFlow(listingId: String): Flow<Listing?> =
        listingRepository.listingFlow(listingId)

    fun askWhenBack(listing: Listing, user: User) {
        viewModelScope.launch {
            val enquiry = ListingEnquiry(
                id = java.util.UUID.randomUUID().toString(),
                listingId = listing.id,
                userUid = user.uid,
                userName = user.name,
                userEmail = user.email,
                message = "Interested in ${listing.title} when it's back in stock."
            )
            val result = listingRepository.createEnquiry(enquiry)
            if (result.isSuccess) {
                notificationRepository.sendNotification(
                    Notification(
                        recipientUid = listing.sellerUid,
                        title = "New Restock Request",
                        message = "${user.name} is interested in '${listing.title}'. Consider restocking soon!",
                        type = NotificationType.SYSTEM.name,
                        listingId = listing.id
                    )
                )
            }
        }
    }

    fun hasEnquired(listingId: String, userUid: String): Flow<Boolean> =
        listingRepository.getEnquiriesForListing(listingId, pendingOnly = true)
            .map { enquiries -> enquiries.any { it.userUid == userUid } }

    fun restock(listingId: String, quantity: Int) {
        viewModelScope.launch {
            listingRepository.updateListingQuantity(listingId, quantity)
            if (quantity > 0) {
                // Notify interested buyers
                listingRepository.getEnquiriesForListing(listingId, pendingOnly = true).first().forEach { enquiry ->
                    notificationRepository.sendNotification(
                        Notification(
                            recipientUid = enquiry.userUid,
                            title = "Item Back in Stock!",
                            message = "The item you were interested in is now back in stock with $quantity units.",
                            type = NotificationType.SYSTEM.name,
                            listingId = listingId
                        )
                    )
                    // Mark enquiry as responded
                    listingRepository.updateEnquiryStatus(enquiry.id, "RESPONDED")
                }
            }
        }
    }
}
