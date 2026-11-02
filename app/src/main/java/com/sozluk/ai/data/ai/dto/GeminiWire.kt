package com.sozluk.ai.data.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request/response envelopes for the Google Gemini Generative Language API
 * (`POST /v1beta/models/{model}:generateContent`).
 *
 * Serialize these with [com.sozluk.ai.data.ai.geminiJson], which omits nulls — Gemini rejects
 * a part that carries an explicit `"text": null` next to `inlineData`, so unused fields must not
 * be emitted.
 */

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent? = null,
    @SerialName("generationConfig") val generationConfig: GenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

/** One part of a message: either text or inline (base64) image data — never both. */
@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inlineData") val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(
    @SerialName("mimeType") val mimeType: String,
    val data: String,
)

@Serializable
data class GenerationConfig(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    // Ask the model for raw JSON; combined with the prompt this cuts down parse failures.
    @SerialName("responseMimeType") val responseMimeType: String = "application/json",
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("usageMetadata") val usageMetadata: GeminiUsage? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null,
)

/** Token usage reported by Gemini for a single response (spec §10). */
@Serializable
data class GeminiUsage(
    @SerialName("promptTokenCount") val promptTokenCount: Int = 0,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int = 0,
)

/** Error envelope Gemini returns on a non-2xx (used to tell a bad key from a bad request). */
@Serializable
data class GeminiErrorEnvelope(
    val error: GeminiErrorBody? = null,
)

@Serializable
data class GeminiErrorBody(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
)
