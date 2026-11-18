package com.escrowmalawi.presentation.screens.marketplace

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escrowmalawi.presentation.theme.*

/** Compact Follow / Following button used on the listing page and the seller catalog. */
@Composable
fun FollowButton(isFollowing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val padding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    if (isFollowing) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(34.dp),
            contentPadding = padding
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = EscrowGreen)
            Spacer(Modifier.width(4.dp))
            Text("Following", fontSize = 12.sp, color = EscrowGreen)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(34.dp),
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Follow", fontSize = 12.sp)
        }
    }
}
