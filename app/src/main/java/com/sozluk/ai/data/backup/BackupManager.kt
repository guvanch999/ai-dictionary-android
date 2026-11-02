package com.sozluk.ai.data.backup

import com.sozluk.ai.domain.repository.WordRepository
import javax.inject.Inject

/** How many words were added vs. skipped during an import (spec F-7). */
data class ImportReport(val imported: Int, val skipped: Int)

/** Outcome of importing a backup file. */
sealed interface ImportOutcome {
    data class Success(val report: ImportReport) : ImportOutcome
    /** The file's schema version is not supported by this app version. */
    data object UnsupportedVersion : ImportOutcome
    /** The file is not valid backup JSON. */
    data object Invalid : ImportOutcome
}

/**
 * Exports the whole dictionary to a JSON document and merges one back (spec F-7). Merge strategy:
 * match on `normalizedTerm`; an existing word is skipped (never overwritten), a new one is inserted.
 * Because [WordRepository.upsert] persists immediately, duplicates within the same file are also
 * de-duplicated. Exam-session history is untouched.
 */
class BackupManager @Inject constructor(
    private val wordRepository: WordRepository,
) {
    suspend fun exportAll(exportedAt: Long = System.currentTimeMillis()): String =
        encodeBackup(wordRepository.getAll(), exportedAt)

    suspend fun import(json: String): ImportOutcome {
        val backup = decodeBackup(json) ?: return ImportOutcome.Invalid
        if (backup.schemaVersion != BACKUP_SCHEMA_VERSION) return ImportOutcome.UnsupportedVersion

        var imported = 0
        var skipped = 0
        for (backupWord in backup.words) {
            val word = backupWord.toDomain()
            val normalized = word.normalizedTerm.ifBlank { word.term.trim().lowercase() }
            if (wordRepository.findByNormalizedTerm(normalized).isNotEmpty()) {
                skipped++
            } else {
                wordRepository.upsert(word)
                imported++
            }
        }
        return ImportOutcome.Success(ImportReport(imported, skipped))
    }
}
