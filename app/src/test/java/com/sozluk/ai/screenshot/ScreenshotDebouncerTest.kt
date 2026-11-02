package com.sozluk.ai.screenshot

import com.sozluk.ai.domain.screenshot.ScreenshotDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDebouncerTest {

    private var now = 0L
    private val debouncer = ScreenshotDebouncer(windowMs = 500L) { now }

    @Test
    fun burstWithinWindow_collapsesToSingleFire() {
        // Three onChange events within 500ms (a MediaStore storm for one capture).
        now = 0L; debouncer.onEvent()
        now = 100L; debouncer.onEvent()
        now = 200L; debouncer.onEvent()

        // Each event schedules a check at eventTime+window; only the last one finds a quiet window.
        var fires = 0
        now = 500L; if (debouncer.shouldFire()) fires++ // job from t=0
        now = 600L; if (debouncer.shouldFire()) fires++ // job from t=100
        now = 700L; if (debouncer.shouldFire()) fires++ // job from t=200 (last)

        assertEquals(1, fires)
    }

    @Test
    fun eventsFartherApartThanWindow_fireTwice() {
        var fires = 0
        now = 0L; debouncer.onEvent()
        now = 500L; if (debouncer.shouldFire()) fires++

        now = 1000L; debouncer.onEvent()
        now = 1500L; if (debouncer.shouldFire()) fires++

        assertEquals(2, fires)
    }

    @Test
    fun fires_exactlyAtWindowEdge_notBeforeWindowElapses() {
        now = 0L; debouncer.onEvent()
        now = 499L; assertFalse(debouncer.shouldFire())
        now = 500L; assertTrue(debouncer.shouldFire())
    }

    @Test
    fun neverFires_withoutAnyEvent() {
        now = 10_000L
        assertFalse(debouncer.shouldFire())
    }
}
