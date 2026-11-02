package app.deutschlern

import android.app.Application
import app.deutschlern.di.ApplicationScope
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.ui.dialog.components.TranscriptTokenizer
import app.deutschlern.util.TempImageCleaner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DeutschLernApp : Application() {

    @Inject
    lateinit var dialogRepository: DialogRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        TempImageCleaner.clearTempOcrDirectory(this)
        // No recording can be active in a fresh process, so any RECORDING dialog was interrupted
        applicationScope.launch { dialogRepository.markInterruptedRecordings() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            TranscriptTokenizer.clearCache()
        }
    }
}
