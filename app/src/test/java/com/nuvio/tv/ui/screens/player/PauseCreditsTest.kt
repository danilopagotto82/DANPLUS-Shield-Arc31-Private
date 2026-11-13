package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.MetaCastMember
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseCreditsTest {
    @Test
    fun repeatedDirectorHasOneCardAndKeepsTheEnrichedPortrait() {
        val names = listOf("David Robert Mitchell", " david  robert mitchell ", "David Robert Mitchell")
        val credited = listOf(MetaCastMember("David Robert Mitchell", photo = "portrait"))

        assertEquals(credited, pauseDirectors(names, credited, emptyList()))
    }

    @Test
    fun actorWithAnExistingPortraitGetsTheCharacterFromTheExistingEnrichment() {
        val actor = MetaCastMember("Anne Hathaway", photo = "original", tmdbId = 1)
        val enriched = actor.copy(character = "Personagem", photo = "new")

        assertEquals(listOf(actor.copy(character = "Personagem")), mergePauseCast(listOf(actor), listOf(enriched)))
    }
}
