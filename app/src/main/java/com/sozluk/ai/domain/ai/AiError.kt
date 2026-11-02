package com.sozluk.ai.domain.ai

/**
 * Typed failures for [AiClient] operations. Extends [Exception] so instances fit into
 * [kotlin.Result.failure]. Never carries the API key.
 */
sealed class AiError(message: String? = null) : Exception(message) {
    /** No API key stored — the request is not sent. */
    data object NoApiKey : AiError("No API key configured")

    /** Network failure (timeout / IO), retried once before surfacing. */
    data object Network : AiError("Network error")

    /** HTTP 429; [retryAfterSeconds] from the Retry-After header when present. */
    data class RateLimited(val retryAfterSeconds: Long? = null) : AiError("Rate limited")

    /** HTTP 401 — the stored key is invalid. */
    data object InvalidKey : AiError("Invalid API key")

    /** Model returned something that is not parseable JSON after one retry. */
    data object InvalidResponse : AiError("Invalid AI response")

    /** Anything else. */
    data class Unknown(val detail: String) : AiError(detail)
}
