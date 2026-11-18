package com.escrowmalawi.presentation.screens.moderator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escrowmalawi.data.model.ModAiProposal
import com.escrowmalawi.data.model.ModDispute
import com.escrowmalawi.presentation.components.UserAvatar
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.Loadable

private enum class StatusFilter(val label: String) { OPEN("Open"), RESOLVED("Resolved"), ALL("All") }

private sealed interface Ev {
    val ts: Long
    data class Sys(override val ts: Long, val text: String) : Ev
    data class Side(override val ts: Long, val isBuyer: Boolean, val text: String, val photos: List<String>) : Ev
    data class Ai(override val ts: Long, val p: ModAiProposal) : Ev
    data class Decision(override val ts: Long, val text: String) : Ev
}

private fun buildChat(d: ModDispute): List<Ev> {
    val start = d.createdAt ?: 0L
    val opener = if (d.openedByRole == "seller") "seller" else "buyer"
    val out = mutableListOf<Ev>(Ev.Sys(start, "Dispute opened by the $opener · ${clock(d.createdAt)}"))
    fun side(isBuyer: Boolean) {
        val text = if (isBuyer) d.buyerStatement else d.sellerStatement
        val photos = if (isBuyer) d.buyerEvidence else d.sellerEvidence
        if (text.isBlank() && photos.isEmpty()) return
        val role = if (isBuyer) "buyer" else "seller"
        val submitted = if (isBuyer) d.buyerSubmittedAt else d.sellerSubmittedAt
        val ts = if (role == opener) start + 1 else (submitted ?: (start + 2))
        out += Ev.Side(ts, isBuyer, text, photos)
    }
    side(true); side(false)
    d.aiProposal?.let { out += Ev.Ai(d.aiAnalyzedAt ?: (start + 3), it) }
    if (d.status == "resolved") {
        out += Ev.Decision(
            d.resolvedAt ?: (start + 4),
            if (d.resolution == "release") "Decided in favour of the seller. Funds released."
            else "Decided in favour of the buyer. Buyer refunded."
        )
    }
    return out.sortedBy { it.ts }
}

