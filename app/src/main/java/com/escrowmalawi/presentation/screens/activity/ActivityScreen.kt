package com.escrowmalawi.presentation.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.data.model.Trade
import com.escrowmalawi.data.model.TradeStatus
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.TradeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ActivityItem(val trade: Trade, val isBuyer: Boolean) {
    val time: Long get() = trade.updatedAt.toDate().time
    val key: String get() = "${trade.id}-${if (isBuyer) "b" else "s"}"
}

private enum class ActivityFilter(val label: String) {
    ALL("All"), IN_PROGRESS("In progress"), COMPLETED("Completed"), ISSUES("Issues")
}

private fun ActivityItem.matches(f: ActivityFilter): Boolean = when (f) {
    ActivityFilter.ALL -> true
    ActivityFilter.IN_PROGRESS -> trade.status == TradeStatus.PENDING.name || trade.status == TradeStatus.ACTIVE.name
    ActivityFilter.COMPLETED -> trade.status == TradeStatus.COMPLETED.name
    ActivityFilter.ISSUES -> trade.status in listOf(
        TradeStatus.REFUNDED.name, TradeStatus.REJECTED.name, TradeStatus.DISPUTED.name
    )
}

/** Short plain-language status for one trade, from this user's point of view. */
private fun statusText(t: Trade, isBuyer: Boolean): String = when (t.status) {
    TradeStatus.PENDING.name -> if (isBuyer) "Waiting for the seller to accept" else "Needs your response"
    TradeStatus.ACTIVE.name -> when {
        t.txHash.isBlank() -> if (isBuyer) "Accepted · pay to lock the funds" else "Accepted · waiting for payment"
        t.deliveredAt != null -> if (isBuyer) "Delivered · confirm you got it" else "Delivered · waiting for the buyer"
        else -> if (isBuyer) "Paid · waiting for delivery" else "Paid · deliver the item"
    }
    TradeStatus.COMPLETED.name -> if (isBuyer) "Completed" else "Completed · funds released to you"
    TradeStatus.REFUNDED.name -> if (isBuyer) "Refunded to you" else "Refunded to the buyer"
    TradeStatus.REJECTED.name -> if (isBuyer) "Declined by the seller" else "You declined this request"
    TradeStatus.DISPUTED.name -> "In dispute · a moderator is reviewing"
    else -> t.status.lowercase().replaceFirstChar { it.uppercase() }
}

private fun statusColor(status: String): Color = when (status) {
    TradeStatus.COMPLETED.name -> EscrowGreen
    TradeStatus.ACTIVE.name -> EscrowGreen
    TradeStatus.PENDING.name, TradeStatus.DISPUTED.name -> EscrowAmber
    TradeStatus.REFUNDED.name, TradeStatus.REJECTED.name -> EscrowRed
    else -> Color.Gray
}

private fun statusIcon(status: String): ImageVector = when (status) {
    TradeStatus.COMPLETED.name -> Icons.Default.CheckCircle
    TradeStatus.ACTIVE.name -> Icons.Default.Lock
    TradeStatus.PENDING.name -> Icons.Default.HourglassTop
    TradeStatus.DISPUTED.name -> Icons.Default.Gavel
    TradeStatus.REFUNDED.name -> Icons.Default.Replay
    TradeStatus.REJECTED.name -> Icons.Default.Cancel
    else -> Icons.Default.SwapHoriz
}

private fun dayLabel(millis: Long): String {
    if (millis == 0L) return "Earlier"
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(day, today)) return "Today"
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(day, yesterday)) return "Yesterday"
    return SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(millis))
}

private fun clockTime(millis: Long): String =
    if (millis == 0L) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun mwk(amount: Double) = "MWK ${"%,.0f".format(amount)}"

/** Timeline of the user's recent transactions (as buyer and as seller) with live status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    onTradeClick: (String) -> Unit,
    viewModel: TradeViewModel = hiltViewModel()
) {
    val buyerTrades by viewModel.buyerTrades.collectAsState()
    val sellerTrades by viewModel.sellerTrades.collectAsState()
    var filter by remember { mutableStateOf(ActivityFilter.ALL) }

    val all = remember(buyerTrades, sellerTrades) {
        (buyerTrades.map { ActivityItem(it, true) } + sellerTrades.map { ActivityItem(it, false) })
            .sortedByDescending { it.time }
    }
    val shown = remember(all, filter) { all.filter { it.matches(filter) } }

    // Money summary
    val spent = all.filter { it.isBuyer && it.trade.status == TradeStatus.COMPLETED.name }.sumOf { it.trade.amount }
    val earned = all.filter { !it.isBuyer && it.trade.status == TradeStatus.COMPLETED.name }.sumOf { it.trade.amount }
    val inEscrow = all.filter {
        it.isBuyer && it.trade.status == TradeStatus.ACTIVE.name && it.trade.txHash.isNotBlank()
    }.sumOf { it.trade.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard("Spent", mwk(spent), Icons.Default.ArrowUpward, EscrowRed, Modifier.weight(1f))
                    SummaryCard("Earned", mwk(earned), Icons.Default.ArrowDownward, EscrowGreen, Modifier.weight(1f))
                    SummaryCard("In escrow", mwk(inEscrow), Icons.Default.Lock, EscrowAmber, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityFilter.values().forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (shown.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.History, null, tint = OnSurfaceDark.copy(0.3f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (all.isEmpty()) "No activity yet" else "Nothing matches this filter",
                            color = OnSurfaceDark.copy(0.5f)
                        )
                    }
                }
            } else {
                var lastLabel = ""
                shown.forEach { entry ->
                    val label = dayLabel(entry.time)
                    if (label != lastLabel) {
                        lastLabel = label
                        item(key = "h-$label") {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EscrowGreenLight,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                    }
                    item(key = entry.key) { ActivityRow(entry, onClick = { onTradeClick(entry.trade.id) }) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 11.sp, color = OnSurfaceDark.copy(0.6f))
            }
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val t = item.trade
    val tint = statusColor(t.status)
    val counterparty = (if (item.isBuyer) t.sellerName else t.buyerName).trim().ifBlank { if (item.isBuyer) "the seller" else "the buyer" }
    val title = (if (item.isBuyer) "Bought" else "Sold") + " " + t.itemDescription.trim()
    val done = t.status == TradeStatus.COMPLETED.name
    val amountColor = when {
        done && item.isBuyer -> OnSurfaceDark
        done -> EscrowGreen
        else -> OnSurfaceDark.copy(0.55f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(tint.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(statusIcon(t.status), null, tint = tint, modifier = Modifier.size(22.dp)) }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = OnSurfaceDark, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (item.isBuyer) "From " else "To ") + counterparty,
                    fontSize = 12.sp, color = OnSurfaceDark.copy(0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(statusText(t, item.isBuyer), fontSize = 12.sp, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (item.isBuyer) "-" else "+") + mwk(t.amount),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = amountColor
                )
                Text(clockTime(item.time), fontSize = 11.sp, color = OnSurfaceDark.copy(0.4f))
            }
        }
    }
}
