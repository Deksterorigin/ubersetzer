package app.deutschlern.ui.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.repository.DialogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DialoguesUiState(
    val dialogs: List<Dialog> = emptyList(),
    val isLoading: Boolean = false,
    val deletedDialog: Dialog? = null
)

@HiltViewModel
class DialoguesViewModel @Inject constructor(
    private val dialogRepository: DialogRepository
) : ViewModel() {

    private val _deletedDialogBackup = MutableStateFlow<Triple<Dialog, List<DialogSegment>, List<DialogTask>>?>(null)
    private val _uiState = MutableStateFlow(DialoguesUiState(isLoading = true))
    val uiState: StateFlow<DialoguesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dialogRepository.markInterruptedRecordings()
            combine(
                dialogRepository.getAllDialogs(),
                _deletedDialogBackup
            ) { dialogs, backup ->
                DialoguesUiState(
                    dialogs = dialogs,
                    isLoading = false,
                    deletedDialog = backup?.first
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteDialog(dialog: Dialog) {
        viewModelScope.launch {
            val segments = dialogRepository.getSegmentsSync(dialog.id)
            _deletedDialogBackup.value = Triple(dialog, segments, emptyList())
            dialogRepository.deleteDialog(dialog.id)
        }
    }

    fun undoDelete() {
        val backup = _deletedDialogBackup.value ?: return
        viewModelScope.launch {
            val (dialog, segments, _) = backup
            val reinsertedId = dialogRepository.saveDialog(dialog.copy(id = 0))
            if (segments.isNotEmpty()) {
                val remappedSegments = segments.map { it.copy(id = 0, dialogId = reinsertedId) }
                dialogRepository.insertSegments(remappedSegments)
            }
            _deletedDialogBackup.value = null
        }
    }

    fun clearUndo() {
        _deletedDialogBackup.value = null
    }
}
