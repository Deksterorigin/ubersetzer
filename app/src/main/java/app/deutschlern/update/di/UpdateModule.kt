package app.deutschlern.update.di

import android.content.Context
import app.deutschlern.update.data.ApkMetadataProvider
import app.deutschlern.update.data.ApkVerifier
import app.deutschlern.update.data.DefaultApkMetadataProvider
import app.deutschlern.update.data.DefaultNetworkConnectivityProvider
import app.deutschlern.update.data.DefaultPackageInstallerWrapper
import app.deutschlern.update.data.NetworkConnectivityProvider
import app.deutschlern.update.data.PackageInstallerWrapper
import app.deutschlern.update.data.UpdateRepository
import app.deutschlern.update.data.UpdateRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindingsModule {

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideApkMetadataProvider(
        @ApplicationContext context: Context
    ): ApkMetadataProvider = DefaultApkMetadataProvider(context)

    @Provides
    @Singleton
    fun provideApkVerifier(
        metadataProvider: ApkMetadataProvider
    ): ApkVerifier = ApkVerifier(metadataProvider)

    @Provides
    @Singleton
    fun providePackageInstallerWrapper(
        @ApplicationContext context: Context
    ): PackageInstallerWrapper = DefaultPackageInstallerWrapper(context)

    @Provides
    @Singleton
    fun provideNetworkConnectivityProvider(
        @ApplicationContext context: Context
    ): NetworkConnectivityProvider = DefaultNetworkConnectivityProvider(context)
}
