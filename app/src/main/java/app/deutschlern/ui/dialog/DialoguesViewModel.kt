package app.deutschlern.ui.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.dialog.DeletedDialog
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.repository.DialogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val lastDeleted = MutableStateFlow<DeletedDialog?>(null)

    val uiState: StateFlow<DialoguesUiState> = combine(
        dialogRepository.getAllDialogs(),
        lastDeleted
    ) { dialogs, deleted ->
        DialoguesUiState(dialogs = dialogs, isLoading = false, deletedDialog = deleted?.dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialoguesUiState(isLoading = true))

    fun deleteDialog(dialog: Dialog) {
        viewModelScope.launch {
            lastDeleted.value = dialogRepository.deleteDialog(dialog.id)
        }
    }

    fun undoDelete() {
        val deleted = lastDeleted.value ?: return
        lastDeleted.value = null
        viewModelScope.launch {
            dialogRepository.restoreDialog(deleted)
        }
    }

    fun clearUndo() {
        lastDeleted.value = null
    }
}
