package com.sozluk.ai.data.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request/response envelopes for the Anthropic Messages API (`POST /v1/messages`). */

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<WireMessage>,
)

@Serializable
data class WireMessage(
    val role: String,
    val content: String,
)

@Serializable
data class MessagesResponse(
    val content: List<ContentBlock> = emptyList(),
    val usage: Usage? = null,
)

/** Token usage reported by the Messages API for a single response (spec §10). */
@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String? = null,
)

/**
 * Vision request envelope (spec F-6). Kept separate from [MessagesRequest] so the text path's
 * plain-string `content` wire format is untouched; here `content` is an array of typed blocks
 * (image + text), which is what the Messages API expects for vision.
 */
@Serializable
data class VisionRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<VisionMessage>,
)

@Serializable
data class VisionMessage(
    val role: String,
    val content: List<ContentPart>,
)

/**
 * One block of a vision message. The `"type"` field the API requires ("text"/"image") is emitted
 * automatically as the polymorphic class discriminator from each subclass's [SerialName] — do not
 * add an explicit `type` property (it would clash with the discriminator key).
 */
@Serializable
sealed interface ContentPart

@Serializable
@SerialName("text")
data class TextPart(val text: String) : ContentPart

@Serializable
@SerialName("image")
data class ImagePart(val source: ImageSource) : ContentPart

@Serializable
data class ImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String,
)
