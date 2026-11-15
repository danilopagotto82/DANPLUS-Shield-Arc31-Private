package com.nuvio.tv.core.sync

import com.nuvio.tv.core.danexus.RecommendationAccount
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.SpecialHighlightSelection
import com.nuvio.tv.domain.model.CollectionHighlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpecialHighlightSync @Inject constructor(
    private val account: RecommendationAccount,
    private val profileManager: ProfileManager,
    private val collections: CollectionsDataStore
) {
    private data class Target(val userId: String, val profileId: Int, val serverProfileId: String)
    @Serializable
    private data class RemoteState(val revision: Long, val selection: SpecialHighlightSelection? = null)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val changes = MutableStateFlow(0L)

    init {
        scope.launch {
            combine(account.changes, profileManager.activeProfileId, profileManager.profiles, changes) { user, active, profiles, tick ->
                val remoteId = profiles.firstOrNull { it.id == active }?.serverProfileId
                val target = if (user != null && remoteId != null) Target(user, active, remoteId) else null
                target to tick
            }.distinctUntilChanged().collectLatest { (target, _) ->
                if (target == null) return@collectLatest
                var waitMs = 5_000L
                while (isActive && isCurrent(target)) {
                    val succeeded = synchronize(target)
                    if (succeeded) waitMs = 60_000L
                    delay(waitMs)
                    if (!succeeded) waitMs = (waitMs * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    internal fun refresh() { changes.update { it + 1 } }

    private fun isCurrent(target: Target): Boolean =
        account.owner == target.userId && profileManager.activeProfile?.let {
            it.id == target.profileId && it.serverProfileId == target.serverProfileId
        } == true

    private suspend fun synchronize(target: Target): Boolean = try {
        val local = collections.specialHighlightSnapshot(target.profileId, target.serverProfileId)
        val payload = buildJsonObject {
            local.mutation?.let { mutation ->
                put("mutation", mutation)
                put("expected_revision", local.revision)
                put("selection", local.selection?.let { account.json.encodeToJsonElement(it) } ?: JsonNull)
            }
        }
        val remote = account.decode<RemoteState>(
            account.request(target.userId, "special_highlight", target.serverProfileId, payload)
        )
        check(remote.revision >= 0)
        remote.selection?.let { choice -> check(CollectionHighlight.entries.any { it.name == choice.style }) }
        if (isCurrent(target)) {
            collections.applySpecialHighlight(
                target.profileId, target.serverProfileId, local, remote.revision, remote.selection
            )
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
