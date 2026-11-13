package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R

private val LoadingGold = Color(0xFFE6BC68)
private val LoadingBackground = Color(0xFF090A0F)
private val LoadingText = Color(0xFFF7F2E9)

@Composable
fun LoadingOverlay(
    visible: Boolean,
    backdropUrl: String?,
    logoUrl: String?,
    title: String? = null,
    message: String? = null,
    sourceLine: String? = null,
    filename: String? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
        modifier = modifier
    ) {
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val backdropShade = remember(rtl) {
            val edge = Color(0xF5090A0F)
            val middle = Color(0xC4090A0F)
            val clear = Color(0x22090A0F)
            Brush.horizontalGradient(if (rtl) listOf(clear, middle, edge) else listOf(edge, middle, clear))
        }
        val bottomShade = remember {
            Brush.verticalGradient(0.32f to Color.Transparent, 1f to LoadingBackground)
        }
        var titleLogoReady by remember(logoUrl) { mutableStateOf(false) }

        BoxWithConstraints(Modifier.fillMaxSize().background(LoadingBackground)) {
            backdropUrl?.takeIf { it.isNotBlank() }?.let { artwork ->
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd
                )
                Box(Modifier.fillMaxSize().background(backdropShade))
                Box(Modifier.fillMaxSize().background(bottomShade))
            }
            val wide = maxWidth >= 600.dp
            val sideInset = if (wide) 58.dp else 24.dp
            val bottomInset = if (maxHeight >= 380.dp) 48.dp else 22.dp
            Image(
                painter = painterResource(R.drawable.danplus_startup_logo),
                contentDescription = "DAN+",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = sideInset, top = if (wide) 32.dp else 18.dp)
                    .width(104.dp)
                    .height(36.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sideInset, end = sideInset, bottom = bottomInset)
                    .widthIn(max = 384.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box {
                    if (!title.isNullOrBlank() && !titleLogoReady) {
                        Text(
                            text = title,
                            style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                            color = LoadingText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = title.takeIf { titleLogoReady },
                            onSuccess = { titleLogoReady = true },
                            onError = { titleLogoReady = false },
                            modifier = Modifier.width(if (wide) 276.dp else 214.dp)
                                .height(66.dp).alpha(if (titleLogoReady) 1f else 0f),
                            alignment = Alignment.CenterStart,
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                PlaybackLoadingBar(progress)
                Text(
                    text = message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.danplus_loading_preparing),
                    style = MaterialTheme.typography.titleSmall,
                    color = LoadingText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val source = sourceLine?.takeIf { it.isNotBlank() }
                    ?: filename?.takeIf { it.isNotBlank() }
                if (source != null) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelMedium,
                        color = LoadingText.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackLoadingBar(progress: Float?) {
    val measured = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shimmer = remember {
        listOf(LoadingGold.copy(alpha = 0.06f), LoadingGold, Color(0xFFFFE6A8), LoadingGold.copy(alpha = 0.06f))
    }
    val modifier = Modifier.fillMaxWidth().height(3.dp)
    if (measured != null) {
        val fraction by animateFloatAsState(measured, tween(210), label = "danPlusLoadingProgress")
        Canvas(modifier.progressSemantics(measured)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(color = LoadingGold.copy(alpha = 0.25f), cornerRadius = radius)
            val fill = fraction * size.width
            if (fill > 0) {
                drawRoundRect(
                    color = LoadingGold,
                    topLeft = Offset(if (rtl) size.width - fill else 0f, 0f),
                    size = Size(fill, size.height),
                    cornerRadius = radius
                )
            }
        }
    } else {
        val transition = rememberInfiniteTransition(label = "danPlusLoading")
        val position by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "danPlusLoadingSweep"
        )
        Canvas(modifier.progressSemantics()) {
            drawRoundRect(color = LoadingGold.copy(alpha = 0.25f), cornerRadius = CornerRadius(size.height / 2))
            val length = size.width * 0.32f
            val travel = position * (size.width + length)
            val start = if (rtl) size.width - travel else travel - length
            clipRect {
                drawRect(
                    brush = Brush.horizontalGradient(shimmer, startX = start, endX = start + length),
                    topLeft = Offset(start, 0f),
                    size = Size(length, size.height)
                )
            }
        }
    }
}
