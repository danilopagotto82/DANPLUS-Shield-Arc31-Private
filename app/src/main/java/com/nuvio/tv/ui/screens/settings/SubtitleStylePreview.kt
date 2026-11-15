package com.nuvio.tv.ui.screens.settings

import android.text.Spannable
import android.text.SpannableString
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.nuvio.tv.R
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.ui.screens.player.exoSubtitleEdges
import com.nuvio.tv.ui.screens.player.subtitleEdgeSpan
import com.nuvio.tv.ui.screens.player.subtitleTypeface
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(UnstableApi::class)
@Composable
internal fun SubtitleStylePreview(style: SubtitleStyleSettings) {
    val sample = stringResource(R.string.subtitle_style_preview_sample)
    val colors = NuvioTheme.colors
    AndroidView(
        factory = { SubtitleView(it) },
        modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(colors.Background, colors.TextSecondary))),
        update = { view ->
            val edges = exoSubtitleEdges(style)
            view.setStyle(CaptionStyleCompat(
                style.textColor, edges.background, android.graphics.Color.TRANSPARENT,
                edges.edgeType, edges.edgeColor, subtitleTypeface(view.context, style.font, style.bold, style.importedFontId)
            ))
            view.setFractionalTextSize(.20f * style.size.coerceIn(50, 200) / 100f)
            val text = SpannableString(sample)
            subtitleEdgeSpan(style)?.let { text.setSpan(it, 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            view.setCues(listOf(Cue.Builder()
                .setText(text)
                .setLine((.62f - style.verticalOffset / 100f).coerceIn(.2f, .8f), Cue.LINE_TYPE_FRACTION)
                .setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                .build()))
        }
    )
}
