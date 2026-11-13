package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionLogoCropTest {
    @Test
    fun paddedWideAndTallLogosBothUseTheVisibleArtwork() {
        assertEquals(CollectionLogoBounds(6, 8, 36, 12), bounds(48, 28, 8..39, 10..17))
        assertEquals(CollectionLogoBounds(9, 3, 14, 32), bounds(32, 40, 11..20, 5..32))
    }

    @Test
    fun opaqueAndEmptyImagesKeepTheirOriginalBounds() {
        assertNull(bounds(12, 8, 0..11, 0..7))
        assertNull(bounds(12, 8, IntRange.EMPTY, IntRange.EMPTY))
    }

    private fun bounds(width: Int, height: Int, visibleX: IntRange, visibleY: IntRange) =
        collectionLogoBounds(width, height) { y, row ->
            for (x in row.indices) {
                row[x] = if (y in visibleY && x in visibleX) -1 else 0x04000000
            }
        }
}
