package com.sozluk.ai.di

import com.sozluk.ai.data.image.CropStorage
import com.sozluk.ai.data.prefs.AppCompatInterfaceLocaleProvider
import com.sozluk.ai.data.repository.ExamRepositoryImpl
import com.sozluk.ai.data.repository.PendingRepositoryImpl
import com.sozluk.ai.data.repository.WordRepositoryImpl
import com.sozluk.ai.data.work.WorkManagerOfflineQueue
import com.sozluk.ai.domain.OfflineQueue
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.repository.CropStore
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.PendingRepository
import com.sozluk.ai.domain.repository.WordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository

    @Binds
    @Singleton
    abstract fun bindExamRepository(impl: ExamRepositoryImpl): ExamRepository

    @Binds
    @Singleton
    abstract fun bindPendingRepository(impl: PendingRepositoryImpl): PendingRepository

    @Binds
    @Singleton
    abstract fun bindOfflineQueue(impl: WorkManagerOfflineQueue): OfflineQueue

    @Binds
    @Singleton
    abstract fun bindCropStore(impl: CropStorage): CropStore

    @Binds
    @Singleton
    abstract fun bindInterfaceLocaleProvider(
        impl: AppCompatInterfaceLocaleProvider,
    ): InterfaceLocaleProvider
}
