package app.deutschlern

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeutschLernApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app.deutschlern.util.TempImageCleaner.clearTempOcrDirectory(this)
    }
}
