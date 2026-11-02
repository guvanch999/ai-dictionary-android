package com.sozluk.ai.domain.image

/**
 * Pure geometry for the annotate/crop flow (spec F-6, step 2), kept out of the UI so it is unit
 * testable. Framework-free types (no Compose/Android) — the screen holds Compose `Offset`/`Size`
 * and adapts to these at the call boundary.
 */

data class PointF(val x: Float, val y: Float)

data class SizeF(val width: Float, val height: Float)

/** Integer crop rectangle in bitmap pixel coordinates. */
data class IntBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Maps a touch [point] in canvas coordinates to bitmap pixel coordinates, undoing the
 * letterboxing of `ContentScale.Fit` (uniform scale + centering), and clamps to the bitmap bounds.
 */
fun screenToBitmap(point: PointF, canvas: SizeF, bitmap: SizeF): PointF {
    if (canvas.width <= 0f || canvas.height <= 0f || bitmap.width <= 0f || bitmap.height <= 0f) {
        return PointF(0f, 0f)
    }
    val scale = minOf(canvas.width / bitmap.width, canvas.height / bitmap.height)
    val displayedWidth = bitmap.width * scale
    val displayedHeight = bitmap.height * scale
    val offsetX = (canvas.width - displayedWidth) / 2f
    val offsetY = (canvas.height - displayedHeight) / 2f

    val bx = ((point.x - offsetX) / scale).coerceIn(0f, bitmap.width)
    val by = ((point.y - offsetY) / scale).coerceIn(0f, bitmap.height)
    return PointF(bx, by)
}

/**
 * Bounding box of [points] (already in bitmap coordinates), grown by [padFraction] of the box's
 * own width/height on each side (spec F-6: ~15% margin for context) and clamped to the bitmap.
 * With fewer than two points the whole bitmap is returned so the crop is never empty.
 */
fun boundingBox(
    points: List<PointF>,
    padFraction: Float,
    bitmap: SizeF,
): IntBox {
    val maxRight = bitmap.width.toInt()
    val maxBottom = bitmap.height.toInt()
    if (points.size < 2) return IntBox(0, 0, maxRight, maxBottom)

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
    }

    val padX = (maxX - minX) * padFraction
    val padY = (maxY - minY) * padFraction

    val left = (minX - padX).coerceIn(0f, bitmap.width)
    val top = (minY - padY).coerceIn(0f, bitmap.height)
    val right = (maxX + padX).coerceIn(0f, bitmap.width)
    val bottom = (maxY + padY).coerceIn(0f, bitmap.height)

    // Guard against a zero-size crop (e.g. a single-point-thin selection).
    val l = left.toInt()
    val t = top.toInt()
    val r = maxOf(right.toInt(), l + 1).coerceAtMost(maxRight)
    val b = maxOf(bottom.toInt(), t + 1).coerceAtMost(maxBottom)
    return IntBox(l, t, r, b)
}
