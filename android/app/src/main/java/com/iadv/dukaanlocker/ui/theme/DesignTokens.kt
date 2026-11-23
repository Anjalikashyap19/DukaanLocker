package com.iadv.dukaanlocker.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── DukaanLocker Design Tokens ────────────────────────────────────────────
// Single source of truth for spacing, radius, elevation and layout constraints.
// Screens must read from here instead of hardcoding values.
// ──────────────────────────────────────────────────────────────────────────

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val circle = 1000.dp // effectively circular
}

object Elevation {
    val card = 0.dp
    val raised = 1.dp
    val sheet = 4.dp
    val dialog = 8.dp
}

object Layout {
    // Max width for centered login/register content cards
    val maxContentWidth = 520.dp
    // Bottom nav bar width fraction
    val bottomNavWidth = 0.94f
    // Standard input height
    val inputHeight = 52.dp
    val buttonHeight = 50.dp
    val compactButtonHeight = 46.dp
    // Minimum touch target (a11y)
    val touchTarget = 48.dp
    // Page horizontal padding
    var pagePadding = 20.dp
    // Compact header padding
    val headerPadding = 16.dp
}

object Type {
    // Display / hero headings (Serif)
    val displayLarge = 32.sp
    val displayMedium = 28.sp
    val displaySmall = 24.sp
    // Screen headings
    val headingLarge = 22.sp
    val headingMedium = 20.sp
    val headingSmall = 18.sp
    // Titles
    val titleLarge = 18.sp
    val titleMedium = 16.sp
    val titleSmall = 14.sp
    // Body
    val bodyLarge = 16.sp
    val bodyMedium = 14.sp
    val bodySmall = 12.sp
    // Labels / captions
    val labelLarge = 14.sp
    val labelMedium = 12.sp
    val labelSmall = 11.sp
    // Helper / footnote
    val caption = 10.sp
}
