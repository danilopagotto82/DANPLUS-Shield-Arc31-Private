@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.image.sizedHomeArtworkUrl
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.scout.ScoutChip
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import java.text.NumberFormat

@Composable
internal fun FolderSidePreview(
    item: MetaPreview?,
    credits: FolderPreviewCredits?,
    modifier: Modifier = Modifier
) {
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(NuvioTheme.radii.lg)
    val wash = remember(colors.Secondary) {
        Brush.verticalGradient(listOf(colors.Secondary.copy(alpha = .14f), Color.Transparent))
    }
    Column(
        modifier.background(colors.BackgroundCard, shape)
            .background(wash, shape)
            .border(1.dp, colors.Secondary.copy(alpha = .26f), shape)
            .padding(NuvioTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        if (item != null) {
            FolderTitleArtwork(item, logoHeight = 64.dp, centered = true)
            FolderPreviewDivider()
            FolderMetadata(item, forGrade = true)
            FolderPreviewDivider()
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.TextSecondary, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                FolderPreviewDivider()
            }
            val director = credits?.director ?: item.director.firstOrNull { it.isNotBlank() }
            val cast = credits?.cast.orEmpty()
            if (director != null || cast.isNotEmpty()) {
                Text(stringResource(R.string.dan_folder_credits), style = MaterialTheme.typography.titleSmall)
                director?.let {
                    Text(stringResource(R.string.dan_folder_director, it), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cast.isNotEmpty()) {
                    Text(cast.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FolderPreviewDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NuvioTheme.colors.TextSecondary.copy(alpha = .22f)))
}

@Composable
internal fun FolderShowcase(
    item: MetaPreview,
    addon: String,
    onWatch: (MetaPreview, String) -> Unit,
    onOpen: (MetaPreview, String) -> Unit
) {
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(NuvioTheme.radii.xl)
    val scrim = remember(colors.Background) {
        Brush.horizontalGradient(
            listOf(colors.Background, colors.Background.copy(alpha = .84f), colors.Background.copy(alpha = .36f))
        )
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = NuvioTheme.spacing.xxxl)
            .height(270.dp).clip(shape).background(colors.BackgroundCard)
            .border(1.dp, colors.Secondary.copy(alpha = .35f), shape)
    ) {
        val image = item.backdropUrl?.takeIf { it.isNotBlank() }
        AsyncImage(
            model = image?.let { sizedHomeArtworkUrl(it, 780) },
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd
        )
        Box(Modifier.matchParentSize().background(scrim))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            Column(Modifier.fillMaxWidth(.68f), verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                FolderTitleArtwork(item, logoHeight = 64.dp)
                FolderMetadata(item)
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                if (item.apiType == "movie" || item.apiType == "series" || item.apiType == "tv") {
                    ScoutChip(stringResource(R.string.dan_folder_watch), onClick = { onWatch(item, addon) }, primary = true)
                }
                ScoutChip(stringResource(R.string.tmdb_details_title), onClick = { onOpen(item, addon) })
            }
        }
    }
}

@Composable
private fun FolderTitleArtwork(item: MetaPreview, logoHeight: Dp = 58.dp, centered: Boolean = false) {
    val logo = item.logo?.takeIf { it.isNotBlank() }
    var failed by remember(logo) { mutableStateOf(false) }
    if (logo != null && !failed) {
        AsyncImage(
            model = sizedHomeArtworkUrl(logo, 500),
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().height(logoHeight),
            contentScale = ContentScale.Fit,
            alignment = if (centered) Alignment.Center else Alignment.CenterStart,
            onError = { failed = true }
        )
    } else {
        Text(item.name, style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.fillMaxWidth(), textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderMetadata(item: MetaPreview, forGrade: Boolean = false) {
    val rating = item.imdbRating?.takeIf { it.isFinite() && it > 0f }?.let {
        val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
        val number = remember(it, locale) {
            NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = 1
            }.format(it)
        }
        "★ $number"
    }
    val duration = item.runtime?.takeIf { it.isNotBlank() }?.let {
        val minutes = if (forGrade) folderRuntimeMinutes.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull()
            else it.toIntOrNull()
        when {
            minutes == null -> it
            !forGrade || minutes < 60 -> stringResource(R.string.dan_folder_minutes, minutes)
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}"
        }
    }
    val details = listOfNotNull(
        item.releaseInfo?.takeIf { it.isNotBlank() }, duration,
        item.ageRating?.takeIf { it.isNotBlank() }, rating
    ).joinToString(" · ")
    if (details.isNotEmpty()) {
        val style = MaterialTheme.typography.labelLarge
        Text(details, color = NuvioTheme.colors.TextPrimary,
            style = if (forGrade) style.copy(fontSize = (style.fontSize.value + 2).sp) else style,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val context = LocalContext.current
    val genres = item.genres.take(3).map { localizedGenreLabel(context, it) }
        .joinToString(" · ").ifBlank { localizedContentType(item.apiType) }
    if (genres.isNotBlank()) {
        val style = MaterialTheme.typography.labelMedium
        Text(genres, color = NuvioTheme.colors.TextSecondary,
            style = if (forGrade) style.copy(fontSize = (style.fontSize.value + 2).sp) else style,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val folderRuntimeMinutes = Regex("""(\d+)(?:\s*m(?:in)?)?""", RegexOption.IGNORE_CASE)
