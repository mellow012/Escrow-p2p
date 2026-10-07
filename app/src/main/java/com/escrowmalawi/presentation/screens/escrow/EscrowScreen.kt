package com.escrowmalawi.presentation.screens.escrow

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.EscrowUiState
import com.escrowmalawi.presentation.viewmodel.EscrowViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowScreen(
    tradeId       : String,
    currentUserUid: String,
    onBack        : () -> Unit,
    tradeViewModel : TradeViewModel  = hiltViewModel(),
    escrowViewModel: EscrowViewModel = hiltViewModel()
) {
    val buyerTrades  by tradeViewModel.buyerTrades.collectAsState()
    val sellerTrades by tradeViewModel.sellerTrades.collectAsState()
    val escrowState  by escrowViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val trade  = (buyerTrades + sellerTrades).firstOrNull { it.id == tradeId }
    val isBuyer = trade?.buyerUid == currentUserUid
    val fundsLocked = trade?.txHash?.isNotBlank() == true

    var successMessage  by remember { mutableStateOf<String?>(null) }
    var pendingAction    by remember { mutableStateOf<EscrowAction?>(null) }

    // Open the PayChangu checkout page in the browser as soon as it's ready
    LaunchedEffect(escrowState) {
        when (val state = escrowState) {
            is EscrowUiState.CheckoutReady -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.checkoutUrl)))
                escrowViewModel.resetState()
                // No explicit "success" here — the payment itself completes
                // asynchronously. The trade's real-time Firestore listener
                // picks up trade.txHash once the backend webhook confirms
                // the payment, and this screen re-renders automatically.
            }
            is EscrowUiState.Success -> {
                successMessage = state.message
                pendingAction = null
                escrowViewModel.resetState()
            }
            else -> {}
        }
    }

    // Confirm dialog for release/refund (no wallet key needed anymore)
    pendingAction?.let { action ->
        if (action != EscrowAction.LOCK) {
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                containerColor = SurfaceDark,
                title = {
                    Text(
                        if (action == EscrowAction.RELEASE) "Release funds to seller?" else "Refund the buyer?",
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                },
                text = {
                    Text(
                        if (action == EscrowAction.RELEASE)
                            "Confirm you've received the item. This marks the trade as completed."
                        else
                            "This marks the trade as refunded.",
                        color = OnSurfaceDark.copy(0.7f)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (action == EscrowAction.RELEASE) escrowViewModel.releaseFunds(tradeId)
                            else escrowViewModel.refundBuyer(tradeId)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (action == EscrowAction.RELEASE) EscrowGreen else EscrowAmber
                        )
                    ) { Text("Confirm", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAction = null }) {
                        Text("Cancel", color = OnSurfaceDark.copy(0.6f))
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trade Escrow", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Status header ─────────────────────────────────────────────
            TradeStatusHeader(trade.status)

            // ── Progress stepper ─────────────────────────────────────────
            EscrowStepper(trade.status, fundsLocked)

            // ── Trade detail card ─────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape  = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    DetailRow(Icons.Default.ShoppingBag,          "Item",          trade.itemDescription)
                    RowDivider()
                    DetailRow(Icons.Default.Payments,             "Amount",        "MWK ${"%,.0f".format(trade.amount)}", EscrowGreen)
                    RowDivider()
                    DetailRow(Icons.Default.Person,               "Seller",        trade.sellerName.ifBlank { trade.sellerPhone })
                    if (fundsLocked) {
                        RowDivider()
                        DetailRow(Icons.Default.Receipt, "Payment Ref", trade.txHash, EscrowGreen)
                    }
                }
            }

            // ── Success message ───────────────────────────────────────────
            successMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowGreen.copy(0.08f)),
                    shape  = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(EscrowGreen.copy(0.3f), EscrowGreenLight.copy(0.1f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(EscrowGreen.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = EscrowGreen, modifier = Modifier.size(22.dp))
                        }
                        Text(message, fontWeight = FontWeight.Bold, color = EscrowGreen)
                    }
                }
            }

            // ── Error message ─────────────────────────────────────────────
            if (escrowState is EscrowUiState.Error) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowRed.copy(0.08f)),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Error, null, tint = EscrowRed, modifier = Modifier.size(22.dp))
                        Column {
                            Text("Something went wrong", fontWeight = FontWeight.Bold, color = EscrowRed)
                            Text((escrowState as EscrowUiState.Error).message, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                        }
                    }
                }
            }

            if (!fundsLocked && trade.status == TradeStatus.ACTIVE.name && isBuyer) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowAmber.copy(0.08f)),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Tapping below opens PayChangu's secure checkout in your browser. Once payment completes, this screen updates automatically — no need to come back and refresh.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = EscrowAmber.copy(0.9f)
                    )
                }
            }

            // ── Action buttons ────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (escrowState is EscrowUiState.Loading) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape  = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = EscrowGreen)
                            Text("Processing…", color = OnSurfaceDark.copy(0.7f))
                        }
                    }
                } else {
                    when {
                        // Buyer: pay into escrow when trade is accepted (ACTIVE) but not yet paid
                        isBuyer && trade.status == TradeStatus.ACTIVE.name && !fundsLocked -> {
                            EscrowActionBtn(
                                label = "Lock Funds in Escrow",
                                icon  = Icons.Default.Lock,
                                color = EscrowGreen
                            ) { escrowViewModel.initiateCheckout(tradeId) }
                        }

                        // Buyer: confirm receipt → release funds (only once funds are actually locked)
                        isBuyer && trade.status == TradeStatus.ACTIVE.name && fundsLocked -> {
                            EscrowActionBtn(
                                label = "Confirm Receipt & Release Funds",
                                icon  = Icons.Default.CheckCircle,
                                color = EscrowGreen
                            ) { pendingAction = EscrowAction.RELEASE }
                        }

                        // Buyer: request refund
                        isBuyer && (trade.status == TradeStatus.ACTIVE.name || trade.status == TradeStatus.PENDING.name) -> {
                            EscrowActionBtn(
                                label    = "Request Refund",
                                icon     = Icons.Default.Undo,
                                color    = EscrowAmber,
                                outlined = true
                            ) { pendingAction = EscrowAction.REFUND }
                        }

                        // Completed or refunded
                        trade.status == TradeStatus.COMPLETED.name || trade.status == TradeStatus.REFUNDED.name -> {
                            StatusInfoCard(
                                if (trade.status == TradeStatus.COMPLETED.name)
                                    "✓ Trade completed — funds released to seller."
                                else
                                    "↩ Trade refunded — funds returned to buyer.",
                                if (trade.status == TradeStatus.COMPLETED.name) EscrowGreen else EscrowAmber
                            )
                        }

                        // Rejected
                        trade.status == TradeStatus.REJECTED.name -> {
                            StatusInfoCard("✕ Trade rejected by seller.", EscrowRed)
                        }

                        // Pending (seller hasn't accepted yet)
                        trade.status == TradeStatus.PENDING.name -> {
                            StatusInfoCard("Awaiting seller acceptance…", EscrowAmber)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun TradeStatusHeader(status: String) {
    val (color, label, icon) = when (status) {
        TradeStatus.PENDING.name   -> Triple(EscrowAmber,     "Awaiting Seller",  Icons.Default.HourglassEmpty)
        TradeStatus.ACTIVE.name    -> Triple(EscrowGreen,     "Trade Active",     Icons.Default.GppGood)
        TradeStatus.COMPLETED.name -> Triple(EscrowGreen,     "Completed",        Icons.Default.CheckCircle)
        TradeStatus.REFUNDED.name  -> Triple(EscrowAmber,     "Refunded",         Icons.Default.Undo)
        TradeStatus.REJECTED.name  -> Triple(EscrowRed,       "Rejected",         Icons.Default.Cancel)
        else                       -> Triple(Color.Gray,      "Unknown",          Icons.Default.Help)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(color.copy(0.18f), color.copy(0.04f))))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
                Text("Trade ID: ${tradeStatusShortId(status)}", fontSize = 11.sp, color = OnSurfaceDark.copy(0.4f))
            }
        }
    }
}

