package com.sozluk.ai.ui.screenshot

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.sozluk.ai.R
import com.sozluk.ai.ui.imageshare.ImageShareActivity
import kotlin.math.abs

/**
 * The floating ✏️ bubble shown after a screenshot is detected (spec F-6, task 10). A lightweight
 * `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`) — deliberately not Compose-in-window to keep
 * it cheap. Appears near the screen edge with a scale/fade-in, auto-hides after [AUTO_HIDE_MS], can
 * be dragged, and on tap launches [ImageShareActivity] with the screenshot Uri then dismisses.
 *
 * Owned by `ScreenshotWatcherService`; the service calls [hide] on teardown.
 */
class PencilBubble(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    private var view: View? = null

    /** Shows the bubble for [uri]. Replaces any bubble already on screen. */
    fun show(uri: Uri) {
        hide()

        val bubble = buildBubbleView(uri)
        val params = layoutParams()

        runCatching { windowManager.addView(bubble, params) }
            .onFailure { return } // overlay permission revoked mid-flight: fail silently
        view = bubble

        animateIn(bubble)
        mainHandler.postDelayed(autoHide, AUTO_HIDE_MS)
    }

    /** Removes the bubble immediately; safe to call when nothing is shown (idempotent). */
    fun hide() {
        mainHandler.removeCallbacks(autoHide)
        view?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        view = null
    }

    private fun buildBubbleView(uri: Uri): View {
        val size = dp(56f)
        val icon = TextView(context).apply {
            text = BUBBLE_GLYPH
            contentDescription = context.getString(R.string.screenshot_bubble_content_description)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(BUBBLE_COLOR)
            }
            elevation = dp(6f).toFloat()
            addView(
                icon,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            attachTouchHandling(this, uri)
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        x = dp(12f)
        y = 0
    }

    /** Distinguishes a tap (open editor) from a drag (reposition the bubble). */
    private fun attachTouchHandling(bubble: View, uri: Uri) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false

        bubble.setOnTouchListener { _, event ->
            val params = bubble.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // gravity is END, so a rightward drag decreases x.
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) dragged = true
                    params.x = startX - dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(bubble, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) openEditor(uri)
                    true
                }

                else -> false
            }
        }
    }

    private fun openEditor(uri: Uri) {
        val intent = Intent(context, ImageShareActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        hide()
    }

    private fun animateIn(v: View) {
        v.alpha = 0f
        v.scaleX = 0.6f
        v.scaleY = 0.6f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        ).toInt()

    private companion object {
        const val AUTO_HIDE_MS = 6_000L
        const val TOUCH_SLOP = 16
        const val BUBBLE_GLYPH = "✏️" // ✏️
        val BUBBLE_COLOR = Color.parseColor("#3F51B5")
    }
}
