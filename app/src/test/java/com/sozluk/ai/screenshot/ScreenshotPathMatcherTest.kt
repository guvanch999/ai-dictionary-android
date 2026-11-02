package com.sozluk.ai.screenshot

import com.sozluk.ai.domain.screenshot.ScreenshotPathMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotPathMatcherTest {

    @Test
    fun screenshotPathsFromCommonRoms_matchAcrossVendors() {
        val positives = listOf(
            // Pixel / AOSP absolute path
            "/storage/emulated/0/DCIM/Screenshots/Screenshot_20240101-120000.png",
            // Pixel RELATIVE_PATH form
            "Pictures/Screenshots/",
            // Samsung
            "/storage/emulated/0/DCIM/Screenshots/Screenshot_20240101_120000_Chrome.jpg",
            // Xiaomi / MIUI
            "DCIM/Screenshots/IMG_20240101_120000.png",
            // OnePlus
            "Pictures/Screenshots/Screenshot_2024-01-01.png",
            // Case-insensitivity
            "pictures/screenshots/x.png",
            // ASUS ScreenCapture variant
            "/storage/emulated/0/Pictures/ScreenCapture/img.png",
        )
        positives.forEach { assertTrue("expected screenshot: $it", ScreenshotPathMatcher.isScreenshot(it)) }
    }

    @Test
    fun nonScreenshotPaths_areRejected() {
        val negatives = listOf(
            "/storage/emulated/0/DCIM/Camera/IMG_20240101_120000.jpg",
            "/storage/emulated/0/Download/picture.jpg",
            "Pictures/WhatsApp/IMG-20240101.jpg",
            "/storage/emulated/0/Movies/clip.mp4",
            null,
            "",
        )
        negatives.forEach { assertFalse("expected non-screenshot: $it", ScreenshotPathMatcher.isScreenshot(it)) }
    }

    @Test
    fun everyPattern_matchesAtLeastOnePositiveSample() {
        // Guards against a stale/typo'd pattern that can never match anything.
        val samples = listOf(
            "dcim/screenshots/a.png",
            "pictures/screenshots/a.png",
            "dcim/screenshot/a.png",
            "pictures/screen_shots/a.png",
            "pictures/screencapture/a.png",
        )
        ScreenshotPathMatcher.SCREENSHOT_PATH_PATTERNS.forEach { pattern ->
            assertTrue(
                "pattern never matches any sample: $pattern",
                samples.any { it.contains(pattern) },
            )
        }
    }
}
