package com.sozluk.ai.domain.usage

import kotlinx.coroutines.flow.Flow

/** The four billed AI operations we count separately (spec §10, task 11C). */
enum class AiRequestType { ARTICLE, QUIZ, VISION, CHECK }

/** Request counts (per type) and total tokens for one period (a month, or all time). */
data class UsageBucket(
    val requestsByType: Map<AiRequestType, Int> = emptyMap(),
    val tokens: Long = 0,
) {
    val totalRequests: Int get() = requestsByType.values.sum()
    fun requests(type: AiRequestType): Int = requestsByType[type] ?: 0
}

/** Current-month and all-time usage. */
data class UsageStats(
    val month: UsageBucket = UsageBucket(),
    val total: UsageBucket = UsageBucket(),
)

/**
 * Counts successful AI requests and their token spend (spec §10). Incremented from
 * [com.sozluk.ai.data.ai.AnthropicAiClient] after every billed response; surfaced in Settings.
 * Monthly counters roll over automatically when the calendar month changes.
 */
interface UsageCounter {
    val usage: Flow<UsageStats>

    /** Record one billed response of [type] with its reported input/output tokens. */
    suspend fun record(type: AiRequestType, inputTokens: Int, outputTokens: Int)

    /** Zero both the monthly and all-time counters. */
    suspend fun reset()
}
