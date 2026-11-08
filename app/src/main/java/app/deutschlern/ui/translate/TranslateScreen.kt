package app.deutschlern.ui.translate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.deutschlern.R
import app.deutschlern.ui.components.PrimaryActionButton
import app.deutschlern.ui.components.appearOnce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SectionGap = 12.dp

// Long enough for the result's entrance and the keyboard's exit to finish, so the measured bounds are final.
private const val RESULT_SETTLE_MILLIS = 340L

@Composable
fun TranslateScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    viewModel: TranslateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.showTtsInstallSnackbar) {
        if (uiState.showTtsInstallSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.tts_voice_missing),
                actionLabel = context.getString(R.string.action_install_tts),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
            viewModel.onEvent(TranslateUiEvent.OnDismissTtsSnackbar)
        }
    }

    LaunchedEffect(uiState.saveSuccessMessage) {
        uiState.saveSuccessMessage?.let { savedLemma ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.saved_success, savedLemma),
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(TranslateUiEvent.OnDismissSaveMessage)
        }
    }

    LaunchedEffect(uiState.fallbackNotification) {
        uiState.fallbackNotification?.let { notice ->
            snackbarHostState.showSnackbar(
                message = notice,
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(TranslateUiEvent.OnDismissFallbackNotification)
        }
    }

    LaunchedEffect(uiState.speechErrorMessage) {
        uiState.speechErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(TranslateUiEvent.OnDismissSpeechError)
        }
    }

    var resultTop by remember { mutableFloatStateOf(0f) }
    var resultHeight by remember { mutableIntStateOf(0) }
    val revealMarginPx = with(LocalDensity.current) { 24.dp.toPx() }

    // Keyed on the result rather than isLoading: a cached answer can flip loading on and off within one frame.
    val currentResult = uiState.wordTranslation?.lemma ?: uiState.translatedText
    var revealedResult by remember { mutableStateOf(currentResult) }

    LaunchedEffect(currentResult) {
        val isNewResult = currentResult != null && currentResult != revealedResult
        revealedResult = currentResult
        if (isNewResult) {
            delay(RESULT_SETTLE_MILLIS)
            scrollState.animateScrollTo(
                scrollState.offsetToReveal(resultTop, resultTop + resultHeight, revealMarginPx)
            )
        }
    }

    var pendingMicIsContext by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = pendingMicIsContext))
        } else {
            showPermissionRationale = true
        }
    }

    val requestMic: (Boolean) -> Unit = { isContext ->
        pendingMicIsContext = isContext
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (uiState.isListening && uiState.isListeningContext == isContext) {
                viewModel.onEvent(TranslateUiEvent.OnStopListening)
            } else {
                viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = isContext))
            }
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val translate = {
        // The keyboard would otherwise cover the result the user is about to read.
        if (uiState.canTranslate) focusManager.clearFocus()
        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        // Sections pad themselves instead of using spacedBy: a gap around an animated slot would pop in and out.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Header(
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.appearOnce(0)
            )

            AnimatedVisibility(
                visible = !uiState.hasAnyKeyConfigured,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(120))
            ) {
                MissingKeyCard(
                    isWordMode = uiState.isWordMode,
                    isMlKitDownloaded = uiState.isMlKitDownloaded,
                    isDownloadingMlKit = uiState.isDownloadingMlKit,
                    onOpenSettings = onNavigateToSettings,
                    onDownloadMlKit = { viewModel.onEvent(TranslateUiEvent.OnDownloadMlKit) },
                    modifier = Modifier.padding(top = SectionGap)
                )
            }

            LanguageSelector(
                direction = uiState.direction,
                selectedPartner = uiState.selectedPartnerLanguage,
                onSwap = { viewModel.onEvent(TranslateUiEvent.OnSwapDirectionClicked) },
                onSelectPartner = { viewModel.onEvent(TranslateUiEvent.OnSelectPartnerLanguage(it)) },
                modifier = Modifier
                    .padding(top = SectionGap)
                    .appearOnce(1)
            )

            InputCard(
                state = uiState,
                onEvent = viewModel::onEvent,
                onPaste = {
                    val pasted = clipboardManager.getText()?.text.orEmpty()
                    if (pasted.isNotEmpty()) {
                        viewModel.onEvent(TranslateUiEvent.OnPasteText(pasted))
                    }
                },
                onTranslate = translate,
                onMicClicked = { requestMic(false) },
                modifier = Modifier
                    .padding(top = SectionGap)
                    .appearOnce(2)
            )

            AnimatedVisibility(
                visible = uiState.isWordMode,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
            ) {
                ContextCard(
                    contextText = uiState.contextText,
                    isExpanded = uiState.isContextExpanded,
                    isListening = uiState.isListening && uiState.isListeningContext,
                    partialText = uiState.speechPartialText,
                    onContextChanged = { viewModel.onEvent(TranslateUiEvent.OnContextTextChanged(it)) },
                    onToggleExpanded = { viewModel.onEvent(TranslateUiEvent.OnToggleContextExpanded) },
                    onMicClicked = { requestMic(true) },
                    modifier = Modifier.padding(top = SectionGap)
                )
            }

            PrimaryActionButton(
                text = stringResource(R.string.action_translate),
                onClick = translate,
                enabled = uiState.canTranslate,
                isLoading = uiState.isLoading,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .appearOnce(3)
            )

            AnimatedContent(
                targetState = uiState.errorMessage,
                contentKey = { it != null },
                transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(150))) },
                modifier = Modifier.fillMaxWidth(),
                label = "translateError"
            ) { message ->
                if (message != null) {
                    ErrorBanner(
                        message = message,
                        onRetry = { viewModel.onEvent(TranslateUiEvent.OnRetryClicked) },
                        modifier = Modifier.padding(top = SectionGap)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth())
                }
            }

            TranslateResultArea(
                state = uiState,
                onEvent = viewModel::onEvent,
                onCopy = { translation ->
                    clipboardManager.setText(AnnotatedString(translation))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.copied_to_clipboard),
                            withDismissAction = true
                        )
                    }
                },
                onShare = { translation ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, translation)
                        type = "text/plain"
                    }
                    val chooser = Intent.createChooser(sendIntent, null).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                },
                modifier = Modifier
                    .padding(top = SectionGap)
                    .onGloballyPositioned { coordinates ->
                        resultTop = coordinates.positionInParent().y
                        resultHeight = coordinates.size.height
                    }
            )
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.stt_permission_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.stt_permission_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.duplicate_word_cancel))
                }
            }
        )
    }
}

@Composable
private fun Header(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.tab_translate),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Scrolls as little as possible, except that a result taller than the screen is shown from its start. */
private fun ScrollState.offsetToReveal(top: Float, bottom: Float, margin: Float): Int {
    val visibleBottom = value + viewportSize
    return when {
        top >= value && bottom <= visibleBottom -> value
        bottom - top + 2 * margin >= viewportSize -> (top - margin).toInt()
        else -> (bottom - viewportSize + margin).toInt()
    }
}
