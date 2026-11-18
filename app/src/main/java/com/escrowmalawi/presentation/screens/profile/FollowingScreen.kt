package com.escrowmalawi.presentation.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.presentation.components.UserAvatar
import com.escrowmalawi.presentation.screens.marketplace.FollowButton
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.FollowViewModel

/** Sellers the user follows. Tap one to open their catalog; the button unfollows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    onBack: () -> Unit,
    onSellerClick: (String) -> Unit,
    viewModel: FollowViewModel = hiltViewModel()
) {
    val follows by viewModel.follows.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sellers you follow", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (follows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PersonSearch, null, tint = OnSurfaceDark.copy(0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("You're not following anyone yet", fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Open a seller's profile and tap Follow. Their products show up first on your home screen, and you get an alert when they list or restock.",
                    fontSize = 13.sp, color = OnSurfaceDark.copy(0.6f), lineHeight = 19.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = EscrowGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "You get an alert when these sellers list something new or restock.",
                            fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f)
                        )
                    }
                }
                items(follows, key = { it.id }) { f ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSellerClick(f.sellerUid) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(name = f.sellerName, photoUrl = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.sellerName.trim().ifBlank { "Seller" }, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
                                Text("View catalog", fontSize = 12.sp, color = EscrowGreen)
                            }
                            FollowButton(isFollowing = true, onClick = { viewModel.unfollow(f.sellerUid) })
                        }
                    }
                }
            }
        }
    }
}
