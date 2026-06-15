package com.escrowmalawi.presentation.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.data.model.Notification
import com.escrowmalawi.data.model.NotificationType
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.NotificationViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToTradeDetail: (String) -> Unit,
    onNavigateToEscrow: (String) -> Unit,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = { viewModel.deleteAllNotifications() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear All", tint = OnSurfaceDark)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnSurfaceDark,
                    navigationIconContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (notifications.isEmpty()) {
            EmptyNotificationsView(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(notifications, key = { _, n -> n.id }) { _, notification ->
                    val swipeToDismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteNotification(notification.id)
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = swipeToDismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = when (swipeToDismissState.dismissDirection) {
                                SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                        NotificationItem(
                            notification = notification,
                            onMarkAsRead = { viewModel.markAsRead(notification.id) },
                            onDelete = { viewModel.deleteNotification(notification.id) },
                            onClick = {
                                viewModel.markAsRead(notification.id)
                                notification.tradeId?.let { tradeId ->
                                    when (notification.type) {
                                        NotificationType.FUNDS_LOCKED.name -> onNavigateToEscrow(tradeId)
                                        else -> onNavigateToTradeDetail(tradeId)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor = if (notification.isRead) SurfaceDark else SurfaceDark.copy(alpha = 0.7f)
    val indicatorColor = when (notification.type) {
        NotificationType.TRADE_REQUEST.name -> EscrowAmber
        NotificationType.TRADE_ACCEPTED.name -> EscrowGreen
        NotificationType.TRADE_COMPLETED.name -> EscrowGreen
        NotificationType.TRADE_REJECTED.name -> Color.Red
        NotificationType.FUNDS_LOCKED.name -> Color(0xFF7C83FD)
        else -> EscrowGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unread indicator and Type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                }
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = indicatorColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        notification.title,
                        fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                        color = OnSurfaceDark,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatTimestamp(notification.createdAt),
                        fontSize = 10.sp,
                        color = OnSurfaceDark.copy(0.4f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    notification.message,
                    fontSize = 13.sp,
                    color = OnSurfaceDark.copy(0.6f),
                    lineHeight = 18.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Delete",
                    tint = OnSurfaceDark.copy(0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyNotificationsView(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.NotificationsNone,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = OnSurfaceDark.copy(0.1f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No notifications yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceDark.copy(0.6f)
        )
        Text(
            "We'll notify you about your trades here.",
            fontSize = 14.sp,
            color = OnSurfaceDark.copy(0.4f)
        )
    }
}

fun formatTimestamp(timestamp: Timestamp): String {
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}
