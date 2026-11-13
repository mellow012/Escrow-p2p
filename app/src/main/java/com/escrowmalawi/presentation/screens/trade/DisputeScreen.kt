package com.escrowmalawi.presentation.screens.trade

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.DisputeUiState
import com.escrowmalawi.presentation.viewmodel.DisputeViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel

private const val MAX_PHOTOS = 3
private const val MIN_STATEMENT = 20

/**
 * Open a dispute (buyer or seller), add your side, and follow the moderator's decision.
 *  - no dispute yet + trade ACTIVE and paid  → "open dispute" form
 *  - dispute open, your statement empty       → "add your side" form
 *  - dispute open, you've written yours       → both sides + "waiting for the moderator"
 *  - dispute resolved                         → the decision
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisputeScreen(
    tradeId: String,
    currentUserUid: String,
    onBack: () -> Unit,
    viewModel: DisputeViewModel = hiltViewModel(),
    tradeViewModel: TradeViewModel = hiltViewModel()
) {
    val context = LocalContext.current.applicationContext
    val buyerTrades by tradeViewModel.buyerTrades.collectAsState()
    val sellerTrades by tradeViewModel.sellerTrades.collectAsState()
    val dispute by remember(tradeId) { viewModel.disputeFlow(tradeId) }.collectAsState(initial = null)
    val uiState by viewModel.uiState.collectAsState()

    val trade = (buyerTrades + sellerTrades).firstOrNull { it.id == tradeId }
    val isBuyer = trade?.buyerUid == currentUserUid
    val role = if (isBuyer) "buyer" else "seller"

    var statement by remember(tradeId) { mutableStateOf("") }
    var photos by remember(tradeId) { mutableStateOf<List<Uri>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris -> photos = (photos + uris).distinct().take(MAX_PHOTOS) }

    LaunchedEffect(uiState) {
        if (uiState is DisputeUiState.Done) {
            statement = ""
            photos = emptyList()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dispute", fontWeight = FontWeight.SemiBold) },
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

        val d = dispute
        val myStatement = if (isBuyer) d?.buyerStatement.orEmpty() else d?.sellerStatement.orEmpty()
        val myEvidence = if (isBuyer) d?.buyerEvidence.orEmpty() else d?.sellerEvidence.orEmpty()
        val canOpen = d == null && trade.status == TradeStatus.ACTIVE.name && trade.txHash.isNotBlank()
        val needsMyStatement = d != null && d.status == "open" && myStatement.isBlank()
        val working = uiState is DisputeUiState.Working

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trade summary
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trade.itemDescription, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = OnSurfaceDark)
                    Text("MWK ${"%,.0f".format(trade.amount)}", color = EscrowGreen, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "You are the $role. Buyer: ${trade.buyerName.ifBlank { "—" }}  •  Seller: ${trade.sellerName.ifBlank { "—" }}",
                        fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f)
                    )
                }
            }

            // Resolution banner
            if (d != null && d.status == "resolved") {
                val forSeller = d.resolution == "release"
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowGreen.copy(0.10f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Dispute resolved", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                        Text(
                            if (forSeller) "The moderator decided in favour of the seller. The funds are released to the seller."
                            else "The moderator decided in favour of the buyer. The buyer is refunded.",
                            fontSize = 13.sp, color = OnSurfaceDark.copy(0.8f)
                        )
                        if (d.moderatorNote.isNotBlank()) {
                            Text("Moderator note: ${d.moderatorNote}", fontSize = 13.sp, color = OnSurfaceDark.copy(0.8f))
                        }
                    }
                }
            } else if (d != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowAmber.copy(0.10f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gavel, null, tint = EscrowAmber)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "This trade is frozen. A moderator will review both sides and decide whether the funds go to the seller or back to the buyer.",
                            fontSize = 13.sp, color = OnSurfaceDark.copy(0.85f)
                        )
                    }
                }
            }

            // Statements so far
            if (d != null) {
                SideCard("Buyer's side", d.buyerStatement, d.buyerEvidence)
                SideCard("Seller's side", d.sellerStatement, d.sellerEvidence)
            }

            // Form: open a dispute, or add my side
            if (canOpen || needsMyStatement) {
                Text(
                    if (canOpen) "Report a problem" else "Give your side",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OnSurfaceDark
                )
                if (canOpen) {
                    Text(
                        "Opening a dispute freezes this trade: nothing is released or refunded until a moderator decides. " +
                                "Explain what went wrong, and add photos if you have them.",
                        fontSize = 13.sp, color = OnSurfaceDark.copy(0.7f)
                    )
                }
                OutlinedTextField(
                    value = statement,
                    onValueChange = { statement = it.take(1000) },
                    label = { Text("What happened?") },
                    supportingText = { Text("${statement.trim().length}/1000  (at least $MIN_STATEMENT characters)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EscrowGreen,
                        unfocusedBorderColor = OnSurfaceDark.copy(0.2f),
                        focusedLabelColor = EscrowGreen,
                        cursorColor = EscrowGreen,
                        focusedTextColor = OnSurfaceDark,
                        unfocusedTextColor = OnSurfaceDark
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    photos.forEach { uri ->
                        Box {
                            AsyncImage(
                                model = uri, contentDescription = "Evidence",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            )
                            IconButton(
                                onClick = { photos = photos - uri },
                                modifier = Modifier.size(24.dp).align(Alignment.TopEnd).background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp))
                            ) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                    if (photos.size < MAX_PHOTOS) {
                        OutlinedButton(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !working,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add photo")
                        }
                    }
                }

                (uiState as? DisputeUiState.Error)?.let {
                    Text(it.message, color = EscrowRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                (uiState as? DisputeUiState.Working)?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EscrowGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(it.step, fontSize = 13.sp, color = OnSurfaceDark.copy(0.7f))
                    }
                }

                Button(
                    onClick = {
                        viewModel.submit(
                            context = context,
                            tradeId = tradeId,
                            opening = canOpen,
                            statement = statement,
                            photos = photos,
                            keepEvidence = myEvidence
                        )
                    },
                    enabled = !working && statement.trim().length >= MIN_STATEMENT,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (canOpen) EscrowRed else EscrowGreen)
                ) { Text(if (canOpen) "Open dispute" else "Send my side", fontWeight = FontWeight.SemiBold) }
            } else if (d == null) {
                Text(
                    "A dispute can only be opened after payment, while the trade is active.",
                    fontSize = 13.sp, color = OnSurfaceDark.copy(0.7f)
                )
            } else if (d.status == "open") {
                Text(
                    "Thanks — your side is in. You'll get a notification when the moderator decides.",
                    fontSize = 13.sp, color = OnSurfaceDark.copy(0.7f)
                )
            }
        }
    }
}

@Composable
private fun SideCard(title: String, statement: String, photos: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
            Text(
                statement.ifBlank { "Nothing submitted yet." },
                fontSize = 13.sp,
                color = OnSurfaceDark.copy(if (statement.isBlank()) 0.4f else 0.85f)
            )
            if (photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { url ->
                        AsyncImage(
                            model = url, contentDescription = "Evidence",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        }
    }
}
