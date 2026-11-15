@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.danexus.ProfileRecommendation
import com.nuvio.tv.core.danexus.ProfileRecommendationRules
import com.nuvio.tv.core.danexus.RecommendationProfile
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.v2.appearance.LocalV2Appearance
import com.nuvio.tv.ui.v2.appearance.V2Atmosphere

@Composable
fun DanexusRecommendDialog(
    meta: Meta,
    onDismiss: () -> Unit,
    viewModel: DanexusProfilesViewModel = hiltViewModel()
) {
    val profiles by viewModel.recommendationProfiles.collectAsStateWithLifecycle()
    val active by viewModel.viewer.collectAsStateWithLifecycle()
    val email by viewModel.accountEmail.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val pictures by viewModel.profilePictures.collectAsStateWithLifecycle()
    val colors = NuvioTheme.colors
    val recipients = profiles.filter { it.id != active?.id }
    var selected by remember(active, meta.id, meta.apiType) { mutableStateOf(emptySet<String>()) }
    val canReview = active != null && !locked && selected.isNotEmpty()
    var confirmation by remember(active, meta.id) { mutableStateOf<Map<Int, String>?>(null) }
    var sentCount by remember(active, meta.id) { mutableIntStateOf(0) }
    var error by remember(active, meta.id) { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    val firstRecipient = remember { FocusRequester() }

    DisposableEffect(viewModel) {
        viewModel.startSync()
        onDispose { viewModel.stopSync() }
    }
    LaunchedEffect(active, meta.id, confirmation == null, recipients.map { it.id }, accountOpen, locked) {
        repeat(2) { withFrameNanos { } }
        if (confirmation == null && !accountOpen && !locked && active != null && recipients.isNotEmpty()) {
            runCatching { firstRecipient.requestFocus() }
        }
    }

    if (accountOpen) {
        RecommendationAccountDialog(viewModel) { accountOpen = false }
    } else if (confirmation == null) NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.d12_select_title),
        subtitle = meta.name,
        width = 620.dp, titleTextAlign = TextAlign.Center,
        solidWithoutBlur = true
    ) {
        Text(stringResource(R.string.d12_select_hint), Modifier.fillMaxWidth(), color = colors.TextSecondary, textAlign = TextAlign.Center)
        if (!viewModel.remoteEnabled) {
            Text(stringResource(R.string.d12_sync_unavailable), color = colors.TextSecondary)
        } else if (active == null || locked) {
            Text(stringResource(if (locked) R.string.d12_unlock_hint else if (email == null) R.string.d12_setup_hint else R.string.d12_profile_sync_hint), color = colors.TextSecondary)
        }
        if (active != null && !locked && recipients.isEmpty()) {
            Text(stringResource(R.string.d12_no_profiles), color = colors.TextSecondary)
        } else if (active != null && !locked) {
            LazyColumn(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recipients, key = { it.id }) { profile ->
                    val chosen = profile.id in selected
                    Button(
                        onClick = {
                            selected = if (chosen) selected - profile.id else selected + profile.id
                            sentCount = 0
                            error = false
                        },
                        modifier = Modifier.fillMaxWidth()
                            .then(if (profile.id == recipients.first().id) Modifier.focusRequester(firstRecipient) else Modifier),
                        colors = indicationButtonColors(chosen)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IndicationProfileName(profile, viewModel.localProfileFor(profile)?.id?.let(pictures::get))
                            Spacer(Modifier.weight(1f))
                            Icon(if (chosen) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
            Text(stringResource(R.string.d12_selected_count, selected.size), color = colors.Secondary)
        }
        if (error) Text(stringResource(R.string.d12_send_error), Modifier.fillMaxWidth(), color = colors.Error, textAlign = TextAlign.Center)
        if (sentCount > 0) Text(stringResource(R.string.d12_send_queued, sentCount), Modifier.fillMaxWidth(), color = colors.Secondary, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.remoteEnabled && (email == null || active == null || locked)) {
                Button(onClick = { accountOpen = true }, colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(if (locked) R.string.d12_unlock_action else R.string.d12_account_action))
                }
            }
            if (active != null && !locked && recipients.isNotEmpty()) {
                Button(onClick = { selected = if (selected.size == recipients.size) emptySet() else recipients.map { it.id }.toSet() },
                    colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_all_profiles))
                }
            }
            if (active != null && !locked) {
                Button(onClick = {
                    confirmation = recipients.filter { it.id in selected }.associate { it.index to it.id }
                }, enabled = canReview, colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_review_send))
                }
            }
            Button(onClick = onDismiss, colors = indicationButtonColors(), border = indicationFocusBorder()) { Text(stringResource(R.string.d12_close)) }
        }
    }

    confirmation?.let { targets ->
        val names = recipients.filter { targets[it.index] == it.id }.map { it.name }
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(targets) {
            repeat(2) { withFrameNanos { } }
            runCatching { cancelFocus.requestFocus() }
        }
        NuvioDialog(onDismiss = { confirmation = null }, title = stringResource(R.string.d12_confirm_title),
            subtitle = meta.name, width = 560.dp, titleTextAlign = TextAlign.Center, solidWithoutBlur = true) {
            Text(stringResource(R.string.d12_confirm_targets, names.joinToString(", ")), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Button(onClick = { confirmation = null }, modifier = Modifier.focusRequester(cancelFocus),
                    colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_cancel))
                }
                Button(onClick = {
                    confirmation = null // Avoid duplicate DPAD delivery.
                    val viewerId = active?.id ?: return@Button
                    viewModel.send(viewerId, targets, meta).onSuccess {
                        sentCount = it
                        error = it == 0
                        if (it > 0) selected = emptySet()
                    }.onFailure { error = true; sentCount = 0 }
                }, enabled = targets.isNotEmpty() && names.size == targets.size,
                    colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_confirm_send, names.size))
                }
            }
        }
    }
}

