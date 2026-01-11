package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiMaskRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiMaskRepository(impl: AiMaskRepositoryImpl): AiMaskRepository
}
