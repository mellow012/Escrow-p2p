package com.escrowmalawi.presentation.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.escrowmalawi.data.model.Listing
import com.escrowmalawi.data.model.ListingCategory
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.data.model.User
import com.escrowmalawi.data.model.isSoldOut
import com.escrowmalawi.presentation.components.UserAvatar
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import com.escrowmalawi.presentation.viewmodel.FollowViewModel
import com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel
import com.escrowmalawi.presentation.viewmodel.NotificationViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

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
    onNavigateToActivity     : () -> Unit = {},
    authViewModel            : AuthViewModel         = hiltViewModel(),
    tradeViewModel           : TradeViewModel        = hiltViewModel(),
    marketplaceViewModel     : MarketplaceViewModel  = hiltViewModel(),
    notificationViewModel    : NotificationViewModel = hiltViewModel(),
    followViewModel          : FollowViewModel       = hiltViewModel()
) {
    val followedUids by followViewModel.followedUids.collectAsState()
    val currentUser   by authViewModel.currentUser.collectAsState()
    val buyerTrades   by tradeViewModel.buyerTrades.collectAsState()
    val sellerTrades  by tradeViewModel.sellerTrades.collectAsState()
    val listings      by marketplaceViewModel.listings.collectAsState()
    val sellerRatings by marketplaceViewModel.sellerRatings.collectAsState()
    val unreadCount   by notificationViewModel.unreadCount.collectAsState()

    HomeScreenContent(
        currentUser = currentUser,
        buyerTrades = buyerTrades,
        sellerTrades = sellerTrades,
        listings = listings,
        sellerRatings = sellerRatings,
        unreadCount = unreadCount,
        followedUids = followedUids,
        onBrowse = { marketplaceViewModel.setCategory(null); onNavigateToMarketplace() },
        onCategoryClick = { marketplaceViewModel.setCategory(it); onNavigateToMarketplace() },
        onNavigateToTrades = onNavigateToTrades,
        onNavigateToActivity = onNavigateToActivity,
        onNavigateToMyListings = onNavigateToMyListings,
        onNavigateToCreateListing = onNavigateToCreateListing,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToNotifications = onNavigateToNotifications,
        onListingClick = onListingClick
    )
}

private data class Todo(val icon: ImageVector, val text: String, val tint: Color)