@Composable
fun DanexusRecommendationsScreen(
    onOpen: (String, String) -> Unit,
    onNewRecommendation: () -> Unit,
    viewModel: DanexusProfilesViewModel = hiltViewModel()
) {
    val active by viewModel.viewer.collectAsStateWithLifecycle()
    val profiles by viewModel.recommendationProfiles.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val email by viewModel.accountEmail.collectAsStateWithLifecycle()
    val records by viewModel.items.collectAsState()
    val syncFailed by viewModel.syncFailed.collectAsStateWithLifecycle()
    val pictures by viewModel.profilePictures.collectAsStateWithLifecycle()
    val colors = NuvioTheme.colors
    var sent by remember(active) { mutableStateOf(false) }
    var person by remember(active, sent) { mutableStateOf<Int?>(null) }
    var accountOpen by remember { mutableStateOf(false) }
    var toRemove by remember(active) { mutableStateOf<List<ProfileRecommendation>?>(null) }
    val receivedFocus = remember { FocusRequester() }
    val sentFocus = remember { FocusRequester() }
    val cards = remember(records, active, sent, person) {
        active?.let { ProfileRecommendationRules.cards(records, it.index, sent, person) }.orEmpty()
    }
    val profilesByIndex = remember(profiles) { profiles.associateBy(RecommendationProfile::index) }

    LaunchedEffect(active) { viewModel.refresh() }
    DisposableEffect(viewModel) {
        viewModel.startSync()
        onDispose { viewModel.stopSync() }
    }
    LaunchedEffect(active, sent) {
        repeat(2) { withFrameNanos { } }
        runCatching { if (sent) sentFocus.requestFocus() else receivedFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        LocalV2Appearance.current?.let { V2Atmosphere(background = it.settingsBackground) }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).background(colors.Secondary.copy(alpha = .14f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FavoriteBorder, null, Modifier.size(24.dp), tint = colors.Secondary)
                    }
                    Text(stringResource(R.string.d12_screen_title), style = MaterialTheme.typography.headlineLarge)
                }
                if (!viewModel.remoteEnabled) {
                    Text(stringResource(R.string.d12_sync_unavailable), color = colors.TextSecondary)
                } else if (active == null || locked) {
                    Text(stringResource(if (locked) R.string.d12_unlock_hint else if (email == null) R.string.d12_setup_hint else R.string.d12_profile_sync_hint), color = colors.TextSecondary)
                }
                if (syncFailed && !locked) {
                    Text(stringResource(R.string.d12_sync_retry), color = colors.TextSecondary)
                }
            }
            Row(
                Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { sent = false }, modifier = Modifier.focusRequester(receivedFocus), colors = indicationButtonColors(!sent), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_received))
                }
                Button(onClick = { sent = true }, modifier = Modifier.focusRequester(sentFocus), colors = indicationButtonColors(sent), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_sent))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onNewRecommendation, colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Icon(Icons.Default.Movie, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.d12_find_to_send))
                }
                if (viewModel.remoteEnabled && (email == null || active == null || locked)) Button(onClick = { accountOpen = true }, colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(if (locked) R.string.d12_unlock_action else R.string.d12_account_action))
                }
            }
            if (active != null && !locked && profiles.size > 1) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Button(onClick = { person = null }, colors = indicationButtonColors(person == null), border = indicationFocusBorder()) {
                            Text(stringResource(R.string.d12_all_profiles_filter))
                        }
                    }
                    items(profiles.filter { it.id != active?.id }, key = { it.id }) { profile ->
                        Button(onClick = { person = profile.index }, colors = indicationButtonColors(person == profile.index), border = indicationFocusBorder()) {
                            IndicationProfileName(profile, viewModel.localProfileFor(profile)?.id?.let(pictures::get))
                        }
                    }
                }
            }
            if (cards.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.d12_empty), style = MaterialTheme.typography.bodyLarge, color = colors.TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 928.dp).fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cards, key = { it.first }) { (_, rows) ->
                        val row = rows.first()
                        val people = rows.mapNotNull { profilesByIndex[if (sent) it.to else it.from] }
                        val watchedFlow = remember(rows, viewModel) { viewModel.watchedRecipientCount(rows) }
                        val watchedCount by watchedFlow.collectAsStateWithLifecycle(initialValue = 0)
                        RecommendationCard(
                            row = row,
                            backdrop = viewModel.indicationBackdrop(row),
                            people = people,
                            pictureFor = { profile -> viewModel.localProfileFor(profile)?.id?.let(pictures::get) },
                            sent = sent,
                            pending = rows.any { it.uploadPending },
                            watchedCount = watchedCount,
                            recipientCount = rows.size,
                            onOpen = { if (viewModel.open(row)) onOpen(row.contentId, row.type) },
                            onRemove = { toRemove = rows }
                        )
                    }
                }
            }
        }
    }

    toRemove?.let { rows ->
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(rows) {
            repeat(2) { withFrameNanos { } }
            runCatching { cancelFocus.requestFocus() }
        }
        NuvioDialog(onDismiss = { toRemove = null }, title = stringResource(R.string.d12_remove),
            subtitle = rows.first().title, titleTextAlign = TextAlign.Center, solidWithoutBlur = true) {
            val message = if (rows.size == 1) stringResource(R.string.d12_remove_confirm)
                else stringResource(R.string.d12_remove_send_confirm, rows.size)
            Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Button(onClick = { toRemove = null }, modifier = Modifier.focusRequester(cancelFocus),
                    colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_cancel))
                }
                Button(onClick = {
                    rows.forEach(viewModel::dismiss)
                    toRemove = null
                    if (sent) sentFocus.requestFocus() else receivedFocus.requestFocus()
                }, colors = indicationButtonColors(), border = indicationFocusBorder()) {
                    Text(stringResource(R.string.d12_remove))
                }
            }
        }
    }
    if (accountOpen) RecommendationAccountDialog(viewModel) { accountOpen = false }
}

