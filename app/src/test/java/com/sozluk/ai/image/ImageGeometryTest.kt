package com.sozluk.ai.image

import com.sozluk.ai.domain.image.PointF
import com.sozluk.ai.domain.image.SizeF
import com.sozluk.ai.domain.image.boundingBox
import com.sozluk.ai.domain.image.screenToBitmap
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGeometryTest {

    // --- screenToBitmap: undo ContentScale.Fit letterboxing ---

    @Test
    fun screenToBitmap_horizontalLetterbox_centersAndUnscales() {
        // 100x100 bitmap in a 200x100 canvas -> scale 1, 50px bars left/right.
        val p = screenToBitmap(PointF(100f, 50f), SizeF(200f, 100f), SizeF(100f, 100f))
        assertEquals(50f, p.x, 0.001f)
        assertEquals(50f, p.y, 0.001f)
    }

    @Test
    fun screenToBitmap_verticalLetterbox_centersAndUnscales() {
        // 100x100 bitmap in a 100x200 canvas -> scale 1, 50px bars top/bottom.
        val p = screenToBitmap(PointF(50f, 100f), SizeF(100f, 200f), SizeF(100f, 100f))
        assertEquals(50f, p.x, 0.001f)
        assertEquals(50f, p.y, 0.001f)
    }

    @Test
    fun screenToBitmap_scaledUp_dividesByScale() {
        // 100x50 bitmap in a 200x200 canvas -> scale 2, vertical bars of 50px.
        val p = screenToBitmap(PointF(100f, 100f), SizeF(200f, 200f), SizeF(100f, 50f))
        assertEquals(50f, p.x, 0.001f)
        assertEquals(25f, p.y, 0.001f)
    }

    @Test
    fun screenToBitmap_outsideImage_clampsToBounds() {
        // Touch inside the left bar maps to x=0; top-left corner clamps to (0,0).
        val p = screenToBitmap(PointF(0f, 0f), SizeF(200f, 100f), SizeF(100f, 100f))
        assertEquals(0f, p.x, 0.001f)
        assertEquals(0f, p.y, 0.001f)
    }

    // --- boundingBox: 15% margin, clamped ---

    @Test
    fun boundingBox_addsMarginPerSide() {
        val box = boundingBox(
            points = listOf(PointF(20f, 20f), PointF(40f, 60f)),
            padFraction = 0.15f,
            bitmap = SizeF(100f, 100f),
        )
        // width 20 -> padX 3; height 40 -> padY 6.
        assertEquals(17, box.left)
        assertEquals(14, box.top)
        assertEquals(43, box.right)
        assertEquals(66, box.bottom)
    }

    @Test
    fun boundingBox_clampsToImageBounds() {
        val box = boundingBox(
            points = listOf(PointF(5f, 5f), PointF(95f, 95f)),
            padFraction = 0.15f,
            bitmap = SizeF(100f, 100f),
        )
        assertEquals(0, box.left)
        assertEquals(0, box.top)
        assertEquals(100, box.right)
        assertEquals(100, box.bottom)
    }

    @Test
    fun boundingBox_tooFewPoints_returnsWholeImage() {
        val box = boundingBox(emptyList(), 0.15f, SizeF(120f, 80f))
        assertEquals(0, box.left)
        assertEquals(0, box.top)
        assertEquals(120, box.right)
        assertEquals(80, box.bottom)
    }
}
