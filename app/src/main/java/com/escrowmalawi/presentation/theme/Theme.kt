package com.escrowmalawi.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.escrowmalawi.data.local.AppSettings

// ── Theme mode (saved in Profile > Appearance) ───────────────────────────────
enum class ThemeMode(val label: String) {
    SYSTEM("Follow phone setting"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * Holds the chosen mode as Compose state. The colour names below (BackgroundDark,
 * SurfaceDark, ...) read it, so every screen that uses them switches automatically
 * when the mode changes; no screen needs to be edited.
 */
object ThemeController {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var systemDark by mutableStateOf(true)
        private set

    val isDark: Boolean
        get() = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        }

    /** Call once in MainActivity.onCreate, before setContent. */
    fun init(context: Context) {
        mode = runCatching { ThemeMode.valueOf(AppSettings.themeMode(context)) }.getOrDefault(ThemeMode.SYSTEM)
        systemDark = isSystemDark(context)
    }

    fun setMode(context: Context, newMode: ThemeMode) {
        mode = newMode
        AppSettings.setThemeMode(context, newMode.name)
    }

    private fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

// ── Brand colors (same in both themes) ───────────────────────────────────────
val EscrowGreen     = Color(0xFF00897B)   // Primary teal-green (trust, money)
val EscrowGreenDark = Color(0xFF00695C)
val EscrowRed       = Color(0xFFE53935)   // Error / refund

// ── Colors that change with the theme (names kept so existing screens work) ──
/** Section titles and small green text: lighter on dark, deeper on light so it stays readable. */
val EscrowGreenLight: Color get() = if (ThemeController.isDark) Color(0xFF4DB6AC) else Color(0xFF00796B)
/** Pending / warning: bright amber on dark, a darker amber on light (plain amber is too faint on white). */
val EscrowAmber: Color get() = if (ThemeController.isDark) Color(0xFFFFB300) else Color(0xFFB45309)
val BackgroundDark: Color get() = if (ThemeController.isDark) Color(0xFF0D1117) else Color(0xFFF1F4F8)
val SurfaceDark: Color get() = if (ThemeController.isDark) Color(0xFF161B22) else Color(0xFFFFFFFF)
val OnSurfaceDark: Color get() = if (ThemeController.isDark) Color(0xFFE6EDF3) else Color(0xFF101828)

// ── Material3 color schemes ──────────────────────────────────────────────────
private val DarkScheme = darkColorScheme(
    primary          = EscrowGreen,
    onPrimary        = Color.White,
    primaryContainer = EscrowGreenDark,
    secondary        = Color(0xFFFFB300),
    onSecondary      = Color.Black,
    error            = EscrowRed,
    background       = Color(0xFF0D1117),
    surface          = Color(0xFF161B22),
    onBackground     = Color(0xFFE6EDF3),
    onSurface        = Color(0xFFE6EDF3),
    outline          = Color(0xFF30363D)
)

// Every surface role is set explicitly: the default light roles are purple-tinted.
private val LightScheme = lightColorScheme(
    primary                 = EscrowGreen,
    onPrimary               = Color.White,
    primaryContainer        = Color(0xFFB2DFDB),
    onPrimaryContainer      = Color(0xFF00352F),
    secondary               = Color(0xFFB45309),
    onSecondary             = Color.White,
    secondaryContainer      = Color(0xFFD7EFEB),
    onSecondaryContainer    = Color(0xFF00352F),
    error                   = EscrowRed,
    background              = Color(0xFFF1F4F8),
    onBackground            = Color(0xFF101828),
    surface                 = Color.White,
    onSurface               = Color(0xFF101828),
    surfaceVariant          = Color(0xFFE6EAF0),
    onSurfaceVariant        = Color(0xFF475467),
    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = Color(0xFFF7F9FB),
    surfaceContainer        = Color(0xFFFFFFFF),
    surfaceContainerHigh    = Color(0xFFF1F4F8),
    surfaceContainerHighest = Color(0xFFE9EDF2),
    outline                 = Color(0xFFD0D5DD),
    outlineVariant          = Color(0xFFE4E7EC)
)

@Composable
fun EscrowMalawiTheme(content: @Composable () -> Unit) {
    val dark = ThemeController.isDark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx = view.context
            while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
            (ctx as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
