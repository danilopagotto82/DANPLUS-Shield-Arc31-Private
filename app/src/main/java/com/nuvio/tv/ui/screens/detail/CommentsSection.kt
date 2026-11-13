package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.nuvio.tv.ui.v2.appearance.LocalV2Appearance
import com.nuvio.tv.ui.v2.components.nuvioGlass
import com.nuvio.tv.ui.v2.components.GlassRole
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.TmdbReview
import com.nuvio.tv.ui.util.contentTextDirection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.nuvio.tv.ui.screens.ambient.AmbientDialogEffect

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun CommentsSection(
    comments: List<TmdbReview>,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    upFocusRequester: FocusRequester? = null,
    onLoadMore: () -> Unit,
    onCommentClick: (TmdbReview) -> Unit,
    listState: LazyListState,
    rowEntryFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    windowResetKey: String? = null
) {
    val cardShape = RoundedCornerShape(NuvioTheme.radii.xl)
    val commentFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocusedCommentId by rememberSaveable(windowResetKey) { mutableStateOf<String?>(null) }
    var lockCommentFocusId by remember { mutableStateOf(false) }
    val commentWindowIds = remember(comments) { comments.map { it.id } }
    val anchoredCommentId = listState.keepDetailRowWindow(
        itemIds = commentWindowIds,
        lazyKeyAt = { index -> comments.getOrNull(index)?.id },
        resetKey = windowResetKey
    )
    val commentsFocusTargetId = lastFocusedCommentId?.takeIf { it in commentWindowIds }
        ?: anchoredCommentId
        ?: comments.getOrNull(listState.firstVisibleItemIndex)?.id
        ?: comments.firstOrNull()?.id
    val commentsTargetFocusRequester = commentsFocusTargetId?.let {
        commentFocusRequesters.getOrPut(it) { FocusRequester() }
    }
    val upFocusModifier = if (upFocusRequester != null) {
        Modifier.focusProperties { up = upFocusRequester }
    } else {
        Modifier
    }
    LaunchedEffect(listState, comments.size, canLoadMore) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            canLoadMore && totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) onLoadMore()
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = NuvioTheme.spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.detail_tmdb_comments_title),
            style = MaterialTheme.typography.titleLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(start = detailStartInset, end = NuvioTheme.spacing.xxxl)
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
                    ) {
                        lockCommentFocusId = true
                    }
                    false
                }
                .onFocusChanged { state ->
                    if (!state.hasFocus) lockCommentFocusId = false
                }
                .focusProperties {
                    onEnter = {
                        commentsTargetFocusRequester?.requestFocus()
                    }
                }
                .focusGroup(),
            state = listState,
            contentPadding = PaddingValues(start = detailStartInset, end = NuvioTheme.spacing.xxxl, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            items(comments, key = { it.id }) { review ->
                val commentFocusRequester = commentFocusRequesters.getOrPut(review.id) { FocusRequester() }
                CommentCard(
                    review = review,
                    shape = cardShape,
                    modifier = Modifier
                        .then(
                            if (rowEntryFocusRequester != null && review.id == commentsFocusTargetId) {
                                Modifier.focusRequester(rowEntryFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .focusRequester(commentFocusRequester)
                        .then(upFocusModifier)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && !lockCommentFocusId) {
                                lastFocusedCommentId = review.id
                            }
                        },
                    onClick = { onCommentClick(review) }
                )
            }
            if (isLoadingMore) {
                item(key = "loading_more_comments") {
                    LoadingCommentCard(shape = cardShape)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentCard(
    review: TmdbReview,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(360.dp)
            .height(230.dp),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundCard
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = shape
            )
        ),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = review.authorDisplayName,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp,
                    textDirection = review.comment.contentTextDirection()
                ),
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            review.rating?.let { rating ->
                Text(
                    text = stringResource(R.string.detail_tmdb_comments_rating, rating),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun CommentOverlay(
    review: TmdbReview,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    isLoadingNext: Boolean,
    transitionDirection: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }
    val mainContentFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AmbientDialogEffect(onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (LocalV2Appearance.current != null) {
                    Modifier.nuvioGlass(GlassRole.MODAL)
                } else {
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF070707), Color(0xFF101010), Color(0xFF151515))))
                })
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (canNavigatePrevious) {
                                    onPrevious()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionRight -> {
                                if (canNavigateNext) {
                                    if (!isLoadingNext) onNext()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                }
                .padding(horizontal = NuvioTheme.spacing.xl, vertical = 10.dp)
        ) {
            AnimatedContent(
                targetState = review,
                transitionSpec = {
                    when {
                        transitionDirection > 0 -> {
                            slideInHorizontally(initialOffsetX = { it / 5 }) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { -it / 5 }) + fadeOut()
                        }
                        transitionDirection < 0 -> {
                            slideInHorizontally(initialOffsetX = { -it / 5 }) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { it / 5 }) + fadeOut()
                        }
                        else -> fadeIn() togetherWith fadeOut()
                    }
                },
                label = "comment_overlay_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 10.dp)
            ) {
                currentReview ->
                CommentOverlayContent(
                    review = currentReview,
                    primaryFocusRequester = primaryFocusRequester,
                    mainContentFocusRequester = mainContentFocusRequester,
                    isLoadingNext = isLoadingNext
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(168.dp)
                    .padding(top = 6.dp, end = NuvioTheme.spacing.xs)
                    .focusRequester(primaryFocusRequester)
                    .focusable()
                    // The node itself has no text: it is the Trakt wordmark plus the
                    // "click back to close" hint, so merge those two children into its name.
                    .semantics(mergeDescendants = true) { }
                    .focusProperties {
                        down = mainContentFocusRequester
                    },
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.trakt_logo_wordmark),
                    contentDescription = stringResource(R.string.cd_trakt_logo),
                    modifier = Modifier.width(168.dp),
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f))
                )
                Text(
                    text = stringResource(R.string.detail_comments_back_hint),
                    modifier = Modifier.padding(start = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.34f)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CommentOverlayContent(
    review: TmdbReview,
    primaryFocusRequester: FocusRequester,
    mainContentFocusRequester: FocusRequester,
    isLoadingNext: Boolean
) {
    val commentScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val commentText = review.comment
    val commentStyle = readerCommentStyle(commentText.length)
    val formattedCommentDate = remember(review.createdAt, review.updatedAt) {
        formatCommentTimestamp(review.createdAt, review.updatedAt)
    }
    val overlayLabels = buildList {
        review.rating?.let { add(stringResource(R.string.detail_tmdb_comments_rating, it)) }
        formattedCommentDate?.let { add(it) }
    }

    LaunchedEffect(review.id) {
        mainContentFocusRequester.requestFocus()
        withFrameNanos { }
        commentScrollState.scrollTo(0)
        withFrameNanos { }
        commentScrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_tmdb_comments_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.82f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = review.authorDisplayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                review.authorUsername
                    ?.takeIf { it.isNotBlank() }
                    ?.let { username ->
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                if (overlayLabels.isNotEmpty()) {
                    OverlayMetaRow(labels = overlayLabels)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(commentScrollState)
                    .focusRequester(mainContentFocusRequester)
                    .focusable()
                    // The scrollable body captures D-pad scrolling, so it needs the comment text
                    // as its name rather than being an unnamed focus stop.
                    .semantics { contentDescription = commentText }
                    .focusProperties {
                        up = primaryFocusRequester
                    }
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type != KeyEventType.KeyDown -> false
                            event.key == Key.DirectionDown && commentScrollState.value < commentScrollState.maxValue -> {
                                coroutineScope.launch {
                                    commentScrollState.animateScrollTo(
                                        (commentScrollState.value + 260).coerceAtMost(commentScrollState.maxValue)
                                    )
                                }
                                true
                            }
                            event.key == Key.DirectionUp && commentScrollState.value > 0 -> {
                                coroutineScope.launch {
                                    commentScrollState.animateScrollTo(
                                        (commentScrollState.value - 260).coerceAtLeast(0)
                                    )
                                }
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = commentText,
                    style = commentStyle.copy(textDirection = commentText.contentTextDirection()),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLoadingNext) {
                Text(
                    text = stringResource(R.string.detail_tmdb_comments_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.42f)
                )
            }
        }
    }
}

@Composable
private fun OverlayMetaRow(labels: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .size(NuvioTheme.spacing.xs)
                        .background(Color.White.copy(alpha = 0.42f), CircleShape)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun readerCommentStyle(length: Int): TextStyle {
    val typography = MaterialTheme.typography
    return when {
        length <= 160 -> typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp)
        length <= 280 -> typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 38.sp)
        length <= 420 -> typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 31.sp)
        length <= 650 -> typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp)
        length <= 900 -> typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp)
        else -> typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp)
    }
}

