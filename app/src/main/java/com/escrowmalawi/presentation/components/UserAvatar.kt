package com.escrowmalawi.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.escrowmalawi.presentation.theme.EscrowGreen
import com.escrowmalawi.presentation.theme.EscrowGreenDark

/**
 * One avatar for the whole app: the photo if there is one, else the first letter of the name,
 * else a person icon. It never shows a "?".
 */
@Composable
fun UserAvatar(
    name: String?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fontSize: TextUnit = 18.sp
) {
    val initial = name?.trim()?.firstOrNull()?.uppercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(EscrowGreen, EscrowGreenDark))),
        contentAlignment = Alignment.Center
    ) {
        when {
            !photoUrl.isNullOrBlank() -> AsyncImage(
                model = photoUrl,
                contentDescription = name ?: "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            initial != null -> Text(initial, color = Color.White, fontWeight = FontWeight.Black, fontSize = fontSize)
            else -> Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}