@Composable
private fun EscrowStepper(status: String, fundsLocked: Boolean) {
    val steps = listOf(
        Pair("Created",   Icons.Default.Description),
        Pair("Accepted",  Icons.Default.Handshake),
        Pair("Paid",      Icons.Default.Lock),
        Pair("Completed", Icons.Default.CheckCircle)
    )
    val currentStep = when {
        status == TradeStatus.PENDING.name   -> 0
        status == TradeStatus.COMPLETED.name -> 3
        status == TradeStatus.REFUNDED.name  -> 2
        status == TradeStatus.REJECTED.name  -> -1
        status == TradeStatus.ACTIVE.name && fundsLocked -> 2
        status == TradeStatus.ACTIVE.name    -> 1
        else                                  -> 0
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape  = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, (label, icon) ->
                val done    = index < currentStep
                val current = index == currentStep
                val active  = done || current

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done    -> EscrowGreen.copy(0.2f)
                                    current -> EscrowGreen.copy(0.12f)
                                    else    -> OnSurfaceDark.copy(0.05f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            Icon(Icons.Default.Check, null, tint = EscrowGreen, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                icon,
                                null,
                                tint = if (current) EscrowGreen else OnSurfaceDark.copy(0.25f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) EscrowGreen else OnSurfaceDark.copy(0.3f)
                    )
                }

                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .offset(y = (-10).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (done) EscrowGreen.copy(0.5f) else OnSurfaceDark.copy(0.08f))
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon      : ImageVector,
    label     : String,
    value     : String,
    valueColor: Color = OnSurfaceDark
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EscrowGreen.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = EscrowGreen, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = OnSurfaceDark.copy(0.45f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 2)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = OnSurfaceDark.copy(0.05f), thickness = 0.5.dp)
}

@Composable
private fun EscrowActionBtn(
    label   : String,
    icon    : ImageVector,
    color   : Color,
    outlined: Boolean = false,
    onClick : () -> Unit
) {
    if (outlined) {
        OutlinedButton(
            onClick  = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = color),
            border   = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.35f))
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    } else {
        Button(
            onClick  = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun StatusInfoCard(message: String, color: Color) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = color.copy(0.08f)),
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            color      = color,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign  = TextAlign.Center
        )
    }
}

enum class EscrowAction { LOCK, RELEASE, REFUND }

private fun tradeStatusShortId(status: String): String = status.lowercase().replaceFirstChar { it.uppercase() }
