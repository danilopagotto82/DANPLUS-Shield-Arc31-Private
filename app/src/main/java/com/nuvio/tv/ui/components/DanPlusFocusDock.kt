package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.util.localizedGenreLabel
import java.text.NumberFormat

@Composable
internal fun DanPlusFocusDock(item: MetaPreview, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val panel = Color(0xFF0B1725)
    val shape = RoundedCornerShape(14.dp)
    val artwork = item.background?.takeIf { it.isNotBlank() }
        ?: item.landscapePoster?.takeIf { it.isNotBlank() }
    val backdrop = remember(context, artwork) {
        artwork?.let { ImageRequest.Builder(context).data(it).size(780, 320)
            .networkCachePolicy(CachePolicy.DISABLED).build() }
    }
    val logo = item.logo?.takeIf { it.isNotBlank() }
    val logoRequest = remember(context, logo) {
        logo?.let { ImageRequest.Builder(context).data(it).size(320, 108)
            .networkCachePolicy(CachePolicy.DISABLED).build() }
    }
    var logoLoaded by remember(logoRequest) { mutableStateOf(false) }
    val rating = item.imdbRating?.takeIf { it.isFinite() && it > 0f }
    val metadata = listOfNotNull(
        item.releaseInfo?.takeIf { it.isNotBlank() },
        item.runtime?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.toIntOrNull() != null) "$it min" else it },
        rating?.let { "★ " + NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(it) }
    ).joinToString(" · ")
    val genres = item.genres.take(3).joinToString(" · ") { localizedGenreLabel(context, it) }

    Box(modifier.fillMaxWidth().padding(bottom = 12.dp).height(160.dp).clip(shape)
        .background(panel).border(1.dp, Color(0xFF33445B), shape)) {
        AsyncImage(backdrop, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to panel, .48f to panel.copy(alpha = .94f), 1f to panel.copy(alpha = .26f)
        )))
        Column(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(.69f).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                if (!logoLoaded) {
                    Text(item.name, color = Color.White, style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (logoRequest != null) {
                    AsyncImage(logoRequest, item.name, Modifier.matchParentSize(), contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart, onSuccess = { logoLoaded = true }, onError = { logoLoaded = false })
                }
            }
            if (metadata.isNotEmpty()) {
                Text(metadata, color = Color(0xFFDCE7F4), style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (genres.isNotEmpty()) {
                Text(genres, color = Color(0xFFDCE7F4), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
