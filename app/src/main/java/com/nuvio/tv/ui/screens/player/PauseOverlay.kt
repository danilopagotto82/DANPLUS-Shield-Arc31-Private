package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import com.nuvio.tv.ui.util.localizedGenreLabel

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.domain.model.MetaCastMember
import android.text.format.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.localizeEpisodeTitle

@Composable
fun PauseOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    title: String,
    logo: String?,
    poster: String?,
    episodeTitle: String?,
    season: Int?,
    episode: Int?,
    year: String?,
    type: String?,
    genres: List<String>,
    imdbRating: Float?,
    description: String?,
    cast: List<MetaCastMember>,
    directors: List<String>,
    directorMembers: List<MetaCastMember>,
    peopleFocusRequester: FocusRequester,
    onPeopleFocus: (Boolean) -> Unit,
    onOpenPerson: (Int, String, Boolean) -> Unit,
    remainingMinutes: Long?,
    modifier: Modifier = Modifier,
    showClock: Boolean = true
) {
    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        dismissOnBackgroundClick = true,
        contentPadding = PaddingValues(
            start = NuvioTheme.spacing.huge,
            end = NuvioTheme.spacing.huge,
            top = 24.dp,
            bottom = 24.dp
        ),
        topEndContent = {
            if (showClock) {
                PauseOverlayClock()
            }
        }
    ) {
        val shade = remember {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.18f to Color(0x87090A0F),
                0.6f to Color(0xE5090A0F),
                1f to Color(0xFA090A0F)
            )
        }
        Box(Modifier.fillMaxSize().background(shade, RoundedCornerShape(24.dp))) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                PauseMetadataView(
                    title = title,
                    logo = logo,
                    poster = poster,
                    episodeTitle = episodeTitle,
                    season = season,
                    episode = episode,
                    year = year,
                    type = type,
                    genres = genres,
                    imdbRating = imdbRating,
                    description = description,
                    cast = cast,
                    directors = directors,
                    directorMembers = directorMembers,
                    remainingMinutes = remainingMinutes,
                    peopleFocusRequester = peopleFocusRequester,
                    onPeopleFocus = onPeopleFocus,
                    onOpenPerson = onOpenPerson
                )
            }
        }
    }
}

@Composable
private fun PauseOverlayClock(modifier: Modifier = Modifier) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }

    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMillis = current
            val delayMs = (60_000L - (current % 60_000L)).coerceAtLeast(1_000L)
            delay(delayMs)
        }
    }

    Text(
        text = formatter.format(Date(nowMillis)),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Normal,
            fontSize = 34.sp
        ),
        color = Color.White.copy(alpha = 0.95f),
        modifier = modifier
    )
}