@Composable
private fun RecommendationCard(
    row: ProfileRecommendation,
    backdrop: String?,
    people: List<RecommendationProfile>,
    pictureFor: (RecommendationProfile) -> String?,
    sent: Boolean,
    pending: Boolean,
    watchedCount: Int,
    recipientCount: Int,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val deleteShape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onOpen,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.colors(
                containerColor = colors.BackgroundCard,
                contentColor = colors.TextPrimary,
                focusedContainerColor = colors.Secondary.copy(alpha = .10f).compositeOver(colors.BackgroundCard),
                focusedContentColor = colors.TextPrimary
            ),
            shape = ButtonDefaults.shape(shape),
            scale = ButtonDefaults.scale(focusedScale = 1f),
            border = ButtonDefaults.border(
                border = Border(BorderStroke(1.dp, colors.TextSecondary.copy(alpha = .18f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, colors.Secondary), shape = shape)
            ),
            contentPadding = PaddingValues(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecommendationArtwork(backdrop, row.poster)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        row.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    row.synopsis?.takeIf(String::isNotBlank)?.let { synopsis ->
                        Text(
                            synopsis, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, color = colors.TextSecondary
                        )
                    }
                    if (people.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                stringResource(if (sent) R.string.d12_to else R.string.d12_from),
                                style = MaterialTheme.typography.labelMedium, color = colors.TextSecondary
                            )
                            if (people.size == 1) {
                                val person = people.single()
                                IndicationProfileName(person, pictureFor(person))
                            } else {
                                RecommendationRecipients(people, pictureFor)
                            }
                        }
                    }
                    if ((!sent && !row.seen) || pending || watchedCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!sent && !row.seen) {
                                Text(
                                    stringResource(R.string.d12_unread),
                                    style = MaterialTheme.typography.labelMedium, color = colors.Secondary
                                )
                            }
                            if (pending) {
                                Text(
                                    stringResource(R.string.d12_pending_sync),
                                    style = MaterialTheme.typography.labelMedium, color = colors.TextSecondary
                                )
                            }
                            if (watchedCount > 0) {
                                Row(
                                    Modifier.background(colors.Secondary.copy(alpha = .15f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = colors.Secondary)
                                    Text(
                                        if (watchedCount == recipientCount) stringResource(R.string.d12_watched)
                                        else stringResource(R.string.d12_watched_count, watchedCount, recipientCount),
                                        color = colors.Secondary, style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onRemove,
            colors = indicationButtonColors(),
            shape = ButtonDefaults.shape(deleteShape),
            scale = ButtonDefaults.scale(focusedScale = 1f),
            border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, colors.Secondary), shape = deleteShape)),
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, stringResource(R.string.d12_remove), Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RecommendationArtwork(backdrop: String?, poster: String?) {
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(11.dp)
    var backdropFailed by remember(backdrop) { mutableStateOf(false) }
    var posterFailed by remember(poster) { mutableStateOf(false) }
    Box(
        Modifier.size(width = 160.dp, height = 90.dp).clip(shape)
            .background(Brush.horizontalGradient(listOf(colors.Background, colors.BackgroundCard)))
            .border(1.dp, colors.TextSecondary.copy(alpha = .18f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (backdrop != null && !backdropFailed) {
            AsyncImage(
                backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onError = { backdropFailed = true }
            )
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .27f))))
            )
        } else if (!poster.isNullOrBlank() && !posterFailed) {
            AsyncImage(
                poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                onError = { posterFailed = true }
            )
        } else {
            Icon(Icons.Default.Movie, null, Modifier.size(24.dp), tint = colors.TextSecondary)
        }
    }
}

