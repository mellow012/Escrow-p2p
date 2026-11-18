package com.escrowmalawi.presentation.screens.moderator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.escrowmalawi.data.model.ModVerification
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.Loadable

private enum class VerifyFilter(val label: String) { PENDING("Pending"), REVIEWED("Reviewed"), ALL("All") }

@Composable
fun ModeratorVerifyTab(
    state: Loadable<List<ModVerification>>,
    busy: Set<String>,
    onRetry: () -> Unit,
    onReview: (uid: String, approve: Boolean, reason: String) -> Unit
) {
    var filter by remember { mutableStateOf(VerifyFilter.PENDING) }
    var zoom by remember { mutableStateOf<String?>(null) }
    var confirmApprove by remember { mutableStateOf<ModVerification?>(null) }
    val reasons = remember { mutableStateMapOf<String, String>() }

    val items = state.data
    if (items == null) {
        if (state.loading) ModLoading() else ModError(state.error ?: "Could not load", onRetry)
        return
    }

    val pending = items.count { it.status == "pending" }
    val shown = items.filter {
        when (filter) {
            VerifyFilter.PENDING -> it.status == "pending"
            VerifyFilter.REVIEWED -> it.status != "pending"
            VerifyFilter.ALL -> true
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerifyFilter.values().forEach { f ->
                    FilterChip(
                        selected = filter == f, onClick = { filter = f },
                        label = { Text(if (f == VerifyFilter.PENDING) "${f.label} $pending" else f.label, fontSize = 12.sp) }
                    )
                }
            }
        }
        if (shown.isEmpty()) item { ModEmpty("Nothing here.") }

        items(shown, key = { it.uid }) { v ->
            val isPending = v.status == "pending"
            val working = "review:${v.uid}" in busy
            val reason = reasons[v.uid].orEmpty()
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name.ifBlank { "(no name)" }, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                            Text("Submitted ${clock(v.submittedAt)}", fontSize = 12.sp, color = OnSurfaceDark.copy(0.55f))
                        }
                        Chip(v.status, statusTint(v.status))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("ID" to v.idImageUrl, "Selfie" to v.selfieUrl).forEach { (label, url) ->
                            if (url.isNotBlank()) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(
                                        model = url, contentDescription = label, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).clickable { zoom = url }
                                    )
                                    Text(label, fontSize = 11.sp, color = OnSurfaceDark.copy(0.55f))
                                }
                            }
                        }
                    }

                    if (isPending) {
                        OutlinedTextField(
                            value = reason, onValueChange = { reasons[v.uid] = it.take(300) },
                            placeholder = { Text("Reason (required to reject)") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { confirmApprove = v }, enabled = !working,
                                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                            ) {
                                if (working) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                else Text("Approve")
                            }
                            Button(
                                onClick = { onReview(v.uid, false, reason.trim()) },
                                enabled = !working && reason.isNotBlank(),
                                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EscrowRed)
                            ) { Text("Reject") }
                        }
                    } else {
                        Text(
                            "Reviewed ${clock(v.reviewedAt)}" + if (v.rejectReason.isNotBlank()) " · reason: ${v.rejectReason}" else "",
                            fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f)
                        )
                    }
                }
            }
        }
    }

    zoom?.let { ZoomDialog(it) { zoom = null } }

    confirmApprove?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmApprove = null },
            title = { Text("Approve ${v.name.ifBlank { "this user" }}?") },
            text = { Text("They get the verified badge and a notification.") },
            confirmButton = {
                TextButton(onClick = { onReview(v.uid, true, ""); confirmApprove = null }) {
                    Text("Approve", color = EscrowGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmApprove = null }) { Text("Cancel") } }
        )
    }
}
