package com.escrowmalawi.presentation.screens.profile

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.escrowmalawi.data.local.AppSettings
import com.escrowmalawi.presentation.viewmodel.VerificationViewModel
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.escrowmalawi.presentation.components.UserAvatar
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel
import com.escrowmalawi.presentation.viewmodel.WalletViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onVerifyClick: () -> Unit = {},
    onViewCatalog: (String) -> Unit = {},
    onManageListings: () -> Unit = {},
    onHelp: () -> Unit = {},
    onOpenModerator: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    tradeViewModel: TradeViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    verificationViewModel: VerificationViewModel = hiltViewModel(),
    moderatorViewModel: com.escrowmalawi.presentation.viewmodel.ModeratorViewModel = hiltViewModel(),
    followViewModel: com.escrowmalawi.presentation.viewmodel.FollowViewModel = hiltViewModel()
) {
    val following by followViewModel.follows.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val walletBalance by walletViewModel.balance.collectAsState()
    LaunchedEffect(currentUser?.walletAddress) {
        walletViewModel.refresh(currentUser?.walletAddress ?: "")
    }
    val buyerTrades by tradeViewModel.buyerTrades.collectAsState()
    val sellerTrades by tradeViewModel.sellerTrades.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val verification by verificationViewModel.verification.collectAsState()
    val isModerator by moderatorViewModel.isModerator.collectAsState()
    LaunchedEffect(Unit) { moderatorViewModel.checkAccess() }

    var showEdit by remember { mutableStateOf(false) }
    var showAlerts by remember { mutableStateOf(false) }
    var showPasswordConfirm by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadingPhoto = true
            authViewModel.updatePhoto(context.applicationContext, uri) { error ->
                uploadingPhoto = false
                Toast.makeText(context, error ?: "Profile photo updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Verified once the profile flag is set OR a moderator approved the request
    val isVerified = currentUser?.isVerified == true || verification?.status == "approved"
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

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
            Box(modifier = Modifier.size(110.dp)) {
                UserAvatar(
                    name = currentUser?.name,
                    photoUrl = currentUser?.profilePictureUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = !uploadingPhoto) {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .border(2.dp, EscrowGreen.copy(0.5f), CircleShape),
                    size = 110.dp,
                    fontSize = 42.sp
                )
                if (uploadingPhoto) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
                    }
                }
                // Change-photo button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(2.dp, BackgroundDark, CircleShape)
                        .clickable(enabled = !uploadingPhoto) {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, "Change photo", tint = EscrowGreen, modifier = Modifier.size(16.dp))
                }
                // Verified badge
                if (isVerified) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
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
                    text = if (isVerified) "Verified Trader" else "Standard Account",
                    fontSize = 14.sp,
                    color = if (isVerified) EscrowGreen else OnSurfaceDark.copy(0.5f)
                )
                if (isVerified) {
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

            // ── Verification status ──────────────────────────────────────
            if (!isVerified && verification != null) {
                val status = verification?.status.orEmpty()
                val (tint, title, body, button) = when (status) {
                    "pending" -> VerifyCard(EscrowAmber, "Verification under review", "A moderator is checking your photos. We'll notify you.", "View")
                    "rejected" -> VerifyCard(EscrowRed, "Verification rejected", verification?.rejectReason?.ifBlank { null } ?: "Please send clearer photos.", "Try again")
                    else -> VerifyCard(EscrowGreen, "Get verified", "Add a verified badge so buyers and sellers know you're a real person.", "Verify")
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = tint.copy(0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(0.2f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, tint = tint)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold, color = tint)
                            Text(body, fontSize = 12.sp, color = OnSurfaceDark.copy(0.7f))
                        }
                        TextButton(onClick = onVerifyClick) {
                            Text(button, color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Moderator console (only for allow-listed accounts) ───────
            if (isModerator == true) {
                Card(
                    onClick = onOpenModerator,
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EscrowGreen.copy(0.35f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = EscrowGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Moderator console", fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                            Text("Disputes, users and verification requests", fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDark.copy(0.4f))
                    }
                }
            }

            // ── Account Information Card ─────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Account Details",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = EscrowGreenLight
                )
                TextButton(onClick = { showEdit = true }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = EscrowGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", color = EscrowGreen, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))

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
                    HorizontalDivider(color = OnSurfaceDark.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(Icons.Default.LocalGasStation, "Gas balance (Sepolia)", walletBalance)
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
                    SettingsItem(Icons.Default.Favorite, "Sellers you follow · ${following.size}") { onOpenFollowing() }
                    SettingsItem(Icons.Default.Palette, "Appearance · ${ThemeController.mode.label}") { showTheme = true }
                    SettingsItem(Icons.Default.Lock, "Change password") { showPasswordConfirm = true }
                    SettingsItem(Icons.Default.Notifications, "Notification alerts") { showAlerts = true }
                    SettingsItem(Icons.AutoMirrored.Filled.Help, "Help & Support") { onHelp() }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Selling Card ─────────────────────────────────────────────
            Text(
                "Selling",
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
                    SettingsItem(Icons.Default.Storefront, "Public Catalog") {
                        currentUser?.uid?.let { onViewCatalog(it) }
                    }
                    SettingsItem(Icons.Default.Inventory, "Manage My Listings") {
                        onManageListings()
                    }
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
                "Version $versionName",
                fontSize = 12.sp,
                color = OnSurfaceDark.copy(0.3f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    // ── Edit name / phone ───────────────────────────────────────────────
    if (showEdit) {
        var name by remember { mutableStateOf(currentUser?.name.orEmpty()) }
        var phone by remember { mutableStateOf(currentUser?.phone.orEmpty()) }
        var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!saving) showEdit = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && name.isNotBlank() && phone.isNotBlank(),
                    onClick = {
                        saving = true
                        authViewModel.updateBasics(name, phone) { error ->
                            saving = false
                            if (error == null) showEdit = false
                            Toast.makeText(context, error ?: "Profile updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text(if (saving) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { showEdit = false }) { Text("Cancel") } }
        )
    }

    // ── Notification alerts ─────────────────────────────────────────────
    if (showAlerts) {
        var on by remember { mutableStateOf(AppSettings.alertsEnabled(context)) }
        AlertDialog(
            onDismissRequest = { showAlerts = false },
            title = { Text("Notification alerts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Status-bar alerts", fontWeight = FontWeight.Medium)
                            Text("Show a pop-up when a trade is accepted, paid, disputed or resolved.", fontSize = 12.sp)
                        }
                        Switch(checked = on, onCheckedChange = { on = it; AppSettings.setAlertsEnabled(context, it) })
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }) { Text("Open phone notification settings") }
                }
            },
            confirmButton = { TextButton(onClick = { showAlerts = false }) { Text("Done") } }
        )
    }

    // ── Appearance ──────────────────────────────────────────────────────
    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    ThemeMode.values().forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ThemeController.setMode(context.applicationContext, m) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ThemeController.mode == m,
                                onClick = { ThemeController.setMode(context.applicationContext, m) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(m.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Done") } }
        )
    }

    // ── Change password ─────────────────────────────────────────────────
    if (showPasswordConfirm) {
        AlertDialog(
            onDismissRequest = { showPasswordConfirm = false },
            title = { Text("Change password") },
            text = { Text("We'll email a link to ${currentUser?.email ?: "your email"} so you can set a new password.") },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordConfirm = false
                    authViewModel.sendPasswordReset { error ->
                        Toast.makeText(context, error ?: "Reset link sent. Check your inbox.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Send link") }
            },
            dismissButton = { TextButton(onClick = { showPasswordConfirm = false }) { Text("Cancel") } }
        )
    }
}

private data class VerifyCard(val tint: Color, val title: String, val body: String, val button: String)

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