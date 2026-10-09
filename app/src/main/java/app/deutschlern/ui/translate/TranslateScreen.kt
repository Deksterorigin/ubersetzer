package app.deutschlern.ui.translate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.deutschlern.domain.model.PartnerLanguage
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.deutschlern.R
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.ui.components.WordCard
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.TextButton

@Composable
fun TranslateScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    viewModel: TranslateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Handle TTS Missing Voice Data Snackbar
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

    // Handle Save Equivalent Snackbar
    LaunchedEffect(uiState.saveSuccessMessage) {
        uiState.saveSuccessMessage?.let { savedLemma ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.saved_success, savedLemma),
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(TranslateUiEvent.OnDismissSaveMessage)
        }
    }

    // Handle Fallback Snackbar
    LaunchedEffect(uiState.fallbackNotification) {
        uiState.fallbackNotification?.let { notice ->
            snackbarHostState.showSnackbar(
                message = notice,
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(TranslateUiEvent.OnDismissFallbackNotification)
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

    // Handle Speech Error Snackbar
    LaunchedEffect(uiState.speechErrorMessage) {
        uiState.speechErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.onEvent(TranslateUiEvent.OnDismissSpeechError)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Title & Settings Gear Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.tab_translate),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.translate_placeholder_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

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

            HairlineDivider()

            // Missing API Key / Offline Hint Card
            if (!uiState.hasAnyKeyConfigured) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (uiState.isWordMode)
                                stringResource(R.string.translate_add_key_card_title)
                            else
                                stringResource(R.string.translate_download_mlkit_prompt),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiState.isWordMode)
                                stringResource(R.string.translate_add_key_card_desc)
                            else
                                stringResource(R.string.translate_offline_mlkit_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onNavigateToSettings,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(stringResource(R.string.translate_open_settings), style = MaterialTheme.typography.labelSmall)
                            }
                            if (!uiState.isWordMode && !uiState.isMlKitDownloaded) {
                                OutlinedButton(
                                    onClick = { viewModel.onEvent(TranslateUiEvent.OnDownloadMlKit) },
                                    enabled = !uiState.isDownloadingMlKit,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (uiState.isDownloadingMlKit) stringResource(R.string.translate_downloading) else stringResource(R.string.translate_download_mlkit_btn),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Language Pair Header with Swap Button and Partner Language Selector
            LanguagePairHeader(
                sourceName = uiState.direction.sourceDisplayName,
                targetName = uiState.direction.targetDisplayName,
                selectedPartner = uiState.selectedPartnerLanguage,
                isFromGerman = uiState.direction.isFromGerman,
                onSwapClicked = {
                    viewModel.onEvent(TranslateUiEvent.OnSwapDirectionClicked)
                },
                onSelectPartnerLanguage = { lang ->
                    viewModel.onEvent(TranslateUiEvent.OnSelectPartnerLanguage(lang))
                }
            )

            // Input Area
            InputArea(
                text = uiState.inputText,
                charCount = uiState.charCount,
                isRtl = uiState.isInputRtl,
                isListening = uiState.isListening,
                isListeningContext = uiState.isListeningContext,
                partialText = uiState.speechPartialText,
                alternatives = uiState.speechAlternatives,
                activeSpeechLang = uiState.activeSpeechLanguageCode,
                selectedPartner = uiState.selectedPartnerLanguage,
                onTextChanged = { viewModel.onEvent(TranslateUiEvent.OnInputTextChanged(it)) },
                onClearClicked = { viewModel.onEvent(TranslateUiEvent.OnClearInputClicked) },
                onPasteClicked = {
                    val pasted = clipboardManager.getText()?.text.orEmpty()
                    if (pasted.isNotEmpty()) {
                        viewModel.onEvent(TranslateUiEvent.OnPasteText(pasted))
                    }
                },
                onTranslateAction = { viewModel.onEvent(TranslateUiEvent.OnTranslateClicked) },
                onMicClicked = { requestMic(false) },
                onSelectSpeechLang = { viewModel.onEvent(TranslateUiEvent.OnSelectSpeechLanguage(it)) },
                onSelectAlternative = { viewModel.onEvent(TranslateUiEvent.OnSelectSpeechAlternative(it)) }
            )

            // Collapsible Context Field (Visible in WORD mode)
            if (uiState.isWordMode) {
                ContextInputField(
                    contextText = uiState.contextText,
                    isExpanded = uiState.isContextExpanded,
                    isListening = uiState.isListening && uiState.isListeningContext,
                    partialText = uiState.speechPartialText,
                    onContextChanged = { viewModel.onEvent(TranslateUiEvent.OnContextTextChanged(it)) },
                    onToggleExpanded = { viewModel.onEvent(TranslateUiEvent.OnToggleContextExpanded) },
                    onMicClicked = { requestMic(true) }
                )
            }

            // Translate Action Button
            Button(
                onClick = { viewModel.onEvent(TranslateUiEvent.OnTranslateClicked) },
                enabled = uiState.canTranslate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = stringResource(R.string.action_translate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Subtle Linear Loading Indicator
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // Error Display with Retry Button
            uiState.errorMessage?.let { errorText ->
                ErrorBanner(
                    message = errorText,
                    onRetryClicked = { viewModel.onEvent(TranslateUiEvent.OnRetryClicked) }
                )
            }

            // Result Area: WordCard (for WORD mode) or ResultArea (for TEXT mode)
            if (uiState.wordTranslation != null || uiState.translatedText != null) {
                uiState.wordTranslation?.let { word ->
                    WordCard(
                        word = word,
                        direction = uiState.direction,
                        selectedMeaningIndex = uiState.selectedMeaningIndex,
                        onMeaningClicked = { viewModel.onEvent(TranslateUiEvent.OnSelectMeaning(it)) },
                        onSaveEquivalent = { viewModel.onEvent(TranslateUiEvent.OnSaveEquivalent(it)) },
                        onSpeak = { viewModel.onEvent(TranslateUiEvent.OnSpeakWord(it)) }
                    )
                } ?: uiState.translatedText?.let { translation ->
                    ResultArea(
                        translatedText = translation,
                        isRtl = uiState.isResultRtl,
                        onCopyClicked = {
                            clipboardManager.setText(AnnotatedString(translation))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.copied_to_clipboard),
                                    withDismissAction = true
                                )
                            }
                        },
                        onShareClicked = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, translation)
                                type = "text/plain"
                            }
                            val chooser = Intent.createChooser(sendIntent, null).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                    )
                }

                // Model attribution caption & Refresh with another model button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.showModelCaption && uiState.sourceModelAttribution != null) {
                        Text(
                            text = uiState.sourceModelAttribution.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = { viewModel.onEvent(TranslateUiEvent.OnRefreshWithDifferentModelClicked) },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.translate_refresh_different_model),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
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
private fun ContextInputField(
    contextText: String,
    isExpanded: Boolean,
    isListening: Boolean,
    partialText: String?,
    onContextChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.context_field_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (contextText.isNotBlank() && !isExpanded) {
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = contextText,
                    onValueChange = onContextChanged,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.context_field_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onMicClicked) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = if (isListening)
                                    stringResource(R.string.stt_stop_listening)
                                else
                                    stringResource(R.string.stt_mic_button_desc),
                                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                AnimatedVisibility(visible = isListening && !partialText.isNullOrBlank()) {
                    Text(
                        text = partialText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePairHeader(
    sourceName: String,
    targetName: String,
    selectedPartner: PartnerLanguage,
    isFromGerman: Boolean,
    onSwapClicked: () -> Unit,
    onSelectPartnerLanguage: (PartnerLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source side
        if (isFromGerman) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Partner language on source side (clickable dropdown)
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.action_select_language),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    PartnerLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lang.displayName,
                                        fontWeight = if (lang == selectedPartner) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (lang == selectedPartner) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelectPartnerLanguage(lang)
                            }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onSwapClicked,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.action_swap_direction),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Target side
        if (!isFromGerman) {
            Text(
                text = targetName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Partner language on target side (clickable dropdown)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.action_select_language),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    PartnerLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lang.displayName,
                                        fontWeight = if (lang == selectedPartner) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (lang == selectedPartner) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelectPartnerLanguage(lang)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    charCount: Int,
    isRtl: Boolean = false,
    isListening: Boolean = false,
    isListeningContext: Boolean = false,
    partialText: String? = null,
    alternatives: List<String> = emptyList(),
    activeSpeechLang: String? = null,
    selectedPartner: PartnerLanguage = PartnerLanguage.RUSSIAN,
    onTextChanged: (String) -> Unit,
    onClearClicked: () -> Unit,
    onPasteClicked: () -> Unit,
    onTranslateAction: () -> Unit,
    onMicClicked: () -> Unit,
    onSelectSpeechLang: (String) -> Unit,
    onSelectAlternative: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // Multiline Text Field
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            placeholder = {
                Text(
                    text = stringResource(R.string.translate_input_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                textAlign = if (isRtl) TextAlign.Right else TextAlign.Start,
                textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { onTranslateAction() },
                onDone = { onTranslateAction() },
                onSend = { onTranslateAction() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        AnimatedVisibility(visible = isListening && !isListeningContext && !partialText.isNullOrBlank()) {
            Text(
                text = partialText.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        AnimatedVisibility(visible = alternatives.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.stt_alternatives_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    alternatives.forEach { alt ->
                        AssistChip(
                            onClick = { onSelectAlternative(alt) },
                            label = { Text(alt, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }

        HairlineDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Bottom Controls: Mic + Language chips + Paste / Clear actions + Character Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Button
                IconButton(
                    onClick = onMicClicked,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isListening && !isListeningContext)
                            stringResource(R.string.stt_stop_listening)
                        else
                            stringResource(R.string.stt_mic_button_desc),
                        tint = if (isListening && !isListeningContext)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }

                // Dual Language Chips (DE and Partner)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isDe = (activeSpeechLang == "de")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onSelectSpeechLang("de") }
                    ) {
                        Text(
                            text = "DE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isDe) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val partnerCode = selectedPartner.code
                    val isPartner = (activeSpeechLang == partnerCode)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPartner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onSelectSpeechLang(partnerCode) }
                    ) {
                        Text(
                            text = partnerCode.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isPartner) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPartner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = onClearClicked,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = onPasteClicked,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.action_paste),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.char_counter_format, charCount, 5000),
                style = MaterialTheme.typography.labelSmall,
                color = if (charCount > 5000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultArea(
    translatedText: String,
    isRtl: Boolean = false,
    onCopyClicked: () -> Unit,
    onShareClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selectable translated text
        SelectionContainer {
            Text(
                text = translatedText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Start,
                    textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                ),
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }

        HairlineDivider()

        // Actions Row: Copy and Share buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCopyClicked,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onShareClicked,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        OutlinedButton(
            onClick = onRetryClicked,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.action_retry),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.action_retry),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
