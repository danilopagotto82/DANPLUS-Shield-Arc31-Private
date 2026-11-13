package com.nuvio.tv.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.danexus.DanexusRecommendations
import com.nuvio.tv.core.danexus.ProfileRecommendation
import com.nuvio.tv.core.danexus.ProfileRecommendationRules
import com.nuvio.tv.core.danexus.RecommendationSync
import com.nuvio.tv.core.danexus.RemoteRecommendation
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.ProfileLockStateDataStore
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

@HiltViewModel
class DanexusProfilesViewModel @Inject constructor(
    val profileManager: ProfileManager,
    private val watched: WatchedItemsPreferences,
    private val authManager: AuthManager,
    private val profileLocks: ProfileLockStateDataStore,
    private val sync: RecommendationSync,
    @ApplicationContext context: Context
) : ViewModel() {
    private val store = DanexusRecommendations(context)
    private val _items = MutableStateFlow(emptyList<ProfileRecommendation>())
    val items: StateFlow<List<ProfileRecommendation>> = _items
    private val pinStates = profileLocks.pinEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private var syncJob: Job? = null
    private var requestedSync = false

    init {
        viewModelScope.launch {
            combine(authManager.authState, profileManager.activeProfileId, profileManager.profiles, pinStates, store.changes) { auth, _, _, _, _ ->
                auth
            }
                .collect { state ->
                    if (state !is AuthState.FullAccount) sync.clearUnlock()
                    refresh()
                }
        }
    }

    fun refresh() {
        val owner = currentOwner()
        val profiles = profileManager.profiles.value.associateBy { it.id }
        val active = profiles[profileManager.activeProfileId.value]
        val viewerId = active?.serverProfileId
        val cacheAllowed = owner != null && remoteCacheAllowed(owner, active)
        _items.value = owner?.let { store.read(it) }.orEmpty().filter {
            (it.from == active?.id || it.to == active?.id) &&
                (!it.remote || (cacheAllowed && it.remoteViewerId == viewerId && it.remoteChange != "dismiss")) &&
                ProfileRecommendationRules.belongsToCurrentProfiles(it, profiles)
        }
    }

    fun startSync() {
        if (!sync.enabled || syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            while (isActive) {
                requestedSync = false
                synchronize()
                repeat(6) {
                    if (!requestedSync) delay(5_000)
                }
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun synchronize() {
        val owner = authManager.currentUserId ?: return
        val profile = profileManager.activeProfile ?: return
        val viewerId = profile.serverProfileId ?: return
        val revision = profileManager.profileSelectionRevision.value
        fun isCurrentViewer() = authManager.currentUserId == owner &&
            profileManager.activeProfile?.serverProfileId == viewerId &&
            profileManager.profileSelectionRevision.value == revision
        if (!isCurrentViewer()) return
        try {
            val profiles = profileManager.profiles.value.associateBy(UserProfile::id)
            val local = withContext(Dispatchers.IO) { store.read(owner) }
            val sentKeys = mutableSetOf<String>()
            local.filter {
                it.fromServerId == viewerId && it.uploadPending && ProfileRecommendationRules.belongsToCurrentProfiles(it, profiles)
            }.forEach { row ->
                if (!isCurrentViewer()) return
                sync.send(owner, viewerId, row)
                sentKeys += row.key
            }
            local.filter { it.remoteViewerId == viewerId && it.remoteChange != null }.forEach { row ->
                if (!isCurrentViewer()) return
                val change = checkNotNull(row.remoteChange)
                sync.update(owner, viewerId, change, row.key)
                withContext(Dispatchers.IO) { if (isCurrentViewer()) store.acknowledgeChange(owner, viewerId, row.key, change) }
            }
            if (!isCurrentViewer()) return
            val downloaded = sync.list(owner, viewerId, sent = false) + sync.list(owner, viewerId, sent = true)
            if (!isCurrentViewer()) return
            val byUuid = profiles.values.mapNotNull { p -> p.serverProfileId?.let { it to p } }.toMap()
            val rows = downloaded.mapNotNull { it.toLocal(viewerId, byUuid) }
            withContext(Dispatchers.IO) {
                if (isCurrentViewer()) {
                    store.cacheRemote(owner, viewerId, rows, sentKeys)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Local rows and the pending outbox remain available for the next attempt.
        } finally {
            refresh()
        }
    }

    fun isRecipientWatched(row: ProfileRecommendation): Flow<Boolean> = flow {
        if (row !in items.value) {
            emit(false)
            return@flow
        }
        emitAll(combine(watched.observeAllItems(row.to), profileLocks.pinEnabled, items) { history, locks, visible ->
            row in visible &&
                (profileManager.activeProfileId.value == row.to ||
                    locks[row.to] == false || (currentOwner() == DanexusRecommendations.GUEST && locks[row.to] != true)) &&
                (row.remoteWatched || ProfileRecommendationRules.isWatched(history, row.contentId, row.type, row.episodeKeys))
        })
    }
            .distinctUntilChanged()

    fun send(expectedSender: Int, expectedServerId: String?, requested: Map<Int, String?>, meta: Meta): Result<Int> = runCatching {
        val active = profileManager.activeProfileId.value
        check(active == expectedSender) { "Profile changed during selection" }
        val owner = checkNotNull(currentOwner())
        require(ProfileRecommendationRules.isSupportedType(meta.apiType))
        val serverIds = profileManager.profiles.value.associate { it.id to it.serverProfileId }
        check(active in serverIds && serverIds[active] == expectedServerId)
        check(requested.all { (id, uuid) -> id in serverIds && serverIds[id] == uuid })
        val targets = ProfileRecommendationRules.recipients(active, requested.keys, serverIds.keys)
        if (targets.isEmpty()) 0
        else store.send(active, targets, meta, owner, serverIds, sync.enabled && owner != DanexusRecommendations.GUEST)
            .also {
                refresh()
                requestedSync = true
                startSync()
            }
    }

    fun open(row: ProfileRecommendation): Boolean {
        val owner = currentOwner() ?: return false
        if (!isCurrent(row)) return false
        if (row.to == profileManager.activeProfileId.value) {
            if (row.remote) store.changeRemote(owner, checkNotNull(row.remoteViewerId), row.key, "read")
            else store.markSeen(row.key, row.to, owner)
            requestedSync = true
        }
        refresh()
        return true
    }

    fun dismiss(row: ProfileRecommendation) {
        val active = profileManager.activeProfileId.value
        val owner = currentOwner() ?: return
        if (!isCurrent(row)) return
        if (row.remote) store.changeRemote(owner, checkNotNull(row.remoteViewerId), row.key, "dismiss")
        else store.dismiss(row.key, active, owner)
        requestedSync = true
        refresh()
    }

    private fun isCurrent(row: ProfileRecommendation): Boolean {
        val active = profileManager.activeProfileId.value
        return row in items.value && (row.from == active || row.to == active) &&
            (!row.remote || currentOwner()?.let { remoteCacheAllowed(it, profileManager.activeProfile) } == true) &&
            ProfileRecommendationRules.belongsToCurrentProfiles(row, profileManager.profiles.value.associateBy { it.id })
    }

    private fun remoteCacheAllowed(owner: String, profile: UserProfile?): Boolean {
        if (!sync.enabled || profile?.serverProfileId == null || !authManager.hasLiveSession()) return false
        return pinStates.value[profile.id] == false || sync.hasUnlock(owner, profile.serverProfileId)
    }

    private fun RemoteRecommendation.toLocal(viewerId: String, profiles: Map<String, UserProfile>): ProfileRecommendation? {
        val from = profiles[sender] ?: return null
        val to = profiles[recipient] ?: return null
        val time = runCatching { OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrNull() ?: return null
        return ProfileRecommendation(
            key = id, from = from.id, to = to.id, contentId = mediaId, type = mediaType, title = title,
            poster = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }, createdAt = time,
            seen = readAt != null, synopsis = synopsis, episodeKeys = episodeKeys,
            fromServerId = sender, toServerId = recipient, remote = true, remoteWatched = watched, remoteViewerId = viewerId
        )
    }

    private fun currentOwner(): String? = when (val state = authManager.authState.value) {
        is AuthState.FullAccount -> state.userId
        AuthState.SignedOut -> DanexusRecommendations.GUEST
        AuthState.Loading -> null
    }
}
