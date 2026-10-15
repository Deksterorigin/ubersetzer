package app.deutschlern.update.receiver

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Events emitted during the package installer session lifecycle.
 */
sealed class InstallEvent {
    data class PendingUserAction(val confirmationIntent: Intent) : InstallEvent()
    object Success : InstallEvent()
    data class Failure(val statusCode: Int, val message: String?) : InstallEvent()
}

/**
 * Singleton notifier bridge between InstallResultReceiver and UpdateRepository/ViewModel.
 */
@Singleton
class InstallStatusNotifier @Inject constructor() {

    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<InstallEvent> = _events.asSharedFlow()

    fun emitEvent(event: InstallEvent) {
        _events.tryEmit(event)
    }
}
