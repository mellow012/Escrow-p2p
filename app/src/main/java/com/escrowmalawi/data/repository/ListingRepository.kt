package com.escrowmalawi.data.repository

import com.escrowmalawi.data.model.Listing
import com.escrowmalawi.data.model.ListingCategory
import com.escrowmalawi.data.model.ListingStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val col = firestore.collection("listings")

    suspend fun createListing(
        sellerUid  : String,
        sellerName : String,
        sellerEmail: String,
        sellerPhone: String,
        title      : String,
        description: String,
        price      : Double,
        category   : ListingCategory,
        quantity   : Int = 1,
        imageUrls  : List<String> = emptyList()
    ): Result<Listing> = try {
        val id = UUID.randomUUID().toString()
        val listing = Listing(
            id          = id,
            sellerUid   = sellerUid,
            sellerName  = sellerName,
            sellerEmail = sellerEmail,
            sellerPhone = sellerPhone,
            title       = title,
            description = description,
            price       = price,
            quantity    = quantity,
            category    = category.name,
            imageUrls   = imageUrls,
            status      = ListingStatus.ACTIVE.name,
            createdAt   = Timestamp.now(),
            updatedAt   = Timestamp.now()
        )
        android.util.Log.d("ListingRepository", "Attempting to save listing: $id")
        col.document(id).set(listing).await()
        Result.success(listing)
    } catch (e: Exception) {
        android.util.Log.e("ListingRepository", "Failed to save listing", e)
        Result.failure(e)
    }

    fun activeListingsFlow(): Flow<List<Listing>> = callbackFlow {
        val listener = col
            .whereEqualTo("status", ListingStatus.ACTIVE.name)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("ListingRepository", "Firestore Error: ${err.message}")
                    return@addSnapshotListener
                }
                val items = snap?.toObjects(Listing::class.java) ?: emptyList()
                // Sort client-side to avoid crashing due to missing composite index
                trySend(items.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
        awaitClose { listener.remove() }
    }

    fun listingsByCategoryFlow(category: ListingCategory): Flow<List<Listing>> = callbackFlow {
        val listener = col
            .whereEqualTo("status",   ListingStatus.ACTIVE.name)
            .whereEqualTo("category", category.name)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("ListingRepository", "Firestore Error: ${err.message}")
                    return@addSnapshotListener
                }
                val items = snap?.toObjects(Listing::class.java) ?: emptyList()
                trySend(items.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
        awaitClose { listener.remove() }
    }

    fun myListingsFlow(sellerUid: String): Flow<List<Listing>> = callbackFlow {
        val listener = col
            .whereEqualTo("sellerUid", sellerUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("ListingRepository", "Firestore Error: ${err.message}")
                    return@addSnapshotListener
                }
                val items = snap?.toObjects(Listing::class.java) ?: emptyList()
                trySend(items.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
        awaitClose { listener.remove() }
    }

    suspend fun getListing(listingId: String): Result<Listing> = try {
        val snap = col.document(listingId).get().await()
        val listing = snap.toObject(Listing::class.java)
        if (listing != null) Result.success(listing) else Result.failure(Exception("Listing not found"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun markSold(listingId: String): Result<Unit> = try {
        col.document(listingId).update(
            mapOf("status" to ListingStatus.SOLD.name, "updatedAt" to Timestamp.now())
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun removeListing(listingId: String): Result<Unit> = try {
        col.document(listingId).update(
            mapOf("status" to ListingStatus.REMOVED.name, "updatedAt" to Timestamp.now())
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun seedDatabase(sellerUid: String, sellerName: String): Result<Unit> = try {
        val dummyListings = listOf(
            Triple("iPhone 13 Pro 256GB", 850000.0, ListingCategory.ELECTRONICS),
            Triple("Samsung S22 Ultra - Mint", 720000.0, ListingCategory.ELECTRONICS),
            Triple("Sony WH-1000XM4 Headphones", 280000.0, ListingCategory.ELECTRONICS),
            Triple("Men's Leather Jacket (M)", 45000.0, ListingCategory.CLOTHING),
            Triple("Nike Air Max - Size 42", 85000.0, ListingCategory.CLOTHING),
            Triple("Toyota Vitz 2012 Silver", 4500000.0, ListingCategory.VEHICLES),
            Triple("MacBook Pro M1 16GB RAM", 1200000.0, ListingCategory.ELECTRONICS),
            Triple("Ergonomic Office Chair", 150000.0, ListingCategory.OTHER),
            Triple("Modern Glass Office Desk", 120000.0, ListingCategory.OTHER),
            Triple("Bags of Maize (50kg) - Grade A", 35000.0, ListingCategory.AGRICULTURE)
        )

        val batch = firestore.batch()
        
        dummyListings.forEach { (title, price, cat) ->
            val id = UUID.randomUUID().toString()
            val listing = Listing(
                id = id,
                sellerUid = sellerUid,
                sellerName = sellerName,
                sellerEmail = "demo@escrow.mw",
                sellerPhone = "+265 999 000 111",
                title = title,
                description = "Authentic $title for sale in Lilongwe. Excellent condition.",
                price = price,
                category = cat.name,
                imageUrls = emptyList(),
                status = ListingStatus.ACTIVE.name,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val docRef = col.document(id)
            batch.set(docRef, listing)
        }
        
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) { 
        android.util.Log.e("ListingRepo", "Seeding failed", e)
        Result.failure(e) 
    }
}
