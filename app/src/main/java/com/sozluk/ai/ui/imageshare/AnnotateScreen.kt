package com.sozluk.ai.ui.imageshare

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sozluk.ai.R
import com.sozluk.ai.domain.image.PointF
import com.sozluk.ai.domain.image.SizeF
import com.sozluk.ai.domain.image.boundingBox
import com.sozluk.ai.domain.image.screenToBitmap

private const val MARGIN_FRACTION = 0.15f
private val MARKER_COLOR = Color(0xFFFFEB3B).copy(alpha = 0.4f) // translucent highlighter

/**
 * Full-screen editor: the shared image with a finger-drawn highlight over the target word
 * (spec F-6, step 2). On confirm, maps the stroke to bitmap coordinates and emits the padded
 * crop rectangle. No zoom/pan in v1.
 */
@Composable
fun AnnotateScreen(
    bitmap: Bitmap,
    onConfirm: (com.sozluk.ai.domain.image.IntBox) -> Unit,
    onCancel: () -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> points = listOf(start) },
                        onDrag = { change, _ -> points = points + change.position },
                    )
                },
        ) {
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = MARKER_COLOR,
                    style = Stroke(width = 48f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
            }
        }

        // Hint at the top.
        Text(
            text = stringResource(R.string.annotate_hint),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Controls at the bottom.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel), color = Color.White)
            }
            TextButton(onClick = { points = emptyList() }) {
                Text(stringResource(R.string.action_clear), color = Color.White)
            }
            Button(
                onClick = {
                    val mapped = points.map {
                        screenToBitmap(
                            point = PointF(it.x, it.y),
                            canvas = SizeF(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
                            bitmap = SizeF(bitmap.width.toFloat(), bitmap.height.toFloat()),
                        )
                    }
                    val box = boundingBox(
                        points = mapped,
                        padFraction = MARGIN_FRACTION,
                        bitmap = SizeF(bitmap.width.toFloat(), bitmap.height.toFloat()),
                    )
                    onConfirm(box)
                },
                enabled = points.size >= 2,
            ) {
                Text(stringResource(R.string.annotate_confirm))
            }
        }
    }
}
