package com.sozluk.ai.data.ai

import com.sozluk.ai.data.ai.dto.GeminiContent
import com.sozluk.ai.data.ai.dto.GeminiErrorEnvelope
import com.sozluk.ai.data.ai.dto.GeminiPart
import com.sozluk.ai.data.ai.dto.GeminiRequest
import com.sozluk.ai.data.ai.dto.GeminiResponse
import com.sozluk.ai.data.ai.dto.GeminiUsage
import com.sozluk.ai.data.ai.dto.GenerationConfig
import com.sozluk.ai.data.ai.dto.InlineData
import com.sozluk.ai.data.prefs.ApiKeyStore
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.ai.GeneratedQuiz
import com.sozluk.ai.domain.ai.OpenAnswerRequest
import com.sozluk.ai.domain.ai.OpenAnswerVerdict
import com.sozluk.ai.domain.ai.QuizGenRequest
import com.sozluk.ai.domain.ai.WordArticle
import com.sozluk.ai.domain.usage.AiRequestType
import com.sozluk.ai.domain.usage.UsageCounter
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AiClient] over the Google Gemini Generative Language API using Ktor. Same contract and
 * robustness as [AnthropicAiClient] (one JSON-repair retry, one network retry on 429/5xx/IO,
 * 30 s timeout from the injected client), but a different wire protocol:
 *  - URL is per-model: `$BASE_URL/{model}:generateContent`.
 *  - The key is sent in the `x-goog-api-key` header only, read per call from [ApiKeyStore].
 *  - Request body is `{ systemInstruction, contents, generationConfig }`; the reply text is at
 *    `candidates[0].content.parts[*].text`.
 *
 * The domain-facing JSON schema (article / quiz / verdict) and prompts are shared with the
 * Anthropic client via [AiPrompts]; only the transport differs.
 */
