package com.sozluk.ai.ai

import com.sozluk.ai.data.ai.GeminiAiClient
import com.sozluk.ai.data.ai.aiJson
import com.sozluk.ai.data.ai.dto.GeminiCandidate
import com.sozluk.ai.data.ai.dto.GeminiContent
import com.sozluk.ai.data.ai.dto.GeminiPart
import com.sozluk.ai.data.ai.dto.GeminiResponse
import com.sozluk.ai.data.ai.dto.GeminiUsage
import com.sozluk.ai.data.ai.geminiJson
import com.sozluk.ai.data.prefs.ApiKeyStore
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.usage.AiRequestType
import com.sozluk.ai.domain.usage.UsageCounter
import com.sozluk.ai.domain.usage.UsageStats
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAiClientTest {

    private val articleJson = """
        {"term":"bank","partOfSpeech":"noun","ipa":"/bæŋk/",
         "explanationEn":"the land beside a river","translationRu":"берег",
         "translationTk":"kenar","synonyms":["shore"],
         "examples":[{"en":"on the bank","ru":"на берегу","tk":"kenarda"}],
         "commonMistake":null}
    """.trimIndent()

    /** Wrap [text] in a canonical Gemini generateContent envelope. */
    private fun envelope(text: String, usage: GeminiUsage? = null): String =
        geminiJson.encodeToString(
            GeminiResponse(
                candidates = listOf(
                    GeminiCandidate(
                        content = GeminiContent(role = "model", parts = listOf(GeminiPart(text = text))),
                        finishReason = "STOP",
                    ),
                ),
                usageMetadata = usage,
            ),
        )

    private class FakeApiKeyStore(private var key: String?) : ApiKeyStore {
        override fun getKey() = key
        override fun setKey(key: String) { this.key = key }
        override fun clear() { key = null }
        override fun hasKey() = key != null
    }

    private class RecordingUsageCounter : UsageCounter {
        val records = mutableListOf<Triple<AiRequestType, Int, Int>>()
        override val usage: Flow<UsageStats> = flowOf(UsageStats())
        override suspend fun record(type: AiRequestType, inputTokens: Int, outputTokens: Int) {
            records += Triple(type, inputTokens, outputTokens)
        }
        override suspend fun reset() {}
    }

    /** MockEngine that replies with each queued (status, body) in order. */
    private fun engineOf(vararg responses: Pair<HttpStatusCode, String>): MockEngine {
        var i = 0
        return MockEngine {
            val (status, body) = responses[i.coerceAtMost(responses.lastIndex)]
            i++
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private fun clientWith(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(aiJson) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.clientFor(
        engine: MockEngine,
        key: String? = "AQ.test",
        usageCounter: UsageCounter = RecordingUsageCounter(),
    ) = GeminiAiClient(
        clientWith(engine),
        FakeApiKeyStore(key),
        UnconfinedTestDispatcher(testScheduler),
        usageCounter,
    )

    @Test
    fun success_parsesPlainJson_andSendsKeyHeaderAndModelUrl() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(articleJson))
        val result = clientFor(engine).explainWord("bank", null)

        val article = result.getOrThrow()
        assertEquals("bank", article.term)
        assertEquals("kenar", article.translationTk)
        assertEquals(1, article.examples.size)
        assertEquals(1, engine.requestHistory.size)
        val request = engine.requestHistory.first()
        assertEquals("AQ.test", request.headers["x-goog-api-key"])
        // Model goes in the URL, not the body.
        assertTrue(request.url.toString().contains("gemini-flash-latest:generateContent"))
    }

    @Test
    fun success_parsesFencedJson() = runTest {
        val fenced = "```json\n$articleJson\n```"
        val result = clientFor(engineOf(HttpStatusCode.OK to envelope(fenced))).explainWord("bank", null)
        assertEquals("bank", result.getOrThrow().term)
    }

    @Test
    fun success_parsesJsonWithSurroundingText() = runTest {
        val around = "Sure! Here is the article:\n$articleJson\nLet me know if you need more."
        val result = clientFor(engineOf(HttpStatusCode.OK to envelope(around))).explainWord("bank", null)
        assertEquals("bank", result.getOrThrow().term)
    }

    @Test
    fun invalidJson_retriesOnce_thenInvalidResponse() = runTest {
        val garbage = envelope("{ not valid json at all }")
        val engine = engineOf(
            HttpStatusCode.OK to garbage,
            HttpStatusCode.OK to garbage,
        )
        val result = clientFor(engine).explainWord("bank", null)

        assertTrue(result.exceptionOrNull() is AiError.InvalidResponse)
        assertEquals(2, engine.requestHistory.size) // one JSON retry
    }

    @Test
    fun http400_apiKeyMessage_mapsToInvalidKey_noRetry() = runTest {
        val body = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        val engine = engineOf(HttpStatusCode.BadRequest to body)
        val result = clientFor(engine).explainWord("bank", null)

        assertTrue(result.exceptionOrNull() is AiError.InvalidKey)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun http400_otherMessage_mapsToUnknown_noRetry() = runTest {
        val body = """{"error":{"code":400,"message":"Invalid JSON payload","status":"INVALID_ARGUMENT"}}"""
        val engine = engineOf(HttpStatusCode.BadRequest to body)
        val result = clientFor(engine).explainWord("bank", null)

        assertTrue(result.exceptionOrNull() is AiError.Unknown)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun http403_mapsToInvalidKey_noRetry() = runTest {
        val engine = engineOf(HttpStatusCode.Forbidden to "")
        val result = clientFor(engine).explainWord("bank", null)

        assertTrue(result.exceptionOrNull() is AiError.InvalidKey)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun http429_retriesThenSucceeds() = runTest {
        val engine = engineOf(
            HttpStatusCode.TooManyRequests to "",
            HttpStatusCode.OK to envelope(articleJson),
        )
        val result = clientFor(engine).explainWord("bank", null)

        assertEquals("bank", result.getOrThrow().term)
        assertEquals(2, engine.requestHistory.size) // one network retry
    }

    @Test
    fun noApiKey_returnsNoApiKey_withoutNetworkCall() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(articleJson))
        val result = clientFor(engine, key = null).explainWord("bank", null)

        assertTrue(result.exceptionOrNull() is AiError.NoApiKey)
        assertTrue(engine.requestHistory.isEmpty())
    }

    // --- explainImage (vision) ---

    @Test
    fun explainImage_success_parsesArticle_andSendsInlineData() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(articleJson))
        val bytes = byteArrayOf(1, 2, 3, 4)
        val result = clientFor(engine).explainImage(bytes, "image/jpeg")

        val article = result.getOrThrow()
        assertEquals("bank", article.term)

        val body = (engine.requestHistory.first().body as TextContent).text
        assertTrue(body.contains("\"inlineData\""))
        assertTrue(body.contains("\"mimeType\":\"image/jpeg\""))
        // 0x01020304 base64 = "AQIDBA==".
        assertTrue(body.contains("AQIDBA=="))
        // explicitNulls off: an unused "text":null must NOT be emitted on the image part.
        assertTrue(!body.contains("\"text\":null"))
        assertEquals("AQ.test", engine.requestHistory.first().headers["x-goog-api-key"])
        assertTrue(engine.requestHistory.first().url.toString().contains("gemini-flash-latest:generateContent"))
    }

    @Test
    fun explainImage_noApiKey_returnsNoApiKey_withoutNetworkCall() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(articleJson))
        val result = clientFor(engine, key = null).explainImage(byteArrayOf(1), "image/jpeg")

        assertTrue(result.exceptionOrNull() is AiError.NoApiKey)
        assertTrue(engine.requestHistory.isEmpty())
    }

    // --- generateQuizzes (batch) ---

    private fun quizWord(i: Int) = com.sozluk.ai.domain.ai.QuizWord(
        wordId = "w$i",
        term = "term$i",
        translationRu = "ru$i",
        translationTk = "tk$i",
        contextSentence = null,
    )

    private fun quizRequest(count: Int) = com.sozluk.ai.domain.ai.QuizGenRequest(
        words = (1..count).map { quizWord(it) },
        types = com.sozluk.ai.data.ai.AiQuizTypeMapping.AI_TYPES,
        uiLang = "ru",
    )

    private val quizArray = """
        [{"type":"gap_fill","wordId":"w1","question":"I need to ___ my skills.",
          "options":["improve","prove","approve","reprove"],"answerIndex":0,"explanation":"why"}]
    """.trimIndent()

    @Test
    fun quiz_parsesArray_andSendsQuizModelUrlAndKey() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(quizArray))
        val result = clientFor(engine).generateQuizzes(quizRequest(1))

        val quizzes = result.getOrThrow()
        assertEquals(1, quizzes.size)
        assertEquals("w1", quizzes.first().wordId)
        assertEquals(0, quizzes.first().answerIndex)
        val request = engine.requestHistory.first()
        assertTrue(request.url.toString().contains("gemini-flash-latest:generateContent"))
        assertEquals("AQ.test", request.headers["x-goog-api-key"])
    }

    @Test
    fun quiz_chunksWordsIntoBatchesOf15() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(quizArray))
        val result = clientFor(engine).generateQuizzes(quizRequest(16))

        assertTrue(result.isSuccess)
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun quiz_emptyWords_returnsEmpty_withoutNetworkCall() = runTest {
        val engine = engineOf(HttpStatusCode.OK to envelope(quizArray))
        val result = clientFor(engine).generateQuizzes(quizRequest(0))

        assertEquals(emptyList<com.sozluk.ai.domain.ai.GeneratedQuiz>(), result.getOrThrow())
        assertTrue(engine.requestHistory.isEmpty())
    }

    // --- checkOpenAnswer ---

    private val sampleArticle = com.sozluk.ai.domain.ai.WordArticle(
        term = "bank",
        explanationEn = "the land beside a river",
        translationRu = "берег",
        translationTk = "kenar",
    )

    private fun openRequest(sentence: String) = com.sozluk.ai.domain.ai.OpenAnswerRequest(
        term = "bank",
        article = sampleArticle,
        userSentence = sentence,
        uiLang = "ru",
    )

    @Test
    fun check_parsesVerdict_andSendsCheckModelAndKey() = runTest {
        val verdictJson = """{"verdict":"MINOR","corrected":"I sat on the bank.","comment":"Почти!"}"""
        val engine = engineOf(HttpStatusCode.OK to envelope(verdictJson))
        val result = clientFor(engine).checkOpenAnswer(openRequest("I sit on bank."))

        val verdict = result.getOrThrow()
        assertEquals(com.sozluk.ai.domain.ai.OpenAnswerVerdict.Verdict.MINOR, verdict.verdict)
        assertEquals("I sat on the bank.", verdict.corrected)
        assertEquals("AQ.test", engine.requestHistory.first().headers["x-goog-api-key"])
    }

    @Test
    fun check_invalidJson_retriesOnce_thenInvalidResponse() = runTest {
        val garbage = envelope("{ not a verdict }")
        val engine = engineOf(
            HttpStatusCode.OK to garbage,
            HttpStatusCode.OK to garbage,
        )
        val result = clientFor(engine).checkOpenAnswer(openRequest("x"))

        assertTrue(result.exceptionOrNull() is AiError.InvalidResponse)
        assertEquals(2, engine.requestHistory.size)
    }

    // --- usage counter interceptor ---

    @Test
    fun success_recordsUsageForTheRequestType() = runTest {
        val counter = RecordingUsageCounter()
        val engine = engineOf(HttpStatusCode.OK to envelope(articleJson, GeminiUsage(12, 34)))
        clientFor(engine, usageCounter = counter).explainWord("bank", null)

        assertEquals(1, counter.records.size)
        assertEquals(Triple(AiRequestType.ARTICLE, 12, 34), counter.records.first())
    }

    @Test
    fun failedResponse_doesNotRecordUsage() = runTest {
        val counter = RecordingUsageCounter()
        val engine = engineOf(HttpStatusCode.Forbidden to "")
        clientFor(engine, usageCounter = counter).explainWord("bank", null)

        assertTrue(counter.records.isEmpty())
    }
}
