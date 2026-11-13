package com.nuvio.tv.core.danexus

import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.domain.model.WatchedItem

internal object ProfileRecommendationRules {
    fun belongsToCurrentProfiles(row: ProfileRecommendation, profiles: Map<Int, UserProfile>): Boolean =
        profiles[row.from]?.let { it.serverProfileId == row.fromServerId } == true &&
            profiles[row.to]?.let { it.serverProfileId == row.toServerId } == true

    fun isSupportedType(type: String): Boolean = type.equals("movie", ignoreCase = true) || isSeries(type)

    fun recipients(active: Int, requested: Collection<Int>, available: Set<Int>): Set<Int> =
        requested.filterTo(linkedSetOf()) { it != active && it in available }

    fun inbox(rows: List<ProfileRecommendation>, active: Int, sent: Boolean, person: Int?) =
        rows.filter { row ->
            if (sent) row.from == active && (person == null || row.to == person)
            else row.to == active && (person == null || row.from == person)
        }

    fun deliver(rows: List<ProfileRecommendation>, item: ProfileRecommendation): List<ProfileRecommendation> {
        if (item.from == item.to) return rows
        return listOf(item) + rows.filterNot {
            it.from == item.from && it.to == item.to && it.contentId == item.contentId && it.type == item.type
        }
    }

    fun watchedRecipient(row: ProfileRecommendation, active: Int, available: Set<Int>): Int? =
        row.to.takeIf { it in available && (row.from == active || row.to == active) }

    fun episodeKeys(meta: Meta): Set<String> {
        if (!isSeries(meta.apiType)) return emptySet()
        return meta.watchableEpisodes().mapNotNull { video ->
            val season = video.season ?: return@mapNotNull null
            val episode = video.episode ?: return@mapNotNull null
            "$season:$episode"
        }.toSet()
    }

    fun isWatched(
        items: List<WatchedItem>,
        contentId: String,
        type: String,
        episodeKeys: Set<String>
    ): Boolean {
        val contentItems = items.filter {
            it.contentId == contentId && isSeries(it.contentType) == isSeries(type)
        }
        if (!isSeries(type)) return contentItems.any { it.season == null && it.episode == null }
        if (episodeKeys.isEmpty()) return false
        val watchedEpisodes = contentItems.mapNotNull { item ->
            val season = item.season ?: return@mapNotNull null
            val episode = item.episode ?: return@mapNotNull null
            "$season:$episode"
        }.toSet()
        return episodeKeys.all { it in watchedEpisodes }
    }

    private fun isSeries(type: String): Boolean =
        type.lowercase() in setOf("series", "tv", "show")

    fun markSeen(rows: List<ProfileRecommendation>, key: String, active: Int) =
        rows.map { if (it.key == key && it.to == active) it.copy(seen = true) else it }

    fun dismiss(rows: List<ProfileRecommendation>, key: String, active: Int) =
        rows.filterNot { it.key == key && (it.to == active || it.from == active) }

    fun removeProfile(rows: List<ProfileRecommendation>, id: Int) = rows.filterNot { it.from == id || it.to == id }
}
