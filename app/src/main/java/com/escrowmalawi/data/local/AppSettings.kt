package com.escrowmalawi.data.local

import android.content.Context

/** Small on-device preferences. */
object AppSettings {
    private const val PREFS = "escrow_settings"
    private const val KEY_ALERTS = "alerts_enabled"
    private const val KEY_THEME = "theme_mode"

    /** Status-bar alerts for new trade updates (default on). */
    fun alertsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ALERTS, true)

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALERTS, enabled).apply()
    }

    /** "SYSTEM", "LIGHT" or "DARK" (default SYSTEM). */
    fun themeMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, mode).apply()
    }
}
