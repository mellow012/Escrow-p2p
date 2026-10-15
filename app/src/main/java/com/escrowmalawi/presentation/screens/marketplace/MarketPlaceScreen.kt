package com.escrowmalawi.presentation.screens.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.escrowmalawi.data.model.Listing
import com.escrowmalawi.data.model.ListingCategory
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel

// ════════════════════════════════════════════════════════════════════════
//  MARKETPLACE SCREEN
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    onListingClick : (String) -> Unit,
    onCreateListing: () -> Unit,
    onMyTrades     : () -> Unit,
    onMyListings   : () -> Unit,
    onLogout       : () -> Unit,
    viewModel      : MarketplaceViewModel = hiltViewModel(),
    authViewModel  : AuthViewModel        = hiltViewModel()
) {
    val listings     by viewModel.listings.collectAsState()
    val sellerRatings by viewModel.sellerRatings.collectAsState()
    val searchQuery  by viewModel.searchQuery.collectAsState()
    val selectedCat  by viewModel.selectedCategory.collectAsState()
    val currentUser  by authViewModel.currentUser.collectAsState()
    var showMenu     by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EscrowMarket", fontWeight = FontWeight.Black, fontSize = 17.sp, color = OnSurfaceDark)
                        Text("Safe P2P · Malawi", fontSize = 10.sp, color = OnSurfaceDark.copy(0.4f))
                    }
                },
                actions = {
                    IconButton(onClick = onMyTrades) {
                        Icon(Icons.Default.SwapHoriz, "Trades", tint = OnSurfaceDark.copy(0.7f))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(EscrowGreen, EscrowGreenDark))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    currentUser?.name?.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("My Listings", color = OnSurfaceDark) },
                                leadingIcon = { Icon(Icons.Default.Storefront, null, tint = EscrowGreen) },
                                onClick = { showMenu = false; onMyListings() }
                            )
                            DropdownMenuItem(
                                text = { Text("My Trades", color = OnSurfaceDark) },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null, tint = EscrowGreen) },
                                onClick = { showMenu = false; onMyTrades() }
                            )
                            HorizontalDivider(color = OnSurfaceDark.copy(0.08f))
                            DropdownMenuItem(
                                text = { Text("Log out", color = EscrowRed) },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = EscrowRed) },
                                onClick = { showMenu = false; onLogout() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateListing,
                containerColor = EscrowGreen,
                icon = { Icon(Icons.Default.Add, null, tint = Color.White) },
                text = { Text("Sell", color = Color.White, fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(16.dp)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // ── Search bar ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                MarketSearchBar(
                    query    = searchQuery,
                    onChange = viewModel::setSearchQuery,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(14.dp))
            }

            // ── Category chips ───────────────────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryPill("All", null, selectedCat == null) { viewModel.setCategory(null) }
                    }
                    items(ListingCategory.entries) { cat ->
                        CategoryPill(
                            label    = cat.label,
                            icon     = categoryIcon(cat.name),
                            selected = selectedCat == cat,
                            onClick  = { viewModel.setCategory(if (selectedCat == cat) null else cat) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Hero banner (no filter active) ───────────────────────────
            if (searchQuery.isBlank() && selectedCat == null) {
                item {
                    HeroBanner(Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Available Now", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = OnSurfaceDark)
                        Text("${listings.size} items", fontSize = 12.sp, color = OnSurfaceDark.copy(0.4f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Grid ─────────────────────────────────────────────────────
            if (listings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Storefront, null, tint = OnSurfaceDark.copy(0.15f), modifier = Modifier.size(72.dp))
                            Text("No listings found", color = OnSurfaceDark.copy(0.4f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Try a different search or category", color = OnSurfaceDark.copy(0.25f), fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(listings.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { listing ->
                            ListingCard(
                                listing = listing,
                                sellerRating = sellerRatings[listing.sellerUid],
                                onClick = { onListingClick(listing.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  LISTING DETAIL SCREEN
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId     : String,
    currentUserUid: String,
    onBack        : () -> Unit,
    onBuyNow      : (Listing) -> Unit,
    viewModel     : MarketplaceViewModel = hiltViewModel()
) {
    val allListings by viewModel.listings.collectAsState()
    val listing = allListings.firstOrNull { it.id == listingId }
    var selectedImageIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.title ?: "Listing", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
            )
        },
        bottomBar = {
            if (listing != null && listing.sellerUid != currentUserUid) {
                val isSoldOut = listing.quantity <= 0 || listing.status == "SOLD"
                
                Surface(color = SurfaceDark, shadowElevation = 12.dp) {
                    Button(
                        enabled = !isSoldOut,
                        onClick = { onBuyNow(listing) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                        shape  = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EscrowGreen,
                            disabledContainerColor = OnSurfaceDark.copy(0.12f)
                        )
                    ) {
                        if (isSoldOut) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp), tint = OnSurfaceDark.copy(0.4f))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Sold Out",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = OnSurfaceDark.copy(0.4f)
                            )
                        } else {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Buy with Escrow  ·  MWK ${"%,.0f".format(listing.price)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (listing == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EscrowGreen)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Hero image ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (listing.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(listing.imageUrls.getOrElse(selectedImageIndex) { listing.imageUrls.first() })
                            .crossfade(true).build(),
                        contentDescription = listing.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(categoryIcon(listing.category), null, tint = EscrowGreen.copy(0.25f), modifier = Modifier.size(90.dp))
                }
                // Category chip overlay
                Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BackgroundDark.copy(0.75f)
                    ) {
                        Text(
                            ListingCategory.valueOf(listing.category).label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = EscrowGreenLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Thumbnail strip
            if (listing.imageUrls.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listing.imageUrls.indices.toList()) { i ->
                        AsyncImage(
                            model = listing.imageUrls[i],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    2.dp,
                                    if (i == selectedImageIndex) EscrowGreen else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedImageIndex = i }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Price + title
                Text(
                    "MWK ${"%,.0f".format(listing.price)}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = EscrowGreen
                )
                Text(listing.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark)

                // Escrow trust badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowGreen.copy(0.08f)),
                    shape  = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(EscrowGreen.copy(0.3f), EscrowGreen.copy(0.1f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, null, tint = EscrowGreen, modifier = Modifier.size(22.dp))
                        Column {
                            Text("Escrow Protected", fontWeight = FontWeight.Bold, color = EscrowGreen, fontSize = 13.sp)
                            Text(
                                "Payment is held securely until you confirm receipt.",
                                fontSize = 11.sp,
                                color = OnSurfaceDark.copy(0.55f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Description
                if (listing.description.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Description", fontWeight = FontWeight.Bold, color = EscrowGreenLight, fontSize = 13.sp, letterSpacing = 0.5.sp)
                            if (listing.quantity > 0) {
                                Text("${listing.quantity} in stock", color = EscrowGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(listing.description, color = OnSurfaceDark.copy(0.75f), lineHeight = 22.sp, fontSize = 14.sp)
                    }
                }

                // Seller card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(EscrowGreen, EscrowGreenDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                listing.sellerName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Sold by", fontSize = 11.sp, color = OnSurfaceDark.copy(0.4f))
                            Text(listing.sellerName, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                            if (listing.sellerEmail.isNotBlank()) {
                                Text(listing.sellerEmail, fontSize = 12.sp, color = OnSurfaceDark.copy(0.45f))
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDark.copy(0.2f))
                    }
                }

                // Own listing notice
                if (listing.sellerUid == currentUserUid) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EscrowAmber.copy(0.08f)),
                        shape  = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = EscrowAmber, modifier = Modifier.size(18.dp))
                            Text("This is your listing", color = EscrowAmber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  MY LISTINGS SCREEN
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    onBack        : () -> Unit,
    onListingClick: (String) -> Unit,
    viewModel     : MarketplaceViewModel = hiltViewModel()
) {
    val myListings by viewModel.myListings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Listings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (myListings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Inventory2, null, tint = OnSurfaceDark.copy(0.2f), modifier = Modifier.size(40.dp))
                    }
                    Text("No listings yet", color = OnSurfaceDark.copy(0.5f), fontWeight = FontWeight.SemiBold)
                    Text("Items you post will appear here", color = OnSurfaceDark.copy(0.3f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(myListings, key = { it.id }) { listing ->
                    MyListingCard(
                        listing  = listing,
                        onClick  = { onListingClick(listing.id) },
                        onRemove = { viewModel.removeListing(listing.id) }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  PRIVATE COMPONENTS
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun MarketSearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search listings…", color = OnSurfaceDark.copy(0.35f)) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceDark.copy(0.4f)) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, null, tint = OnSurfaceDark.copy(0.4f))
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = EscrowGreen,
            unfocusedBorderColor    = OnSurfaceDark.copy(0.12f),
            focusedContainerColor   = SurfaceDark,
            unfocusedContainerColor = SurfaceDark
        )
    )
}

@Composable
private fun CategoryPill(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) EscrowGreen else SurfaceDark
    val txtColor = if (selected) Color.White else OnSurfaceDark.copy(0.65f)

    Surface(
        onClick    = onClick,
        shape      = RoundedCornerShape(20.dp),
        color      = bgColor,
        border     = if (!selected) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = txtColor, modifier = Modifier.size(13.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = txtColor)
        }
    }
}

@Composable
private fun HeroBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(EscrowGreenDark, Color(0xFF00796B))))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(18.dp))
                Text("Buy & Sell Safely", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Text(
                "Funds are held in escrow until you confirm delivery. Zero risk.",
                fontSize = 12.sp,
                color = Color.White.copy(0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ListingCard(listing: Listing, sellerRating: Pair<Double, Int>?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(EscrowGreenDark.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (listing.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(listing.imageUrls.first()).crossfade(true).build(),
                        contentDescription = listing.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(categoryIcon(listing.category), null, tint = EscrowGreen.copy(0.4f), modifier = Modifier.size(38.dp))
                }
                // Price badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BackgroundDark.copy(0.85f)
                    ) {
                        Text(
                            "MWK ${"%,.0f".format(listing.price)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.ExtraBold,
                            color = EscrowGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        listing.title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                    if (listing.quantity <= 0) {
                        Surface(
                            color = EscrowRed.copy(0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SOLD",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = EscrowRed
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, null, tint = EscrowGreenLight, modifier = Modifier.size(10.dp))
                    Text("Escrow protected", fontSize = 10.sp, color = EscrowGreenLight.copy(0.8f))
                    val (avg, count) = sellerRating ?: Pair(0.0, 0)
                    if (count > 0) {
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Default.Star, null, tint = EscrowAmber, modifier = Modifier.size(10.dp))
                        Text("%.1f".format(avg), fontSize = 10.sp, color = EscrowAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyListingCard(listing: Listing, onClick: () -> Unit, onRemove: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Remove listing?") },
            text  = { Text("\"${listing.title}\" will be removed from the marketplace.") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onRemove() }) {
                    Text("Remove", color = EscrowRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            },
            containerColor = SurfaceDark
        )
    }

    Card(
        onClick = onClick,
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EscrowGreen.copy(0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (listing.imageUrls.isNotEmpty()) {
                        AsyncImage(
                            model = listing.imageUrls.first(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(categoryIcon(listing.category), null, tint = EscrowGreen, modifier = Modifier.size(24.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(listing.title, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("MWK ${"%,.0f".format(listing.price)}", color = EscrowGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor(listing.status).copy(0.12f)
                    ) {
                        Text(
                            listing.status.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor(listing.status)
                        )
                    }
                }
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, null, tint = EscrowRed.copy(0.6f))
            }
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun categoryIcon(category: String): ImageVector = when (category) {
    ListingCategory.ELECTRONICS.name -> Icons.Default.PhoneAndroid
    ListingCategory.CLOTHING.name    -> Icons.Default.Checkroom
    ListingCategory.FOOD.name        -> Icons.Default.LocalGroceryStore
    ListingCategory.SERVICES.name    -> Icons.Default.Build
    ListingCategory.VEHICLES.name    -> Icons.Default.DirectionsCar
    ListingCategory.PROPERTY.name    -> Icons.Default.Home
    ListingCategory.AGRICULTURE.name -> Icons.Default.Grass
    else                             -> Icons.Default.Category
}

private fun statusColor(status: String): Color = when (status.uppercase()) {
    "ACTIVE"  -> EscrowGreen
    "SOLD"    -> Color(0xFF7C83FD)
    "REMOVED" -> EscrowRed
    else      -> EscrowAmber
}