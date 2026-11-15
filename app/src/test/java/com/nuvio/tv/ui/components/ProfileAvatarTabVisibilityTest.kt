package com.nuvio.tv.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAvatarTabVisibilityTest {
    @Test
    fun `only the requested tabs are hidden`() {
        listOf("animals", "linear", "originals", "portraits", "sketches", "seketches")
            .forEach { assertTrue("Expected hidden tab: $it", isHiddenProfileAvatarTab(it)) }

        listOf("anime", "animation", "tv", "movie", "gaming", "all", "supporter", "local", "custom")
            .forEach { assertFalse("Unrelated tab must stay: $it", isHiddenProfileAvatarTab(it)) }
    }

    @Test
    fun `category matching ignores casing and surrounding whitespace`() {
        listOf("Animals", "LINEAR", " originals ", "Portraits", " SKETCHES ", "SeKetChes")
            .forEach { assertTrue("Expected hidden alias: $it", isHiddenProfileAvatarTab(it)) }
    }

    @Test
    fun `similar but different category names are never hidden`() {
        listOf("animal", "linear-tv", "original", "portrait", "sketch", "custom_sketches")
            .forEach { assertFalse("Must not hide by approximation: $it", isHiddenProfileAvatarTab(it)) }
    }
}
