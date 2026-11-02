package com.sozluk.ai.data.ai

import com.sozluk.ai.data.ai.dto.ContentPart
import com.sozluk.ai.data.ai.dto.ImagePart
import com.sozluk.ai.data.ai.dto.ImageSource
import com.sozluk.ai.data.ai.dto.MessagesRequest
import com.sozluk.ai.data.ai.dto.MessagesResponse
import com.sozluk.ai.data.ai.dto.TextPart
import com.sozluk.ai.data.ai.dto.Usage
import com.sozluk.ai.data.ai.dto.VisionMessage
import com.sozluk.ai.data.ai.dto.VisionRequest
import com.sozluk.ai.data.ai.dto.WireMessage
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
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
 * [AiClient] over the Anthropic Messages API using Ktor (spec §6.1). Robust JSON parsing with
 * one retry, one network retry on 429/5xx/IO, 30 s timeout (configured on the injected client).
 * The API key is read per call from [ApiKeyStore] and only ever sent as the `x-api-key` header.
 */
class AnthropicAiClient @Inject constructor(
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

        // Two attempts total: base prompt, then a retry that reminds the model to return JSON.
        var systemPrompt = AiPrompts.EXPLAIN_WORD_SYSTEM
        repeat(2) {
            val body = aiJson.encodeToString(
                MessagesRequest(
                    model = AiModels.ARTICLE_MODEL,
                    maxTokens = AiModels.ARTICLE_MAX_TOKENS,
                    system = systemPrompt,
                    messages = listOf(WireMessage(role = "user", content = user)),
                ),
            )
            when (val text = postForArticle(key, body, AiRequestType.ARTICLE)) {
                is Attempt.Failure -> return@withContext Result.failure(text.error)
                is Attempt.Success -> {
                    parseArticle(text.text)?.let { return@withContext Result.success(it) }
                    // Parse failed — retry once with the addendum.
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

        // Same two-attempt JSON-repair loop as explainWord, but the message carries an image block.
        var systemPrompt = AiPrompts.EXPLAIN_IMAGE_SYSTEM
        repeat(2) {
            val content: List<ContentPart> = listOf(
                ImagePart(ImageSource(mediaType = mediaType, data = base64)),
                TextPart(AiPrompts.EXPLAIN_IMAGE_USER),
            )
            val body = aiJson.encodeToString(
                VisionRequest(
                    model = AiModels.VISION_MODEL,
                    maxTokens = AiModels.VISION_MAX_TOKENS,
                    system = systemPrompt,
                    messages = listOf(VisionMessage(role = "user", content = content)),
                ),
            )
            when (val text = postForArticle(key, body, AiRequestType.VISION)) {
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

        // One batched request per 10-15 words (spec §6.2). A failed chunk is tolerated: keep the
        // good ones so the exam still gets AI items; the composer fills any gap locally.
        val accumulated = mutableListOf<GeneratedQuiz>()
        var lastError: AiError? = null
        for (chunk in request.words.chunked(AiModels.QUIZ_CHUNK_SIZE)) {
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
            val body = aiJson.encodeToString(
                MessagesRequest(
                    model = AiModels.QUIZ_MODEL,
                    maxTokens = AiModels.QUIZ_MAX_TOKENS,
                    system = systemPrompt,
                    messages = listOf(WireMessage(role = "user", content = AiPrompts.quizUser(chunkRequest))),
                ),
            )
            when (val text = postForArticle(key, body, AiRequestType.QUIZ)) {
                is Attempt.Failure -> return ChunkResult.Failure(text.error)
                is Attempt.Success -> {
                    parseQuizzes(text.text)?.let { return ChunkResult.Success(it) }
                    systemPrompt = AiPrompts.QUIZ_SYSTEM + AiPrompts.RETRY_ADDENDUM
                }
            }
        }
        return ChunkResult.Failure(AiError.InvalidResponse)
    }

    private fun parseQuizzes(text: String): List<GeneratedQuiz>? =
        extractJsonArray(text)?.let {
            try {
                aiJson.decodeFromString<List<GeneratedQuiz>>(it)
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun checkOpenAnswer(
        request: OpenAnswerRequest,
    ): Result<OpenAnswerVerdict> = withContext(ioDispatcher) {
        val key = keyStore.getKey() ?: return@withContext Result.failure(AiError.NoApiKey)

        // Same two-attempt JSON-repair loop as explainWord (spec §6.3).
        var systemPrompt = AiPrompts.CHECK_OPEN_SYSTEM
        repeat(2) {
            val body = aiJson.encodeToString(
                MessagesRequest(
                    model = AiModels.CHECK_MODEL,
                    maxTokens = AiModels.CHECK_MAX_TOKENS,
                    system = systemPrompt,
                    messages = listOf(WireMessage(role = "user", content = AiPrompts.checkOpenUser(request))),
                ),
            )
            when (val text = postForArticle(key, body, AiRequestType.CHECK)) {
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
     * One turn to the Messages API with a single network retry on 429/5xx/IO. [jsonBody] is the
     * already-serialized request envelope (text or vision), sent verbatim as the JSON body — this
     * keeps the retry/status/parse machinery shared by both [explainWord] and [explainImage].
     * Every billed 2xx response is recorded against [type] in the [usageCounter] (spec §10).
     */
    private suspend fun postForArticle(
        key: String,
        jsonBody: String,
        type: AiRequestType,
    ): Attempt {
        var lastError: AiError = AiError.Network
        repeat(2) { attempt ->
            val response: HttpResponse? = try {
                http.post(AiModels.BASE_URL) {
                    header("x-api-key", key)
                    header("anthropic-version", AiModels.ANTHROPIC_VERSION)
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
                        // Count every billed response (spec §10) — including a 200 whose body later
                        // fails to parse, since the API charges tokens for it either way.
                        if (attemptResult is Attempt.Success) {
                            usageCounter.record(
                                type,
                                attemptResult.usage?.inputTokens ?: 0,
                                attemptResult.usage?.outputTokens ?: 0,
                            )
                        }
                        return attemptResult
                    }
                    401 -> return Attempt.Failure(AiError.InvalidKey)
                    429 -> lastError = AiError.RateLimited(retryAfterSeconds(response)) // retryable
                    in 500..599 -> lastError = AiError.Unknown("HTTP $code") // retryable
                    else -> return Attempt.Failure(AiError.Unknown("HTTP $code"))
                }
            }

            if (attempt == 0) delay(backoffMillis(lastError))
        }
        return Attempt.Failure(lastError)
    }

    private suspend fun readText(response: HttpResponse): Attempt =
        try {
            val message = response.body<MessagesResponse>()
            val text = message.content.firstOrNull { it.type == "text" }?.text
            if (text != null) Attempt.Success(text, message.usage) else Attempt.Failure(AiError.InvalidResponse)
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
        data class Success(val text: String, val usage: Usage?) : Attempt
        data class Failure(val error: AiError) : Attempt
    }

    /** Result of generating one chunk of quiz questions. */
    private sealed interface ChunkResult {
        data class Success(val quizzes: List<GeneratedQuiz>) : ChunkResult
        data class Failure(val error: AiError) : ChunkResult
    }
}
