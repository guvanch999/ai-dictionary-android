package com.sozluk.ai.backup

import com.sozluk.ai.data.backup.BackupManager
import com.sozluk.ai.data.backup.ImportOutcome
import com.sozluk.ai.data.backup.decodeBackup
import com.sozluk.ai.data.backup.encodeBackup
import com.sozluk.ai.data.backup.toDomain
import com.sozluk.ai.domain.model.Example
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.repository.WordSort
import com.sozluk.ai.support.word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    /** In-memory repository so import's dedup can observe words it just inserted. */
    private class FakeWordRepository(initial: List<WordEntry> = emptyList()) : WordRepository {
        val store: MutableMap<String, WordEntry> = initial.associateBy { it.id }.toMutableMap()
        override suspend fun upsert(word: WordEntry) {
            store[word.id] = word.copy(normalizedTerm = word.term.trim().lowercase())
        }
        override suspend fun getById(id: String): WordEntry? = store[id]
        override suspend fun getAll(): List<WordEntry> = store.values.toList()
        override suspend fun deleteById(id: String) { store.remove(id) }
        override fun observeAll(sort: WordSort): Flow<List<WordEntry>> = emptyFlow()
        override fun search(query: String): Flow<List<WordEntry>> = emptyFlow()
        override suspend fun findByNormalizedTerm(term: String): List<WordEntry> =
            store.values.filter { it.normalizedTerm == term }
        override suspend fun getDueForReview(now: Long): List<WordEntry> = emptyList()
        override suspend fun countByStatus(status: WordStatus): Int = 0
    }

    private val sampleWords = listOf(
        word("w1", term = "apple", synonyms = listOf("fruit"), examples = listOf(Example("an apple", "яблоко", "alma"))),
        word("w2", term = "bread", box = 3, consecutiveCorrect = 2, correctCount = 5, status = WordStatus.MASTERED),
    )

    @Test
    fun roundTrip_preservesWords_includingSrsProgress() {
        val json = encodeBackup(sampleWords, exportedAt = 123L)
        val decoded = decodeBackup(json)!!

        assertEquals(1, decoded.schemaVersion)
        assertEquals(sampleWords, decoded.words.map { it.toDomain() })
    }

    @Test
    fun import_onEmptyBase_insertsAll() = runTest {
        val repo = FakeWordRepository()
        val json = encodeBackup(sampleWords, exportedAt = 0L)

        val outcome = BackupManager(repo).import(json)

        val success = outcome as ImportOutcome.Success
        assertEquals(2, success.report.imported)
        assertEquals(0, success.report.skipped)
        assertEquals(2, repo.store.size)
    }

    @Test
    fun import_skipsExistingNormalizedTerm_noDuplicates() = runTest {
        val repo = FakeWordRepository(listOf(word("existing", term = "apple")))
        // Backup has a different-id "Apple" (same normalized term) plus a genuinely new word.
        val json = encodeBackup(
            listOf(word("a2", term = "Apple"), word("b1", term = "bread")),
            exportedAt = 0L,
        )

        val outcome = BackupManager(repo).import(json)

        val success = outcome as ImportOutcome.Success
        assertEquals(1, success.report.imported) // bread only
        assertEquals(1, success.report.skipped) // apple already present
        // Exactly one word per normalized term.
        assertEquals(1, repo.store.values.count { it.normalizedTerm == "apple" })
        assertEquals(1, repo.store.values.count { it.normalizedTerm == "bread" })
    }

    @Test
    fun import_dedupesWithinTheSameFile() = runTest {
        val repo = FakeWordRepository()
        val json = encodeBackup(
            listOf(word("a1", term = "apple"), word("a2", term = "apple")),
            exportedAt = 0L,
        )

        val outcome = BackupManager(repo).import(json) as ImportOutcome.Success
        assertEquals(1, outcome.report.imported)
        assertEquals(1, outcome.report.skipped)
    }

    @Test
    fun import_rejectsUnsupportedVersion() = runTest {
        val badVersion = """{"schemaVersion":999,"exportedAt":0,"words":[]}"""
        assertTrue(BackupManager(FakeWordRepository()).import(badVersion) is ImportOutcome.UnsupportedVersion)
    }

    @Test
    fun import_rejectsInvalidJson() = runTest {
        assertTrue(BackupManager(FakeWordRepository()).import("not json at all") is ImportOutcome.Invalid)
    }
}