private fun formatCommentTimestamp(createdAt: String?, updatedAt: String?): String? {
    val rawTimestamp = createdAt?.trim()?.takeIf { it.isNotBlank() }
        ?: updatedAt?.trim()?.takeIf { it.isNotBlank() }
        ?: return null

    val instant = runCatching {
        if (rawTimestamp.all { it.isDigit() }) {
            val epoch = rawTimestamp.toLong()
            val epochMillis = if (epoch < 100_000_000_000L) epoch * 1000L else epoch
            Instant.ofEpochMilli(epochMillis)
        } else {
            runCatching { Instant.parse(rawTimestamp) }.getOrElse {
                runCatching { OffsetDateTime.parse(rawTimestamp).toInstant() }.getOrElse {
                    LocalDateTime.parse(rawTimestamp).atZone(ZoneId.systemDefault()).toInstant()
                }
            }
        }
    }.getOrNull() ?: return null

    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

private fun commentMaxLines(length: Int): Int = when {
    length <= 160 -> 7
    length <= 280 -> 10
    length <= 420 -> 13
    length <= 650 -> 17
    length <= 900 -> 22
    else -> 28
}

@Composable
private fun LoadingCommentCard(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(360.dp)
            .height(230.dp)
            .background(
                color = NuvioTheme.colors.BackgroundCard,
                shape = shape
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(18.dp)
                .background(NuvioTheme.colors.BackgroundElevated, shape = shape)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(NuvioTheme.spacing.xl)
                        .background(NuvioTheme.colors.BackgroundElevated, shape = shape)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(NuvioTheme.colors.BackgroundElevated, shape = shape)
        )
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(NuvioTheme.spacing.lg)
                .background(NuvioTheme.colors.BackgroundElevated, shape = shape)
        )
    }
}
