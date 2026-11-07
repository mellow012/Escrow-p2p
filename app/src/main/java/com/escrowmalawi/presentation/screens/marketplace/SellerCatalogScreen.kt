package com.escrowmalawi.presentation.screens.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.escrowmalawi.data.model.Listing
import com.escrowmalawi.data.model.ListingCategory
import com.escrowmalawi.data.model.isSoldOut
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerCatalogScreen(
    sellerUid: String,
    currentUserUid: String,
    onBack: () -> Unit,
    onListingClick: (String) -> Unit,
    onAddProduct: () -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel(),
    authViewModel: com.escrowmalawi.presentation.viewmodel.AuthViewModel = hiltViewModel()
) {
    val listings by viewModel.sellerListingsFlow(sellerUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val rating by viewModel.sellerRatingFlow(sellerUid).collectAsStateWithLifecycle(initialValue = 0.0 to 0)
    val isOwner = sellerUid == currentUserUid

    var selectedCategory by remember { mutableStateOf<ListingCategory?>(null) }
    val categories = remember(listings) {
        listings.mapNotNull {
            try { ListingCategory.valueOf(it.category) } catch (e: Exception) { null }
        }.distinct().sortedBy { it.name }
    }

    val filteredListings = remember(listings, selectedCategory) {
        if (selectedCategory == null) listings
        else listings.filter { it.category == selectedCategory?.name }
    }

    val activeListings = filteredListings.filter { !it.isSoldOut() }
    val soldOutListings = filteredListings.filter { it.isSoldOut() }

    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    var showRestockDialog by remember { mutableStateOf<Listing?>(null) }
    var restockQuantity by remember { mutableStateOf("10") }

    if (showRestockDialog != null) {
        AlertDialog(
            onDismissRequest = { showRestockDialog = null },
            title = { Text("Restock Product") },
            text = {
                Column {
                    Text("Enter new quantity for ${showRestockDialog?.title}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restockQuantity,
                        onValueChange = { if (it.all { char -> char.isDigit() }) restockQuantity = it },
                        label = { Text("Quantity") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = restockQuantity.toIntOrNull() ?: 0
                        if (q > 0) {
                            viewModel.restock(showRestockDialog!!.id, q)
                            showRestockDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                ) { Text("Update Stock") }
            },
            dismissButton = {
                TextButton(onClick = { showRestockDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isOwner) "My Catalog" else "Seller Catalog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (activeListings.isNotEmpty()) {
                            Text(
                                "${activeListings.size} active listings",
                                fontSize = 12.sp,
                                color = OnSurfaceDark.copy(0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = onAddProduct) {
                            Icon(Icons.Default.Add, "Add Product", tint = EscrowGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Seller Header ────────────────────────────────────────────
            item {
                SellerHeader(
                    sellerName = listings.firstOrNull()?.sellerName ?: "Seller",
                    rating = rating.first,
                    ratingCount = rating.second
                )
            }

            // ── Category Filter ──────────────────────────────────────────
            if (categories.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EscrowGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceDark,
                                    labelColor = OnSurfaceDark.copy(0.7f)
                                ),
                                border = null
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        category.name.lowercase().replaceFirstChar {
                                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EscrowGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceDark,
                                    labelColor = OnSurfaceDark.copy(0.7f)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }

            // ── Active Listings ──────────────────────────────────────────
            if (activeListings.isNotEmpty()) {
                item {
                    Text(
                        "Available Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = EscrowGreenLight
                    )
                }
                items(activeListings) { listing ->
                    CatalogListingItem(
                        listing = listing,
                        onClick = onListingClick,
                        isOwner = isOwner,
                        onRemove = { viewModel.removeListing(listing.id) }
                    )
                }
            }

            // ── Sold Out Listings ────────────────────────────────────────
            if (soldOutListings.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sold Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = OnSurfaceDark.copy(0.4f)
                    )
                }
                items(soldOutListings) { listing ->
                    val notified by viewModel.hasEnquired(listing.id, currentUserUid)
                        .collectAsStateWithLifecycle(initialValue = false)

                    CatalogListingItem(
                        listing = listing,
                        onClick = onListingClick,
                        isSoldOut = true,
                        isOwner = isOwner,
                        notified = notified,
                        onRestock = { 
                            restockQuantity = "10"
                            showRestockDialog = listing 
                        },
                        onNotify = {
                            currentUser?.let { user ->
                                viewModel.askWhenBack(listing, user)
                            }
                        }
                    )
                }
            }

            if (listings.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Storefront, null, modifier = Modifier.size(64.dp), tint = OnSurfaceDark.copy(0.1f))
                            Spacer(Modifier.height(16.dp))
                            Text("No listings found", color = OnSurfaceDark.copy(0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerHeader(sellerName: String, rating: Double, ratingCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(EscrowGreen.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                sellerName.take(1).uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = EscrowGreen
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(sellerName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnSurfaceDark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = EscrowGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (ratingCount > 0) "%.1f (%d)".format(rating, ratingCount) else "No ratings",
                    fontSize = 14.sp,
                    color = OnSurfaceDark.copy(0.6f)
                )
            }
        }
    }
}

@Composable
private fun CatalogListingItem(
    listing: Listing,
    onClick: (String) -> Unit,
    isSoldOut: Boolean = false,
    isOwner: Boolean = false,
    notified: Boolean = false,
    onRestock: () -> Unit = {},
    onRemove: () -> Unit = {},
    onNotify: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = { if (!showMenu) onClick(listing.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(80.dp)
        ) {
            AsyncImage(
                model = listing.imageUrls.firstOrNull(),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundDark),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        listing.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSoldOut) OnSurfaceDark.copy(0.5f) else OnSurfaceDark
                    )
                    Text(
                        "MWK ${"%,.0f".format(listing.price)}",
                        color = if (isSoldOut) OnSurfaceDark.copy(0.4f) else EscrowGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (isSoldOut) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = EscrowRed.copy(0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SOLD OUT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = EscrowRed
                            )
                        }
                        if (!isOwner) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (notified) "We'll notify you!" else "Notify me",
                                fontSize = 11.sp,
                                color = if (notified) EscrowGreen else EscrowGreenLight,
                                modifier = Modifier.clickable {
                                    if (!notified) {
                                        onNotify()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        "${listing.quantity} in stock",
                        fontSize = 12.sp,
                        color = OnSurfaceDark.copy(0.5f)
                    )
                }
            }
            
            if (isOwner) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = OnSurfaceDark.copy(0.3f))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        if (isSoldOut) {
                            DropdownMenuItem(
                                text = { Text("Restock", color = EscrowGreen) },
                                onClick = { showMenu = false; onRestock() },
                                leadingIcon = { Icon(Icons.Default.Add, null, tint = EscrowGreen) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove", color = EscrowRed) },
                            onClick = { showMenu = false; onRemove() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = EscrowRed) }
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = OnSurfaceDark.copy(0.2f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}
