package com.iadv.dukaanlocker.ui.theme

import androidx.compose.ui.graphics.Color

// ── DukaanLocker Brand Palette (per UI/UX brief: 60/30/10) ────────────────
// Light:  bg #F5F7FB (60%), brand #173B70 (30%), action #3978ED (10%)
// Dark:   derived slate variants, same brand & action hues
// ────────────────────────────────────────────────────────────────────────────

// ── Brand (30%) ──
val BrandDark = Color(0xFF173B70)   // deep navy — brand identity, structural
val BrandAction = Color(0xFF3978ED) // bright blue — primary actions, selected

// ── Background (60%) ──
val BgLight = Color(0xFFF5F7FB)     // primary background / neutral surfaces
val BgDark = Color(0xFF0F172A)      // dark mode background

// ── Surface ──
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1E293B)

// ── Text ──
val TextPrimaryLight = Color(0xFF172033)   // headings, labels
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryLight = Color(0xFF7B8798) // captions, hints
val TextSecondaryDark = Color(0xFF94A3B8)
val TextOnBrand = Color(0xFFFFFFFF)

// ── Borders ──
val BorderLight = Color(0xFFDCE3EC)
val BorderDark = Color(0xFF334155)
val BorderFocused = BrandAction

// ── Semantic ──
val SuccessColor = Color(0xFF168A78)   // green
val WarningColor = Color(0xFFD69B35)   // amber
val ErrorColor = Color(0xFFEF4444)     // red

// ── Deprecated legacy aliases (kept for backward compat during transition) ──
@Deprecated("Use BrandDark / BrandAction / BgLight instead", level = DeprecationLevel.HIDDEN)
val PrimaryBlue = BrandDark

@Deprecated("Use BrandAction instead", level = DeprecationLevel.HIDDEN)
val AccentCyan = BrandAction

@Deprecated("Use BgDark instead", level = DeprecationLevel.HIDDEN)
val DarkBg = BgDark

@Deprecated("Use SurfaceDark instead", level = DeprecationLevel.HIDDEN)
val CardBg = SurfaceDark

@Deprecated("Use TextSecondaryDark instead", level = DeprecationLevel.HIDDEN)
val GrayText = TextSecondaryDark

@Deprecated("Use BorderDark instead", level = DeprecationLevel.HIDDEN)
val BorderColor = BorderDark

@Deprecated("Use BrandDark instead", level = DeprecationLevel.HIDDEN)
val SecondaryTeal = BrandDark

@Deprecated("Use SuccessColor instead", level = DeprecationLevel.HIDDEN)
val EmeraldColor = SuccessColor

@Deprecated("Use WarningColor instead", level = DeprecationLevel.HIDDEN)
val WarningColorLegacy = WarningColor
