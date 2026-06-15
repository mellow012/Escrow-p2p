package com.escrowmalawi.presentation.screens.escrow

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.EscrowUiState
import com.escrowmalawi.presentation.viewmodel.EscrowViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel
import java.math.BigDecimal
import java.math.BigInteger

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
    val lockedAmount by escrowViewModel.lockedAmount.collectAsState()

    val trade  = (buyerTrades + sellerTrades).firstOrNull { it.id == tradeId }
    val isBuyer = trade?.buyerUid == currentUserUid

    var privateKeyHex    by remember { mutableStateOf("") }
    var sellerEthAddress by remember { mutableStateOf("") }
    var showKeyInput     by remember { mutableStateOf(false) }
    var pendingAction    by remember { mutableStateOf<EscrowAction?>(null) }
    var successMessage   by remember { mutableStateOf<String?>(null) }

    // Auto-fill private key and seller address when trade is loaded or action starts
    LaunchedEffect(trade, showKeyInput) {
        if (showKeyInput) {
            if (privateKeyHex.isBlank()) {
                escrowViewModel.getStoredPrivateKey()?.let {
                    privateKeyHex = it
                }
            }
            if (pendingAction == EscrowAction.LOCK && sellerEthAddress.isBlank()) {
                trade?.sellerWalletAddress?.let {
                    sellerEthAddress = it
                }
            }
        }
    }

    LaunchedEffect(trade) {
        trade?.blockchainTradeId?.let { escrowViewModel.loadLockedAmount(it) }
    }

    LaunchedEffect(escrowState) {
        if (escrowState is EscrowUiState.Success) {
            val s = escrowState as EscrowUiState.Success
            successMessage = if (s.txHash.isNotBlank()) s.txHash else "Transaction complete"
            pendingAction  = null
            escrowViewModel.resetState()
            trade?.blockchainTradeId?.let { escrowViewModel.loadLockedAmount(it) }
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
            EscrowStepper(trade.status)

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
                    if (lockedAmount > BigInteger.ZERO) {
                        RowDivider()
                        val ethFormatted = BigDecimal(lockedAmount)
                            .divide(BigDecimal.TEN.pow(18))
                            .setScale(6, java.math.RoundingMode.HALF_UP)
                            .toPlainString()
                        DetailRow(Icons.Default.AccountBalanceWallet, "Locked ETH",    "$ethFormatted ETH", EscrowGreen)
                    }
                    RowDivider()
                    DetailRow(Icons.Default.Fingerprint,          "Blockchain ID",  trade.blockchainTradeId.take(14) + "…")
                }
            }

            // ── Success message ───────────────────────────────────────────
            successMessage?.let { txHash ->
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
                        Column {
                            Text("Transaction Successful", fontWeight = FontWeight.Bold, color = EscrowGreen)
                            Text("TX: ${txHash.take(28)}…", fontSize = 11.sp, color = OnSurfaceDark.copy(0.5f))
                        }
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
                            Text("Transaction Failed", fontWeight = FontWeight.Bold, color = EscrowRed)
                            Text((escrowState as EscrowUiState.Error).message, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                        }
                    }
                }
            }

            // ── Wallet key input ──────────────────────────────────────────
            if (showKeyInput) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, null, tint = EscrowAmber, modifier = Modifier.size(18.dp))
                            Text("Wallet Credentials", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                        }
                        Text(
                            "⚠ Never share your private key with anyone. In production this would use Android Keystore.",
                            fontSize = 11.sp,
                            color = EscrowAmber.copy(0.8f),
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = privateKeyHex,
                            onValueChange = { privateKeyHex = it },
                            label = { Text("Private Key (Hex)") },
                            leadingIcon = { Icon(Icons.Default.Key, null, tint = EscrowGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            shape  = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                        if (pendingAction == EscrowAction.LOCK) {
                            OutlinedTextField(
                                value = sellerEthAddress,
                                onValueChange = { sellerEthAddress = it },
                                label = { Text("Seller ETH Address") },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = EscrowGreen) },
                                modifier = Modifier.fillMaxWidth(),
                                shape  = RoundedCornerShape(12.dp),
                                colors = fieldColors()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showKeyInput = false; pendingAction = null },
                                modifier = Modifier.weight(1f),
                                shape  = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark.copy(0.6f))
                            ) { Text("Cancel") }

                            Button(
                                onClick = {
                                    when (pendingAction) {
                                        EscrowAction.LOCK -> {
                                            val wei = BigDecimal.valueOf(trade.amount)
                                                .multiply(BigDecimal.valueOf(1_000_000_000_000_000L)) // Convert to Wei
                                                .toBigInteger()
                                            
                                            escrowViewModel.lockFunds(
                                                privateKeyHex, trade.id, trade.blockchainTradeId,
                                                sellerEthAddress, trade.itemDescription, wei
                                            )
                                        }
                                        EscrowAction.RELEASE -> escrowViewModel.releaseFunds(
                                            privateKeyHex, trade.id, trade.blockchainTradeId
                                        )
                                        EscrowAction.REFUND -> escrowViewModel.refundBuyer(
                                            privateKeyHex, trade.id, trade.blockchainTradeId
                                        )
                                        null -> {}
                                    }
                                    showKeyInput = false
                                },
                                enabled  = privateKeyHex.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                            ) { Text("Confirm", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────
            if (!showKeyInput) {
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
                                Text("Processing transaction…", color = OnSurfaceDark.copy(0.7f))
                            }
                        }
                    } else {
                        when {
                            // Buyer: lock funds when trade is accepted (ACTIVE) but no funds locked yet
                            isBuyer && trade.status == TradeStatus.ACTIVE.name && lockedAmount == BigInteger.ZERO -> {
                                EscrowActionBtn(
                                    label = "Lock Funds in Escrow",
                                    icon  = Icons.Default.Lock,
                                    color = EscrowGreen
                                ) { pendingAction = EscrowAction.LOCK; showKeyInput = true }
                            }

                            // Buyer: confirm receipt → release funds (Only if funds are actually locked)
                            isBuyer && trade.status == TradeStatus.ACTIVE.name && lockedAmount > BigInteger.ZERO -> {
                                EscrowActionBtn(
                                    label = "Confirm Receipt & Release Funds",
                                    icon  = Icons.Default.CheckCircle,
                                    color = EscrowGreen
                                ) { pendingAction = EscrowAction.RELEASE; showKeyInput = true }
                            }

                            // Buyer: request refund
                            isBuyer && (trade.status == TradeStatus.ACTIVE.name || trade.status == TradeStatus.PENDING.name) -> {
                                EscrowActionBtn(
                                    label    = "Request Refund",
                                    icon     = Icons.Default.Undo,
                                    color    = EscrowAmber,
                                    outlined = true
                                ) { pendingAction = EscrowAction.REFUND; showKeyInput = true }
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
private fun EscrowStepper(status: String) {
    val steps = listOf(
        Pair("Created",   Icons.Default.Description),
        Pair("Accepted",  Icons.Default.Handshake),
        Pair("Locked",    Icons.Default.Lock),
        Pair("Completed", Icons.Default.CheckCircle)
    )
    val currentStep = when (status) {
        TradeStatus.PENDING.name   -> 0
        TradeStatus.ACTIVE.name    -> 1
        TradeStatus.COMPLETED.name -> 3
        TradeStatus.REFUNDED.name  -> 2
        TradeStatus.REJECTED.name  -> -1
        else                       -> 0
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = EscrowGreen,
    unfocusedBorderColor    = OnSurfaceDark.copy(0.15f),
    focusedLabelColor       = EscrowGreen,
    cursorColor             = EscrowGreen,
    focusedContainerColor   = BackgroundDark,
    unfocusedContainerColor = BackgroundDark
)

enum class EscrowAction { LOCK, RELEASE, REFUND }

private fun tradeStatusShortId(status: String): String = status.lowercase().replaceFirstChar { it.uppercase() }