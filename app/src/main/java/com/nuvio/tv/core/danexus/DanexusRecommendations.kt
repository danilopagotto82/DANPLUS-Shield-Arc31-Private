package com.nuvio.tv.core.danexus

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nuvio.tv.domain.model.Meta
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ProfileRecommendation(
    val key: String,
    val from: Int,
    val to: Int,
    val contentId: String,
    val type: String,
    val title: String,
    val poster: String?,
    val createdAt: Long,
    val seen: Boolean = false,
    val backdrop: String? = null,
    val synopsis: String? = null,
    val episodeKeys: Set<String> = emptySet(),
    val fromServerId: String? = null,
    val toServerId: String? = null,
    val uploadPending: Boolean = false,
    val remote: Boolean = false,
    val remoteWatched: Boolean = false,
    val remoteViewerId: String? = null,
    val remoteChange: String? = null,
)

/** Shared by local profiles only. Deleting a profile removes its recommendations. */
class DanexusRecommendations(context: Context) {
    private val prefs = context.getSharedPreferences("danplus-profile-recommendations", Context.MODE_PRIVATE)
    val changes = revision.asStateFlow()

    fun read(owner: String = GUEST): List<ProfileRecommendation> = synchronized(lock) {
        runCatching {
            val array = JSONArray(prefs.getString(owner, "[]"))
            (0 until array.length()).mapNotNull { i -> runCatching {
                val r = array.getJSONObject(i)
                ProfileRecommendation(r.getString("key"), r.getInt("from"), r.getInt("to"),
                    r.getString("id"), r.getString("type"), r.getString("title"),
                    r.optString("poster").takeIf { it.isNotBlank() }, r.getLong("created"), r.optBoolean("seen"),
                    r.optString("backdrop").takeIf { it.isNotBlank() },
                    r.optString("synopsis").takeIf { it.isNotBlank() },
                    r.optJSONArray("episodes")?.let { episodes ->
                        (0 until episodes.length()).mapNotNull { index ->
                            episodes.optString(index).takeIf { it.isNotBlank() }
                        }.toSet()
                    } ?: emptySet(),
                    r.optString("fromServerId").takeIf { it.isNotBlank() },
                    r.optString("toServerId").takeIf { it.isNotBlank() },
                    r.optBoolean("uploadPending"),
                    r.optBoolean("remote"),
                    r.optBoolean("remoteWatched"),
                    r.optString("remoteViewerId").takeIf { it.isNotBlank() },
                    r.optString("remoteChange").takeIf { it.isNotBlank() })
                    }.getOrNull() }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun send(from: Int, to: Int, meta: Meta) = send(from, listOf(to), meta)

    fun send(
        from: Int,
        recipients: Collection<Int>,
        meta: Meta,
        owner: String = GUEST,
        serverIds: Map<Int, String?> = emptyMap(),
        syncEnabled: Boolean = false
    ): Int = synchronized(lock) {
        val targets = recipients.distinct().filter { it != from }
        if (targets.isEmpty()) return@synchronized 0
        val createdAt = System.currentTimeMillis()
        val updated = targets.fold(read(owner)) { rows, to ->
            ProfileRecommendationRules.deliver(rows, ProfileRecommendation(
                UUID.randomUUID().toString(), from, to, meta.id, meta.apiType,
                meta.name, meta.poster, createdAt,
                backdrop = meta.background?.takeIf { it.isNotBlank() } ?: meta.landscapePoster?.takeIf { it.isNotBlank() },
                synopsis = meta.description?.takeIf { it.isNotBlank() },
                episodeKeys = ProfileRecommendationRules.episodeKeys(meta),
                fromServerId = serverIds[from],
                toServerId = serverIds[to],
                uploadPending = syncEnabled && serverIds[from] != null && serverIds[to] != null
            ))
        }
        save(owner, updated)
        targets.size
    }
    fun markSeen(key: String, recipient: Int, owner: String = GUEST) = synchronized(lock) {
        save(owner, ProfileRecommendationRules.markSeen(read(owner), key, recipient))
    }
    fun dismiss(key: String, active: Int, owner: String = GUEST) = synchronized(lock) {
        save(owner, ProfileRecommendationRules.dismiss(read(owner), key, active))
    }

    fun cacheRemote(owner: String, viewerId: String, incoming: List<ProfileRecommendation>, sentKeys: Set<String>) = synchronized(lock) {
        val current = read(owner)
        val pending = current.filter { it.remoteViewerId == viewerId && it.remoteChange != null }.associateBy { it.key }
        val changed = incoming.map { row -> pending[row.key]?.let { row.copy(seen = it.seen, remoteChange = it.remoteChange) } ?: row }
        val otherRows = current.filterNot { it.remoteViewerId == viewerId || it.key in sentKeys }
        save(owner, (changed + otherRows).sortedByDescending { it.createdAt })
    }

    fun changeRemote(owner: String, viewerId: String, key: String, change: String?) = synchronized(lock) {
        save(owner, read(owner).map {
            if (it.remoteViewerId == viewerId && it.key == key) {
                it.copy(seen = it.seen || change == "read", remoteChange = change)
            } else it
        })
    }

    fun acknowledgeChange(owner: String, viewerId: String, key: String, change: String) = synchronized(lock) {
        val rows = read(owner)
        save(owner, rows.mapNotNull {
            when {
                it.remoteViewerId != viewerId || it.key != key || it.remoteChange != change -> it
                change == "dismiss" -> null
                else -> it.copy(remoteChange = null)
            }
        })
    }
    fun removeProfile(id: Int) = synchronized(lock) {
        val updates = prefs.all.keys.mapNotNull { owner ->
            val rows = read(owner)
            val kept = ProfileRecommendationRules.removeProfile(rows, id)
            if (kept.size == rows.size) null else owner to encode(kept)
        }
        if (updates.isNotEmpty()) {
            val editor = prefs.edit()
            updates.forEach { (owner, content) -> editor.putString(owner, content) }
            check(editor.commit()) { "Unable to remove profile indications" }
            revision.value++
        }
    }
    /** Avoid showing one signed-out account's indications to the next account. */
    fun clear() = synchronized(lock) {
        check(prefs.edit().clear().commit()) { "Unable to clear indications" }
        revision.value++
    }
    private fun save(owner: String, rows: List<ProfileRecommendation>) {
        check(prefs.edit().putString(owner, encode(rows)).commit()) { "Unable to save indications" }
        revision.value++
    }

    private fun encode(rows: List<ProfileRecommendation>): String {
        val array = JSONArray()
        rows.take(600).forEach { r -> array.put(JSONObject().put("key", r.key).put("from", r.from).put("to", r.to)
            .put("id", r.contentId).put("type", r.type).put("title", r.title).put("poster", r.poster.orEmpty())
            .put("created", r.createdAt).put("seen", r.seen).put("backdrop", r.backdrop.orEmpty())
            .put("synopsis", r.synopsis.orEmpty()).put("episodes", JSONArray(r.episodeKeys.toList()))
            .put("fromServerId", r.fromServerId.orEmpty()).put("toServerId", r.toServerId.orEmpty())
            .put("uploadPending", r.uploadPending).put("remote", r.remote).put("remoteWatched", r.remoteWatched)
            .put("remoteViewerId", r.remoteViewerId.orEmpty()).put("remoteChange", r.remoteChange.orEmpty())) }
        return array.toString()
    }
    companion object {
        const val GUEST = "guest"
        private val lock = Any()
        private val revision = MutableStateFlow(0L)
    }
}
