package com.sozluk.ai.domain.screenshot

/**
 * Trailing-edge debounce for the MediaStore `ContentObserver` (spec F-6, task 10). Some ROMs emit a
 * storm of `onChange` callbacks for a single screenshot capture; this collapses that burst into one
 * action.
 *
 * Pure and clock-injected (mirrors `SrsUpdater { now }`) so it is unit-testable without real time.
 * Usage from the service: call [onEvent] on every `onChange`, then schedule a coroutine that waits
 * [windowMs] and calls [shouldFire]. Only the job scheduled after the final event of a burst sees a
 * quiet window and returns true — earlier jobs observe a newer event time and return false.
 */
class ScreenshotDebouncer(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long,
) {
    private var lastEventAt: Long? = null

    /** Records an incoming observer event at the current clock time. */
    fun onEvent() {
        lastEventAt = now()
    }

    /**
     * True when at least [windowMs] have elapsed since the most recent [onEvent] — i.e. the burst
     * has gone quiet. Returns false if no event was ever recorded.
     */
    fun shouldFire(): Boolean {
        val last = lastEventAt ?: return false
        return now() - last >= windowMs
    }

    companion object {
        /** Quiet period that ends a burst; ~500 ms per task 10. */
        const val DEFAULT_WINDOW_MS = 500L
    }
}
