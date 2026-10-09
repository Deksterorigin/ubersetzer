package app.deutschlern.ui.check

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import app.deutschlern.util.TempImageCleaner
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.ui.components.HairlineDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataIntent = result.data
            val scanningResult = if (dataIntent != null) {
                GmsDocumentScanningResult.fromActivityResultIntent(dataIntent)
            } else null
            val pages = scanningResult?.pages ?: emptyList()
            val uris = pages.mapNotNull { it.imageUri }
            if (uris.isNotEmpty()) {
                viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(uris))
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = tempCameraUri
        if (success && uri != null) {
            viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(listOf(uri)))
        } else if (uri != null) {
            TempImageCleaner.deleteTempUri(context, uri)
        }
        tempCameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(uris))
        }
    }

    val launchDocumentScanner = {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(3)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            val scanner = GmsDocumentScanning.getClient(options)
            val activity = context as? Activity
            if (activity != null) {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener {
                        try {
                            val file = TempImageCleaner.createTempImageFile(context)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        } catch (_: Exception) {}
                    }
            }
        } catch (_: Exception) {
            try {
                val file = TempImageCleaner.createTempImageFile(context)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (_: Exception) {}
        }
    }

    val launchCamera = {
        try {
            val file = TempImageCleaner.createTempImageFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {}
    }

    val launchGallery = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(uiState.addWordSuccessMessage) {
        uiState.addWordSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onEvent(CheckUiEvent.OnDismissAddWordMessage)
        }
    }

    LaunchedEffect(uiState.ocrErrorMessage) {
        uiState.ocrErrorMessage?.let { errKey ->
            val msgRes = if (errKey == "ocr_error_no_text") {
                R.string.ocr_error_no_text
            } else {
                R.string.ocr_error_failed
            }
            snackbarHostState.showSnackbar(context.getString(msgRes))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.result == null) {
                // Input View
                CheckInputSection(
                    inputText = uiState.inputText,
                    charCount = uiState.charCount,
                    canCheck = uiState.canCheck,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onInputChanged = { viewModel.onEvent(CheckUiEvent.OnInputChanged(it)) },
                    onClear = { viewModel.onEvent(CheckUiEvent.OnClearInput) },
                    onPaste = {
                        val text = clipboardManager.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            viewModel.onEvent(CheckUiEvent.OnPasteText(text))
                        }
                    },
                    onCamera = { viewModel.onEvent(CheckUiEvent.OnOpenPhotoSourceDialog) },
                    onCheck = { viewModel.onEvent(CheckUiEvent.OnCheckClicked) },
                    onRetry = { viewModel.onEvent(CheckUiEvent.OnRetryClicked) }
                )
            } else {
                // Result View
                CheckResultSection(
                    uiState = uiState,
                    onSelectMode = { viewModel.onEvent(CheckUiEvent.OnSelectViewMode(it)) },
                    onSelectCategory = { viewModel.onEvent(CheckUiEvent.OnSelectCategoryFilter(it)) },
                    onHunkClick = { viewModel.onEvent(CheckUiEvent.OnOpenHunkDetail(it)) },
                    onAcceptAll = { viewModel.onEvent(CheckUiEvent.OnAcceptAllHunks) },
                    onCopy = {
                        val finalTxt = viewModel.getAssembledFinalText()
                        clipboardManager.setText(AnnotatedString(finalTxt))
                    },
                    onShare = {
                        val finalTxt = viewModel.getAssembledFinalText()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, finalTxt)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                    onNewCheck = { viewModel.onEvent(CheckUiEvent.OnResetCheck) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Hunk Detail Bottom Sheet
        uiState.activeHunkDetail?.let { hunk ->
            HunkDetailBottomSheet(
                hunk = hunk,
                isAddingToVocab = uiState.isAddingWordToVocab,
                onDismiss = { viewModel.onEvent(CheckUiEvent.OnDismissHunkDetail) },
                onToggleAccepted = { viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance(hunk.id)) },
                onAddToVocab = { viewModel.onEvent(CheckUiEvent.OnAddHunkToVocabulary(hunk)) }
            )
        }

        // Photo Source Bottom Sheet
        if (uiState.showPhotoSourceDialog) {
            PhotoSourceBottomSheet(
                onDismiss = { viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog) },
                onSelectScanner = {
                    viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
                    launchDocumentScanner()
                },
                onSelectCamera = {
                    viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
                    launchCamera()
                },
                onSelectGallery = {
                    viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
                    launchGallery()
                }
            )
        }

        // OCR Loading Dialog
        if (uiState.isOcrLoading) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.ocr_processing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )
        }

        // OCR Preview Bottom Sheet
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

