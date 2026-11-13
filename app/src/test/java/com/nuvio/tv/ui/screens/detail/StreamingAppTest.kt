package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.MetaLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingAppTest {
    @Test
    fun acceptsNetflixTitleAndDropsTracking() {
        val url = "https://www.netflix.com/br/title/80057281"
        assertEquals(url, streamingTitleLink(StreamingApp.NETFLIX, listOf(link("$url?tracking=1#fragment"))))
    }

    @Test
    fun rejectsAnUnrelatedHostAndSearchPages() {
        assertNull(streamingTitleLink(StreamingApp.NETFLIX, listOf(
            link("https://www.netflix.com.example.com/title/80057281"),
            link("https://www.netflix.com/search?q=Stranger+Things"),
            link("https://www.themoviedb.org/tv/66732/watch"),
            link("http://www.netflix.com/title/80057281")
        )))
    }

    @Test
    fun routesPrimeChannelsToPrimeAndUsesFireTvPackage() {
        val app = StreamingApp.forProvider(2156)
        assertEquals(StreamingApp.PRIME, app)
        assertEquals("com.amazon.firebat", app?.packages(fireTv = true)?.first())
        assertEquals("com.amazon.amazonvideo.livingroom", app?.packages(fireTv = false)?.first())
    }

    private fun link(url: String) = MetaLink(name = "Provider", category = "Streaming", url = url)
}
