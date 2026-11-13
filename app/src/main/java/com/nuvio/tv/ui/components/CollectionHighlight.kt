package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.domain.model.CollectionHighlight
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.ThemeColors

internal val CollectionHighlightVerticalPadding = 10.dp

@Composable
internal fun Modifier.collectionHighlight(
    highlight: CollectionHighlight?,
    rgb: Int?,
    firstCardEnd: Dp
): Modifier {
    if (highlight == null) return this
    val color = rgb?.let { Color(it or 0xFF000000.toInt()) }
        ?: ThemeColors.getColorPalette(AppTheme.GLASS).secondary.copy(alpha = 1f)
    val radius = NuvioTheme.radii.md
    if (highlight == CollectionHighlight.SOLID) return background(color, RoundedCornerShape(radius))
    val end = lerp(color, NuvioTheme.colors.Background.copy(alpha = 1f), .78f)
    return drawWithCache {
        val plateauEnd = firstCardEnd.toPx().coerceIn(0f, size.width)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val gradient = if (plateauEnd < size.width) {
            Brush.horizontalGradient(
                colors = listOf(color, end),
                startX = if (rtl) size.width - plateauEnd else plateauEnd,
                endX = if (rtl) 0f else size.width
            )
        } else null
        val corners = CornerRadius(radius.toPx())
        onDrawBehind {
            if (gradient == null) drawRoundRect(color = color, cornerRadius = corners)
            else drawRoundRect(brush = gradient, cornerRadius = corners)
        }
    }
}
