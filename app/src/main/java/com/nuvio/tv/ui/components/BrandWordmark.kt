package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nuvio.tv.R

internal const val BrandWordmarkAspect = 3f
internal const val BrandMarkAspect = 543f / 399f

@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    drawableOverride: Int? = null,
    alignment: Alignment = Alignment.Center
) {
    Image(
        painter = painterResource(drawableOverride ?: R.drawable.danplus_startup_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha
    )
}

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    alignment: Alignment = Alignment.Center
) {
    Image(
        painter = painterResource(R.drawable.danplus_brand_mark),
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha
    )
}
