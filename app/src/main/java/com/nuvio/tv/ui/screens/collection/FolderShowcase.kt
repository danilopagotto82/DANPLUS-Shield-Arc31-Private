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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
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
internal fun FolderSidePreview(item: MetaPreview?, modifier: Modifier = Modifier) {
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
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        if (item != null) {
            FolderTitleArtwork(item)
            FolderMetadata(item)
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun FolderShowcase(
    tabs: List<FolderTab>,
    onWatch: (MetaPreview, String) -> Unit,
    onOpen: (MetaPreview, String) -> Unit
) {
    val slides = remember(tabs) {
        val rows = tabs.filterNot { it.isAllTab }.mapNotNull { it.catalogRow }
        val seen = HashSet<Pair<String, String>>()
        buildList {
            for (index in 0 until 6) {
                for (row in rows) {
                    val item = row.items.getOrNull(index) ?: continue
                    if (seen.add(item.apiType to item.id)) add(item to (item.sourceAddonBaseUrl ?: row.addonBaseUrl))
                    if (size == 6) return@buildList
                }
            }
        }
    }
    if (slides.isEmpty()) return

    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = index.coerceIn(slides.indices)
    val (item, addon) = slides[current]
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
                FolderTitleArtwork(item)
                FolderMetadata(item)
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                    if (item.apiType == "movie" || item.apiType == "series" || item.apiType == "tv") {
                        ScoutChip(stringResource(R.string.dan_folder_watch), onClick = { onWatch(item, addon) }, primary = true)
                    }
                    ScoutChip(stringResource(R.string.tmdb_details_title), onClick = { onOpen(item, addon) })
                }
                if (slides.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScoutChip(
                            label = "",
                            onClick = { index = (current + slides.size - 1) % slides.size },
                            icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.dan_folder_previous), Modifier.size(20.dp)) }
                        )
                        Text(stringResource(R.string.dan_folder_slide, current + 1, slides.size))
                        ScoutChip(
                            label = "",
                            onClick = { index = (current + 1) % slides.size },
                            icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.dan_folder_next), Modifier.size(20.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitleArtwork(item: MetaPreview) {
    val logo = item.logo?.takeIf { it.isNotBlank() }
    var failed by remember(logo) { mutableStateOf(false) }
    if (logo != null && !failed) {
        AsyncImage(
            model = sizedHomeArtworkUrl(logo, 500),
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true }
        )
    } else {
        Text(item.name, style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderMetadata(item: MetaPreview) {
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
        it.toIntOrNull()?.let { minutes -> stringResource(R.string.dan_folder_minutes, minutes) } ?: it
    }
    val details = listOfNotNull(
        item.releaseInfo?.takeIf { it.isNotBlank() }, duration,
        item.ageRating?.takeIf { it.isNotBlank() }, rating
    ).joinToString(" · ")
    if (details.isNotEmpty()) {
        Text(details, color = NuvioTheme.colors.TextPrimary, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val context = LocalContext.current
    val genres = item.genres.take(3).map { localizedGenreLabel(context, it) }
        .joinToString(" · ").ifBlank { localizedContentType(item.apiType) }
    if (genres.isNotBlank()) {
        Text(genres, color = NuvioTheme.colors.TextSecondary, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
