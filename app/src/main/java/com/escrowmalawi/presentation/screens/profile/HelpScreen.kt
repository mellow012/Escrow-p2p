package com.escrowmalawi.presentation.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escrowmalawi.presentation.theme.*

// Change these to your real support details.
private const val SUPPORT_EMAIL = "wiz116mlambia@gmail.com"
private const val SUPPORT_PHONE = "+265890570403"

private val FAQ = listOf(
    "How does escrow protect me?" to
        "When you pay, the money is held by the platform, not sent to the seller. The seller is only paid after you confirm you received the item.",
    "When does the seller get paid?" to
        "After you tap confirm receipt. If you don't respond within the confirm window after the seller marks the item delivered, the funds are released automatically.",
    "The item didn't arrive or isn't as described" to
        "Open the trade and tap \"Report a problem\". Describe what happened and add photos. The seller can reply with their side, then a moderator reviews both and decides whether to release or refund.",
    "What happens to my trade during a dispute?" to
        "It is frozen. Nothing is released or refunded until the moderator decides, and you'll get a notification with the result.",
    "How do I get the verified badge?" to
        "Go to Profile > Get verified, send a photo of your ID and a selfie holding it. A moderator reviews them and you'll be notified.",
    "What is the on-chain record?" to
        "Each step of a trade is also written to the Ethereum Sepolia test network. Tap \"View on Etherscan\" on a trade to check it. No real crypto is involved.",
    "I forgot my password" to
        "Go to Profile > Change password. We'll email you a link to set a new one."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("Common questions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EscrowGreenLight)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    FAQ.forEachIndexed { i, (q, a) ->
                        Column(Modifier.fillMaxWidth().clickable { open = if (open == i) -1 else i }.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(q, modifier = Modifier.weight(1f), color = OnSurfaceDark, fontWeight = FontWeight.Medium)
                                Icon(
                                    if (open == i) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = OnSurfaceDark.copy(0.5f)
                                )
                            }
                            if (open == i) {
                                Spacer(Modifier.height(8.dp))
                                Text(a, fontSize = 13.sp, color = OnSurfaceDark.copy(0.75f), lineHeight = 19.sp)
                            }
                        }
                        if (i < FAQ.lastIndex) HorizontalDivider(color = OnSurfaceDark.copy(0.06f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Still need help?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EscrowGreenLight)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    ContactRow(Icons.Default.Email, "Email us", SUPPORT_EMAIL) {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).putExtra(Intent.EXTRA_SUBJECT, "Escrow P2P support"))
                    }
                    HorizontalDivider(color = OnSurfaceDark.copy(0.06f))
                    ContactRow(Icons.Default.Phone, "Call us", SUPPORT_PHONE) {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE")))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = EscrowGreen)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnSurfaceDark, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
        }
        Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDark.copy(0.3f))
    }
}
