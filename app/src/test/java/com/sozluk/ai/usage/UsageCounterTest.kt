package com.sozluk.ai.usage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.sozluk.ai.data.prefs.UsageCounterImpl
import com.sozluk.ai.data.prefs.monthKey
import com.sozluk.ai.domain.usage.AiRequestType
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UsageCounterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // Mid-month noon UTC so every device time zone still lands in the same calendar month.
    private val monthA = Instant.parse("2026-08-15T12:00:00Z").toEpochMilli()
    private val monthB = Instant.parse("2026-09-15T12:00:00Z").toEpochMilli()

    private fun TestScope.counterWith(clock: () -> Long): UsageCounterImpl {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            produceFile = { File(tmp.root, "usage.preferences_pb") },
        )
        return UsageCounterImpl(store, clock)
    }

    @Test
    fun record_incrementsTypeAndTokens_forMonthAndTotal() = runTest {
        val counter = counterWith { monthA }
        counter.record(AiRequestType.ARTICLE, 10, 5)
        counter.record(AiRequestType.QUIZ, 1, 1)

        val stats = counter.usage.first()
        assertEquals(1, stats.month.requests(AiRequestType.ARTICLE))
        assertEquals(1, stats.month.requests(AiRequestType.QUIZ))
        assertEquals(2, stats.month.totalRequests)
        assertEquals(17L, stats.month.tokens)
        assertEquals(17L, stats.total.tokens)
        assertEquals(2, stats.total.totalRequests)
    }

    @Test
    fun newMonth_resetsMonthly_butKeepsTotal() = runTest {
        var now = monthA
        val counter = counterWith { now }
        counter.record(AiRequestType.ARTICLE, 10, 5)
        counter.record(AiRequestType.QUIZ, 1, 1)

        now = monthB
        counter.record(AiRequestType.CHECK, 2, 3)

        val stats = counter.usage.first()
        // Monthly bucket only reflects the new month.
        assertEquals(1, stats.month.requests(AiRequestType.CHECK))
        assertEquals(0, stats.month.requests(AiRequestType.ARTICLE))
        assertEquals(0, stats.month.requests(AiRequestType.QUIZ))
        assertEquals(5L, stats.month.tokens)
        // All-time keeps accumulating across the month boundary.
        assertEquals(3, stats.total.totalRequests)
        assertEquals(22L, stats.total.tokens)
    }

    @Test
    fun reset_zeroesMonthlyAndTotal() = runTest {
        val counter = counterWith { monthA }
        counter.record(AiRequestType.VISION, 4, 6)
        counter.reset()

        val stats = counter.usage.first()
        assertEquals(0, stats.month.totalRequests)
        assertEquals(0L, stats.month.tokens)
        assertEquals(0, stats.total.totalRequests)
        assertEquals(0L, stats.total.tokens)
    }

    @Test
    fun monthKey_formatsYearAndMonth() {
        assertTrue(monthKey(monthA).matches(Regex("""\d{4}-\d{2}""")))
        assertEquals(monthKey(monthA), monthKey(monthA))
    }
}
