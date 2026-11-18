package com.escrowmalawi.presentation.screens.moderator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.data.model.ModOverview
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.Loadable
import com.escrowmalawi.presentation.viewmodel.ModeratorViewModel

/** In-app moderator console: Overview, Disputes, Users and Verify tabs. Only reachable for allow-listed accounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorScreen(
    onBack: () -> Unit,
    viewModel: ModeratorViewModel = hiltViewModel()
) {
    val overview by viewModel.overview.collectAsState()
    val disputes by viewModel.disputes.collectAsState()
    val users by viewModel.users.collectAsState()
    val verifications by viewModel.verifications.collectAsState()
    val userTrades by viewModel.userTrades.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    // Load what the tab badges need right away; Users loads when its tab opens
    LaunchedEffect(Unit) {
        viewModel.loadOverview()
        viewModel.loadDisputes()
        viewModel.loadVerifications()
    }
    LaunchedEffect(tab) { if (tab == 2 && users.data == null && !users.loading) viewModel.loadUsers() }

    val openDisputes = disputes.data?.count { it.status == "open" } ?: 0
    val pendingVerifications = verifications.data?.count { it.status == "pending" } ?: 0
    val titles = listOf(
        "Overview",
        if (openDisputes > 0) "Disputes ($openDisputes)" else "Disputes",
        "Users",
        if (pendingVerifications > 0) "Verify ($pendingVerifications)" else "Verify"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moderator console", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        when (tab) {
                            0 -> viewModel.loadOverview()
                            1 -> viewModel.loadDisputes()
                            2 -> viewModel.loadUsers()
                            else -> viewModel.loadVerifications()
                        }
                    }) { Icon(Icons.Default.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark, titleContentColor = OnSurfaceDark)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BackgroundDark
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = SurfaceDark, contentColor = EscrowGreen) {
                titles.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(title, fontSize = 13.sp, maxLines = 1, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = EscrowGreen,
                        unselectedContentColor = OnSurfaceDark.copy(0.6f)
                    )
                }
            }

            when (tab) {
                0 -> OverviewTab(overview, onRetry = viewModel::loadOverview)
                1 -> ModeratorDisputesTab(
                    state = disputes, busy = busy,
                    onRetry = viewModel::loadDisputes,
                    onAnalyze = viewModel::analyze,
                    onResolve = viewModel::resolve
                )
                2 -> ModeratorUsersTab(
                    state = users, userTrades = userTrades, busy = busy,
                    onRetry = viewModel::loadUsers,
                    onLoadTrades = viewModel::loadUserTrades,
                    onSetVerified = viewModel::setVerified
                )
                else -> ModeratorVerifyTab(
                    state = verifications, busy = busy,
                    onRetry = viewModel::loadVerifications,
                    onReview = viewModel::review
                )
            }
        }
    }
}

// ── Overview ─────────────────────────────────────────────────────────────────

private const val LOW_ETH = 0.002

@Composable
private fun OverviewTab(state: Loadable<ModOverview>, onRetry: () -> Unit) {
    val data = state.data
    when {
        data == null && state.loading -> ModLoading()
        data == null -> ModError(state.error ?: "Could not load", onRetry)
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Stat("Open disputes", "${data.openDisputes}", if (data.openDisputes > 0) EscrowAmber else EscrowGreen, Modifier.weight(1f))
                    Stat("Active trades", "${data.statusCounts["ACTIVE"] ?: 0}", EscrowGreen, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Stat("Completed volume", mwk(data.completedVolume), EscrowGreen, Modifier.weight(1f))
                    Stat("Users · listings", "${data.users} · ${data.activeListings}", EscrowGreen, Modifier.weight(1f))
                }
            }
            item {
                val a = data.arbiter
                val low = a.error.isBlank() && a.balanceEth < LOW_ETH
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (low || a.error.isNotBlank()) EscrowAmber.copy(0.14f) else SurfaceDark),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Arbiter wallet", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                        if (a.error.isNotBlank()) {
                            Text("Could not read balance: ${a.error}", color = EscrowRed, fontSize = 13.sp)
                        } else {
                            Text("${"%.4f".format(a.balanceEth)} ETH", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = OnSurfaceDark)
                            Text(a.address, fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f))
                            if (low) Text(
                                "Low balance: disputes can't be resolved on-chain without gas. Top up from a Sepolia faucet.",
                                fontSize = 12.sp, color = EscrowRed
                            )
                        }
                    }
                }
            }
            item {
                val max = (data.statusCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Trades by status", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                        data.statusCounts.forEach { (status, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(status.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.width(86.dp), fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                                Box(Modifier.weight(1f).height(10.dp).background(OnSurfaceDark.copy(0.08f), RoundedCornerShape(6.dp))) {
                                    Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().background(statusTint(status), RoundedCornerShape(6.dp)))
                                }
                                Text("$n", modifier = Modifier.width(30.dp), fontSize = 12.sp, color = OnSurfaceDark, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }
                }
            }
            item { Text("Recent activity", fontWeight = FontWeight.Bold, color = OnSurfaceDark) }
            if (data.recent.isEmpty()) item { ModEmpty("No trades yet.") }
            items(data.recent) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t.itemDescription.ifBlank { "(no description)" }} · ${mwk(t.amount)}", color = OnSurfaceDark, fontSize = 14.sp, maxLines = 1)
                            Text(
                                "${t.buyerName.ifBlank { "Buyer" }} → ${t.sellerName.ifBlank { "Seller" }} · ${ago(t.updatedAt)}",
                                fontSize = 12.sp, color = OnSurfaceDark.copy(0.55f), maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Chip(t.status.lowercase(), statusTint(t.status))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1)
        }
    }
}
