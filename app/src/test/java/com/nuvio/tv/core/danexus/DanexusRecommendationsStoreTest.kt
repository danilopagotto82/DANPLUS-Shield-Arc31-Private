package com.nuvio.tv.core.danexus

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.tv.domain.model.Meta
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class DanexusRecommendationsStoreTest {
    private class Fixture {
        val context = mockk<Context>()
        private val prefs = mockk<SharedPreferences>()
        private val editor = mockk<SharedPreferences.Editor>()
        var persisted = "[]"
        init {
            every { context.getSharedPreferences("danplus-profile-recommendations", Context.MODE_PRIVATE) } returns prefs
            every { prefs.getString("guest", "[]") } answers { persisted }
            every { prefs.edit() } returns editor
            every { editor.putString("guest", any()) } answers { persisted = secondArg(); editor }
            every { editor.commit() } returns true
        }
        val store = DanexusRecommendations(context)
        fun meta(id: String = "film") = mockk<Meta> {
            every { this@mockk.id } returns id
            every { apiType } returns "movie"
            every { name } returns "Filme"
            every { poster } returns null
            every { background } returns null
            every { landscapePoster } returns null
            every { description } returns null
        }
        fun assertSingleWrite() = verify(exactly = 1) { editor.commit() }
    }

    @Test fun anotherStoreObservesSuccessfulDeliveryAndKeepsBothBoxes() {
        val f = Fixture()
        val inboxStore = DanexusRecommendations(f.context)
        val before = inboxStore.changes.value
        assertEquals(1, f.store.send(1, listOf(2), f.meta()))
        assertTrue(inboxStore.changes.value > before)
        val rows = inboxStore.read()
        assertEquals(rows, ProfileRecommendationRules.inbox(rows, 1, sent = true, person = null))
        assertEquals(rows, ProfileRecommendationRules.inbox(rows, 2, sent = false, person = null))
    }

    @Test fun batchDeliveryPersistsOnceAndSurvivesStoreRecreation() {
        val f = Fixture()
        f.store.send(1, listOf(1, 2, 2, 3), f.meta())
        f.assertSingleWrite()
        val restored = DanexusRecommendations(f.context).read()
        assertEquals(setOf(2, 3), restored.map { it.to }.toSet())
        assertEquals(2, restored.size)
        assertEquals(2, restored.map { it.key }.toSet().size)
    }

    @Test fun resendingBatchReplacesOnlyMatchingRecommendations() {
        val f = Fixture()
        f.store.send(1, listOf(2, 3), f.meta())
        f.store.send(4, 2, f.meta("other"))
        f.store.send(1, listOf(2, 3), f.meta())
        assertEquals(3, DanexusRecommendations(f.context).read().size)
        assertTrue(f.store.read().any { it.from == 4 && it.contentId == "other" })
    }

    @Test fun recipientRemovalPersistsAndDoesNotRemoveOtherRecipients() {
        val f = Fixture()
        f.store.send(1, listOf(2, 3), f.meta())
        val received = f.store.read().single { it.to == 2 }
        f.store.dismiss(received.key, 4)
        assertEquals(2, f.store.read().size)
        f.store.dismiss(received.key, 2)
        val restored = DanexusRecommendations(f.context).read()
        assertEquals(listOf(3), restored.map { it.to })
    }

    @Test fun selectedRecipientsExcludeSenderDuplicatesAndDeletedProfiles() {
        assertEquals(setOf(2, 3), ProfileRecommendationRules.recipients(
            active = 1, requested = listOf(1, 2, 2, 3, 9), available = setOf(1, 2, 3)
        ))
        assertTrue(ProfileRecommendationRules.recipients(1, listOf(1, 9), setOf(1, 2)).isEmpty())
    }

    @Test fun backdropSurvivesRecreationAndLegacyRecordsRemainReadable() {
        val f = Fixture()
        val meta = f.meta()
        every { meta.background } returns "https://images.example/backdrop.jpg"
        f.store.send(1, 2, meta)
        assertEquals("https://images.example/backdrop.jpg", DanexusRecommendations(f.context).read().single().backdrop)
        f.persisted = """[{"key":"legacy","from":1,"to":2,"id":"film","type":"movie","title":"Filme","poster":"","created":1,"seen":false}]"""
        val legacy = DanexusRecommendations(f.context).read().single()
        assertEquals("legacy", legacy.key)
        assertNull(legacy.backdrop)
        assertNull(legacy.synopsis)
        assertTrue(legacy.episodeKeys.isEmpty())
    }

    @Test fun optionalSynopsisSurvivesRecreation() {
        val f = Fixture()
        val meta = f.meta()
        every { meta.description } returns "Uma aventura entre as estrelas."
        f.store.send(1, 2, meta)
        assertEquals(
            "Uma aventura entre as estrelas.",
            DanexusRecommendations(f.context).read().single().synopsis
        )
    }

    @Test fun deliveryCountUsesDistinctRealTargetsAndExcludesTheSender() {
        val f = Fixture()
        assertEquals(2, f.store.send(1, listOf(1, 2, 2, 3), f.meta()))
        assertEquals(0, f.store.send(1, listOf(1), f.meta()))
        assertEquals(2, f.store.read().size)
    }

    @Test fun missingOrBlankBackgroundUsesLandscapeArtwork() {
        val f = Fixture()
        val meta = f.meta()
        every { meta.background } returns ""
        every { meta.landscapePoster } returns "https://images.example/landscape.jpg"
        f.store.send(1, 2, meta)
        assertEquals("https://images.example/landscape.jpg", DanexusRecommendations(f.context).read().single().backdrop)
    }
}
