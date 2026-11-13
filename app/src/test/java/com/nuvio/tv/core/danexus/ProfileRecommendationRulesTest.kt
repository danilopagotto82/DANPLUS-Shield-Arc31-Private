package com.nuvio.tv.core.danexus

import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.*
import org.junit.Test

class ProfileRecommendationRulesTest {
    private fun row(key: String, from: Int, to: Int, id: String = "film") =
        ProfileRecommendation(key, from, to, id, "movie", "Filme", null, 1L)

    @Test fun receivingProfileSeesOnlyItsOwnRecommendations() {
        val rows = listOf(row("a", 1, 2), row("b", 3, 2), row("c", 1, 3))
        assertEquals(listOf("a", "b"), ProfileRecommendationRules.inbox(rows, 2, false, null).map { it.key })
        assertEquals(listOf("a"), ProfileRecommendationRules.inbox(rows, 2, false, 1).map { it.key })
    }

    @Test fun sentListBelongsToTheActiveSender() {
        val rows = listOf(row("a", 1, 2), row("b", 3, 2), row("c", 1, 3))
        assertEquals(listOf("a", "c"), ProfileRecommendationRules.inbox(rows, 1, true, null).map { it.key })
        assertEquals(listOf("c"), ProfileRecommendationRules.inbox(rows, 1, true, 3).map { it.key })
    }

    @Test fun recommendingAgainReplacesOnlyTheSameSenderRecipientAndTitle() {
        val rows = listOf(row("old", 1, 2), row("other-profile", 1, 3), row("other-film", 1, 2, "second"))
        val updated = ProfileRecommendationRules.deliver(rows, row("new", 1, 2))
        assertEquals(listOf("new", "other-profile", "other-film"), updated.map { it.key })
        assertEquals(rows, ProfileRecommendationRules.deliver(rows, row("self", 1, 1)))
    }

    @Test fun unrelatedProfileCannotReadOrDismissARecommendation() {
        val rows = listOf(row("a", 1, 2))
        assertFalse(ProfileRecommendationRules.markSeen(rows, "a", 3).first().seen)
        assertTrue(ProfileRecommendationRules.markSeen(rows, "a", 2).first().seen)
        assertEquals(rows, ProfileRecommendationRules.dismiss(rows, "a", 3))
        assertTrue(ProfileRecommendationRules.dismiss(rows, "a", 2).isEmpty())
    }

    @Test fun deletingProfilePreventsRecommendationsFromReappearingInReusedSlot() {
        val rows = listOf(row("received", 1, 2), row("sent", 2, 3), row("unrelated", 1, 3))
        assertEquals(listOf("unrelated"), ProfileRecommendationRules.removeProfile(rows, 2).map { it.key })
    }

    @Test fun seriesIsWatchedOnlyWhenTheRecipientCompletedEveryExpectedEpisode() {
        val episodeOne = watched("series", season = 1, episode = 1)
        val episodeTwo = watched("series", season = 1, episode = 2)
        val expected = setOf("1:1", "1:2")

        assertFalse(ProfileRecommendationRules.isWatched(emptyList(), "series", "series", expected))
        assertFalse(ProfileRecommendationRules.isWatched(listOf(episodeOne), "series", "series", expected))
        assertTrue(ProfileRecommendationRules.isWatched(listOf(episodeOne, episodeTwo), "series", "series", expected))
        assertFalse(ProfileRecommendationRules.isWatched(listOf(episodeOne, episodeTwo), "other-series", "series", expected))
    }

    @Test fun movieAndLegacySeriesDoNotInferCompletionFromAnEpisode() {
        val parent = watched("movie")
        val episode = watched("series", season = 1, episode = 1)

        assertTrue(ProfileRecommendationRules.isWatched(listOf(parent), "movie", "movie", emptySet()))
        assertFalse(ProfileRecommendationRules.isWatched(listOf(episode), "series", "series", emptySet()))
        // Unknown episode inventory must not infer that an entire series is watched.
        assertFalse(ProfileRecommendationRules.isWatched(listOf(watched("series")), "series", "series", emptySet()))
    }

    @Test fun receivedAndSentCompletionBothFollowTheRecipientWithoutLeakingTheSender() {
        val recommendation = row("film", from = 1, to = 2)
        assertEquals(2, ProfileRecommendationRules.watchedRecipient(recommendation, 1, setOf(1, 2)))
        assertEquals(2, ProfileRecommendationRules.watchedRecipient(recommendation, 2, setOf(1, 2)))
        assertNull(ProfileRecommendationRules.watchedRecipient(recommendation, 3, setOf(1, 2, 3)))
        assertNull(ProfileRecommendationRules.watchedRecipient(recommendation, 1, setOf(1)))
    }

    private fun watched(contentId: String, season: Int? = null, episode: Int? = null) = WatchedItem(
        contentId = contentId,
        contentType = if (season == null) "movie" else "series",
        title = contentId,
        season = season,
        episode = episode,
        watchedAt = 1L
    )
}