@Composable
private fun RecommendationRecipients(
    people: List<RecommendationProfile>,
    pictureFor: (RecommendationProfile) -> String?
) {
    val colors = NuvioTheme.colors
    val names = people.joinToString(", ") { it.name }
    val visible = people.take(5)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = names },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-5).dp)
    ) {
        visible.forEach { person ->
            Box(
                Modifier.background(colors.BackgroundCard, CircleShape)
                    .border(1.dp, colors.TextSecondary.copy(alpha = .30f), CircleShape).padding(2.dp)
            ) {
                ProfileAvatarCircle(
                    name = person.name, colorHex = person.avatarColorHex, size = 26.dp,
                    avatarImageUrl = pictureFor(person), imageCrossfade = false
                )
            }
        }
        if (people.size > visible.size) {
            Text(
                stringResource(R.string.d12_recipient_overflow, people.size - visible.size),
                Modifier.padding(start = 10.dp),
                color = colors.TextSecondary, style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
internal fun IndicationProfileName(profile: RecommendationProfile, picture: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileAvatarCircle(
            name = profile.name,
            colorHex = profile.avatarColorHex,
            size = 26.dp,
            avatarImageUrl = picture,
            imageCrossfade = false
        )
        Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun indicationFocusBorder() = ButtonDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, NuvioTheme.colors.Secondary))
)

@Composable
private fun indicationButtonColors(selected: Boolean = false): ButtonColors {
    val colors = NuvioTheme.colors
    return ButtonDefaults.colors(
        containerColor = if (selected) colors.Secondary else colors.BackgroundCard,
        contentColor = if (selected) colors.OnSecondary else colors.TextPrimary,
        focusedContainerColor = colors.FocusBackground,
        focusedContentColor = colors.FocusContent
    )
}
