package com.nuvio.tv.core.danexus

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.domain.model.UserProfile
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CancellationException
import android.os.SystemClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class RemoteRecommendation(
    val id: String,
    @SerialName("sender_id") val sender: String,
    @SerialName("recipient_id") val recipient: String,
    @SerialName("media_id") val mediaId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("poster_path") val posterPath: String? = null,
    val synopsis: String? = null,
    @SerialName("episode_keys") val episodeKeys: Set<String> = emptySet(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
    val watched: Boolean = false
)

@Serializable
internal data class RecommendationUnlock(val unlocked: Boolean, val proof: String? = null)

@Singleton
class RecommendationSync @Inject constructor(
    private val postgrest: Postgrest,
    private val authManager: AuthManager
) {
    private data class Unlock(val owner: String, val profileId: String, val proof: String, val expiresAt: Long)

    @Volatile private var unlock: Unlock? = null
    internal val enabled: Boolean get() = BuildConfig.DANPLUS_D12_SYNC_ENABLED

    internal suspend fun authorize(profile: UserProfile, pin: String) {
        if (!enabled) return
        val owner = authManager.currentUserId ?: return
        val profileId = profile.serverProfileId ?: return
        unlock = null
        // This is optional for native profile selection. The remote list remains locked if it fails.
        try {
            val result = rpc(owner, profileId, "unlock", buildJsonObject { put("pin", pin) })
                .decodeAs<RecommendationUnlock>()
            if (result.unlocked && result.proof != null && owner == authManager.currentUserId) {
                unlock = Unlock(owner, profileId, result.proof, SystemClock.elapsedRealtime() + 590_000L)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Native profile selection remains available.
        }
    }

    internal fun clearUnlock() {
        unlock = null
    }

    internal fun retainUnlock(profileId: String?) {
        if (unlock?.profileId != profileId) clearUnlock()
    }

    internal fun hasUnlock(owner: String, profileId: String?): Boolean = unlock?.let {
        it.owner == owner && it.profileId == profileId && it.expiresAt > SystemClock.elapsedRealtime()
    } == true

    internal suspend fun send(owner: String, profileId: String, row: ProfileRecommendation) {
        val recipient = checkNotNull(row.toServerId)
        val mediaType = if (row.type.equals("movie", ignoreCase = true)) "movie" else "series"
        rpc(owner, profileId, "send", buildJsonObject {
            put("request_id", row.key)
            put("recipients", buildJsonArray { add(JsonPrimitive(recipient)) })
            put("media_id", row.contentId)
            put("media_type", mediaType)
            put("title", row.title.take(350))
            row.synopsis?.let { put("synopsis", it.take(2000)) }
            row.poster?.let(::tmdbPosterPath)?.let { put("poster_path", it) }
            put("episode_keys", buildJsonArray { row.episodeKeys.forEach { add(JsonPrimitive(it)) } })
        })
    }

    internal suspend fun list(owner: String, profileId: String, sent: Boolean): List<RemoteRecommendation> {
        val rows = mutableListOf<RemoteRecommendation>()
        var cursor: RemoteRecommendation? = null
        repeat(12) {
            val page = rpc(owner, profileId, "list", buildJsonObject {
                put("box", if (sent) "sent" else "received")
                cursor?.let {
                    put("cursor_id", it.id)
                    put("cursor_time", it.createdAt)
                }
            }).decodeList<RemoteRecommendation>()
            rows.addAll(page)
            if (page.size < 50) return rows
            cursor = page.last()
        }
        return rows
    }

    internal suspend fun update(owner: String, profileId: String, operation: String, id: String) {
        rpc(owner, profileId, operation, buildJsonObject { put("id", id) })
    }

    private suspend fun rpc(owner: String, profileId: String, operation: String, payload: JsonObject) =
        postgrest.rpc("recommendations_v1", buildJsonObject {
            check(enabled && authManager.currentUserId == owner && authManager.hasLiveSession())
            put("p_operation", operation)
            put("p_profile_id", profileId)
            put("p_payload", payload)
            unlock?.takeIf {
                it.owner == owner && it.profileId == profileId && it.expiresAt > SystemClock.elapsedRealtime()
            }?.let { put("p_proof", it.proof) }
        })

    private fun tmdbPosterPath(url: String): String? =
        TMDB_POSTER.matchEntire(url)?.groupValues?.get(1)

    private companion object {
        val TMDB_POSTER = Regex("https://image\\.tmdb\\.org/t/p/[a-zA-Z0-9]+(/[a-zA-Z0-9_.-]{1,159})")
    }
}
