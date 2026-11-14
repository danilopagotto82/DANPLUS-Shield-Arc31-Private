package com.nuvio.tv.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.danexus.DanexusRecommendations
import com.nuvio.tv.core.danexus.ProfileRecommendation
import com.nuvio.tv.core.danexus.ProfileRecommendationRules
import com.nuvio.tv.core.danexus.RecommendationSync
import com.nuvio.tv.core.danexus.RecommendationProfile
import com.nuvio.tv.core.danexus.RemoteRecommendation
import com.nuvio.tv.core.party.PartyAvatars
import com.nuvio.tv.core.profile.LocalProfileAvatars
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.remote.supabase.AvatarRepository
import com.nuvio.tv.data.repository.MemberAccessRepository
import com.nuvio.tv.domain.model.CosmeticEntitlement
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import java.time.OffsetDateTime
import com.nuvio.tv.R

@HiltViewModel
class DanexusProfilesViewModel @Inject constructor(
    val profileManager: ProfileManager,
    private val watched: WatchedItemsPreferences,
    private val sync: RecommendationSync,
    private val profileSync: ProfileSyncService,
    localAvatars: LocalProfileAvatars,
    avatarRepository: AvatarRepository,
    memberAccess: MemberAccessRepository,
    @ApplicationContext context: Context
) : ViewModel() {
    private val store = DanexusRecommendations(context)
    private val _items = MutableStateFlow(emptyList<ProfileRecommendation>())
    val items: StateFlow<List<ProfileRecommendation>> = _items
    internal val remoteEnabled: Boolean get() = sync.enabled
    private val account = sync.account
    private val remoteProfiles = MutableStateFlow<List<RecommendationProfile>>(emptyList())
    internal val recommendationProfiles = remoteProfiles.asStateFlow()
    private val _viewer = MutableStateFlow<RecommendationProfile?>(null)
    internal val viewer = _viewer.asStateFlow()
    private val _accountEmail = MutableStateFlow<String?>(null)
    internal val accountEmail = _accountEmail.asStateFlow()
    private val _locked = MutableStateFlow(false)
    internal val locked = _locked.asStateFlow()
    private val _busy = MutableStateFlow(false)
    internal val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    internal val message = _message.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val avatarCatalog = memberAccess.access
        .map { it.entitlements.includes(CosmeticEntitlement.PROFILE_AVATARS) }
        .distinctUntilChanged()
        .mapLatest { hasAccess ->
            try {
                avatarRepository.getAvatarCatalog(hasAccess)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        .onStart { emit(emptyList()) }

    internal val profilePictures = combine(profileManager.profiles, localAvatars.avatars, avatarCatalog) { profiles, local, catalog ->
        profiles.mapNotNull { profile ->
            val picture = local[profile.id]?.let(PartyAvatars::imageUri)
                ?: profile.avatarUrl?.takeIf(String::isNotBlank)
                ?: profile.avatarId?.let { avatarRepository.getAvatarImageUrl(it, catalog) }
            picture?.let { profile.id to it }
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private var syncJob: Job? = null
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private var loadedUser: String? = null
    private var syncOwner: String? = null
    private val _syncFailed = MutableStateFlow(false)
    internal val syncFailed = _syncFailed.asStateFlow()

    init {
        viewModelScope.launch {
            combine(account.changes, profileManager.profileSelectionRevision) { owner, _ -> owner }
                .collect { owner ->
                    if (loadedUser != owner) {
                        loadedUser = owner
                        syncOwner = null
                        remoteProfiles.value = emptyList()
                        _syncFailed.value = false
                    }
                    _accountEmail.value = account.email
                    if (owner == null) sync.clearUnlock()
                    refresh()
                    syncRequests.trySend(Unit)
                }
        }
        viewModelScope.launch {
            combine(profileManager.activeProfileId, profileManager.profiles, sync.changes, store.changes) { _, _, _, _ -> Unit }
                .collect { refresh() }
        }
    }

    internal fun clearMessage() { _message.value = null }

    internal fun unlockProfile(pin: String) = accountAction {
        val owner = checkNotNull(account.owner)
        val profile = checkNotNull(currentViewer(owner))
        if (!sync.authorize(owner, profile.id, pin)) {
            _message.value = R.string.d12_wrong_pin
        } else {
            syncRequests.trySend(Unit)
        }
    }

    private fun accountAction(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                sync.operationLock.withLock { action() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.value = R.string.d12_account_error
            } finally {
                _busy.value = false
                _accountEmail.value = account.email
                refresh()
            }
        }
    }

    private suspend fun refreshProfiles(owner: String) {
        val effectiveOwner = account.resolveOwner(owner)
        val profiles = account.profiles(owner)
        if (owner != account.owner) return
        val local = profileManager.activeProfile
        if (local != null && profiles.any { it.index == local.id } && profiles.none { it.id == local.serverProfileId }) {
            profileSync.pullFromRemote(force = true).getOrThrow()
            if (owner != account.owner) return
        }
        loadedUser = owner
        syncOwner = effectiveOwner
        remoteProfiles.value = profiles
        refresh()
    }

    fun refresh() {
        val owner = account.owner
        val active = owner?.let(::currentViewer)
        val permitted = owner != null && active != null && canUseProfile(owner, active)
        _viewer.value = active
        _locked.value = active != null && !permitted
        _items.value = if (!permitted || owner == null || active == null) emptyList() else {
            val profiles = remoteProfiles.value.associateBy(RecommendationProfile::index)
            store.read(cacheOwner(owner)).filter {
                (it.fromServerId == active.id || it.toServerId == active.id) &&
                    (!it.uploadPending || it.fromServerId == active.id) &&
                    (!it.remote || (it.remoteViewerId == active.id && it.remoteChange != "dismiss")) &&
                    matchesProfiles(it, profiles)
            }
        }
    }

    fun startSync() {
        if (!sync.enabled || syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            while (isActive) {
                synchronize()
                withTimeoutOrNull(30_000) { syncRequests.receive() }
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    internal fun loadIndicationsOnce() {
        if (sync.enabled) viewModelScope.launch { synchronize() }
    }

    private suspend fun synchronize() = sync.operationLock.withLock {
        val owner = account.owner ?: return@withLock
        try {
            refreshProfiles(owner)
            val profile = currentViewer(owner) ?: return@withLock
            val viewerId = profile.id
            val revision = profileManager.profileSelectionRevision.value
            fun isCurrentViewer() = account.owner == owner &&
                currentViewer(owner)?.id == viewerId &&
                profileManager.profileSelectionRevision.value == revision && canUseProfile(owner, profile)
            if (!isCurrentViewer()) return@withLock
            val profiles = remoteProfiles.value.associateBy(RecommendationProfile::index)
            val local = withContext(Dispatchers.IO) { store.read(cacheOwner(owner)) }
            val sentKeys = mutableSetOf<String>()
            local.filter {
                it.fromServerId == viewerId && it.uploadPending && matchesProfiles(it, profiles)
            }.forEach { row ->
                if (!isCurrentViewer()) return@withLock
                sync.send(owner, viewerId, row)
                sentKeys += row.key
            }
            local.filter { it.remoteViewerId == viewerId && it.remoteChange != null }.forEach { row ->
                if (!isCurrentViewer()) return@withLock
                val change = checkNotNull(row.remoteChange)
                sync.update(owner, viewerId, change, row.key)
                withContext(Dispatchers.IO) { if (isCurrentViewer()) store.acknowledgeChange(cacheOwner(owner), viewerId, row.key, change) }
            }
            if (!isCurrentViewer()) return@withLock
            val downloaded = sync.list(owner, viewerId, sent = false) + sync.list(owner, viewerId, sent = true)
            if (!isCurrentViewer()) return@withLock
            val byUuid = profiles.values.associateBy(RecommendationProfile::id)
            val rows = downloaded.mapNotNull { it.toLocal(viewerId, byUuid) }
            withContext(Dispatchers.IO) {
                if (isCurrentViewer()) {
                    store.cacheRemote(cacheOwner(owner), viewerId, rows, sentKeys)
                }
            }
            _syncFailed.value = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error.message?.contains("D12_PROFILE_LOCKED") == true) sync.clearUnlock()
            _syncFailed.value = true
        } finally {
            refresh()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun isRecipientWatched(row: ProfileRecommendation): Flow<Boolean> {
        val history = profileManager.activeProfileId.flatMapLatest(watched::observeAllItems)
        return combine(history, viewer, items) { items, profile, visible ->
            row in visible && (row.remoteWatched ||
                (profile?.id == row.toServerId && ProfileRecommendationRules.isWatched(items, row.contentId, row.type, row.episodeKeys)))
        }.distinctUntilChanged()
    }

    internal fun send(expectedSender: String, requested: Map<Int, String>, meta: Meta): Result<Int> = runCatching {
        val owner = checkNotNull(account.owner)
        val active = checkNotNull(currentViewer(owner))
        check(active.id == expectedSender && canUseProfile(owner, active))
        require(ProfileRecommendationRules.isSupportedType(meta.apiType))
        val serverIds = remoteProfiles.value.associate { it.index to it.id }
        check(requested.all { (id, uuid) -> serverIds[id] == uuid })
        val targets = ProfileRecommendationRules.recipients(active.index, requested.keys, serverIds.keys)
        if (targets.isEmpty()) 0
        else store.send(active.index, targets, meta, cacheOwner(owner), serverIds, syncEnabled = true)
            .also {
                refresh()
                loadIndicationsOnce()
            }
    }

    fun open(row: ProfileRecommendation): Boolean {
        val owner = account.owner ?: return false
        val profile = currentViewer(owner) ?: return false
        if (!isCurrent(owner, profile, row)) return false
        if (row.toServerId == profile.id && row.remote) {
            store.changeRemote(cacheOwner(owner), profile.id, row.key, "read")
            syncRequests.trySend(Unit)
        }
        refresh()
        return true
    }

    fun dismiss(row: ProfileRecommendation) {
        val owner = account.owner ?: return
        val profile = currentViewer(owner) ?: return
        if (!isCurrent(owner, profile, row)) return
        if (row.remote) store.changeRemote(cacheOwner(owner), profile.id, row.key, "dismiss")
        else store.dismiss(row.key, profile.index, cacheOwner(owner))
        syncRequests.trySend(Unit)
        refresh()
    }

    private fun isCurrent(owner: String, profile: RecommendationProfile, row: ProfileRecommendation): Boolean =
        canUseProfile(owner, profile) && row in items.value && (row.fromServerId == profile.id || row.toServerId == profile.id)

    private fun currentViewer(owner: String): RecommendationProfile? {
        if (owner != loadedUser || syncOwner == null) return null
        val local = profileManager.activeProfile ?: return null
        val id = local.serverProfileId ?: return null
        return remoteProfiles.value.firstOrNull { it.id == id }
    }

    private fun canUseProfile(owner: String, profile: RecommendationProfile): Boolean =
        !profile.pinEnabled || sync.hasUnlock(owner, profile.id)

    private fun matchesProfiles(row: ProfileRecommendation, profiles: Map<Int, RecommendationProfile>): Boolean =
        profiles[row.from]?.id == row.fromServerId && profiles[row.to]?.id == row.toServerId

    internal fun localProfileFor(profile: RecommendationProfile): UserProfile? {
        if (account.owner == null) return null
        return profileManager.profiles.value.firstOrNull { it.serverProfileId == profile.id }
    }

    private fun RemoteRecommendation.toLocal(viewerId: String, profiles: Map<String, RecommendationProfile>): ProfileRecommendation? {
        val from = profiles[sender] ?: return null
        val to = profiles[recipient] ?: return null
        val time = runCatching { OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrNull() ?: return null
        return ProfileRecommendation(
            key = id, from = from.index, to = to.index, contentId = mediaId, type = mediaType, title = title,
            poster = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }, createdAt = time,
            seen = readAt != null, synopsis = synopsis, episodeKeys = episodeKeys,
            fromServerId = sender, toServerId = recipient, remote = true, remoteWatched = watched, remoteViewerId = viewerId
        )
    }

    private fun cacheOwner(owner: String) = "nuvio-indications:${checkNotNull(syncOwner.takeIf { loadedUser == owner })}"
}
