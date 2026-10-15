package com.escrowmalawi.presentation.screens.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.presentation.screens.auth.EscrowTextField
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.ReviewUiState
import com.escrowmalawi.presentation.viewmodel.ReviewViewModel
import com.escrowmalawi.presentation.viewmodel.TradeUiState
import com.escrowmalawi.presentation.viewmodel.TradeViewModel

// ════════════════════════════════════════════════════════════════════════
//  CREATE TRADE SCREEN  (Buyer initiates trade)
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTradeScreen(
    onTradeCreated: (String) -> Unit,
    onBack: () -> Unit,
    initialSellerUid: String = "",
    initialSellerName: String = "",
    initialSellerPhone: String = "",
    initialDescription: String = "",
    initialAmount: Double = 0.0,
    initialListingId: String? = null,
    viewModel: TradeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var sellerPhone by remember { mutableStateOf(initialSellerPhone) }
    var sellerUid by remember { mutableStateOf(initialSellerUid) }
    var sellerName by remember { mutableStateOf(initialSellerName) }
    var itemDescription by remember { mutableStateOf(initialDescription) }
    var listingId by remember { mutableStateOf(initialListingId) }
    var amountText by remember {
        mutableStateOf(if (initialAmount > 0) initialAmount.toString() else "")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is TradeUiState.Success -> {
                viewModel.resetState()
                s.trade?.id?.let { onTradeCreated(it) }
            }
            is TradeUiState.Error -> errorMessage = s.message
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Trade", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, null, tint = EscrowAmber, modifier = Modifier.size(20.dp))
                    Text(
                        "The seller will be notified and must accept before funds are locked in escrow.",
                        fontSize = 13.sp,
                        color = OnSurfaceDark.copy(0.7f)
                    )
                }
            }

            Text("Seller Details", fontWeight = FontWeight.SemiBold, color = EscrowGreenLight)

            EscrowTextField(
                value = sellerPhone,
                onValueChange = { sellerPhone = it },
                label = "Seller Phone Number",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )

            EscrowTextField(
                value = sellerName,
                onValueChange = { sellerName = it },
                label = "Seller Name",
                leadingIcon = Icons.Default.Person
            )

            // In a real implementation, sellerUid would be resolved via phone lookup
            EscrowTextField(
                value = sellerUid,
                onValueChange = { sellerUid = it },
                label = "Seller User ID",
                leadingIcon = Icons.Default.Badge
            )

            Divider(color = OnSurfaceDark.copy(0.08f))

            Text("Trade Details", fontWeight = FontWeight.SemiBold, color = EscrowGreenLight)

            EscrowTextField(
                value = itemDescription,
                onValueChange = { itemDescription = it },
                label = "Item / Service Description",
                leadingIcon = Icons.Default.Description
            )

            EscrowTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "Amount (MWK)",
                leadingIcon = Icons.Default.CurrencyExchange,
                keyboardType = KeyboardType.Decimal
            )

            errorMessage?.let {
                Text(it, color = EscrowRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    errorMessage = null
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        errorMessage = "Enter a valid amount"
                    } else {
                        viewModel.createTrade(
                            sellerUid = sellerUid,
                            sellerName = sellerName,
                            sellerPhone = sellerPhone,
                            itemDescription = itemDescription,
                            amount = amount,
                            listingId = listingId
                        )
                    }
                },
                enabled = uiState !is TradeUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
            ) {
                if (uiState is TradeUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Trade Request", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  TRADE LIST SCREEN
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeListScreen(
    onTradeClick: (String) -> Unit,
    onCreateTrade: () -> Unit,
    viewModel: TradeViewModel = hiltViewModel()
) {
    val buyerTrades by viewModel.buyerTrades.collectAsState()
    val sellerTrades by viewModel.sellerTrades.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Trades", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTrade,
                containerColor = EscrowGreen
            ) {
                Icon(Icons.Default.Add, "New Trade", tint = Color.White)
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = EscrowGreen
            ) {
                Tab(selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Buying (${buyerTrades.size})") })
                Tab(selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Selling (${sellerTrades.size})") })
            }

            val trades = if (selectedTab == 0) buyerTrades else sellerTrades

            if (trades.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            null,
                            tint = OnSurfaceDark.copy(0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No trades yet", color = OnSurfaceDark.copy(0.4f))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trades, key = { it.id }) { trade ->
                        TradeCard(trade = trade, onClick = { onTradeClick(trade.id) }, showBuyer = selectedTab == 1)
                    }
                }
            }
        }
    }
}

