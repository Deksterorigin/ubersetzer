package app.deutschlern.di

import app.deutschlern.data.local.RoomTransactionRunner
import app.deutschlern.data.local.TransactionRunner
import app.deutschlern.data.ocr.MlKitTextRecognitionEngine
import app.deutschlern.data.repository.DialogRepositoryImpl
import app.deutschlern.data.repository.ReviewRepositoryImpl
import app.deutschlern.data.repository.SettingsRepositoryImpl
import app.deutschlern.data.repository.TextCheckRepositoryImpl
import app.deutschlern.data.repository.TranslationRepositoryImpl
import app.deutschlern.data.repository.VocabularyRepositoryImpl
import app.deutschlern.domain.ocr.TextRecognitionEngine
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.TextCheckRepository
import app.deutschlern.domain.repository.TranslationRepository
import app.deutschlern.domain.repository.VocabularyRepository
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
    abstract fun bindTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyRepository(impl: VocabularyRepositoryImpl): VocabularyRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTextCheckRepository(impl: TextCheckRepositoryImpl): TextCheckRepository

    @Binds
    @Singleton
    abstract fun bindTextRecognitionEngine(impl: MlKitTextRecognitionEngine): TextRecognitionEngine

    @Binds
    @Singleton
    abstract fun bindDialogRepository(impl: DialogRepositoryImpl): DialogRepository

    @Binds
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
