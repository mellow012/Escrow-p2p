package com.escrowmalawi.presentation.screens.moderator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escrowmalawi.data.model.ModUser
import com.escrowmalawi.data.model.ModUserTrades
import com.escrowmalawi.data.model.ModUsersData
import com.escrowmalawi.presentation.components.UserAvatar
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.Loadable

private enum class UserFilter(val label: String) { ALL("All"), TRADERS("Traders"), NEVER("Never traded"), VERIFIED("Verified") }

@Composable
fun ModeratorUsersTab(
    state: Loadable<ModUsersData>,
    userTrades: Map<String, Loadable<ModUserTrades>>,
    busy: Set<String>,
    onRetry: () -> Unit,
    onLoadTrades: (String) -> Unit,
    onSetVerified: (uid: String, verified: Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(UserFilter.ALL) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val data = state.data
    if (data == null) {
        if (state.loading) ModLoading() else ModError(state.error ?: "Could not load", onRetry)
        return
    }
    val m = data.metrics
    val shown = data.users.filter { u ->
        val traded = u.tradesBuying + u.tradesSelling > 0
        val passesFilter = when (filter) {
            UserFilter.TRADERS -> traded
            UserFilter.NEVER -> !traded
            UserFilter.VERIFIED -> u.isVerified
            UserFilter.ALL -> true
        }
        val matchesQuery = query.isBlank() ||
            listOf(u.name, u.email, u.phone).any { it.contains(query.trim(), ignoreCase = true) }
        passesFilter && matchesQuery
    }
    val traderPct = if (m.totalUsers > 0) (m.everTraded * 100 / m.totalUsers) else 0

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Metric("Total users", "${m.totalUsers}", Modifier.weight(1f))
                    Metric("New · 7d · 30d", "${m.joined7} · ${m.joined30}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Metric("Active · 7d · 30d", "${m.active7} · ${m.active30}", Modifier.weight(1f))
                    Metric("Have traded", "${m.everTraded} ($traderPct%)", Modifier.weight(1f))
                }
                Text(
                    "Active = took part in a trade in that period. Verified: ${m.verified} · with a wallet: ${m.withWallet}.",
                    fontSize = 11.sp, color = OnSurfaceDark.copy(0.5f)
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search name, email or phone") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserFilter.values().forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label, fontSize = 12.sp) })
                    }
                }
            }
        }
        if (shown.isEmpty()) item { ModEmpty("No users match.") }

        items(shown, key = { it.uid }) { u ->
            UserCard(
                u = u,
                expanded = expanded == u.uid,
                trades = userTrades[u.uid],
                verifying = "verify:${u.uid}" in busy,
                onToggle = {
                    val next = if (expanded == u.uid) null else u.uid
                    expanded = next
                    if (next != null) onLoadTrades(next)
                },
                onSetVerified = { onSetVerified(u.uid, it) }
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = OnSurfaceDark.copy(0.6f))
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = OnSurfaceDark, maxLines = 1)
        }
    }
}

@Composable
private fun UserCard(
    u: ModUser,
    expanded: Boolean,
    trades: Loadable<ModUserTrades>?,
    verifying: Boolean,
    onToggle: () -> Unit,
    onSetVerified: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(u.name, u.photoUrl, size = 40.dp, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(u.name.ifBlank { "(no name)" }, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (u.isVerified) Chip("verified", EscrowGreen)
                    }
                    Text(
                        "${u.email} · joined ${ago(u.createdAt)} · last trade ${ago(u.lastActive)}",
                        fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${u.tradesBuying + u.tradesSelling} trades", fontSize = 12.sp, color = OnSurfaceDark.copy(0.7f))
                    if (u.ratingCount > 0) Text("★ ${"%.1f".format(u.avgRating)} (${u.ratingCount})", fontSize = 11.sp, color = EscrowAmber)
                }
            }

            if (expanded) {
                HorizontalDivider(color = OnSurfaceDark.copy(0.08f))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Phone: ${u.phone.ifBlank { "—" }}", fontSize = 13.sp, color = OnSurfaceDark)
                    Text("Wallet: ${u.walletAddress.ifBlank { "none yet" }}", fontSize = 12.sp, color = OnSurfaceDark.copy(0.7f))

                    Spacer(Modifier.height(6.dp))
                    Text("Trades", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                    val tradeData = trades?.data
                    when {
                        tradeData != null -> TradesList(tradeData)
                        trades == null || trades.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EscrowGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("Loading trades…", fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                        }
                        else -> Text(trades.error ?: "Could not load trades", color = EscrowRed, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onSetVerified(!u.isVerified) },
                        enabled = !verifying,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (verifying) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(if (u.isVerified) "Remove verified badge" else "Mark as verified trader")
                    }
                }
            }
        }
    }
}

@Composable
private fun TradesList(t: ModUserTrades) {
    val context = LocalContext.current
    Text(
        "${t.asBuyer} as buyer · ${t.asSeller} as seller · spent ${mwk(t.spent)} · earned ${mwk(t.earned)}",
        fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f)
    )
    if (t.trades.isEmpty()) Text("No trades yet.", fontSize = 12.sp, color = OnSurfaceDark.copy(0.5f))
    t.trades.forEach { tr ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (tr.role == "buyer") "bought" else "sold", if (tr.role == "buyer") Color(0xFF6366F1) else EscrowGreen)
                    Text(tr.itemDescription.ifBlank { "(no description)" }, fontSize = 13.sp, color = OnSurfaceDark, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Text(
                    "${if (tr.role == "buyer") "from" else "to"} ${tr.counterparty.ifBlank { "—" }} · ${ago(tr.updatedAt)}" +
                        if (tr.hasDispute) " · dispute" else "",
                    fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f)
                )
                if (tr.chainTxHash.isNotBlank()) {
                    Text(
                        "on-chain ↗", fontSize = 11.sp, color = EscrowGreen,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sepolia.etherscan.io/tx/${tr.chainTxHash}")))
                        }
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (tr.role == "buyer") "−" else "+"}${mwk(tr.amount)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                Chip(tr.status.lowercase(), statusTint(tr.status))
            }
        }
    }
}