@Composable
fun CheckInputSection(
    inputText: String,
    charCount: Int,
    canCheck: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onInputChanged: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onCamera: () -> Unit,
    onCheck: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.check_input_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Toolbar: counter, Camera, Paste, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$charCount / 5000",
                style = MaterialTheme.typography.bodySmall,
                color = if (charCount > 5000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onCamera,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = stringResource(R.string.ocr_scan_button_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (inputText.isNotBlank()) {
                    IconButton(onClick = onClear, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = onPaste, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.action_paste),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Check Action Button
        Button(
            onClick = onCheck,
            enabled = canCheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = stringResource(R.string.check_action_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_retry),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HairlineDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.check_duden_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckResultSection(
    uiState: CheckUiState,
    onSelectMode: (CheckViewMode) -> Unit,
    onSelectCategory: (CheckCategory?) -> Unit,
    onHunkClick: (CheckEditHunk) -> Unit,
    onAcceptAll: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNewCheck: () -> Unit
) {
    val result = uiState.result ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        // Mode Selector: "Исправлено" / "Оригинал"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.viewMode == CheckViewMode.CORRECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelectMode(CheckViewMode.CORRECTED) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.check_mode_corrected),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (uiState.viewMode == CheckViewMode.CORRECTED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.viewMode == CheckViewMode.ORIGINAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelectMode(CheckViewMode.ORIGINAL) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.check_mode_original),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (uiState.viewMode == CheckViewMode.ORIGINAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Over-edit Warning Banner
        if (result.isOverEditWarning) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.check_overedit_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Header: "Исправлений: N"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.check_edits_count, uiState.totalEditsCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (uiState.totalEditsCount > 0) {
                TextButton(onClick = onAcceptAll) {
                    Text(stringResource(R.string.check_action_accept_all))
                }
            }
        }

        // Category Filter Chips
        if (uiState.totalEditsCount > 0) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.categoryFilter == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text(stringResource(R.string.check_filter_all)) }
                )
                FilterChip(
                    selected = uiState.categoryFilter == CheckCategory.SPELLING,
                    onClick = { onSelectCategory(CheckCategory.SPELLING) },
                    label = { Text(stringResource(R.string.check_category_spelling)) }
                )
                FilterChip(
                    selected = uiState.categoryFilter == CheckCategory.GRAMMAR,
                    onClick = { onSelectCategory(CheckCategory.GRAMMAR) },
                    label = { Text(stringResource(R.string.check_category_grammar)) }
                )
                FilterChip(
                    selected = uiState.categoryFilter == CheckCategory.PUNCTUATION,
                    onClick = { onSelectCategory(CheckCategory.PUNCTUATION) },
                    label = { Text(stringResource(R.string.check_category_punctuation)) }
                )
                FilterChip(
                    selected = uiState.categoryFilter == CheckCategory.WORD_CHOICE,
                    onClick = { onSelectCategory(CheckCategory.WORD_CHOICE) },
                    label = { Text(stringResource(R.string.check_category_word_choice)) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Content Area: Strict LTR bidi isolation for German text
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (uiState.totalEditsCount == 0) {
                        Column {
                            Text(
                                text = result.originalText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.check_empty_no_errors),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.check_empty_no_errors_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (uiState.viewMode == CheckViewMode.ORIGINAL) {
                        Text(
                            text = result.originalText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        // Inline Diff
                        InlineDiffContent(
                            originalText = result.originalText,
                            hunks = result.hunks,
                            onHunkClick = onHunkClick
                        )
                    }
                }
            }
        }

        // Comments ("Замечания") Section
        if (result.comments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.check_comments_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            result.comments.forEach { comment ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "«${comment.fragment}»",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = comment.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Toolbar: Copy, Share, New check
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_copy))
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_share))
            }

            Button(
                onClick = onNewCheck,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.check_action_new))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HairlineDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.check_duden_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun InlineDiffContent(
    originalText: String,
    hunks: List<CheckEditHunk>,
    onHunkClick: (CheckEditHunk) -> Unit
) {
    val sortedHunks = remember(hunks) { hunks.sortedBy { it.startOffset } }
    val primaryColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val normalColor = MaterialTheme.colorScheme.onSurface

    val annotatedString = remember(originalText, sortedHunks) {
        buildAnnotatedString {
            var cursor = 0
            for ((index, hunk) in sortedHunks.withIndex()) {
                if (hunk.startOffset > cursor && hunk.startOffset <= originalText.length) {
                    append(originalText.substring(cursor, hunk.startOffset))
                }

                val hunkTag = "hunk_$index"
                pushStringAnnotation(tag = "HUNK", annotation = hunkTag)

                if (hunk.isAccepted) {
                    // Show corrected in accent color
                    withStyle(
                        SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(hunk.corrected)
                    }
                } else {
                    // Show original with strikethrough in muted grey
                    withStyle(
                        SpanStyle(
                            color = mutedColor,
                            textDecoration = TextDecoration.LineThrough
                        )
                    ) {
                        append(hunk.original)
                    }
                }
                pop()

                cursor = maxOf(cursor, hunk.endOffset)
            }

            if (cursor < originalText.length) {
                append(originalText.substring(cursor))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(color = normalColor),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "HUNK", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val index = annotation.item.removePrefix("hunk_").toIntOrNull()
                    if (index != null && index in sortedHunks.indices) {
                        onHunkClick(sortedHunks[index])
                    }
                }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HunkDetailBottomSheet(
    hunk: CheckEditHunk,
    isAddingToVocab: Boolean,
    onDismiss: () -> Unit,
    onToggleAccepted: () -> Unit,
    onAddToVocab: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Category Badge & Rule
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val categoryName = when (hunk.category) {
                    CheckCategory.SPELLING -> stringResource(R.string.check_category_spelling)
                    CheckCategory.GRAMMAR -> stringResource(R.string.check_category_grammar)
                    CheckCategory.PUNCTUATION -> stringResource(R.string.check_category_punctuation)
                    CheckCategory.WORD_CHOICE -> stringResource(R.string.check_category_word_choice)
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                hunk.rule?.let { ruleText ->
                    Text(
                        text = ruleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Original vs Corrected comparison
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hunk.original,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textDecoration = TextDecoration.LineThrough
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = hunk.corrected,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation in partner language
            Text(
                text = hunk.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Actions: Toggle Accept/Reject, Add to Vocabulary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleAccepted,
                    modifier = Modifier.weight(1f),
                    colors = if (hunk.isAccepted) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (hunk.isAccepted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hunk.isAccepted) stringResource(R.string.check_hunk_accepted) else stringResource(R.string.check_hunk_rejected)
                    )
                }

                if (hunk.isSingleWord && (hunk.category == CheckCategory.SPELLING || hunk.category == CheckCategory.WORD_CHOICE)) {
                    OutlinedButton(
                        onClick = onAddToVocab,
                        enabled = !isAddingToVocab,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.check_add_to_vocab))
                    }
                }
            }
        }
    }
}

@Composable
fun ConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.check_consent_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.check_consent_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceBottomSheet(
    onDismiss: () -> Unit,
    onSelectScanner: () -> Unit,
    onSelectCamera: () -> Unit,
    onSelectGallery: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.ocr_source_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Document Scanner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSelectScanner)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.ocr_source_document_scanner),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Option 2: Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSelectCamera)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.ocr_source_camera),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Option 3: Gallery
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSelectGallery)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.ocr_source_gallery),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPreviewBottomSheet(
    previewText: String,
    onTextChanged: (String) -> Unit,
    onInsert: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.ocr_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.ocr_preview_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = previewText,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.ocr_preview_cancel_action))
                }

                Button(
                    onClick = onInsert,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.ocr_preview_insert_action))
                }
            }
        }
    }
}
