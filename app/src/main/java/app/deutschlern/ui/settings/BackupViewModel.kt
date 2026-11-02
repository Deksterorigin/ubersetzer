package app.deutschlern.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.backup.BackupManager
import app.deutschlern.data.backup.BackupSettings
import app.deutschlern.data.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object ExportFailed : BackupMessage
    data class Restored(val result: RestoreResult) : BackupMessage
    data object RestoreFailed : BackupMessage
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    val settings: StateFlow<BackupSettings> = backupManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupSettings())

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun onAutoBackupChanged(enabled: Boolean) {
        viewModelScope.launch { backupManager.setAutoBackupEnabled(enabled) }
    }

    fun onIncludeKeysChanged(include: Boolean) {
        viewModelScope.launch { backupManager.setIncludeApiKeys(include) }
    }

    fun onExport(uri: Uri) = runBusy {
        _message.value = if (backupManager.exportTo(uri).isSuccess) BackupMessage.Exported else BackupMessage.ExportFailed
    }

    fun onImport(uri: Uri) = runBusy {
        _message.value = backupManager.importFrom(uri).fold(
            onSuccess = { BackupMessage.Restored(it) },
            onFailure = { BackupMessage.RestoreFailed }
        )
    }

    fun onMessageShown() {
        _message.value = null
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                block()
            } finally {
                _isBusy.value = false
            }
        }
    }
}