// ── Trade Card ───────────────────────────────────────────────────────────────
@Composable
fun TradeCard(trade: Trade, onClick: () -> Unit, showBuyer: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trade.itemDescription,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "MWK ${"%,.0f".format(trade.amount)}",
                    fontSize = 13.sp,
                    color = EscrowGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (showBuyer) "buyer: ${trade.buyerName.ifBlank { "Unknown" }}"
                    else "seller: ${trade.sellerName.ifBlank { trade.sellerPhone }}",
                    fontSize = 12.sp,
                    color = OnSurfaceDark.copy(0.5f)
                )
            }
            TradeStatusBadge(trade.status)
        }
    }
}

// ── Status Badge ─────────────────────────────────────────────────────────────
@Composable
fun TradeStatusBadge(status: String) {
    val (color, label) = when (status) {
        TradeStatus.PENDING.name   -> EscrowAmber to "Pending"
        TradeStatus.ACTIVE.name    -> EscrowGreenLight to "Active"
        TradeStatus.COMPLETED.name -> EscrowGreen to "Done"
        TradeStatus.REFUNDED.name  -> EscrowRed to "Refunded"
        TradeStatus.REJECTED.name  -> Color.Gray to "Rejected"
        else                       -> Color.Gray to "Unknown"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  TRADE DETAIL SCREEN  (Accept / Reject / Escrow actions)
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeDetailScreen(
    tradeId: String,
    currentUserUid: String,
    onBack: () -> Unit,
    onGoToEscrow: (String) -> Unit,
    viewModel: TradeViewModel = hiltViewModel(),
    reviewViewModel: ReviewViewModel = hiltViewModel()
) {
    val buyerTrades by viewModel.buyerTrades.collectAsState()
    val sellerTrades by viewModel.sellerTrades.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val reviewUiState by reviewViewModel.uiState.collectAsState()

    val trade = (buyerTrades + sellerTrades).firstOrNull { it.id == tradeId }
    val isBuyer = trade?.buyerUid == currentUserUid
    val isSeller = trade?.sellerUid == currentUserUid

    var showRatingDialog by remember { mutableStateOf(false) }
    var alreadyReviewed by remember(tradeId) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is TradeUiState.Success) viewModel.resetState()
    }

    // Once the trade is COMPLETED, check (one-shot) whether this user already rated it
    LaunchedEffect(trade?.status, currentUserUid) {
        if (trade?.status == TradeStatus.COMPLETED.name && currentUserUid.isNotBlank()) {
            alreadyReviewed = reviewViewModel.hasReviewed(tradeId, currentUserUid)
        }
    }

    LaunchedEffect(reviewUiState) {
        if (reviewUiState is ReviewUiState.Success) {
            alreadyReviewed = true
            showRatingDialog = false
            reviewViewModel.resetState()
        }
    }

    if (showRatingDialog && trade != null) {
        val counterpartUid = if (isBuyer) trade.sellerUid else trade.buyerUid
        val counterpartName = if (isBuyer) trade.sellerName else trade.buyerName
        RateTradeDialog(
            counterpartName = counterpartName,
            isSubmitting = reviewUiState is ReviewUiState.Loading,
            errorMessage = (reviewUiState as? ReviewUiState.Error)?.message,
            onDismiss = {
                showRatingDialog = false
                reviewViewModel.resetState()
            },
            onSubmit = { rating, comment ->
                reviewViewModel.submitReview(
                    tradeId = tradeId,
                    ratedUid = counterpartUid,
                    rating = rating,
                    comment = comment
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trade Details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (trade == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EscrowGreen)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status header card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Status", color = OnSurfaceDark.copy(0.5f), fontSize = 12.sp)
                        TradeStatusBadge(trade.status)
                    }
                    Text(trade.itemDescription, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnSurfaceDark)
                    Text("MWK ${"%,.0f".format(trade.amount)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = EscrowGreen)
                }
            }

            // Parties card
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeInfoRow(label = "Buyer", value = trade.buyerName)
                    TradeInfoRow(label = "Seller", value = "${trade.sellerName} (${trade.sellerPhone})")
                    TradeInfoRow(
                        label = "You are",
                        value = when {
                            isBuyer && isSeller -> "Buyer and seller (same account)"
                            isBuyer -> "The buyer"
                            isSeller -> "The seller"
                            else -> "Not a party to this trade (uid mismatch)"
                        }
                    )
                    if (trade.txHash.isNotBlank()) {
                        TradeInfoRow(label = "Payment Ref", value = trade.txHash.take(20) + "…")
                    }
                }
            }

            // Actions
            when {
                // Seller: can accept or reject PENDING trade
                isSeller && trade.status == TradeStatus.PENDING.name -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.rejectTrade(trade.id) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EscrowRed)
                        ) { Text("Reject") }

                        Button(
                            onClick = { viewModel.acceptTrade(trade.id) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                        ) {
                            if (uiState is TradeUiState.Loading)
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("Accept Trade")
                        }
                    }
                }

                // Buyer: ACTIVE but not yet paid → lock funds
                isBuyer && trade.status == TradeStatus.ACTIVE.name && trade.txHash.isBlank() -> {
                    Button(
                        onClick = { onGoToEscrow(trade.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                    ) {
                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lock Funds in Escrow", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Buyer: ACTIVE and paid → confirm receipt (the confirm dialog lives on the escrow screen)
                isBuyer && trade.status == TradeStatus.ACTIVE.name -> {
                    Button(
                        onClick = { onGoToEscrow(trade.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Confirm Receipt & Release Funds", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Seller: ACTIVE → tell them what's happening instead of showing nothing
                isSeller && trade.status == TradeStatus.ACTIVE.name -> {
                    Text(
                        if (trade.txHash.isBlank()) "Trade accepted — waiting for the buyer to pay into escrow."
                        else "Payment received and held in escrow. Deliver the item — funds release once the buyer confirms receipt.",
                        fontSize = 13.sp,
                        color = OnSurfaceDark.copy(0.7f)
                    )
                }

                // Either party: rate the trade once it's COMPLETED (once per user per trade)
                trade.status == TradeStatus.COMPLETED.name && alreadyReviewed == false -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EscrowGreen.copy(0.08f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EscrowGreen.copy(0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, null, tint = EscrowAmber)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Trade complete", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                                Text(
                                    "How was your experience with ${if (isBuyer) trade.sellerName else trade.buyerName}?",
                                    fontSize = 12.sp,
                                    color = OnSurfaceDark.copy(0.7f)
                                )
                            }
                            Button(
                                onClick = { showRatingDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                            ) { Text("Rate") }
                        }
                    }
                }

                trade.status == TradeStatus.COMPLETED.name && alreadyReviewed == true -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = EscrowGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("You've rated this trade. Thanks for the feedback!", fontSize = 13.sp, color = OnSurfaceDark.copy(0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = OnSurfaceDark.copy(0.5f), fontSize = 13.sp)
        Text(value, color = OnSurfaceDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ════════════════════════════════════════════════════════════════════════
//  RATE & REVIEW DIALOG
// ════════════════════════════════════════════════════════════════════════

@Composable
fun RateTradeDialog(
    counterpartName: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String) -> Unit
) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = SurfaceDark,
        title = {
            Text(
                "Rate ${counterpartName.ifBlank { "this trade" }}",
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
        },
        text = {
            Column {
                Text(
                    "Tap a star to rate your experience.",
                    fontSize = 13.sp,
                    color = OnSurfaceDark.copy(0.6f)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (star in 1..5) {
                        Icon(
                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star star",
                            tint = EscrowAmber,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable(enabled = !isSubmitting) { rating = star }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                EscrowTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = "Add a comment (optional)",
                    leadingIcon = Icons.Default.RateReview
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = EscrowRed, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel", color = OnSurfaceDark.copy(0.6f))
            }
        }
    )
}