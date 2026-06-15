package com.escrowmalawi.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand Colors ─────────────────────────────────────────────────────────────
val EscrowGreen      = Color(0xFF00897B)   // Primary teal-green (trust, money)
val EscrowGreenDark  = Color(0xFF00695C)
val EscrowGreenLight = Color(0xFF4DB6AC)
val EscrowAmber      = Color(0xFFFFB300)   // Accent (pending/warning states)
val EscrowRed        = Color(0xFFE53935)   // Error / refund
val BackgroundDark   = Color(0xFF0D1117)
val SurfaceDark      = Color(0xFF161B22)
val OnSurfaceDark    = Color(0xFFE6EDF3)

// ── Material3 Color Scheme ───────────────────────────────────────────────────
private val EscrowColorScheme = darkColorScheme(
    primary          = EscrowGreen,
    onPrimary        = Color.White,
    primaryContainer = EscrowGreenDark,
    secondary        = EscrowAmber,
    onSecondary      = Color.Black,
    error            = EscrowRed,
    background       = BackgroundDark,
    surface          = SurfaceDark,
    onBackground     = OnSurfaceDark,
    onSurface        = OnSurfaceDark,
    outline          = Color(0xFF30363D)
)

@Composable
fun EscrowMalawiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EscrowColorScheme,
        content = content
    )
}

