package com.nuvio.tv.core.scout

import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.model.scoutExclusionName

internal data class ScoutWatchedMovies(
    val ids: Set<String> = emptySet(),
    val namesAndYears: Set<String> = emptySet()
) {
    fun contains(id: String, name: String, year: String?, imdb: String? = null): Boolean {
        if (scoutMovieId(id) in ids || imdb?.let { scoutMovieId(it) in ids } == true) return true
        val key = scoutExclusionName(name, year)
        return '|' in key && key in namesAndYears
    }
}

internal fun scoutMovieId(id: String): String {
    val clean = id.trim().lowercase().removePrefix("movie:").removePrefix("imdb:")
    return clean.removePrefix("tmdb:").toIntOrNull()?.takeIf { it > 0 }?.let { "tmdb:$it" } ?: clean
}

internal fun scoutWatchedMovies(
    history: List<WatchedItem>,
    marked: Set<String>,
    imdbByTmdb: Map<String, String>
): ScoutWatchedMovies {
    val films = history.filter { it.contentType.equals("movie", true) && it.season == null && it.episode == null }
    val ids = (marked.asSequence() + films.asSequence().map { it.contentId }).map(::scoutMovieId).toMutableSet()
    for ((key, rawImdb) in imdbByTmdb) {
        if (!key.endsWith(":movie")) continue
        val imdb = scoutMovieId(rawImdb)
        val tmdb = scoutMovieId(key.substringBefore(':'))
        if (imdb in ids || tmdb in ids) {
            ids += imdb
            ids += tmdb
        }
    }
    return ScoutWatchedMovies(ids, films.map { scoutExclusionName(it.title, it.releaseInfo) }.filter { '|' in it }.toSet())
}