class GeminiAiClient @Inject constructor(
    private val http: HttpClient,
    private val keyStore: ApiKeyStore,
    @param:Named("io") private val ioDispatcher: CoroutineDispatcher,
    private val usageCounter: UsageCounter,
) : AiClient {

    override suspend fun explainWord(
        term: String,
        contextSentence: String?,
    ): Result<WordArticle> = withContext(ioDispatcher) {
        val key = keyStore.getKey() ?: return@withContext Result.failure(AiError.NoApiKey)
        val user = AiPrompts.explainWordUser(term, contextSentence)

        var systemPrompt = AiPrompts.EXPLAIN_WORD_SYSTEM
        repeat(2) {
            val body = encodeText(
                system = systemPrompt,
                userText = user,
                maxTokens = GeminiModels.ARTICLE_MAX_TOKENS,
            )
            when (val text = post(key, GeminiModels.ARTICLE_MODEL, body, AiRequestType.ARTICLE)) {
                is Attempt.Failure -> return@withContext Result.failure(text.error)
                is Attempt.Success -> {
                    parseArticle(text.text)?.let { return@withContext Result.success(it) }
                    systemPrompt = AiPrompts.EXPLAIN_WORD_SYSTEM + AiPrompts.RETRY_ADDENDUM
                }
            }
        }
        Result.failure(AiError.InvalidResponse)
    }

    override suspend fun explainImage(
        imageBytes: ByteArray,
        mediaType: String,
    ): Result<WordArticle> = withContext(ioDispatcher) {
        val key = keyStore.getKey() ?: return@withContext Result.failure(AiError.NoApiKey)
        // java.util.Base64 (not android.util) — available on minSdk 26 and in unit tests, no wrapping.
        val base64 = Base64.getEncoder().encodeToString(imageBytes)

        var systemPrompt = AiPrompts.EXPLAIN_IMAGE_SYSTEM
        repeat(2) {
            val body = geminiJson.encodeToString(
                GeminiRequest(
                    systemInstruction = systemText(systemPrompt),
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(
                                GeminiPart(inlineData = InlineData(mimeType = mediaType, data = base64)),
                                GeminiPart(text = AiPrompts.EXPLAIN_IMAGE_USER),
                            ),
                        ),
                    ),
                    generationConfig = GenerationConfig(maxOutputTokens = GeminiModels.VISION_MAX_TOKENS),
                ),
            )
            when (val text = post(key, GeminiModels.VISION_MODEL, body, AiRequestType.VISION)) {
                is Attempt.Failure -> return@withContext Result.failure(text.error)
                is Attempt.Success -> {
                    parseArticle(text.text)?.let { return@withContext Result.success(it) }
                    systemPrompt = AiPrompts.EXPLAIN_IMAGE_SYSTEM + AiPrompts.RETRY_ADDENDUM
                }
            }
        }
        Result.failure(AiError.InvalidResponse)
    }

    override suspend fun generateQuizzes(
        request: QuizGenRequest,
    ): Result<List<GeneratedQuiz>> = withContext(ioDispatcher) {
        val key = keyStore.getKey() ?: return@withContext Result.failure(AiError.NoApiKey)
        if (request.words.isEmpty()) return@withContext Result.success(emptyList())

        // One batched request per 10-15 words (spec §6.2). A failed chunk is tolerated.
        val accumulated = mutableListOf<GeneratedQuiz>()
        var lastError: AiError? = null
        for (chunk in request.words.chunked(GeminiModels.QUIZ_CHUNK_SIZE)) {
            when (val r = generateChunk(key, request.copy(words = chunk))) {
                is ChunkResult.Success -> accumulated += r.quizzes
                is ChunkResult.Failure -> lastError = r.error
            }
        }
        val error = lastError
        if (accumulated.isEmpty() && error != null) Result.failure(error)
        else Result.success(accumulated)
    }

    /** Two-attempt JSON-repair turn for a single chunk, mirroring [explainWord]. */
    private suspend fun generateChunk(key: String, chunkRequest: QuizGenRequest): ChunkResult {
        var systemPrompt = AiPrompts.QUIZ_SYSTEM
        repeat(2) {
            val body = encodeText(
                system = systemPrompt,
                userText = AiPrompts.quizUser(chunkRequest),
                maxTokens = GeminiModels.QUIZ_MAX_TOKENS,
            )
            when (val text = post(key, GeminiModels.QUIZ_MODEL, body, AiRequestType.QUIZ)) {
                is Attempt.Failure -> return ChunkResult.Failure(text.error)
                is Attempt.Success -> {
                    parseQuizzes(text.text)?.let { return ChunkResult.Success(it) }
                    systemPrompt = AiPrompts.QUIZ_SYSTEM + AiPrompts.RETRY_ADDENDUM
                }
            }
        }
        return ChunkResult.Failure(AiError.InvalidResponse)
    }

    override suspend fun checkOpenAnswer(
        request: OpenAnswerRequest,
    ): Result<OpenAnswerVerdict> = withContext(ioDispatcher) {
        val key = keyStore.getKey() ?: return@withContext Result.failure(AiError.NoApiKey)

        var systemPrompt = AiPrompts.CHECK_OPEN_SYSTEM
        repeat(2) {
            val body = encodeText(
                system = systemPrompt,
                userText = AiPrompts.checkOpenUser(request),
                maxTokens = GeminiModels.CHECK_MAX_TOKENS,
            )
            when (val text = post(key, GeminiModels.CHECK_MODEL, body, AiRequestType.CHECK)) {
                is Attempt.Failure -> return@withContext Result.failure(text.error)
                is Attempt.Success -> {
                    parseVerdict(text.text)?.let { return@withContext Result.success(it) }
                    systemPrompt = AiPrompts.CHECK_OPEN_SYSTEM + AiPrompts.RETRY_ADDENDUM
                }
            }
        }
        Result.failure(AiError.InvalidResponse)
    }

    /**
     * Serialize a plain text turn (system + single user message) to the Gemini wire JSON. The
     * model is not part of the body — it goes in the request URL (see [post]).
     */
    private fun encodeText(system: String, userText: String, maxTokens: Int): String =
        geminiJson.encodeToString(
            GeminiRequest(
                systemInstruction = systemText(system),
                contents = listOf(
                    GeminiContent(role = "user", parts = listOf(GeminiPart(text = userText))),
                ),
                generationConfig = GenerationConfig(maxOutputTokens = maxTokens),
            ),
        )

    private fun systemText(text: String): GeminiContent =
        GeminiContent(parts = listOf(GeminiPart(text = text)))

    /**
     * One turn to `{model}:generateContent` with a single network retry on 429/5xx/IO. [jsonBody]
     * is the already-serialized request envelope. Every billed 2xx response is recorded against
     * [type] in the [usageCounter] (spec §10).
     */
    private suspend fun post(
        key: String,
        model: String,
        jsonBody: String,
        type: AiRequestType,
    ): Attempt {
        val url = "${GeminiModels.BASE_URL}/$model:generateContent"
        var lastError: AiError = AiError.Network
        repeat(2) { attempt ->
            val response: HttpResponse? = try {
                http.post(url) {
                    header(GeminiModels.API_KEY_HEADER, key)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = AiError.Network
                null // retryable
            }

            if (response != null) {
                when (val code = response.status.value) {
                    in 200..299 -> {
                        val attemptResult = readText(response)
                        if (attemptResult is Attempt.Success) {
                            usageCounter.record(
                                type,
                                attemptResult.usage?.promptTokenCount ?: 0,
                                attemptResult.usage?.candidatesTokenCount ?: 0,
                            )
                        }
                        return attemptResult
                    }
                    // Gemini reports a bad/blocked key as 400 (INVALID_ARGUMENT, "API key not
                    // valid") or 403 (PERMISSION_DENIED); other 400s are genuine bad requests.
                    400 -> return Attempt.Failure(classifyBadRequest(response))
                    401, 403 -> return Attempt.Failure(AiError.InvalidKey)
                    429 -> lastError = AiError.RateLimited(retryAfterSeconds(response)) // retryable
                    in 500..599 -> lastError = AiError.Unknown("HTTP $code") // retryable
                    else -> return Attempt.Failure(AiError.Unknown("HTTP $code"))
                }
            }

            if (attempt == 0) delay(backoffMillis(lastError))
        }
        return Attempt.Failure(lastError)
    }

    /** A 400 that mentions the API key is an auth problem, not a malformed request. */
    private suspend fun classifyBadRequest(response: HttpResponse): AiError {
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        val looksLikeKey = bodyText.contains("API key", ignoreCase = true) ||
            bodyText.contains("API_KEY_INVALID", ignoreCase = true)
        return if (looksLikeKey) AiError.InvalidKey else AiError.Unknown("HTTP 400")
    }

    private suspend fun readText(response: HttpResponse): Attempt =
        try {
            val message = geminiJson.decodeFromString<GeminiResponse>(response.bodyAsText())
            val text = message.candidates.firstOrNull()
                ?.content?.parts.orEmpty()
                .firstNotNullOfOrNull { it.text }
            if (text != null) Attempt.Success(text, message.usageMetadata)
            else Attempt.Failure(AiError.InvalidResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Attempt.Failure(AiError.InvalidResponse)
        }

    private fun parseArticle(text: String): WordArticle? =
        extractJson(text)?.let {
            try {
                aiJson.decodeFromString<WordArticle>(it)
            } catch (e: Exception) {
                null
            }
        }

    private fun parseQuizzes(text: String): List<GeneratedQuiz>? =
        extractJsonArray(text)?.let {
            try {
                aiJson.decodeFromString<List<GeneratedQuiz>>(it)
            } catch (e: Exception) {
                null
            }
        }

    private fun parseVerdict(text: String): OpenAnswerVerdict? =
        extractJson(text)?.let {
            try {
                aiJson.decodeFromString<OpenAnswerVerdict>(it)
            } catch (e: Exception) {
                null
            }
        }

    private fun retryAfterSeconds(response: HttpResponse): Long? =
        response.headers["Retry-After"]?.toLongOrNull()

    private fun backoffMillis(error: AiError): Long =
        when (error) {
            is AiError.RateLimited -> (error.retryAfterSeconds ?: 1L).coerceAtLeast(0L) * 1000L
            else -> 500L
        }

    /** Result of a single network turn, before article parsing. */
    private sealed interface Attempt {
        data class Success(val text: String, val usage: GeminiUsage?) : Attempt
        data class Failure(val error: AiError) : Attempt
    }

    /** Result of generating one chunk of quiz questions. */
    private sealed interface ChunkResult {
        data class Success(val quizzes: List<GeneratedQuiz>) : ChunkResult
        data class Failure(val error: AiError) : ChunkResult
    }
}
