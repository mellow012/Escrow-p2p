package com.escrowmalawi.presentation.screens.moderator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.escrowmalawi.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun mwk(amount: Double) = "MWK ${"%,.0f".format(amount)}"

fun ago(ms: Long?): String {
    if (ms == null || ms == 0L) return "never"
    val s = ((System.currentTimeMillis() - ms) / 1000).coerceAtLeast(1)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

fun clock(ms: Long?): String =
    if (ms == null || ms == 0L) "" else SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

fun statusTint(status: String): Color = when (status.uppercase()) {
    "COMPLETED", "RESOLVED", "APPROVED" -> EscrowGreen
    "ACTIVE" -> EscrowGreen
    "PENDING", "DISPUTED", "OPEN" -> EscrowAmber
    "REFUNDED", "REJECTED" -> EscrowRed
    else -> Color.Gray
}

@Composable
fun Chip(text: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(999.dp), color = tint.copy(alpha = 0.14f), modifier = modifier) {
        Text(
            text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ModLoading(label: String = "Loading…") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = EscrowGreen)
        Spacer(Modifier.width(12.dp))
        Text(label, color = OnSurfaceDark.copy(0.6f))
    }
}

@Composable
fun ModError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = EscrowRed, fontSize = 14.sp)
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun ModEmpty(text: String) {
    Text(
        text, color = OnSurfaceDark.copy(0.5f),
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        fontSize = 14.sp
    )
}

/** Horizontal row of evidence photos; tap one to enlarge. */
@Composable
fun PhotoRow(urls: List<String>, onZoom: (String) -> Unit) {
    if (urls.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(urls) { url ->
            AsyncImage(
                model = url, contentDescription = "Photo", contentScale = ContentScale.Crop,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { onZoom(url) }
            )
        }
    }
}

@Composable
fun ZoomDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AsyncImage(
            model = url, contentDescription = "Photo", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss)
        )
    }
}
