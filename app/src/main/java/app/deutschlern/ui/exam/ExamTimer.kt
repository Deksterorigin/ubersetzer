package app.deutschlern.ui.exam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TimerState(
    val totalSeconds: Int,
    val remainingSeconds: Int = totalSeconds,
    val running: Boolean = false
) {
    val finished: Boolean get() = remainingSeconds <= 0
}

/** Countdown for exam tasks, ticking in the owning ViewModel's scope. */
class ExamTimer(private val scope: CoroutineScope, totalSeconds: Int) {

    private val _state = MutableStateFlow(TimerState(totalSeconds))
    val state: StateFlow<TimerState> = _state.asStateFlow()
    private var job: Job? = null

    fun toggle() = if (_state.value.running) pause() else start()

    fun start() {
        if (_state.value.finished) _state.update { it.copy(remainingSeconds = it.totalSeconds) }
        job?.cancel()
        _state.update { it.copy(running = true) }
        job = scope.launch {
            while (isActive && _state.value.remainingSeconds > 0) {
                delay(1_000)
                _state.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }
            _state.update { it.copy(running = false) }
        }
    }

    fun pause() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun reset(totalSeconds: Int = _state.value.totalSeconds) {
        job?.cancel()
        _state.value = TimerState(totalSeconds)
    }
}

fun formatSeconds(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
