package com.sozluk.ai.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sozluk.ai.domain.usage.AiRequestType
import com.sozluk.ai.domain.usage.UsageBucket
import com.sozluk.ai.domain.usage.UsageCounter
import com.sozluk.ai.domain.usage.UsageStats
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Calendar-month key ("YYYY-MM") for [millis] in the device time zone. */
internal fun monthKey(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%04d-%02d".format(date.year, date.monthValue)
}

/**
 * DataStore-backed [UsageCounter] (spec §10). Reuses the shared settings DataStore. The monthly
 * bucket resets lazily: the stored month is compared with the current month on every read and
 * write, so the visible monthly numbers drop to zero as soon as a new month begins.
 *
 * [clock] is injected only for tests; production uses the wall clock via the [Inject] constructor.
 */
class UsageCounterImpl(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long,
) : UsageCounter {

    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, { System.currentTimeMillis() })

    override val usage: Flow<UsageStats> = dataStore.data.map { prefs ->
        val monthActive = prefs[KEY_MONTH] == monthKey(clock())
        UsageStats(
            month = UsageBucket(
                requestsByType = AiRequestType.entries.associateWith {
                    if (monthActive) prefs[monthReqKey(it)] ?: 0 else 0
                },
                tokens = if (monthActive) prefs[KEY_MONTH_TOKENS] ?: 0L else 0L,
            ),
            total = UsageBucket(
                requestsByType = AiRequestType.entries.associateWith { prefs[totalReqKey(it)] ?: 0 },
                tokens = prefs[KEY_TOTAL_TOKENS] ?: 0L,
            ),
        )
    }

    override suspend fun record(type: AiRequestType, inputTokens: Int, outputTokens: Int) {
        val month = monthKey(clock())
        val tokens = (inputTokens + outputTokens).toLong()
        dataStore.edit { prefs ->
            if (prefs[KEY_MONTH] != month) {
                // New month: clear the monthly bucket before adding this request.
                prefs[KEY_MONTH] = month
                AiRequestType.entries.forEach { prefs[monthReqKey(it)] = 0 }
                prefs[KEY_MONTH_TOKENS] = 0L
            }
            prefs[monthReqKey(type)] = (prefs[monthReqKey(type)] ?: 0) + 1
            prefs[KEY_MONTH_TOKENS] = (prefs[KEY_MONTH_TOKENS] ?: 0L) + tokens
            prefs[totalReqKey(type)] = (prefs[totalReqKey(type)] ?: 0) + 1
            prefs[KEY_TOTAL_TOKENS] = (prefs[KEY_TOTAL_TOKENS] ?: 0L) + tokens
        }
    }

    override suspend fun reset() {
        dataStore.edit { prefs ->
            AiRequestType.entries.forEach {
                prefs[monthReqKey(it)] = 0
                prefs[totalReqKey(it)] = 0
            }
            prefs[KEY_MONTH_TOKENS] = 0L
            prefs[KEY_TOTAL_TOKENS] = 0L
            prefs[KEY_MONTH] = monthKey(clock())
        }
    }

    private companion object {
        val KEY_MONTH = stringPreferencesKey("usage_month")
        val KEY_MONTH_TOKENS = longPreferencesKey("usage_m_tokens")
        val KEY_TOTAL_TOKENS = longPreferencesKey("usage_t_tokens")
        fun monthReqKey(t: AiRequestType) = intPreferencesKey("usage_m_req_${t.name}")
        fun totalReqKey(t: AiRequestType) = intPreferencesKey("usage_t_req_${t.name}")
    }
}
