package com.escrowmalawi.presentation.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.escrowmalawi.data.model.Listing
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.data.model.User
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMarketplace  : () -> Unit,
    onNavigateToTrades       : () -> Unit,
    onNavigateToMyListings   : () -> Unit,
    onNavigateToCreateListing: () -> Unit,
    onNavigateToProfile      : () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onListingClick           : (String) -> Unit,
    onLogout                 : () -> Unit,
    authViewModel            : AuthViewModel        = hiltViewModel(),
    tradeViewModel           : TradeViewModel       = hiltViewModel(),
    marketplaceViewModel     : MarketplaceViewModel = hiltViewModel(),
    notificationViewModel: com.escrowmalawi.presentation.viewmodel.NotificationViewModel = hiltViewModel()
) {
    val currentUser  by authViewModel.currentUser.collectAsState()
    val buyerTrades  by tradeViewModel.buyerTrades.collectAsState()
    val sellerTrades by tradeViewModel.sellerTrades.collectAsState()
    val listings     by marketplaceViewModel.listings.collectAsState()
    val sellerRatings by marketplaceViewModel.sellerRatings.collectAsState()
    val unreadCount  by notificationViewModel.unreadCount.collectAsState()

    HomeScreenContent(
        currentUser = currentUser,
        buyerTrades = buyerTrades,
        sellerTrades = sellerTrades,
        listings = listings,
        sellerRatings = sellerRatings,
        unreadCount = unreadCount,
        onNavigateToMarketplace = onNavigateToMarketplace,
        onNavigateToTrades = onNavigateToTrades,
        onNavigateToMyListings = onNavigateToMyListings,
        onNavigateToCreateListing = onNavigateToCreateListing,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToNotifications = onNavigateToNotifications,
        onListingClick = onListingClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    currentUser: User?,
    buyerTrades: List<Trade>,
    sellerTrades: List<Trade>,
    listings: List<Listing>,
    sellerRatings: Map<String, Pair<Double, Int>>,
    unreadCount: Int,
    onNavigateToMarketplace: () -> Unit,
    onNavigateToTrades: () -> Unit,
    onNavigateToMyListings: () -> Unit,
    onNavigateToCreateListing: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onListingClick: (String) -> Unit
) {
    val allTrades    = (buyerTrades + sellerTrades).distinctBy { it.id }
    val pendingCount = allTrades.count { it.status == TradeStatus.PENDING.name }
    val activeCount  = allTrades.count { it.status == TradeStatus.ACTIVE.name }
    val totalCount   = allTrades.size

    val featuredListings = listings
        .filter { it.sellerUid != (currentUser?.uid ?: "") }
        .take(5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(EscrowGreen, EscrowGreenDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                currentUser?.name?.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                        Column {
                            Text("EscrowMarket", fontWeight = FontWeight.Black, fontSize = 15.sp, color = OnSurfaceDark)
                            Text("Secure P2P · Malawi", fontSize = 10.sp, color = OnSurfaceDark.copy(0.4f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(
                                        containerColor = Color.Red,
                                        contentColor = Color.White
                                    ) {
                                        Text(unreadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                "Notifications",
                                tint = OnSurfaceDark.copy(0.7f)
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.AccountCircle, "Profile", tint = OnSurfaceDark.copy(0.5f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Hero ─────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(EscrowGreenDark.copy(0.6f), BackgroundDark),
                                startY = 0f, endY = 600f
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Muli bwanji,",
                            fontSize = 13.sp,
                            color = EscrowGreenLight.copy(0.8f),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                        Text(
                            currentUser?.name?.substringBefore(" ") ?: "Trader",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(EscrowGreen)
                            )
                            Text(
                                "Blockchain-secured escrow",
                                fontSize = 12.sp,
                                color = EscrowGreenLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Trade stats row ───────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniStatCard(Modifier.weight(1f), "$pendingCount", "Pending", EscrowAmber)
                    MiniStatCard(Modifier.weight(1f), "$activeCount",  "Active",  EscrowGreen)
                    MiniStatCard(Modifier.weight(1f), "$totalCount",   "Total",   Color(0xFF7C83FD))
                }
            }

            // ── Quick Actions ─────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Quick Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = OnSurfaceDark.copy(0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAction(Modifier.weight(1f), Icons.Default.Storefront,  "Browse",  EscrowGreen,         onNavigateToMarketplace)
                    QuickAction(Modifier.weight(1f), Icons.Default.AddBox,       "Sell",    EscrowAmber,         onNavigateToCreateListing)
                    QuickAction(Modifier.weight(1f), Icons.Default.SwapHoriz,    "Trades",  Color(0xFF7C83FD),   onNavigateToTrades)
                    QuickAction(Modifier.weight(1f), Icons.Default.Inventory2,   "My Items",OnSurfaceDark.copy(0.5f), onNavigateToMyListings)
                }
            }

            // ── Recent Listings header ────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Listings", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = OnSurfaceDark)
                    TextButton(onClick = onNavigateToMarketplace, contentPadding = PaddingValues(0.dp)) {
                        Text("See all", color = EscrowGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = EscrowGreen, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // ── Featured listing rows ─────────────────────────────────────
            if (featuredListings.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(90.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape  = RoundedCornerShape(16.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No listings yet — be the first to sell!", color = OnSurfaceDark.copy(0.3f), fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(featuredListings, key = { it.id }) { listing ->
                    ListingRow(listing, sellerRatings[listing.sellerUid], { onListingClick(listing.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Trust card ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape  = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(EscrowGreenDark.copy(0.5f), SurfaceDark))
                            )
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Shield, null, tint = EscrowGreen, modifier = Modifier.size(36.dp))
                            Column {
                                Text("100% Secure Trading", fontWeight = FontWeight.Bold, color = OnSurfaceDark, fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Your funds are only released once you confirm receipt of goods.",
                                    fontSize = 12.sp,
                                    color = OnSurfaceDark.copy(0.55f),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun MiniStatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Card(
        modifier = modifier,
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 11.sp, color = OnSurfaceDark.copy(0.45f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark.copy(0.7f))
    }
}

@Composable
private fun ListingRow(listing: Listing, sellerRating: Pair<Double, Int>?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EscrowGreen.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (listing.imageUrls.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = listing.imageUrls.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.ShoppingBag, null, tint = EscrowGreen, modifier = Modifier.size(22.dp))
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listing.title,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "by ${listing.sellerName}",
                        fontSize = 11.sp,
                        color = OnSurfaceDark.copy(0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val (avg, count) = sellerRating ?: Pair(0.0, 0)
                    if (count > 0) {
                        Icon(Icons.Default.Star, null, tint = EscrowAmber, modifier = Modifier.size(11.dp))
                        Text(
                            "%.1f".format(avg),
                            fontSize = 11.sp,
                            color = EscrowAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "MWK ${"%,.0f".format(listing.price)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = EscrowGreen,
                    fontSize = 13.sp
                )
                Text("Escrow", fontSize = 10.sp, color = EscrowGreenLight.copy(0.7f))
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Home Screen Preview", showBackground = true)
@Composable
fun HomeScreenPreview() {
    val sampleUser = User(
        uid = "user1",
        name = "Chikondi Phiri",
        email = "chikondi@example.com"
    )

    val sampleListings = listOf(
        Listing(
            id = "list1",
            sellerUid = "user2",
            sellerName = "Limbani Banda",
            title = "iPhone 13 Pro Max",
            description = "Excellent condition, 256GB battery health 89%",
            price = 850000.0,
            imageUrls = emptyList()
        ),
        Listing(
            id = "list2",
            sellerUid = "user3",
            sellerName = "Aisha Mussa",
            title = "Toyota Vitz 2015",
            description = "Silver color, low mileage, neatly driven",
            price = 4500000.0,
            imageUrls = emptyList()
        )
    )

    val sampleTrades = listOf(
        Trade(
            id = "trade1",
            buyerUid = "user1",
            sellerUid = "user2",
            status = TradeStatus.PENDING.name
        ),
        Trade(
            id = "trade2",
            buyerUid = "user1",
            sellerUid = "user3",
            status = TradeStatus.ACTIVE.name
        )
    )

    val sampleSellerRatings = mapOf(
        "user2" to Pair(4.8, 12),
        "user3" to Pair(4.5, 5)
    )

    EscrowMalawiTheme {
        HomeScreenContent(
            currentUser = sampleUser,
            buyerTrades = sampleTrades,
            sellerTrades = emptyList(),
            listings = sampleListings,
            sellerRatings = sampleSellerRatings,
            unreadCount = 3,
            onNavigateToMarketplace = {},
            onNavigateToTrades = {},
            onNavigateToMyListings = {},
            onNavigateToCreateListing = {},
            onNavigateToProfile = {},
            onNavigateToNotifications = {},
            onListingClick = {}
        )
    }
}