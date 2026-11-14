package com.nuvio.tv.core.danexus

import android.content.Context
import android.os.SystemClock
import com.nuvio.tv.core.profile.ProfileManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
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
    internal val account: RecommendationAccount,
    private val profiles: ProfileManager
) {
    private data class Unlock(
        val owner: String,
        val profileId: String,
        val profileRevision: Long,
        val proof: String,
        val expiresAt: Long
    )

    @Volatile private var unlock: Unlock? = null
    internal val changes = MutableStateFlow(0L)
    internal val operationLock = Mutex()
    internal val enabled: Boolean get() = account.configured

    internal suspend fun authorize(
        owner: String,
        profileId: String,
        pin: String,
        revision: Long = profiles.profileSelectionRevision.value
    ): Boolean {
        val result = account.decode<RecommendationUnlock>(rpc(owner, profileId, "unlock", buildJsonObject { put("pin", pin) }))
        if (result.unlocked && result.proof != null && owner == account.owner && revision == profiles.profileSelectionRevision.value) {
            unlock = Unlock(owner, profileId, revision, result.proof, SystemClock.elapsedRealtime() + 590_000L)
            changes.value++
            return true
        }
        return false
    }

    internal fun clearUnlock() {
        if (unlock == null) return
        unlock = null
        changes.value++
    }

    internal fun hasUnlock(owner: String, profileId: String): Boolean = proofFor(owner, profileId) != null

    private fun proofFor(owner: String, profileId: String): String? = unlock?.takeIf {
        it.owner == owner && it.profileId == profileId &&
            it.profileRevision == profiles.profileSelectionRevision.value &&
            it.expiresAt > SystemClock.elapsedRealtime()
    }?.proof

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
            val page = account.decode<List<RemoteRecommendation>>(rpc(owner, profileId, "list", buildJsonObject {
                put("box", if (sent) "sent" else "received")
                cursor?.let {
                    put("cursor_id", it.id)
                    put("cursor_time", it.createdAt)
                }
            }))
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
        account.request(owner, operation, profileId, payload, proofFor(owner, profileId))

    private fun tmdbPosterPath(url: String): String? =
        TMDB_POSTER.matchEntire(url)?.groupValues?.get(1)

    companion object {
        private const val LEGACY_BINDING_PREFS = "danplus_indications_bindings"
        private val TMDB_POSTER = Regex("https://image\\.tmdb\\.org/t/p/[a-zA-Z0-9]+(/[a-zA-Z0-9_.-]{1,159})")

        internal fun forgetBinding(context: Context, profileId: Int) {
            val preferences = context.getSharedPreferences(LEGACY_BINDING_PREFS, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            preferences.all.keys.filter { it.startsWith("$profileId:") }.forEach(editor::remove)
            check(editor.commit())
        }

        internal fun forgetBindings(context: Context) {
            check(context.getSharedPreferences(LEGACY_BINDING_PREFS, Context.MODE_PRIVATE).edit().clear().commit())
        }
    }
}
