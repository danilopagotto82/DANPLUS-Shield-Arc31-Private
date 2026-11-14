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
    val details = (cast + credited).associateBy { creditName(it.name) }
    return (names + credited.map { it.name })
        .filter(String::isNotBlank)
        .distinctBy(::creditName)
        .map { name ->
            val credit = details[creditName(name)]
            MetaCastMember(name.trim(), photo = credit?.photo, tmdbId = credit?.tmdbId)
        }
        .distinctBy { it.tmdbId ?: creditName(it.name) }
        .take(3)
}

internal fun pauseActors(cast: List<MetaCastMember>, directors: List<MetaCastMember>): List<MetaCastMember> {
    val directorIds = directors.mapNotNull { it.tmdbId }.toSet()
    val directorNames = directors.map { creditName(it.name) }.toSet()
    return cast.filter { member ->
        member.name.isNotBlank() && member.tmdbId !in directorIds && creditName(member.name) !in directorNames
    }.distinctBy { it.tmdbId ?: creditName(it.name) }.take(8)
}