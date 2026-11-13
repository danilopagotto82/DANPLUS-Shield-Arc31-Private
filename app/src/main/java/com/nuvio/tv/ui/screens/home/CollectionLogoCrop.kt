package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal object CollectionLogoCrop : Transformation() {
    override val cacheKey: String = "collection-logo-alpha-crop-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        val context = coroutineContext
        val crop = collectionLogoBounds(input.width, input.height) { y, row ->
            context.ensureActive()
            input.getPixels(row, 0, input.width, 0, y, input.width, 1)
        } ?: return input
        return Bitmap.createBitmap(input, crop.left, crop.top, crop.width, crop.height)
    }
}

internal data class CollectionLogoBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/** Returns a crop only when the image contains visible artwork surrounded by transparency. */
internal fun collectionLogoBounds(
    width: Int,
    height: Int,
    readRow: (Int, IntArray) -> Unit
): CollectionLogoBounds? {
    val row = IntArray(width)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        readRow(y, row)
        val start = row.indexOfFirst { it ushr 24 > 8 }
        if (start < 0) continue
        val end = row.indexOfLast { it ushr 24 > 8 }
        left = minOf(left, start)
        right = maxOf(right, end)
        top = minOf(top, y)
        bottom = y
    }
    if (right < 0) return null

    // Keep a small margin for anti-aliased edges and soft shadows.
    left = (left - 2).coerceAtLeast(0)
    top = (top - 2).coerceAtLeast(0)
    right = (right + 2).coerceAtMost(width - 1)
    bottom = (bottom + 2).coerceAtMost(height - 1)
    val croppedWidth = right - left + 1
    val croppedHeight = bottom - top + 1
    if (croppedWidth == width && croppedHeight == height) return null
    return CollectionLogoBounds(left, top, croppedWidth, croppedHeight)
}
