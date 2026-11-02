package com.sozluk.ai.di

import com.sozluk.ai.data.ai.GeminiAiClient
import com.sozluk.ai.data.prefs.ApiKeyStore
import com.sozluk.ai.data.prefs.EncryptedApiKeyStore
import com.sozluk.ai.domain.ai.AiClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the AI client and key-store implementations to their interfaces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    // App uses the Gemini client (a Google API key works on the free tier). The Anthropic client
    // is kept in the codebase but no longer bound; swap this back to bind it if you switch keys.
    @Binds
    @Singleton
    abstract fun bindAiClient(impl: GeminiAiClient): AiClient

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: EncryptedApiKeyStore): ApiKeyStore
}
