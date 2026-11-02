package com.sozluk.ai.domain.screenshot

/**
 * Pure test for "is this MediaStore image a system screenshot?" (spec F-6, task 10).
 *
 * ROMs place screenshots in different folders (`DCIM/Screenshots`, `Pictures/Screenshots`, some
 * MIUI builds under `DCIM/Screenshots` too), so the check is a case-insensitive substring match
 * against [SCREENSHOT_PATH_PATTERNS] over either the absolute path (`MediaStore.Images.Media.DATA`)
 * or the `RELATIVE_PATH`. Kept free of Android deps so it is unit-testable.
 */
object ScreenshotPathMatcher {

    /** Folder-name fragments that identify a screenshot across the common ROMs. */
    val SCREENSHOT_PATH_PATTERNS: List<String> = listOf(
        "screenshots",   // Pixel/AOSP/Samsung/OnePlus "…/Screenshots/…", plural
        "screenshot",    // some ROMs use the singular folder or file prefix
        "screen_shots",  // rare underscore variant seen on a few OEM builds
        "screencapture", // ASUS/others "ScreenCapture"
    )

    /** True when [pathOrRelativePath] points into a screenshots folder on any supported ROM. */
    fun isScreenshot(pathOrRelativePath: String?): Boolean {
        val value = pathOrRelativePath?.lowercase() ?: return false
        if (value.isEmpty()) return false
        return SCREENSHOT_PATH_PATTERNS.any { value.contains(it) }
    }
}