@Composable
private fun HomeScreenContent(
    currentUser: User?,
    buyerTrades: List<Trade>,
    sellerTrades: List<Trade>,
    listings: List<Listing>,
    sellerRatings: Map<String, Pair<Double, Int>>,
    unreadCount: Int,
    followedUids: Set<String>,
    onBrowse: () -> Unit,
    onCategoryClick: (ListingCategory) -> Unit,
    onNavigateToTrades: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToMyListings: () -> Unit,
    onNavigateToCreateListing: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onListingClick: (String) -> Unit
) {
    val uid = currentUser?.uid.orEmpty()
    val allTrades = (buyerTrades + sellerTrades).distinctBy { it.id }
    val pending = allTrades.count { it.status == TradeStatus.PENDING.name }
    val active = allTrades.count { it.status == TradeStatus.ACTIVE.name }
    val completed = allTrades.count { it.status == TradeStatus.COMPLETED.name }

    // Things the user has to do next
    val todos = buildList {
        val reqs = sellerTrades.count { it.status == TradeStatus.PENDING.name }
        if (reqs > 0) add(Todo(Icons.Default.MarkEmailUnread, "$reqs trade request${if (reqs > 1) "s" else ""} waiting for your answer", EscrowAmber))
        val toPay = buyerTrades.count { it.status == TradeStatus.ACTIVE.name && it.txHash.isBlank() }
        if (toPay > 0) add(Todo(Icons.Default.Payments, "$toPay accepted trade${if (toPay > 1) "s" else ""}: pay to lock the funds", EscrowGreen))
        val toConfirm = buyerTrades.count { it.status == TradeStatus.ACTIVE.name && it.txHash.isNotBlank() && it.deliveredAt != null }
        if (toConfirm > 0) add(Todo(Icons.Default.TaskAlt, "$toConfirm item${if (toConfirm > 1) "s" else ""} delivered: confirm you got ${if (toConfirm > 1) "them" else "it"}", EscrowGreen))
        val toDeliver = sellerTrades.count { it.status == TradeStatus.ACTIVE.name && it.txHash.isNotBlank() && it.deliveredAt == null }
        if (toDeliver > 0) add(Todo(Icons.Default.LocalShipping, "$toDeliver paid order${if (toDeliver > 1) "s" else ""} to deliver", EscrowAmber))
        val disputes = allTrades.count { it.status == TradeStatus.DISPUTED.name }
        if (disputes > 0) add(Todo(Icons.Default.Gavel, "$disputes dispute${if (disputes > 1) "s" else ""} in review", EscrowRed))
    }

    // Other people's products, newest first. Carousel gets the first few (with photos first).
    // Sellers you follow come first, then everyone else
    val market = remember(listings, uid, followedUids) {
        listings
            .filter { it.sellerUid != uid && !it.isSoldOut() }
            .sortedWith(
                compareByDescending<Listing> { it.sellerUid in followedUids }
                    .thenByDescending { it.createdAt.seconds }
            )
    }
    val featured = remember(market, followedUids) {
        val ordered = market.filter { it.imageUrls.isNotEmpty() } + market.filter { it.imageUrls.isEmpty() }
        val (mine, others) = ordered.partition { it.sellerUid in followedUids }
        (mine + others).take(6)
    }
    val fresh = remember(market, featured) { market.filter { l -> featured.none { it.id == l.id } }.take(6) }

    Scaffold(containerColor = BackgroundDark) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(currentUser, onNavigateToProfile)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Muli bwanji", fontSize = 12.sp, color = OnSurfaceDark.copy(0.55f))
                        Text(
                            currentUser?.name?.trim()?.substringBefore(" ")?.ifBlank { null } ?: "Trader",
                            fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = OnSurfaceDark,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(SurfaceDark).clickable(onClick = onNavigateToNotifications),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(badge = {
                            if (unreadCount > 0) Badge(containerColor = EscrowRed, contentColor = Color.White) { Text(unreadCount.toString()) }
                        }) {
                            Icon(Icons.Default.Notifications, "Notifications", tint = OnSurfaceDark.copy(0.8f))
                        }
                    }
                }
            }

            // ── Search pill ──────────────────────────────────────────────
            item {
                Card(
                    onClick = onBrowse,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = OnSurfaceDark.copy(0.5f))
                        Spacer(Modifier.width(10.dp))
                        Text("Search products and sellers", color = OnSurfaceDark.copy(0.5f), fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Featured carousel ────────────────────────────────────────
            item {
                SectionHeader("Featured", "See all", onBrowse)
                Spacer(Modifier.height(12.dp))
                if (featured.isEmpty()) {
                    EmptyHero(onNavigateToCreateListing)
                } else {
                    FeaturedCarousel(featured, sellerRatings, followedUids, onListingClick)
                }
            }

            // ── Needs attention ──────────────────────────────────────────
            if (todos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(22.dp))
                    Card(
                        onClick = onNavigateToTrades,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Needs your attention", fontWeight = FontWeight.Bold, color = OnSurfaceDark, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = OnSurfaceDark.copy(0.4f), modifier = Modifier.size(18.dp))
                            }
                            todos.take(4).forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(34.dp).clip(CircleShape).background(t.tint.copy(0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(t.icon, null, tint = t.tint, modifier = Modifier.size(18.dp)) }
                                    Spacer(Modifier.width(12.dp))
                                    Text(t.text, fontSize = 13.sp, color = OnSurfaceDark.copy(0.85f), lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Quick actions ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAction(Modifier.weight(1f), Icons.Default.AddBox, "Sell", EscrowGreen, onNavigateToCreateListing)
                    QuickAction(Modifier.weight(1f), Icons.Default.Inventory2, "My items", EscrowAmber, onNavigateToMyListings)
                    QuickAction(Modifier.weight(1f), Icons.Default.SwapHoriz, "Trades", Color(0xFF6366F1), onNavigateToTrades)
                    QuickAction(Modifier.weight(1f), Icons.Default.History, "Activity", EscrowRed, onNavigateToActivity)
                }
            }

            // ── Trade summary ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(Modifier.weight(1f), "$pending", "Pending", EscrowAmber)
                    StatCard(Modifier.weight(1f), "$active", "Active", EscrowGreen)
                    StatCard(Modifier.weight(1f), "$completed", "Completed", Color(0xFF6366F1))
                }
            }

            // ── Categories ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Shop by category", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = OnSurfaceDark,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ListingCategory.values().toList()) { cat ->
                        CategoryChip(cat, onClick = { onCategoryClick(cat) })
                    }
                }
            }

            // ── Fresh in the market ──────────────────────────────────────
            if (fresh.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Fresh in the market", "See all", onBrowse)
                    Spacer(Modifier.height(12.dp))
                }
                items(fresh.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { l ->
                            ProductCard(l, sellerRatings[l.sellerUid], l.sellerUid in followedUids, { onListingClick(l.id) }, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ── Trust banner ─────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth().padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(EscrowGreenDark, EscrowGreen)))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(34.dp))
                        Column {
                            Text("Your money stays protected", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Funds are held in escrow and only released after you confirm you received the item.",
                                fontSize = 12.sp, color = Color.White.copy(0.85f), lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun Avatar(user: User?, onClick: () -> Unit) {
    UserAvatar(
        name = user?.name,
        photoUrl = user?.profilePictureUrl,
        modifier = Modifier.clickable(onClick = onClick),
        size = 46.dp
    )
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = OnSurfaceDark)
        TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
            Text(action, color = EscrowGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = EscrowGreen, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarousel(
    items: List<Listing>,
    ratings: Map<String, Pair<Double, Int>>,
    followedUids: Set<String>,
    onClick: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Auto-advance every few seconds, but never while the user is dragging
    LaunchedEffect(items.size) {
        if (items.size > 1) {
            while (true) {
                delay(4500)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        pageSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) { page ->
        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
        val l = items[page]
        FeaturedCard(
            listing = l,
            rating = ratings[l.sellerUid],
            following = l.sellerUid in followedUids,
            onClick = { onClick(l.id) },
            modifier = Modifier.graphicsLayer {
                val s = lerp(0.93f, 1f, 1f - offset)
                scaleX = s
                scaleY = s
                alpha = lerp(0.7f, 1f, 1f - offset)
            }
        )
    }

    if (items.size > 1) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val on = pagerState.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (on) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (on) EscrowGreen else OnSurfaceDark.copy(0.2f))
                )
            }
        }
    }
}

