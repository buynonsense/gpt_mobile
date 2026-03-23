package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.sync.BackupRepository
import dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImpl
import dev.chungjungsoo.gptmobile.data.sync.SyncRepository
import dev.chungjungsoo.gptmobile.data.sync.SyncRepositoryImpl
import dev.chungjungsoo.gptmobile.data.sync.WebDavRepository
import dev.chungjungsoo.gptmobile.data.sync.WebDavRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideBackupRepository(impl: BackupRepositoryImpl): BackupRepository = impl

    @Provides
    @Singleton
    fun provideWebDavRepository(impl: WebDavRepositoryImpl): WebDavRepository = impl

    @Provides
    @Singleton
    fun provideSyncRepository(impl: SyncRepositoryImpl): SyncRepository = impl
}
