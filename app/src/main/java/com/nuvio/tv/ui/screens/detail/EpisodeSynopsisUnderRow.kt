package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import kotlinx.coroutines.delay
import java.util.Locale

internal const val EPISODE_SYNOPSIS_SETTLE_MS = 120L
internal const val EPISODE_SYNOPSIS_LINES = 3

internal data class EpisodeSynopsisLine(
    val episodeNumber: Int?,
    val title: String,
    val synopsis: String?
)

internal fun rowHasEpisodeSynopsis(episodes: List<Video>): Boolean =
    episodes.any { !it.overview.isNullOrBlank() }

internal fun showEpisodeSynopsisUnderRow(settingOn: Boolean, compactLook: Boolean, rowHasSynopsis: Boolean): Boolean =
    settingOn && compactLook && rowHasSynopsis

internal fun episodeSynopsisLine(episodes: List<Video>, episodeId: String?): EpisodeSynopsisLine? {
    val episode = episodes.firstOrNull { it.id == episodeId } ?: return null
    return EpisodeSynopsisLine(
        episodeNumber = episode.episode,
        title = episode.title,
        synopsis = episode.overview?.trim()?.takeIf { it.isNotEmpty() }
    )
}

/** The episode that will hold focus first: the restore target, then the one remembered for the season, then the scroll target. */
internal fun restingEpisodeSynopsisId(
    episodes: List<Video>,
    anchorEpisodeId: String?,
    restoreEpisodeId: String?,
    scrollToEpisodeId: String?
): String? {
    val ids = episodes.mapTo(HashSet()) { it.id }
    return listOf(restoreEpisodeId, anchorEpisodeId, scrollToEpisodeId).firstOrNull { it != null && it in ids }
        ?: episodes.firstOrNull()?.id
}

/** Left and Right never scroll the page; a vertical move shows the card and, when the block is there, the block too. */
internal fun episodeRowBringIntoViewRect(localRect: Rect, stayVertical: Boolean, rowBottom: Float, blockShown: Boolean): Rect =
    when {
        stayVertical -> Rect(localRect.left, 0f, localRect.right, 0f)
        blockShown -> Rect(localRect.left, localRect.top, localRect.right, maxOf(localRect.bottom, rowBottom))
        else -> localRect
    }

/** The first focus shows at once; moving along the row waits a little so a fast scroll does not flicker. */
internal fun episodeSynopsisSettleMs(shownEpisodeId: String?, nextEpisodeId: String?): Long = when {
    nextEpisodeId == null || shownEpisodeId == null -> 0L
    shownEpisodeId == nextEpisodeId -> 0L
    else -> EPISODE_SYNOPSIS_SETTLE_MS
}

@Composable
internal fun EpisodeSynopsisUnderRow(
    episodes: List<Video>,
    focusedEpisodeId: State<String?>,
    restingEpisodeId: String?,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var shownEpisodeId by remember { mutableStateOf<String?>(null) }
    val focused = focusedEpisodeId.value
    LaunchedEffect(focused) {
        val next = focused ?: return@LaunchedEffect
        val wait = episodeSynopsisSettleMs(shownEpisodeId, next)
        if (wait > 0) delay(wait)
        shownEpisodeId = next
    }
    val line = remember(episodes, shownEpisodeId, restingEpisodeId) {
        episodeSynopsisLine(episodes, shownEpisodeId) ?: episodeSynopsisLine(episodes, restingEpisodeId)
    }
    DisposableEffect(onHeightChanged) {
        onDispose { onHeightChanged(0) }
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val labelStyle = remember(typography) { typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp) }
    val titleStyle = remember(typography) { typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp) }
    val bodyStyle = typography.bodyMedium
    val titleLineHeight = with(density) { titleStyle.lineHeight.takeIf { it.isSp }?.toDp() ?: 24.dp }
    val bodyLineHeight = with(density) { bodyStyle.lineHeight.takeIf { it.isSp }?.toDp() ?: 20.dp }
    val blockHeight = 12.dp + (titleLineHeight + 8.dp) + 7.dp + bodyLineHeight * EPISODE_SYNOPSIS_LINES + 12.dp
    val strEpisode = stringResource(R.string.episodes_episode)
    val textSecondary = NuvioTheme.colors.TextSecondary
    val accent = Color(0xFFE6BC68)
    val panelShape = RoundedCornerShape(NuvioTheme.radii.lg)
    val panelBrush = remember {
        Brush.horizontalGradient(listOf(Color(0x62211E24), Color(0x0E211E24)))
    }

    Column(
        modifier = modifier
            .fillMaxWidth(0.78f)
            .onSizeChanged { onHeightChanged(it.height) }
            .padding(start = detailStartInset, top = 5.dp, bottom = 3.dp)
            .height(blockHeight)
            .clip(panelShape)
            .background(panelBrush)
            .padding(start = 16.dp, end = 20.dp, top = 9.dp, bottom = 8.dp)
            .clipToBounds()
    ) {
        if (line == null) return@Column
        val episodeCode = remember(line.episodeNumber, strEpisode) {
            val prefix = strEpisode.uppercase(Locale.getDefault())
            line.episodeNumber?.let { number -> "$prefix $number" } ?: prefix
        }
        Row(
            modifier = Modifier.height(titleLineHeight + 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = episodeCode,
                style = labelStyle,
                color = accent,
                maxLines = 1,
                modifier = Modifier
                    .background(accent.copy(alpha = 0.13f), RoundedCornerShape(NuvioTheme.radii.sm))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = line.title.localizeEpisodeTitle(context),
                style = titleStyle,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        val synopsis = line.synopsis
        if (synopsis != null) {
            Text(
                text = synopsis,
                style = bodyStyle.copy(textDirection = synopsis.contentTextDirection()),
                color = textSecondary,
                maxLines = EPISODE_SYNOPSIS_LINES,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = stringResource(R.string.detail_episode_synopsis_none),
                style = bodyStyle,
                color = textSecondary.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}
