package com.sozluk.ai.data.backup

import com.sozluk.ai.domain.model.Example
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Current export-file schema version (spec F-7). Bump on any breaking change to [BackupWord]. */
const val BACKUP_SCHEMA_VERSION = 1

/** Pretty JSON for human-inspectable export files; tolerant of unknown keys on import. */
val backupJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** Root of an export file: the schema version, the export timestamp and every word. */
@Serializable
data class WordBackup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val words: List<BackupWord>,
)

/**
 * One exported word (spec F-7). Mirrors [WordEntry] including SRS/Leitner progress so a restore on
 * a fresh install brings back learning state. The image crop is NOT exported in v1 — [hasImage]
 * only records that one existed; `imagePath` is reconstructed as null on import. Enums are stored as
 * their names (strings) so the file stays self-contained and forward-tolerant of unknown values.
 */
@Serializable
data class BackupWord(
    val id: String,
    val term: String,
    val normalizedTerm: String,
    val sourceLang: String,
    val explanationEn: String,
    val translationRu: String,
    val translationTk: String,
    val partOfSpeech: String? = null,
    val ipa: String? = null,
    val synonyms: List<String> = emptyList(),
    val examples: List<Example> = emptyList(),
    val commonMistake: String? = null,
    val contextSentence: String? = null,
    val sourceType: String,
    val hasImage: Boolean = false,
    val createdAt: Long,
    val box: Int = 0,
    val nextReviewAt: Long = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val consecutiveCorrect: Int = 0,
    val status: String,
)

fun WordEntry.toBackup(): BackupWord = BackupWord(
    id = id,
    term = term,
    normalizedTerm = normalizedTerm,
    sourceLang = sourceLang,
    explanationEn = explanationEn,
    translationRu = translationRu,
    translationTk = translationTk,
    partOfSpeech = partOfSpeech,
    ipa = ipa,
    synonyms = synonyms,
    examples = examples,
    commonMistake = commonMistake,
    contextSentence = contextSentence,
    sourceType = sourceType.name,
    hasImage = imagePath != null,
    createdAt = createdAt,
    box = box,
    nextReviewAt = nextReviewAt,
    correctCount = correctCount,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    status = status.name,
)

fun BackupWord.toDomain(): WordEntry = WordEntry(
    id = id,
    term = term,
    normalizedTerm = normalizedTerm,
    sourceLang = sourceLang,
    explanationEn = explanationEn,
    translationRu = translationRu,
    translationTk = translationTk,
    partOfSpeech = partOfSpeech,
    ipa = ipa,
    synonyms = synonyms,
    examples = examples,
    commonMistake = commonMistake,
    contextSentence = contextSentence,
    sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.MANUAL),
    imagePath = null, // crops are not exported in v1
    createdAt = createdAt,
    box = box,
    nextReviewAt = nextReviewAt,
    correctCount = correctCount,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    status = runCatching { WordStatus.valueOf(status) }.getOrDefault(WordStatus.LEARNING),
)

/** Serialize [words] into a versioned export document. */
fun encodeBackup(words: List<WordEntry>, exportedAt: Long): String =
    backupJson.encodeToString(
        WordBackup(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = exportedAt,
            words = words.map { it.toBackup() },
        ),
    )

/** Parse an export document, or null if it is not valid backup JSON. */
fun decodeBackup(json: String): WordBackup? =
    runCatching { backupJson.decodeFromString<WordBackup>(json) }.getOrNull()
