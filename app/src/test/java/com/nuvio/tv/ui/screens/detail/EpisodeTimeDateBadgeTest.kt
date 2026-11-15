package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeTimeDateBadgeTest {
    @Test
    fun `duration and original release date appear together`() {
        assertEquals("57m · 12 de outubro de 2023",
            episodeTimeAndDateBadge("57m", "12 de outubro de 2023"))
    }

    @Test
    fun `only valid duration is visible when date is absent`() {
        assertEquals("1h 3m", episodeTimeAndDateBadge("1h 3m", ""))
    }

    @Test
    fun `only date is visible when duration is missing`() {
        assertEquals("12 de outubro de 2023",
            episodeTimeAndDateBadge(null, "12 de outubro de 2023"))
        assertEquals("12 de outubro de 2023",
            episodeTimeAndDateBadge("  ", "12 de outubro de 2023"))
    }

    @Test
    fun `empty values never render an empty badge or a dangling separator`() {
        assertEquals("", episodeTimeAndDateBadge(null, ""))
        assertEquals("", episodeTimeAndDateBadge("  ", "  "))
    }

    @Test
    fun `source metadata is trimmed without changing its words`() {
        assertEquals("57m · 12 de outubro de 2023",
            episodeTimeAndDateBadge(" 57m ", " 12 de outubro de 2023 "))
    }
}
