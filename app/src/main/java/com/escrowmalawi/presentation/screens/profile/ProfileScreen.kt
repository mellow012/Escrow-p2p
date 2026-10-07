package com.escrowmalawi.presentation.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onVerifyClick: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    tradeViewModel: TradeViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val buyerTrades by tradeViewModel.buyerTrades.collectAsState()
    val sellerTrades by tradeViewModel.sellerTrades.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val joinedDate = remember(currentUser?.createdAt) {
        currentUser?.createdAt?.let {
            SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it.toDate())
        } ?: "..."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Profile Header ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(EscrowGreen.copy(0.1f))
                    .border(2.dp, EscrowGreen.copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(65.dp),
                    tint = EscrowGreen
                )
                // Verified Badge
                if (currentUser?.isVerified == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(EscrowGreen)
                            .border(2.dp, BackgroundDark, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Verified, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = currentUser?.name ?: "Loading...",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (currentUser?.isVerified == true) "Verified Trader" else "Standard Account",
                    fontSize = 14.sp,
                    color = if (currentUser?.isVerified == true) EscrowGreen else OnSurfaceDark.copy(0.5f)
                )
                if (currentUser?.isVerified == true) {
                    Icon(Icons.Default.Star, null, tint = EscrowGreen, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Stats Section ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val completedTradeCount = (buyerTrades + sellerTrades).count { it.status == com.escrowmalawi.data.model.TradeStatus.COMPLETED.name }
                ProfileStat(completedTradeCount.toString(), "Trades")
                VerticalDivider(modifier = Modifier.height(30.dp).align(Alignment.CenterVertically), thickness = 1.dp, color = OnSurfaceDark.copy(0.1f))
                val ratingCount = currentUser?.ratingCount ?: 0
                val ratingLabel = if (ratingCount > 0) "%.1f".format(currentUser?.avgRating ?: 0.0) else "—"
                ProfileStat(ratingLabel, if (ratingCount > 0) "Rating ($ratingCount)" else "Rating")
                VerticalDivider(modifier = Modifier.height(30.dp).align(Alignment.CenterVertically), thickness = 1.dp, color = OnSurfaceDark.copy(0.1f))
                ProfileStat(joinedDate, "Joined")
            }

            Spacer(Modifier.height(32.dp))

            // ── Verification Prompt (If not verified) ────────────────────
            if (currentUser?.isVerified == false) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EscrowGreen.copy(0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EscrowGreen.copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, null, tint = EscrowGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Increase Trust", fontWeight = FontWeight.Bold, color = EscrowGreen)
                            Text("Verify your identity to increase trade limits.", fontSize = 12.sp, color = OnSurfaceDark.copy(0.7f))
                        }
                        TextButton(onClick = onVerifyClick) {
                            Text("Verify", color = EscrowGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Account Information Card ─────────────────────────────────
            Text(
                "Account Details",
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = EscrowGreenLight
            )
            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileInfoRow(Icons.Default.Email, "Email", currentUser?.email ?: "-")
                    HorizontalDivider(color = OnSurfaceDark.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(Icons.Default.Phone, "Phone", currentUser?.phone ?: "-")
                    HorizontalDivider(color = OnSurfaceDark.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(
                        Icons.Default.AccountBalanceWallet,
                        "Wallet Address",
                        currentUser?.walletAddress?.takeIf { it.isNotBlank() } ?: "Not linked",
                        isCopyable = currentUser?.walletAddress?.isNotBlank() == true,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(currentUser?.walletAddress ?: ""))
                            Toast.makeText(context, "Address copied!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Settings Card ────────────────────────────────────────────
            Text(
                "Settings",
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = EscrowGreenLight
            )
            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsItem(Icons.Default.Shield, "Privacy & Security") {}
                    SettingsItem(Icons.Default.Notifications, "Notification Settings") {}
                    SettingsItem(Icons.AutoMirrored.Filled.Help, "Help & Support") {}
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Logout Button ────────────────────────────────────────────
            Button(
                onClick = {
                    authViewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EscrowRed.copy(0.1f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EscrowRed.copy(0.2f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = EscrowRed)
                Spacer(Modifier.width(10.dp))
                Text("Logout from Device", color = EscrowRed, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Version 1.0.0",
                fontSize = 12.sp,
                color = OnSurfaceDark.copy(0.3f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            val mViewModel: com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel = hiltViewModel()
            TextButton(
                onClick = { mViewModel.seedData(context) },
                modifier = Modifier.alpha(0.5f)
            ) {
                Text("Seed Database (Debug Only)", color = OnSurfaceDark.copy(0.4f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark)
        Text(label, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isCopyable: Boolean = false,
    onCopy: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EscrowGreen.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = EscrowGreen, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceDark,
                maxLines = 1
            )
        }
        if (isCopyable) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, null, tint = EscrowGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = OnSurfaceDark.copy(0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), color = OnSurfaceDark)
        Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDark.copy(0.3f))
    }
}