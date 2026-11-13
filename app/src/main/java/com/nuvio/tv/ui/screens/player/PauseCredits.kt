package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.MetaCastMember
import java.util.Locale

private fun creditName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

internal fun mergePauseCast(primary: List<MetaCastMember>, enrichment: List<MetaCastMember>): List<MetaCastMember> {
    if (primary.isEmpty()) return enrichment
    if (enrichment.isEmpty()) return primary
    val byId = enrichment.mapNotNull { member -> member.tmdbId?.let { it to member } }.toMap()
    val byName = enrichment.associateBy { creditName(it.name) }
    return primary.map { member ->
        val detail = member.tmdbId?.let(byId::get) ?: byName[creditName(member.name)]
        if (detail == null) member else member.copy(
            character = member.character?.takeIf(String::isNotBlank) ?: detail.character,
            photo = member.photo?.takeIf(String::isNotBlank) ?: detail.photo,
            tmdbId = member.tmdbId ?: detail.tmdbId
        )
    }
}

internal fun pauseDirectors(
    names: List<String>,
    credited: List<MetaCastMember>,
    cast: List<MetaCastMember>
): List<MetaCastMember> {
    val photos = cast.associateBy { creditName(it.name) } + credited.associateBy { creditName(it.name) }
    return (names + credited.map { it.name })
        .filter(String::isNotBlank)
        .distinctBy(::creditName)
        .take(3)
        .map { name ->
            val credit = photos[creditName(name)]
            MetaCastMember(name.trim(), photo = credit?.photo, tmdbId = credit?.tmdbId)
        }
}

internal fun pauseActors(cast: List<MetaCastMember>): List<MetaCastMember> =
    cast.filter { it.name.isNotBlank() }.distinctBy { creditName(it.name) }.take(8)
