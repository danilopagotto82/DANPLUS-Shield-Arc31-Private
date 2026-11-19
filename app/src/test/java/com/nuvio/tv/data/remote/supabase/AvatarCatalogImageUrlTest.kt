package com.nuvio.tv.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCatalogImageUrlTest {
    private val backend = "https://api.nuvio.tv"

    @Test
    fun missingConfiguredBaseUsesOfficialStorageBucket() {
        assertEquals(
            "https://api.nuvio.tv/storage/v1/object/public/avatars/users/picture.png",
            avatarCatalogImageUrl("users/picture.png", "", backend)
        )
    }

    @Test
    fun configuredBaseOverridesDerivedUrl() {
        assertEquals(
            "https://cdn.example.test/avatars/users/picture.png",
            avatarCatalogImageUrl("/users/picture.png", " https://cdn.example.test/avatars/ ", backend)
        )
    }

    @Test
    fun absoluteImageUrlIsPreserved() {
        val url = "https://other.example.test/picture.jpg"
        assertEquals(url, avatarCatalogImageUrl(url, null, backend))
    }

    @Test
    fun blankBackendDoesNotCreateInvalidStorageUrl() {
        assertEquals("users/picture.png", avatarCatalogImageUrl("users/picture.png", null, ""))
    }
}
