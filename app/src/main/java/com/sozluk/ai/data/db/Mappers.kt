package com.sozluk.ai.data.db

import com.sozluk.ai.domain.model.Example
import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.WordEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

// --- WordEntry ---

fun WordEntity.toDomain(): WordEntry = WordEntry(
    id = id,
    term = term,
    normalizedTerm = normalizedTerm,
    sourceLang = sourceLang,
    explanationEn = explanationEn,
    translationRu = translationRu,
    translationTk = translationTk,
    partOfSpeech = partOfSpeech,
    ipa = ipa,
    synonyms = dbJson.decodeFromString(ListSerializer(String.serializer()), synonymsJson),
    examples = dbJson.decodeFromString(ListSerializer(Example.serializer()), examplesJson),
    commonMistake = commonMistake,
    contextSentence = contextSentence,
    sourceType = sourceType,
    imagePath = imagePath,
    createdAt = createdAt,
    box = box,
    nextReviewAt = nextReviewAt,
    correctCount = correctCount,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    status = status,
)

fun WordEntry.toEntity(): WordEntity = WordEntity(
    id = id,
    term = term,
    normalizedTerm = normalizedTerm,
    sourceLang = sourceLang,
    explanationEn = explanationEn,
    translationRu = translationRu,
    translationTk = translationTk,
    partOfSpeech = partOfSpeech,
    ipa = ipa,
    synonymsJson = dbJson.encodeToString(ListSerializer(String.serializer()), synonyms),
    examplesJson = dbJson.encodeToString(ListSerializer(Example.serializer()), examples),
    commonMistake = commonMistake,
    contextSentence = contextSentence,
    sourceType = sourceType,
    imagePath = imagePath,
    createdAt = createdAt,
    box = box,
    nextReviewAt = nextReviewAt,
    correctCount = correctCount,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    status = status,
)

// --- ExamSession ---

fun ExamSessionEntity.toDomain(): ExamSession = ExamSession(
    id = id,
    createdAt = createdAt,
    wordIds = dbJson.decodeFromString(ListSerializer(String.serializer()), wordIdsJson),
    mode = mode,
    score = score,
    total = total,
    state = state,
)

fun ExamSession.toEntity(): ExamSessionEntity = ExamSessionEntity(
    id = id,
    createdAt = createdAt,
    wordIdsJson = dbJson.encodeToString(ListSerializer(String.serializer()), wordIds),
    mode = mode,
    score = score,
    total = total,
    state = state,
)

// --- QuizItem ---

fun QuizItemEntity.toDomain(): QuizItem = QuizItem(
    id = id,
    sessionId = sessionId,
    wordId = wordId,
    type = type,
    payloadJson = payloadJson,
    userAnswer = userAnswer,
    isCorrect = isCorrect,
)

fun QuizItem.toEntity(): QuizItemEntity = QuizItemEntity(
    id = id,
    sessionId = sessionId,
    wordId = wordId,
    type = type,
    payloadJson = payloadJson,
    userAnswer = userAnswer,
    isCorrect = isCorrect,
)
