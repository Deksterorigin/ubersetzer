package app.deutschlern.ui.check

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.domain.model.check.TextCheckEntry
import app.deutschlern.domain.repository.TextCheckRepository
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.appearOnce
import app.deutschlern.ui.components.pressScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CheckHistoryViewModel @Inject constructor(
    private val textCheckRepository: TextCheckRepository
) : ViewModel() {

    val historyItems: StateFlow<List<TextCheckEntry>> = textCheckRepository.getAllChecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteCheck(entry: TextCheckEntry) {
        viewModelScope.launch {
            textCheckRepository.deleteCheck(entry.id)
        }
    }

    fun restoreCheck(entry: TextCheckEntry) {
        viewModelScope.launch {
            textCheckRepository.saveCheck(entry)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            textCheckRepository.clearAllChecks()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckHistoryScreen(
    onNavigateBack: () -> Unit,
    onSelectCheck: (Long) -> Unit,
    viewModel: CheckHistoryViewModel = hiltViewModel()
) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    val deletedMessage = stringResource(R.string.check_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.check_history_clear_title)) },
            text = { Text(stringResource(R.string.check_history_clear_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllHistory()
                    },
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.check_history_clear_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }, shape = ControlShape) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        // The app-level scaffold already pads for the navigation bar
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.check_history_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.check_history_clear_action)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (items.isEmpty()) {
            HistoryEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { entry ->
                    HistoryCard(
                        entry = entry,
                        dateText = dateFormat.format(Date(entry.createdAt)),
                        onClick = { onSelectCheck(entry.id) },
                        onDelete = {
                            viewModel.deleteCheck(entry)
                            scope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = deletedMessage,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreCheck(entry)
                                }
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: TextCheckEntry,
    dateText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.originalText,
                    style = MaterialTheme.typography.bodyMedium.germanText(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.check_edits_count, entry.hunks.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.appearOnce()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.check_history_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.check_history_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
