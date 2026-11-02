package com.sozluk.ai.data.ai

import com.sozluk.ai.domain.ai.OpenAnswerRequest
import com.sozluk.ai.domain.ai.QuizGenRequest

/** Raw prompt strings for AI calls (spec §6.1). Kept out of the client for readability. */
object AiPrompts {

    /** System prompt for the dictionary-article call (spec §6.1, §8.3). */
    val EXPLAIN_WORD_SYSTEM: String = """
        You are a dictionary engine for an English-learning app.
        Return ONLY a single valid JSON object and nothing else: no Markdown, no code fences,
        no commentary before or after.

        Schema (all keys required; use null for partOfSpeech, ipa or commonMistake if unknown):
        {
          "term": "bank",
          "partOfSpeech": "noun",
          "ipa": "/bæŋk/",
          "explanationEn": "The land along the side of a river.",
          "translationRu": "берег",
          "translationTk": "kenar",
          "synonyms": ["shore", "riverside"],
          "examples": [
            {
              "en": "We had a picnic on the river bank.",
              "ru": "Мы устроили пикник на берегу реки.",
              "tk": "Biz derýanyň kenarynda piknik etdik."
            }
          ],
          "commonMistake": "Do not confuse with 'банк' (financial institution)."
        }

        Rules:
        - explanationEn: simple English at CEFR ~B1 level.
        - translationRu: translation into Russian.
        - translationTk: translation into Turkmen, Latin script (türkmen dili) — this is
          explicitly Turkmen, NOT Turkish. Do not output Turkish.
        - examples: 2 to 3 natural example sentences, each translated into BOTH Russian and
          Turkmen (Latin script).
        - If a context sentence is provided, explain and translate the meaning of the word AS
          USED IN THAT SENTENCE.
    """.trimIndent()

    /** Appended to the system prompt on the JSON-parse retry (spec §6.1). */
    const val RETRY_ADDENDUM: String =
        "\n\nYour previous answer was not valid JSON. Return ONLY valid JSON matching the schema."

    /** User message for [EXPLAIN_WORD_SYSTEM]. */
    fun explainWordUser(term: String, contextSentence: String?): String =
        if (contextSentence.isNullOrBlank()) {
            "Word: $term"
        } else {
            "Word: $term\nContext sentence: $contextSentence"
        }

    /**
     * System prompt for the vision call (spec F-6, §6.1). Reuses the exact article schema so the
     * response parses into the same [com.sozluk.ai.domain.ai.WordArticle].
     */
    val EXPLAIN_IMAGE_SYSTEM: String = EXPLAIN_WORD_SYSTEM

    /** Text part accompanying the image block in the vision request (spec F-6, step 4). */
    const val EXPLAIN_IMAGE_USER: String =
        "The image contains (or has a highlighted) English word or short phrase. Identify it and " +
            "return its dictionary article strictly as the JSON schema. Put the identified word in " +
            "\"term\". If a short phrase, use the key word as \"term\"."

    /** System prompt for the batch quiz-generation call (spec §6.2, §7.3). */
    val QUIZ_SYSTEM: String = """
        You are a quiz generator for an English-learning app. You are given a batch of vocabulary
        words the learner is studying. Produce multiple-choice quiz questions about them.

        Return ONLY a single valid JSON array and nothing else: no Markdown, no code fences, no
        commentary before or after. Do NOT wrap it in an object — the top level MUST be an array.

        Each array element (all keys required):
        {
          "type": "gap_fill",
          "wordId": "the id copied verbatim from the input word",
          "question": "I need to ___ my English before the exam.",
          "options": ["improve", "improvise", "prove", "approve"],
          "answerIndex": 0,
          "explanation": "Short reason the correct option fits."
        }

        Allowed "type" values (use a variety across the batch):
        - "gap_fill": a NEW sentence with a blank; the correct option is the word.
        - "definition": an English definition; the options are 4 words, one being the target.
        - "synonym": a sentence/context; pick the option that best fits as a synonym of the word.
        - "find_mistake": a sentence misusing a word; options are candidate fixes, one correct.
        - "dialog": a short 2-3 line dialog with ONE blank; options are 4 words, one correct.

        Rules:
        - "wordId" MUST be copied exactly from the corresponding input word — this is how the app
          matches the question back to a word. Never invent or alter an id.
        - Exactly 4 options per question, all distinct, no duplicates. Distractors must be
          plausible: same part of speech and register as the correct answer.
        - "answerIndex" is the 0-based index (0..3) of the correct option within "options".
        - Write "question" and "explanation" in the interface language given below. Keep the
          "explanation" short — one sentence on why the correct option is right.
        - Do NOT reuse the provided context sentence of a word verbatim as a question; invent new
          material so the learner sees the word in a fresh context.
        - Produce one question per input word unless told otherwise.
    """.trimIndent()

    /** User message for [QUIZ_SYSTEM]: interface language + one line per word. */
    fun quizUser(request: QuizGenRequest): String {
        val header = "Interface language for question and explanation text: ${request.uiLang}\n" +
            "Words (format: wordId | term | ru | tk | context):"
        val lines = request.words.joinToString("\n") { w ->
            "${w.wordId} | ${w.term} | ${w.translationRu} | ${w.translationTk} | " +
                "context: ${w.contextSentence ?: "-"}"
        }
        return "$header\n$lines"
    }

    /** System prompt for grading a learner's open sentence (spec §6.3, F-4). */
    val CHECK_OPEN_SYSTEM: String = """
        You are a friendly English tutor grading a learner's own sentence that must use a given word
        correctly and naturally.

        Return ONLY a single valid JSON object and nothing else: no Markdown, no code fences, no
        commentary before or after.

        Schema (all keys required):
        {
          "verdict": "OK",
          "corrected": "A corrected version of the sentence, or the sentence unchanged if already good.",
          "comment": "A short, kind, encouraging note for the learner."
        }

        Rules:
        - "verdict" MUST be exactly one of: "OK", "MINOR", "WRONG".
          - "OK": the word is used correctly and the sentence is natural.
          - "MINOR": the meaning is correct but there is a small roughness (grammar, article,
            word order, spelling) that does not change the meaning.
          - "WRONG": the word is misused, or the sentence does not make sense.
        - "corrected": always provide a clean, natural version of the sentence.
        - "comment": write it in the interface language given below. Keep it short (one or two
          sentences), warm and encouraging — never harsh.
    """.trimIndent()

    /** User message for [CHECK_OPEN_SYSTEM]: the target word, its article and the learner's sentence. */
    fun checkOpenUser(request: OpenAnswerRequest): String =
        buildString {
            appendLine("Interface language for the comment: ${request.uiLang}")
            appendLine("Target word: ${request.term}")
            appendLine("Meaning (English): ${request.article.explanationEn}")
            appendLine("Russian: ${request.article.translationRu}")
            appendLine("Turkmen: ${request.article.translationTk}")
            append("Learner's sentence: ${request.userSentence}")
        }
}
