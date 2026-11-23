package com.iadv.dukaanlocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DukaanDarkColorScheme = darkColorScheme(
    primary = BrandDark,
    onPrimary = TextOnBrand,
    primaryContainer = BrandDark.copy(alpha = 0.15f),
    onPrimaryContainer = BrandDark,
    secondary = BrandDark,
    onSecondary = TextOnBrand,
    secondaryContainer = BrandDark.copy(alpha = 0.15f),
    onSecondaryContainer = BrandDark,
    tertiary = SuccessColor,
    onTertiary = TextOnBrand,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark.copy(alpha = 0.8f),
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark.copy(alpha = 0.5f),
    error = ErrorColor,
    onError = TextOnBrand
)

private val DukaanLightColorScheme = lightColorScheme(
    primary = BrandDark,
    onPrimary = TextOnBrand,
    primaryContainer = BrandDark.copy(alpha = 0.1f),
    onPrimaryContainer = BrandDark,
    secondary = BrandDark,
    onSecondary = TextOnBrand,
    secondaryContainer = BrandDark.copy(alpha = 0.1f),
    onSecondaryContainer = BrandDark,
    tertiary = SuccessColor,
    onTertiary = TextOnBrand,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = BorderLight.copy(alpha = 0.5f),
    error = ErrorColor,
    onError = TextOnBrand
)

@Composable
fun DukaanLockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DukaanDarkColorScheme else DukaanLightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