private val PauseGold = Color(0xFFF0C67B)
private val PauseText = Color(0xFFF7F2E9)
private val PauseMuted = Color(0xFFD5CED1)
private val PauseCard = Color(0xD91C1C24)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseMetadataView(
    title: String,
    logo: String?,
    poster: String?,
    episodeTitle: String?,
    season: Int?,
    episode: Int?,
    year: String?,
    type: String?,
    genres: List<String>,
    imdbRating: Float?,
    description: String?,
    cast: List<MetaCastMember>,
    directors: List<String>,
    directorMembers: List<MetaCastMember>,
    remainingMinutes: Long?,
    peopleFocusRequester: FocusRequester,
    onPeopleFocus: (Boolean) -> Unit,
    onOpenPerson: (Int, String, Boolean) -> Unit
) {
    val direction = remember(directors, directorMembers, cast) { pauseDirectors(directors, directorMembers, cast) }
    val actors = remember(cast, direction) { pauseActors(cast, direction) }
    val context = LocalContext.current
    val genreLine = remember(context, genres) {
        genres.filter(String::isNotBlank).take(2).joinToString(" • ") { localizedGenreLabel(context, it) }
    }
    val series = type.equals("series", true) || type.equals("tv", true) || type.equals("show", true)
    val kind = when {
        series -> stringResource(R.string.type_series)
        type.equals("movie", true) -> stringResource(R.string.type_movie)
        else -> null
    }
    val episodeLabel = if (series && season != null && episode != null) {
        stringResource(R.string.season_episode_format, season, episode)
    } else null
    val remaining = remainingMinutes?.let { minutes ->
        val hours = minutes / 60
        val remainder = minutes % 60
        when {
            hours == 0L -> stringResource(R.string.pause_ends_in_minutes, remainder)
            remainder == 0L -> stringResource(R.string.pause_ends_in_hours, hours)
            else -> stringResource(R.string.pause_ends_in_hours_minutes, hours, remainder)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 400.dp || maxWidth < 660.dp
        val posterWidth = if (compact) 96.dp else 166.dp
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!poster.isNullOrBlank()) {
                    PauseArtwork(
                        url = poster,
                        modifier = Modifier.size(posterWidth, posterWidth * 1.5f),
                        portrait = false
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                    Text(
                        text = stringResource(R.string.pause_you_are_watching),
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PauseGold
                    )
                    PauseTitle(title, logo, compact)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOfNotNull(kind, year?.takeIf(String::isNotBlank), episodeLabel).forEach { PauseBadge(it) }
                        if (genreLine.isNotEmpty()) PauseBadge(genreLine)
                        imdbRating?.takeIf { it.isFinite() && it > 0f }?.let { rating ->
                            PauseBadge(stringResource(R.string.pause_imdb_score, rating), PauseGold)
                        }
                    }
                    if (!episodeTitle.isNullOrBlank()) {
                        Text(
                            episodeTitle.localizeEpisodeTitle(context), color = PauseText,
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    remaining?.let { PauseBadge(it, Color(0xFFB6F2DB), emphasis = true) }
                    if (!description.isNullOrBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = description.contentTextDirection()),
                            color = PauseMuted, maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (direction.isNotEmpty() || actors.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onPeopleFocus(it.hasFocus) }.focusGroup(),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (direction.isNotEmpty()) {
                        items(direction.size) { index ->
                            val person = direction[index]
                            PauseCreditCard(
                                member = person,
                                group = stringResource(R.string.pause_direction_label),
                                subtitle = null,
                                compact = compact,
                                modifier = if (index == 0) Modifier.focusRequester(peopleFocusRequester) else Modifier,
                                onClick = { person.tmdbId?.let { onOpenPerson(it, person.name, true) } }
                            )
                        }
                    }
                    items(actors.size) { index ->
                        val person = actors[index]
                        PauseCreditCard(
                            member = person,
                            group = stringResource(R.string.pause_cast_label),
                            subtitle = person.character?.takeIf(String::isNotBlank),
                            compact = compact,
                            modifier = if (index == 0 && direction.isEmpty()) Modifier.focusRequester(peopleFocusRequester) else Modifier,
                            onClick = { person.tmdbId?.let { onOpenPerson(it, person.name, false) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseTitle(title: String, logo: String?, compact: Boolean) {
    var failed by remember(logo) { mutableStateOf(false) }
    if (!logo.isNullOrBlank() && !failed) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(logo).memoryCacheKey(logo).build(),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.size(width = if (compact) 175.dp else 238.dp, height = if (compact) 45.dp else 63.dp),
            onError = { failed = true }
        )
    } else {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = PauseText, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PauseBadge(text: String, tint: Color = PauseText, emphasis: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier
            .background(if (emphasis) Color(0xFF173B33) else PauseCard, CircleShape)
            .border(1.dp, tint.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
        color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PauseCreditCard(
    member: MetaCastMember,
    group: String,
    subtitle: String?,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val cardWidth = if (compact) 116.dp else 144.dp
    Card(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        colors = CardDefaults.colors(containerColor = PauseCard, focusedContainerColor = Color(0xFF37313A)),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(group, style = MaterialTheme.typography.labelSmall, color = PauseGold, maxLines = 1)
            PauseArtwork(
                url = member.photo,
                modifier = Modifier.size(if (compact) 50.dp else 66.dp, if (compact) 57.dp else 75.dp),
                portrait = true
            )
            Text(
                member.name, style = MaterialTheme.typography.labelLarge, color = PauseText,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.labelSmall, color = PauseMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PauseArtwork(url: String?, modifier: Modifier, portrait: Boolean) {
    val shape = RoundedCornerShape(if (portrait) 8.dp else 14.dp)
    Box(
        modifier.clip(shape).background(PauseCard).border(1.dp, PauseGold.copy(alpha = 0.26f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (portrait) Icons.Default.Person else Icons.Default.Movie, null, tint = PauseMuted.copy(alpha = 0.6f))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = if (portrait) Alignment.TopCenter else Alignment.Center
            )
        }
    }
}
