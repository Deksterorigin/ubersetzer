package app.deutschlern.di

import app.deutschlern.data.repository.TranslationRepositoryImpl
import app.deutschlern.data.repository.VocabularyRepositoryImpl
import app.deutschlern.domain.repository.TranslationRepository
import app.deutschlern.domain.repository.VocabularyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTranslationRepository(
        impl: TranslationRepositoryImpl
    ): TranslationRepository = impl

    @Provides
    @Singleton
    fun provideVocabularyRepository(
        impl: VocabularyRepositoryImpl
    ): VocabularyRepository = impl

    @Provides
    @Singleton
    fun provideReviewRepository(
        impl: app.deutschlern.data.repository.ReviewRepositoryImpl
    ): app.deutschlern.domain.repository.ReviewRepository = impl

    @Provides
    @Singleton
    fun provideSettingsRepository(
        impl: app.deutschlern.data.repository.SettingsRepositoryImpl
    ): app.deutschlern.domain.repository.SettingsRepository = impl

    @Provides
    @Singleton
    fun provideTextCheckRepository(
        impl: app.deutschlern.data.repository.TextCheckRepositoryImpl
    ): app.deutschlern.domain.repository.TextCheckRepository = impl

    @Provides
    @Singleton
    fun provideTextRecognitionEngine(
        impl: app.deutschlern.data.ocr.MlKitTextRecognitionEngine
    ): app.deutschlern.domain.ocr.TextRecognitionEngine = impl

    @Provides
    @Singleton
    fun provideDialogRepository(
        impl: app.deutschlern.data.repository.DialogRepositoryImpl
    ): app.deutschlern.domain.repository.DialogRepository = impl
}
