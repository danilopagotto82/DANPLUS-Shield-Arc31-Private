package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview

private val releaseYearPattern = Regex("""\b(?:18|19|20|21)\d{2}\b""")

internal fun MetaPreview.matchesDiscoverFilters(
    year: Int?,
    status: DiscoverWatchFilter,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>
): Boolean {
    if (year != null) {
        val releaseYear = releaseInfo?.let { releaseYearPattern.find(it)?.value }
            ?: released?.let { releaseYearPattern.find(it)?.value }
        if (releaseYear?.toIntOrNull() != year) return false
    }
    return when (status) {
        DiscoverWatchFilter.ALL -> true
        DiscoverWatchFilter.WATCHED -> isWatchedInDiscover(watchedMovieIds, watchedSeriesIds)
        DiscoverWatchFilter.UNWATCHED -> !isWatchedInDiscover(watchedMovieIds, watchedSeriesIds)
    }
}

internal fun MetaPreview.isWatchedInDiscover(watchedMovieIds: Set<String>, watchedSeriesIds: Set<String>): Boolean {
    val watchedIds = if (type == ContentType.SERIES || type == ContentType.TV || apiType.equals("series", true) ||
        apiType.equals("tv", true) || apiType.equals("show", true)) watchedSeriesIds else watchedMovieIds
    return id in watchedIds || imdbId?.let { it in watchedIds } == true
}
