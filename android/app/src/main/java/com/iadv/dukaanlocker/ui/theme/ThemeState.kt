package com.iadv.dukaanlocker.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Theme-aware color container ──
// Provides correct colors for dark/light modes so screens don't need
// to hardcode color values.
// ──────────────────────────────────────────────────────────────────────────

data class AppColors(
    val background: Color,
    val cardBg: Color,
    val primary: Color,
    val accent: Color,
    val textSecondary: Color,
    val border: Color,
    val secondary: Color,
    val success: Color,
    val warning: Color,
    val textPrimary: Color,
    val textOnPrimary: Color,
    val error: Color,
)

// Dark mode interactive elements use the bright action blue: BrandDark (#173B70)
// on BgDark (#0F172A) is only ~1.7:1, which makes cursors, focused borders and
// primary button labels invisible. BrandAction gives ~4.3:1 on the same surface.
val DarkAppColors = AppColors(
    background = BgDark,
    cardBg = SurfaceDark,
    primary = BrandAction,
    accent = BrandAction,
    textSecondary = TextSecondaryDark,
    border = BorderDark,
    secondary = BrandAction,
    success = SuccessColor,
    warning = WarningColor,
    textPrimary = TextPrimaryDark,
    textOnPrimary = TextOnBrand,
    error = ErrorColor,
)

val LightAppColors = AppColors(
    background = BgLight,
    cardBg = SurfaceLight,
    primary = BrandDark,
    accent = BrandAction,
    textSecondary = TextSecondaryLight,
    border = BorderLight,
    secondary = BrandDark,
    success = SuccessColor,
    warning = WarningColor,
    textPrimary = TextPrimaryLight,
    textOnPrimary = TextOnBrand,
    error = ErrorColor,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

// Whether the app is currently in dark mode. Screens rarely need this, but a
// few depth cues (app-bar separation) only make sense in dark mode.
val LocalIsDarkTheme = staticCompositionLocalOf { true }
