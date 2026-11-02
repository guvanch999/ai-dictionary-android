package com.sozluk.ai.data.screenshot

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sozluk.ai.domain.screenshot.ScreenshotPathMatcher

/**
 * Finds the newest just-captured screenshot in MediaStore (spec F-6, task 10). Called from the
 * service's `ContentObserver` after debounce: queries images added in the last [WINDOW_SECONDS],
 * newest first, and returns the first whose path/relative-path looks like a screenshot per
 * [ScreenshotPathMatcher]. Returns null when nothing recent matches.
 *
 * Note: `DATE_ADDED` is stored in **seconds**, not millis.
 */
object NewestScreenshotQuery {

    /** How far back to look for a "just now" capture. */
    const val WINDOW_SECONDS = 10L

    private val PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
    ).let { base ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) base + MediaStore.Images.Media.RELATIVE_PATH
        else base
    }

    fun find(resolver: ContentResolver, nowMs: Long): Uri? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cutoffSeconds = nowMs / 1000 - WINDOW_SECONDS
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayOf(cutoffSeconds.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        resolver.query(collection, PROJECTION, selection, args, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val relCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val relativePath = if (relCol >= 0) cursor.getString(relCol) else null
                val absolutePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (ScreenshotPathMatcher.isScreenshot(relativePath) ||
                    ScreenshotPathMatcher.isScreenshot(absolutePath)
                ) {
                    val id = cursor.getLong(idCol)
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        }
        return null
    }
}