@Composable
fun ModeratorDisputesTab(
    state: Loadable<List<ModDispute>>,
    busy: Set<String>,
    onRetry: () -> Unit,
    onAnalyze: (String) -> Unit,
    onResolve: (tradeId: String, outcome: String, note: String) -> Unit
) {
    var filter by remember { mutableStateOf(StatusFilter.OPEN) }
    var query by remember { mutableStateOf("") }
    var noAi by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var zoom by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, String, String>?>(null) } // tradeId, outcome, note

    val items = state.data
    if (items == null) {
        if (state.loading) ModLoading() else ModError(state.error ?: "Could not load", onRetry)
        return
    }

    val counts = mapOf(
        StatusFilter.OPEN to items.count { it.status == "open" },
        StatusFilter.RESOLVED to items.count { it.status == "resolved" },
        StatusFilter.ALL to items.size
    )
    val shown = items.filter { d ->
        (filter == StatusFilter.ALL || d.status == filter.name.lowercase()) &&
            (!noAi || d.aiProposal == null) &&
            (query.isBlank() || listOf(d.itemDescription, d.buyerName, d.sellerName, d.buyerStatement, d.sellerStatement)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusFilter.values().forEach { f ->
                        FilterChip(
                            selected = filter == f, onClick = { filter = f },
                            label = { Text("${f.label} ${counts[f]}", fontSize = 12.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Filter by item, name or statement") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                FilterChip(selected = noAi, onClick = { noAi = !noAi }, label = { Text("Needs AI analysis", fontSize = 12.sp) })
            }
        }
        if (shown.isEmpty()) item { ModEmpty("No disputes match these filters.") }

        items(shown, key = { it.tradeId }) { d ->
            val isOpen = expanded.contains(d.tradeId)
            CaseCard(
                d = d, expanded = isOpen,
                onToggle = { expanded = if (isOpen) expanded - d.tradeId else expanded + d.tradeId },
                onZoom = { zoom = it },
                analysing = "analyze:${d.tradeId}" in busy,
                resolving = "resolve:${d.tradeId}" in busy,
                onAnalyze = { onAnalyze(d.tradeId) },
                onAskResolve = { outcome, note -> confirm = Triple(d.tradeId, outcome, note) }
            )
        }
    }

    zoom?.let { ZoomDialog(it) { zoom = null } }

    confirm?.let { (id, outcome, note) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (outcome == "release") "Release to seller?" else "Refund the buyer?") },
            text = {
                Text(
                    "This sends the decision on-chain and can't be undone." +
                        if (note.isNotBlank()) "\n\nNote shown to both parties: $note" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { onResolve(id, outcome, note); confirm = null }) {
                    Text(if (outcome == "release") "Release" else "Refund", color = if (outcome == "release") EscrowGreen else EscrowRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CaseCard(
    d: ModDispute,
    expanded: Boolean,
    onToggle: () -> Unit,
    onZoom: (String) -> Unit,
    analysing: Boolean,
    resolving: Boolean,
    onAnalyze: () -> Unit,
    onAskResolve: (outcome: String, note: String) -> Unit
) {
    val open = d.status == "open"
    val buyer = d.buyerName.ifBlank { "Buyer" }
    val seller = d.sellerName.ifBlank { "Seller" }
    val photoCount = d.buyerEvidence.size + d.sellerEvidence.size
    val ai = d.aiProposal
    val waitingFor = when {
        !open -> null
        d.buyerStatement.isBlank() && d.buyerEvidence.isEmpty() -> "buyer"
        d.sellerStatement.isBlank() && d.sellerEvidence.isEmpty() -> "seller"
        else -> null
    }
    var note by remember(d.tradeId) { mutableStateOf("") }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${d.itemDescription.ifBlank { "(no description)" }} · ${mwk(d.amount)}",
                        fontWeight = FontWeight.SemiBold, color = OnSurfaceDark, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text("$buyer vs $seller", fontSize = 12.sp, color = OnSurfaceDark.copy(0.55f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(
                            if (open) "open" else "resolved · ${if (d.resolution == "release") "seller paid" else "buyer refunded"}",
                            statusTint(d.status)
                        )
                        if (ai != null) Chip("AI: ${ai.proposedOutcome.replace("_", " ")}", Color(0xFF7C3AED))
                        if (photoCount > 0) Chip("📷 $photoCount", EscrowGreen)
                        if (waitingFor != null) Chip("waiting: $waitingFor", EscrowAmber)
                    }
                    Text(ago(d.createdAt), fontSize = 11.sp, color = OnSurfaceDark.copy(0.4f))
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnSurfaceDark.copy(0.5f))
            }

            if (expanded) {
                HorizontalDivider(color = OnSurfaceDark.copy(0.08f))
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    buildChat(d).forEach { ev ->
                        when (ev) {
                            is Ev.Sys -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(ev.text, fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f))
                            }
                            is Ev.Decision -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(shape = RoundedCornerShape(14.dp), color = EscrowGreen.copy(0.12f)) {
                                    Text("⚖️ ${ev.text}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = OnSurfaceDark)
                                }
                            }
                            is Ev.Ai -> AiCard(ev.p)
                            is Ev.Side -> Bubble(
                                name = if (ev.isBuyer) buyer else seller,
                                photoUrl = if (ev.isBuyer) d.buyerPhoto else d.sellerPhoto,
                                role = if (ev.isBuyer) "buyer" else "seller",
                                isBuyer = ev.isBuyer, text = ev.text, photos = ev.photos, onZoom = onZoom
                            )
                        }
                    }
                    if (waitingFor != null) {
                        Text("Waiting for the $waitingFor's side…", fontSize = 12.sp, color = OnSurfaceDark.copy(0.45f))
                    }
                    if (d.aiError.isNotBlank() && ai == null) {
                        Text("AI analysis failed: ${d.aiError}", fontSize = 12.sp, color = EscrowRed)
                    }

                    HorizontalDivider(color = OnSurfaceDark.copy(0.08f))

                    if (open) {
                        OutlinedButton(onClick = onAnalyze, enabled = !analysing && !resolving, modifier = Modifier.fillMaxWidth()) {
                            if (analysing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Analysing…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (ai != null) "Re-run AI analysis" else "Run AI analysis")
                            }
                        }
                        OutlinedTextField(
                            value = note, onValueChange = { note = it.take(300) },
                            placeholder = { Text("Note shown to both parties (optional)") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onAskResolve("release", note.trim()) },
                                enabled = !resolving && !analysing,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                            ) {
                                if (resolving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                else Text("Release to seller", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { onAskResolve("refund", note.trim()) },
                                enabled = !resolving && !analysing,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EscrowRed)
                            ) { Text("Refund buyer", fontSize = 13.sp) }
                        }
                        if (resolving) Text("Sending the decision on-chain, this can take up to a minute…", fontSize = 12.sp, color = OnSurfaceDark.copy(0.55f))
                    } else {
                        if (d.moderatorNote.isNotBlank()) Text("Moderator note: ${d.moderatorNote}", fontSize = 13.sp, color = OnSurfaceDark)
                        if (d.resolveTxHash.isNotBlank()) {
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sepolia.etherscan.io/tx/${d.resolveTxHash}")))
                            }) { Text("View resolution on Etherscan ↗", color = EscrowGreen) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(name: String, photoUrl: String?, role: String, isBuyer: Boolean, text: String, photos: List<String>, onZoom: (String) -> Unit) {
    val accent = if (isBuyer) Color(0xFF3B82F6) else EscrowGreen
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isBuyer) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        if (isBuyer) UserAvatar(name, photoUrl, size = 30.dp, fontSize = 12.sp)
        Surface(
            shape = if (isBuyer) RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            color = accent.copy(alpha = if (isBuyer) 0.10f else 0.14f),
            modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 8.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = accent)
                    Text(role, fontSize = 10.sp, color = OnSurfaceDark.copy(0.45f))
                }
                if (text.isNotBlank()) Text(text, fontSize = 14.sp, color = OnSurfaceDark)
                PhotoRow(photos, onZoom)
            }
        }
        if (!isBuyer) UserAvatar(name, photoUrl, size = 30.dp, fontSize = 12.sp)
    }
}

@Composable
private fun AiCard(p: ModAiProposal) {
    val purple = Color(0xFF7C3AED)
    Surface(shape = RoundedCornerShape(16.dp), color = purple.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = purple, modifier = Modifier.size(16.dp))
                Text("AI assistant", fontWeight = FontWeight.Bold, color = purple, fontSize = 13.sp)
                Chip("advice only", purple)
            }
            Chip("${p.proposedOutcome.replace("_", " ")} · ${p.confidence} confidence", if (p.proposedOutcome == "insufficient_evidence") EscrowAmber else purple)
            Text(p.summary, fontSize = 13.sp, color = OnSurfaceDark)
            Text("Reasoning", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark.copy(0.55f))
            Text(p.reasoning, fontSize = 13.sp, color = OnSurfaceDark)
            if (p.evidenceNotes.isNotEmpty()) {
                Text("Photos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark.copy(0.55f))
                Text(p.evidenceNotes.joinToString(" "), fontSize = 13.sp, color = OnSurfaceDark)
            }
        }
    }
}
