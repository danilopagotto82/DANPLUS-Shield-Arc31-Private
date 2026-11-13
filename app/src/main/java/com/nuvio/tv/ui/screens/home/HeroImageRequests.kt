package com.nuvio.tv.ui.screens.home

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import androidx.compose.ui.unit.dp

internal val CollectionHeroLogoWidth = 320.dp
internal val CollectionHeroLogoHeight = 130.dp

/** The compositor and neighbour prefetch share dimensions, URL sizing and cache identity. */
internal object HeroImageRequests {
    @Volatile var width: Int = 0
    @Volatile var height: Int = 0

    /** The memory entry Home's hero holds for [url], for a page that opens on the same picture. */
    fun backdropKey(url: String): String? {
        val width = width
        val height = height
        return if (width > 0 && height > 0) backdropKey(heroTransferUrl(url, width), width, height) else null
    }

    private fun backdropKey(source: String, width: Int, height: Int) = "home-hero:$source:${width}x$height"

    fun backdrop(context: Context, url: String, width: Int, height: Int): ImageRequest.Builder {
        val source = heroTransferUrl(url, width)
        return ImageRequest.Builder(context).data(source)
            .memoryCacheKey(backdropKey(source, width, height))
            .size(width, height).scale(Scale.FILL).crossfade(false)
    }

    fun logo(context: Context, url: String, collectionFolder: Boolean = false): ImageRequest.Builder {
        val density = context.resources.displayMetrics.density
        if (collectionFolder) {
            // A square decode budget keeps detail in logos delivered on a tall or padded canvas.
            val edge = (CollectionHeroLogoWidth.value * density).toInt().coerceIn(1, 768)
            return ImageRequest.Builder(context).data(url)
                .memoryCacheKey("home-collection-logo:$url:$edge")
                .size(edge, edge).precision(Precision.EXACT).scale(Scale.FIT).crossfade(false)
                .transformations(CollectionLogoCrop)
        }
        return ImageRequest.Builder(context).data(url)
            .memoryCacheKey(com.nuvio.tv.core.image.titleLogoCacheKey(url))
            .size((220 * density).toInt().coerceAtLeast(1), (100 * density).toInt().coerceAtLeast(1))
            .scale(Scale.FIT).crossfade(false)
    }
}
