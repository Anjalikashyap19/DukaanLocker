package com.iadv.dukaanlocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DukaanDarkColorScheme = darkColorScheme(
    primary = GoldColor,
    onPrimary = Color.White,
    primaryContainer = GoldColor.copy(alpha = 0.15f),
    onPrimaryContainer = GoldColor,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = SecondaryBlue.copy(alpha = 0.15f),
    onSecondaryContainer = SecondaryBlue,
    tertiary = EmeraldColor,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = Color.White,
    surface = CardBg,
    onSurface = Color.White,
    surfaceVariant = CardBg.copy(alpha = 0.8f),
    onSurfaceVariant = GrayText,
    outline = BorderColor,
    outlineVariant = BorderColor.copy(alpha = 0.5f),
    error = Color(0xFFEF4444),
    onError = Color.White
)

private val DukaanLightColorScheme = lightColorScheme(
    primary = GoldColor,
    onPrimary = Color.White,
    primaryContainer = GoldColor.copy(alpha = 0.1f),
    onPrimaryContainer = GoldColor,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = SecondaryBlue.copy(alpha = 0.1f),
    onSecondaryContainer = SecondaryBlue,
    tertiary = EmeraldColor,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun DukaanLockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DukaanDarkColorScheme else DukaanLightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
