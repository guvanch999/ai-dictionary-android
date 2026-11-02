package com.sozluk.ai.data.ai

import kotlinx.serialization.json.Json

/**
 * Shared JSON for the AI layer — wire (de)serialization of Anthropic envelopes and lenient
 * parsing of model output. Mirrors [com.sozluk.ai.data.db.dbJson].
 */
internal val aiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * JSON for the Gemini wire format. Same as [aiJson] but with [explicitNulls] off: a Gemini message
 * part carries either `text` OR `inlineData`, and emitting the unused one as `null` makes the API
 * reject the request — so null properties must be omitted, not serialized.
 */
internal val geminiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
