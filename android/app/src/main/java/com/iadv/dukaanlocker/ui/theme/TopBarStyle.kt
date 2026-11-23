package com.iadv.dukaanlocker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Separation for top app bars.
 *
 * Light mode already gets a real elevation shadow from the bar's Surface, but a
 * cast shadow is black on a near-black background and simply disappears on the
 * dark theme. There the bar keeps its normal background colour and its bottom
 * edge is defined by the same two cues, drawn inside the bar so no layout or
 * spacing changes: a short, soft falloff that deepens toward the edge and a
 * crisp hairline on the very last pixel.
 */
@Composable
fun Modifier.topBarShadow(
    colors: AppColors = LocalAppColors.current,
    enabled: Boolean = LocalIsDarkTheme.current
): Modifier {
    if (!enabled) return this
    val hairline = colors.border.copy(alpha = 0.85f)
    return drawWithContent {
        drawContent()
        val linePx = 1.dp.toPx()
        val fadePx = 7.dp.toPx()
        val edge = size.height
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to Color.Black.copy(alpha = 0.07f),
                1f to Color.Black.copy(alpha = 0.20f),
                startY = edge - fadePx,
                endY = edge
            ),
            topLeft = Offset(0f, edge - fadePx),
            size = Size(size.width, fadePx)
        )
        drawRect(
            color = hairline,
            topLeft = Offset(0f, edge - linePx),
            size = Size(size.width, linePx)
        )
    }
}
