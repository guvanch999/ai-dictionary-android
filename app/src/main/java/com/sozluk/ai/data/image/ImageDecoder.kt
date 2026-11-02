package com.sozluk.ai.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Decodes a shared image [Uri] into a memory-safe [Bitmap] (spec F-6, step 1: downsample so the
 * long side is <= [MAX_LONG_SIDE] — a 12 MP photo must not OOM). Runs off the main thread at the
 * call site.
 */
class ImageDecoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Returns the downsampled bitmap, or null if [uri] can't be read/decoded. */
    fun decodeDownsampled(uri: Uri, maxLongSide: Int = MAX_LONG_SIDE): Bitmap? {
        val resolver = context.contentResolver

        // Pass 1: read only the bounds to pick an integer sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxLongSide)
        }

        // Pass 2: decode the real (downsampled) bitmap.
        return try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_LONG_SIDE = 2048
    }
}

/** Smallest power-of-two sample size that keeps the long side within [maxLongSide]. */
internal fun sampleSizeFor(width: Int, height: Int, maxLongSide: Int): Int {
    var sample = 1
    var longSide = maxOf(width, height)
    while (longSide / 2 >= maxLongSide) {
        longSide /= 2
        sample *= 2
    }
    return sample
}

/** Encodes a bitmap to JPEG bytes (spec F-6, step 3: quality ~85 for the vision upload). */
fun Bitmap.toJpeg(quality: Int = 85): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
