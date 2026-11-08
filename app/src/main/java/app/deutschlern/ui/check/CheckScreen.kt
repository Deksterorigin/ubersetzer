package app.deutschlern.ui.check

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    savedCheckId: Long? = null,
    onSavedCheckOpened: () -> Unit = {},
    viewModel: CheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(savedCheckId) {
        if (savedCheckId != null) {
            viewModel.onEvent(CheckUiEvent.OnLoadSavedCheck(savedCheckId))
            onSavedCheckOpened()
        }
    }

    // The key may have just been added on the settings screen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(CheckUiEvent.OnRefreshProviderKey)
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.copied_to_clipboard)

    val imageSources = rememberOcrImageSources(
        onImages = { viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(it)) },
        onUnavailable = { viewModel.onEvent(CheckUiEvent.OnOcrSourceFailed) }
    )

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(CheckUiEvent.OnDismissMessage)
        }
    }

    LaunchedEffect(uiState.ocrError) {
        uiState.ocrError?.let { error ->
            val messageRes = when (error) {
                OcrError.NO_TEXT -> R.string.ocr_error_no_text
                OcrError.RECOGNITION_FAILED -> R.string.ocr_error_failed
                OcrError.SOURCE_UNAVAILABLE -> R.string.ocr_error_source_unavailable
            }
            snackbarHostState.showSnackbar(context.getString(messageRes))
            viewModel.onEvent(CheckUiEvent.OnDismissOcrError)
        }
    }

    if (uiState.showConsentDialog) {
        ConsentDialog(
            onConfirm = { viewModel.onEvent(CheckUiEvent.OnAcceptConsent) },
            onDismiss = { viewModel.onEvent(CheckUiEvent.OnDeclineConsent) }
        )
    }

    Scaffold(
        // The app-level scaffold already reserves the bottom bar and navigation bar
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.check_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.check_history_action)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        AnimatedContent(
            targetState = uiState.result,
            contentKey = { it != null },
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "checkInputResult",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { result ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                if (result == null) {
                    CheckInputSection(
                        uiState = uiState,
                        onInputChanged = { viewModel.onEvent(CheckUiEvent.OnInputChanged(it)) },
                        onClear = { viewModel.onEvent(CheckUiEvent.OnClearInput) },
                        onPaste = {
                            val text = clipboardManager.getText()?.text.orEmpty()
                            if (text.isNotBlank()) {
                                viewModel.onEvent(CheckUiEvent.OnPasteText(text))
                            }
                        },
                        onScan = { viewModel.onEvent(CheckUiEvent.OnOpenPhotoSourceDialog) },
                        onCheck = { viewModel.onEvent(CheckUiEvent.OnCheckClicked) },
                        onRetry = { viewModel.onEvent(CheckUiEvent.OnRetryClicked) },
                        onOpenSettings = onNavigateToSettings
                    )
                } else {
                    CheckResultSection(
                        // The leaving screen keeps rendering its own result during the transition
                        uiState = uiState.copy(result = result),
                        onSelectMode = { viewModel.onEvent(CheckUiEvent.OnSelectViewMode(it)) },
                        onSelectCategory = { viewModel.onEvent(CheckUiEvent.OnSelectCategoryFilter(it)) },
                        onHunkClick = { viewModel.onEvent(CheckUiEvent.OnOpenHunkDetail(it)) },
                        onAcceptAll = { viewModel.onEvent(CheckUiEvent.OnAcceptAllHunks) },
                        onRejectAll = { viewModel.onEvent(CheckUiEvent.OnRejectAllHunks) },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(viewModel.getAssembledFinalText()))
                            viewModel.onEvent(CheckUiEvent.OnShowMessage(copiedMessage))
                        },
                        onShare = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, viewModel.getAssembledFinalText())
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        },
                        onNewCheck = { viewModel.onEvent(CheckUiEvent.OnResetCheck) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        uiState.activeHunkDetail?.let { hunk ->
            HunkDetailBottomSheet(
                hunk = hunk,
                isAddingToVocab = uiState.isAddingWordToVocab,
                onDismiss = { viewModel.onEvent(CheckUiEvent.OnDismissHunkDetail) },
                onToggleAccepted = { viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance(hunk.id)) },
                onAddToVocab = { viewModel.onEvent(CheckUiEvent.OnAddHunkToVocabulary(hunk)) }
            )
        }

        if (uiState.showPhotoSourceDialog) {
            PhotoSourceBottomSheet(
                onDismiss = { viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog) },
                onSelectScanner = {
                    viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
                    imageSources.launchScanner()
                },
                onSelectCamera = {
                    viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
                    imageSources.launchCamera()
                },
                onSelectGallery = {
                    viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
                    imageSources.launchGallery()
                }
            )
        }

        uiState.ocrSelection?.let { selection ->
            OcrSelectionDialog(selection = selection, onEvent = viewModel::onEvent)
        }

        // Composed after the selection dialog so that its window stacks above it
        uiState.ocrPreviewText?.let { previewText ->
            OcrPreviewBottomSheet(
                previewText = previewText,
                onTextChanged = { viewModel.onEvent(CheckUiEvent.OnOcrPreviewTextChanged(it)) },
                onInsert = { viewModel.onEvent(CheckUiEvent.OnInsertOcrText) },
                onDismiss = { viewModel.onEvent(CheckUiEvent.OnDismissOcrPreview) }
            )
        }
    }
}
