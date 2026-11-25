package com.iadv.dukaanlocker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.R

// ── Font family ───────────────────────────────────────────────────────────────
// Noto Sans, bundled in res/font (regular / medium / semibold / bold).
// Chosen as the app typeface because it matches the Government of India UX4G /
// DBIM standard: high legibility at small sizes, neutral tone, and a "no tofu"
// design so unsupported glyphs degrade to the system Noto families instead of
// blank boxes. Same family DigiLocker's design system specifies.
//
// NOTE: these files cover Latin/Greek/Cyrillic. Hindi, Tamil, Bengali, Gujarati,
// Kannada, Malayalam, Marathi, Odia, Punjabi and Telugu currently render through
// the system's Noto Indic families. Bundling NotoSansDevanagari & co. is a
// separate follow-up.
val NotoSans = FontFamily(
    Font(R.font.noto_sans_regular, FontWeight.Normal),
    Font(R.font.noto_sans_medium, FontWeight.Medium),
    Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_bold, FontWeight.Bold)
)

// ── Type scale ────────────────────────────────────────────────────────────────
// Six steps only: 12 · 14 · 16 · 20 · 24 · 32 sp.
// 12sp is the hard floor — nothing in the app should go below it (Material's
// default labelSmall is 11sp; it is raised here). Prefer these constants over
// new ad-hoc sp values so the scale cannot drift again.
object TypeScale {
    /** Captions, badges, dense metadata. 12sp — the floor. */
    val Caption = 12.sp

    /** Buttons, field labels, secondary text. */
    val Label = 14.sp

    /** Body copy, list rows, input text. */
    val Body = 16.sp

    /** Card and section titles, screen headers. */
    val Title = 20.sp

    /** Major section headings. */
    val Heading = 24.sp

    /** Hero text, splash, empty states. */
    val Display = 32.sp
}

// Line height ≈ 1.3× size; large display steps tighten slightly (−0.25 to −0.5sp).
private fun step(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
    letterSpacing: TextUnit = 0.sp
) = TextStyle(
    fontFamily = NotoSans,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing
)

val Typography = Typography(
    // ── Display ──────────────────────────────────────────────────────────────
    displayLarge = step(TypeScale.Display, 40.sp, FontWeight.Bold, (-0.5).sp),
    displayMedium = step(TypeScale.Display, 40.sp, FontWeight.SemiBold, (-0.25).sp),
    displaySmall = step(TypeScale.Heading, 32.sp, FontWeight.Bold, (-0.25).sp),

    // ── Headlines ────────────────────────────────────────────────────────────
    headlineLarge = step(TypeScale.Heading, 32.sp, FontWeight.SemiBold),
    headlineMedium = step(TypeScale.Heading, 32.sp, FontWeight.Medium),
    headlineSmall = step(TypeScale.Title, 28.sp, FontWeight.SemiBold),

    // ── Titles ───────────────────────────────────────────────────────────────
    titleLarge = step(TypeScale.Title, 28.sp, FontWeight.Medium),
    titleMedium = step(TypeScale.Body, 24.sp, FontWeight.SemiBold, 0.15.sp),
    titleSmall = step(TypeScale.Label, 20.sp, FontWeight.SemiBold, 0.1.sp),

    // ── Body ─────────────────────────────────────────────────────────────────
    bodyLarge = step(TypeScale.Body, 24.sp, FontWeight.Normal, 0.5.sp),
    bodyMedium = step(TypeScale.Label, 20.sp, FontWeight.Normal, 0.25.sp),
    bodySmall = step(TypeScale.Caption, 16.sp, FontWeight.Normal, 0.4.sp),

    // ── Labels ───────────────────────────────────────────────────────────────
    labelLarge = step(TypeScale.Label, 20.sp, FontWeight.Medium, 0.5.sp),
    labelMedium = step(TypeScale.Caption, 16.sp, FontWeight.Medium, 0.5.sp),
    labelSmall = step(TypeScale.Caption, 16.sp, FontWeight.Medium, 0.4.sp)
)
