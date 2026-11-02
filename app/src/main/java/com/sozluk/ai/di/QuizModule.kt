package com.sozluk.ai.di

import com.sozluk.ai.data.quiz.AiQuizSourceImpl
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.quiz.AiQuizSource
import com.sozluk.ai.domain.quiz.ExamComposer
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.generators.AudioChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.ExampleGapGenerator
import com.sozluk.ai.domain.quiz.generators.LetterScrambleGenerator
import com.sozluk.ai.domain.quiz.generators.ReverseChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.TranslationChoiceGenerator
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.srs.SrsUpdater
import com.sozluk.ai.domain.srs.WordSelector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the pure-Kotlin quiz/SRS engine (spec §7). No AI source is bound yet — task 08. */
@Module
@InstallIn(SingletonComponent::class)
object QuizModule {

    @Provides
    @Singleton
    fun provideLocalQuizGenerators(): List<LocalQuizGenerator> = listOf(
        TranslationChoiceGenerator(),
        ReverseChoiceGenerator(),
        LetterScrambleGenerator(),
        AudioChoiceGenerator(),
        ExampleGapGenerator(),
    )

    @Provides
    @Singleton
    fun provideWordSelector(): WordSelector = WordSelector()

    @Provides
    @Singleton
    fun provideSrsUpdater(): SrsUpdater = SrsUpdater()

    @Provides
    @Singleton
    fun provideAiQuizSource(
        aiClient: AiClient,
        generators: List<@JvmSuppressWildcards LocalQuizGenerator>,
        wordRepository: WordRepository,
        interfaceLocaleProvider: InterfaceLocaleProvider,
    ): AiQuizSource = AiQuizSourceImpl(
        aiClient = aiClient,
        generators = generators,
        wordRepository = wordRepository,
        interfaceLocaleProvider = interfaceLocaleProvider,
    )

    @Provides
    @Singleton
    fun provideExamComposer(
        wordSelector: WordSelector,
        generators: List<@JvmSuppressWildcards LocalQuizGenerator>,
        wordRepository: WordRepository,
        examRepository: ExamRepository,
        settingsRepository: SettingsRepository,
        aiSource: AiQuizSource,
    ): ExamComposer = ExamComposer(
        wordSelector = wordSelector,
        generators = generators,
        wordRepository = wordRepository,
        examRepository = examRepository,
        settingsRepository = settingsRepository,
        aiSource = aiSource,
    )
}
