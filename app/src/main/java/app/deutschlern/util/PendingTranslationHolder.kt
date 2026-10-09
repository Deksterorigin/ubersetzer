package app.deutschlern.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder for incoming translation requests passed via system intents
 * (ACTION_PROCESS_TEXT, ACTION_SEND).
 */
@Singleton
class PendingTranslationHolder @Inject constructor() {

    private val _pendingText = MutableSharedFlow<String>(replay = 1)
    val pendingText: SharedFlow<String> = _pendingText.asSharedFlow()

    fun setPendingText(text: String) {
        if (text.isNotBlank()) {
            _pendingText.tryEmit(text.trim())
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumePendingText() {
        _pendingText.resetReplayCache()
    }
}
