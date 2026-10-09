package app.deutschlern.di

import app.deutschlern.data.stt.AndroidSpeechEngine
import app.deutschlern.domain.stt.SpeechToTextEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SttModule {

    @Binds
    @Singleton
    abstract fun bindSpeechToTextEngine(
        impl: AndroidSpeechEngine
    ): SpeechToTextEngine
}
