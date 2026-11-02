package com.sozluk.ai.di

import android.content.Context
import androidx.room.Room
import com.sozluk.ai.data.db.AppDatabase
import com.sozluk.ai.data.db.ExamDao
import com.sozluk.ai.data.db.PendingWordDao
import com.sozluk.ai.data.db.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideWordDao(db: AppDatabase): WordDao = db.wordDao()

    @Provides
    fun provideExamDao(db: AppDatabase): ExamDao = db.examDao()

    @Provides
    fun providePendingWordDao(db: AppDatabase): PendingWordDao = db.pendingWordDao()
}