@Composable
private fun FeaturedCard(listing: Listing, rating: Pair<Double, Int>?, following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cat = ListingCategory.values().firstOrNull { it.name == listing.category } ?: ListingCategory.OTHER
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(EscrowGreenDark, EscrowGreen)))
            .clickable(onClick = onClick)
    ) {
        if (listing.imageUrls.isNotEmpty()) {
            AsyncImage(
                model = listing.imageUrls.first(), contentDescription = listing.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(categoryIcon(listing.category), null, tint = Color.White.copy(0.25f), modifier = Modifier.size(96.dp).align(Alignment.Center))
        }
        // Readable text over any photo
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.78f)), startY = 120f)
            )
        )
        Box(
            Modifier.padding(14.dp).align(Alignment.TopStart).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(0.38f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text(cat.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        if (following) {
            Row(
                Modifier.padding(14.dp).align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(EscrowGreen.copy(0.9f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Following", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listing.title.trim(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MWK ${"%,.0f".format(listing.price)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Escrow", color = Color.White.copy(0.85f), fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("by ${listing.sellerName.trim().ifBlank { "seller" }}", color = Color.White.copy(0.8f), fontSize = 12.sp)
                val (avg, count) = rating ?: Pair(0.0, 0)
                if (count > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                    Text(" ${"%.1f".format(avg)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyHero(onSell: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth().padding(horizontal = 20.dp).height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(EscrowGreenDark, EscrowGreen)))
            .clickable(onClick = onSell)
            .padding(20.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No products yet", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("Be the first to list something and start selling safely.", color = Color.White.copy(0.85f), fontSize = 13.sp)
            Text("Sell an item →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark.copy(0.8f))
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 11.sp, color = OnSurfaceDark.copy(0.6f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CategoryChip(cat: ListingCategory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(categoryIcon(cat.name), null, tint = EscrowGreen, modifier = Modifier.size(18.dp))
        Text(cat.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurfaceDark)
    }
}

@Composable
private fun ProductCard(listing: Listing, rating: Pair<Double, Int>?, following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(120.dp).background(EscrowGreen.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (listing.imageUrls.isNotEmpty()) {
                    AsyncImage(model = listing.imageUrls.first(), contentDescription = listing.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(categoryIcon(listing.category), null, tint = EscrowGreen.copy(0.5f), modifier = Modifier.size(40.dp))
                }
                if (following) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(EscrowGreen),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Favorite, "Following", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(listing.title.trim(), fontWeight = FontWeight.SemiBold, color = OnSurfaceDark, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("MWK ${"%,.0f".format(listing.price)}", fontWeight = FontWeight.ExtraBold, color = EscrowGreen, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listing.sellerName.trim().ifBlank { "seller" }, fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    val (avg, count) = rating ?: Pair(0.0, 0)
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, null, tint = EscrowAmber, modifier = Modifier.size(11.dp))
                        Text(" ${"%.1f".format(avg)}", fontSize = 11.sp, color = EscrowAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

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
