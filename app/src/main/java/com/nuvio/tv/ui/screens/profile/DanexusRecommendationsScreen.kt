@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.UserProfile
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
    val profiles by viewModel.profileManager.profiles.collectAsState()
    val active by viewModel.profileManager.activeProfileId.collectAsState()
    val pictures by viewModel.profilePictures.collectAsStateWithLifecycle()
    val colors = NuvioTheme.colors
    val recipients = profiles.filter { it.id != active }
    var selected by remember(active, meta.id, meta.apiType) { mutableStateOf(emptySet<Int>()) }
    val activeServerId = profiles.firstOrNull { it.id == active }?.serverProfileId
    val canReview = viewModel.remoteEnabled && activeServerId != null &&
        selected.isNotEmpty() && recipients.count { it.id in selected && it.serverProfileId != null } == selected.size
    var confirmation by remember(active, activeServerId, meta.id) { mutableStateOf<Map<Int, String?>?>(null) }
    var sentCount by remember(active, meta.id) { mutableIntStateOf(0) }
    var error by remember(active, meta.id) { mutableStateOf(false) }
    val firstRecipient = remember { FocusRequester() }

    LaunchedEffect(active, meta.id, confirmation == null, recipients.map { it.id }) {
        repeat(2) { withFrameNanos { } }
        if (confirmation == null && recipients.isNotEmpty()) runCatching { firstRecipient.requestFocus() }
    }

    if (confirmation == null) NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.d12_select_title),
        subtitle = meta.name,
        width = 620.dp, titleTextAlign = TextAlign.Center,
        solidWithoutBlur = true
    ) {
        Text(stringResource(R.string.d12_select_hint), Modifier.fillMaxWidth(), color = colors.TextSecondary, textAlign = TextAlign.Center)
        if (!viewModel.remoteEnabled) {
            Text(stringResource(R.string.d12_sync_unavailable), color = colors.TextSecondary)
        } else if (activeServerId == null) {
            Text(stringResource(R.string.d12_sync_profile_required), color = colors.TextSecondary)
        }
        if (recipients.isEmpty()) {
            Text(stringResource(R.string.d12_no_profiles), color = colors.TextSecondary)
        } else {
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
                            IndicationProfileName(profile, pictures[profile.id])
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
        if (viewModel.remoteEnabled && activeServerId != null &&
            recipients.any { it.id in selected && it.serverProfileId == null }
        ) {
            Text(stringResource(R.string.d12_sync_recipient_required), color = colors.TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recipients.isNotEmpty()) {
                Button(onClick = { selected = if (selected.size == recipients.size) emptySet() else recipients.map { it.id }.toSet() }) {
                    Text(stringResource(R.string.d12_all_profiles))
                }
            }
            if (viewModel.remoteEnabled && activeServerId != null) {
                Button(onClick = {
                    confirmation = recipients.filter { it.id in selected }.associate { it.id to it.serverProfileId }
                }, enabled = canReview) {
                    Text(stringResource(R.string.d12_review_send))
                }
            }
            Button(onClick = onDismiss) { Text(stringResource(R.string.d12_close)) }
        }
    }

    confirmation?.let { targets ->
        val names = recipients.filter { it.id in targets && targets[it.id] == it.serverProfileId }.map { it.name }
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(targets) {
            repeat(2) { withFrameNanos { } }
            runCatching { cancelFocus.requestFocus() }
        }
        NuvioDialog(onDismiss = { confirmation = null }, title = stringResource(R.string.d12_confirm_title),
            subtitle = meta.name, width = 560.dp, titleTextAlign = TextAlign.Center, solidWithoutBlur = true) {
            Text(stringResource(R.string.d12_confirm_targets, names.joinToString(", ")), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Button(onClick = { confirmation = null }, modifier = Modifier.focusRequester(cancelFocus)) {
                    Text(stringResource(R.string.d12_cancel))
                }
                Button(onClick = {
                    confirmation = null // Avoid duplicate DPAD delivery.
                    viewModel.send(active, activeServerId, targets, meta).onSuccess {
                        sentCount = it
                        error = it == 0
                        if (it > 0) selected = emptySet()
                    }.onFailure { error = true; sentCount = 0 }
                }, enabled = targets.isNotEmpty() && names.size == targets.size) {
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
    val active by viewModel.profileManager.activeProfileId.collectAsState()
    val profiles by viewModel.profileManager.profiles.collectAsState()
    val records by viewModel.items.collectAsState()
    val pictures by viewModel.profilePictures.collectAsStateWithLifecycle()
    val colors = NuvioTheme.colors
    var sent by remember(active) { mutableStateOf(false) }
    var person by remember(active, sent) { mutableStateOf<Int?>(null) }
    var toRemove by remember(active) { mutableStateOf<ProfileRecommendation?>(null) }
    val receivedFocus = remember { FocusRequester() }
    val sentFocus = remember { FocusRequester() }
    val available = profiles.associateBy(UserProfile::id)
    val visible = ProfileRecommendationRules.inbox(records, active, sent, person?.takeIf { it in available })
        .filter { ProfileRecommendationRules.belongsToCurrentProfiles(it, available) }

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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FavoriteBorder, null, Modifier.size(30.dp), tint = colors.Secondary)
                    Text(stringResource(R.string.d12_screen_title), style = MaterialTheme.typography.headlineLarge)
                }
                if (!viewModel.remoteEnabled) {
                    Text(stringResource(R.string.d12_sync_unavailable), color = colors.TextSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { sent = false }, modifier = Modifier.focusRequester(receivedFocus), colors = indicationButtonColors(!sent)) {
                    Text(stringResource(R.string.d12_received))
                }
                Button(onClick = { sent = true }, modifier = Modifier.focusRequester(sentFocus), colors = indicationButtonColors(sent)) {
                    Text(stringResource(R.string.d12_sent))
                }
                Button(onClick = onNewRecommendation, colors = indicationButtonColors()) {
                    Icon(Icons.Default.Movie, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.d12_find_to_send))
                }
            }
            if (profiles.size > 1) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    item {
                        Button(onClick = { person = null }, colors = indicationButtonColors(person == null)) {
                            Text(stringResource(R.string.d12_all_profiles_filter))
                        }
                    }
                    items(profiles.filter { it.id != active }, key = { it.id }) { profile ->
                        Button(onClick = { person = profile.id }, colors = indicationButtonColors(person == profile.id)) {
                            IndicationProfileName(profile, pictures[profile.id])
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.d12_empty), style = MaterialTheme.typography.bodyLarge, color = colors.TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.key }) { row ->
                        val other = profiles.firstOrNull { it.id == if (sent) row.to else row.from }
                        val watchedFlow = remember(row.key, row.to, row.episodeKeys) { viewModel.isRecipientWatched(row) }
                        val watchedByRecipient by watchedFlow.collectAsState(initial = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (viewModel.open(row)) onOpen(row.contentId, row.type)
                                },
                                modifier = Modifier.weight(1f),
                                colors = indicationButtonColors(),
                                shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                                border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, colors.Secondary))),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(
                                        Modifier.size(width = 100.dp, height = 65.dp)
                                            .clip(RoundedCornerShape(8.dp)).background(colors.Background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Movie, null, tint = colors.TextSecondary)
                                        (row.backdrop ?: row.poster)?.let {
                                            AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(row.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text((if (sent) stringResource(R.string.d12_to) else stringResource(R.string.d12_from)) +
                                            " " + (other?.name ?: "—"), color = colors.TextSecondary)
                                        if (!sent && !row.seen) Text(stringResource(R.string.d12_unread), color = colors.Secondary)
                                        if (row.uploadPending) Text(stringResource(R.string.d12_pending_sync), color = colors.TextSecondary)
                                        row.synopsis?.let {
                                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.TextSecondary)
                                        }
                                    }
                                    if (watchedByRecipient) {
                                        Icon(Icons.Default.Check, stringResource(R.string.d12_watched), tint = colors.Secondary)
                                        Text(stringResource(R.string.d12_watched), color = colors.Secondary)
                                    }
                                }
                            }
                            Button(onClick = { toRemove = row }, colors = indicationButtonColors(), contentPadding = PaddingValues(12.dp)) {
                                Icon(Icons.Default.DeleteOutline, stringResource(R.string.d12_remove))
                            }
                        }
                    }
                }
            }
        }
    }

    toRemove?.let { row ->
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(row.key) {
            repeat(2) { withFrameNanos { } }
            runCatching { cancelFocus.requestFocus() }
        }
        NuvioDialog(onDismiss = { toRemove = null }, title = stringResource(R.string.d12_remove),
            subtitle = row.title, titleTextAlign = TextAlign.Center, solidWithoutBlur = true) {
            Text(stringResource(R.string.d12_remove_confirm), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Button(onClick = { toRemove = null }, modifier = Modifier.focusRequester(cancelFocus)) {
                    Text(stringResource(R.string.d12_cancel))
                }
                Button(onClick = {
                    viewModel.dismiss(row)
                    toRemove = null
                    if (sent) sentFocus.requestFocus() else receivedFocus.requestFocus()
                }) {
                    Text(stringResource(R.string.d12_remove))
                }
            }
        }
    }
}

@Composable
private fun IndicationProfileName(profile: UserProfile, picture: String?) {
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
private fun indicationButtonColors(selected: Boolean = false): ButtonColors {
    val colors = NuvioTheme.colors
    return ButtonDefaults.colors(
        containerColor = if (selected) colors.Secondary else colors.BackgroundCard,
        contentColor = if (selected) colors.OnSecondary else colors.TextPrimary,
        focusedContainerColor = colors.FocusBackground,
        focusedContentColor = colors.FocusContent
    )
}
