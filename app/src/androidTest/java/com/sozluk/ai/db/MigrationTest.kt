package com.sozluk.ai.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sozluk.ai.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_createsPendingWordsTable_andKeepsData() {
        // Create at v1 with one word row that must survive the migration.
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                INSERT INTO words (
                    id, term, normalizedTerm, sourceLang, explanationEn, translationRu,
                    translationTk, partOfSpeech, ipa, synonymsJson, examplesJson, commonMistake,
                    contextSentence, sourceType, imagePath, createdAt, box, nextReviewAt,
                    correctCount, wrongCount, consecutiveCorrect, status
                ) VALUES (
                    'w1', 'bank', 'bank', 'en', 'expl', 'берег', 'kenar', 'noun', NULL, '[]', '[]',
                    NULL, NULL, 'MANUAL', NULL, 0, 0, 0, 0, 0, 'LEARNING'
                )
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 2, true, AppDatabase.MIGRATION_1_2)

        // The pre-existing word survived.
        db.query("SELECT term FROM words WHERE id = 'w1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("bank", c.getString(0))
        }
        // The new table exists and is usable.
        db.execSQL(
            "INSERT INTO pending_words (id, term, contextSentence, createdAt) " +
                "VALUES ('p1', 'serendipity', NULL, 123)",
        )
        db.query("SELECT COUNT(*) FROM pending_words").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }
}
