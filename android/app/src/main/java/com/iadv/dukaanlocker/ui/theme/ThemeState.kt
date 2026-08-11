package com.iadv.dukaanlocker.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Theme-aware color container ──
// Provides correct colors for dark/light modes so screens don't need
// to hardcode color values.

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
)

val DarkAppColors = AppColors(
    background = Color(0xFF1A1A1A),
    cardBg = Color(0xFF2D2D2D),
    primary = Color(0xFF2563EB),
    accent = Color(0xFF06B6D4),
    textSecondary = Color(0xFF94A3B8),
    border = Color(0xFF404040),
    secondary = Color(0xFF0F766E),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    textPrimary = Color.White,
    textOnPrimary = Color.White,
)

val LightAppColors = AppColors(
    background = Color(0xFFF8FAFC),
    cardBg = Color.White,
    primary = Color(0xFF2563EB),
    accent = Color(0xFF06B6D4),
    textSecondary = Color(0xFF64748B),
    border = Color(0xFFCBD5E1),
    secondary = Color(0xFF0F766E),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    textPrimary = Color(0xFF1E293B),
    textOnPrimary = Color.White,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
