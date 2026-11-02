package com.sozluk.ai.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WordEntity::class, ExamSessionEntity::class, QuizItemEntity::class, PendingWordEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun examDao(): ExamDao
    abstract fun pendingWordDao(): PendingWordDao

    companion object {
        const val NAME = "sozluk.db"

        // v2: offline queue for words awaiting AI explanation (spec F-1.5).
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_words` (
                        `id` TEXT NOT NULL,
                        `term` TEXT NOT NULL,
                        `contextSentence` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
