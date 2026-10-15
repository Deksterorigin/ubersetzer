package app.deutschlern.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.deutschlern.BuildConfig
import app.deutschlern.R
import app.deutschlern.domain.model.AppLanguage
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.update.UpdateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissInfoMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Keystore recovery banner if needed
            if (uiState.isKeystoreRecoveryNeeded) {
                KeystoreRecoveryBanner(
                    onDismiss = { viewModel.onDismissRecoveryNotice() }
                )
            }

            // Privacy Notice
            PrivacyNoticeCard()

            // App Language Selector
            AppLanguageSection(
                selectedLanguageCode = uiState.selectedAppLanguageCode,
                onSelectLanguage = { viewModel.onSelectAppLanguage(it) }
            )

            HairlineDivider()

            // 1. Provider Pickers (Word, Text, Check)
            ActiveProvidersSection(
                uiState = uiState,
                onSelectWordProvider = { viewModel.onSelectWordProvider(it) },
                onSelectTextProvider = { viewModel.onSelectTextProvider(it) },
                onSelectCheckProvider = { viewModel.onSelectCheckProvider(it) }
            )

            HairlineDivider()

            // 2. Per-Provider Settings
            Text(
                text = stringResource(R.string.settings_section_providers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            uiState.providers.forEach { provider ->
                ProviderConfigCard(
                    provider = provider,
                    customBaseUrl = uiState.customBaseUrl,
                    customModel = uiState.customModel,
                    openRouterShowAll = uiState.openRouterShowAll,
                    onApiKeyChanged = { viewModel.onApiKeyChanged(provider.id, it) },
                    onToggleKeyVisibility = { viewModel.onToggleKeyVisibility(provider.id) },
                    onPasteKey = {
                        val pasted = clipboardManager.getText()?.text.orEmpty()
                        if (pasted.isNotBlank()) {
                            viewModel.onApiKeyChanged(provider.id, pasted.trim())
                        }
                    },
                    onModelChanged = { viewModel.onModelChanged(provider.id, it) },
                    onLoadModels = { viewModel.onLoadModelsClicked(provider.id) },
                    onTestConnection = { viewModel.onTestConnectionClicked(provider.id) },
                    onCustomConfigChanged = { url, model -> viewModel.onCustomConfigChanged(url, model) },
                    onToggleOpenRouterShowAll = { viewModel.onToggleOpenRouterShowAll(it) },
                    onDownloadMlKit = { viewModel.onDownloadMlKitClicked() },
                    onOpenUrl = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            HairlineDivider()

            // 3. Auto-fallback Section
            AutoFallbackSection(
                isEnabled = uiState.isAutoFallbackEnabled,
                fallbackOrder = uiState.fallbackOrder,
                providers = uiState.providers,
                onToggleFallback = { viewModel.onToggleAutoFallback(it) },
                onMoveUp = { index -> viewModel.onMoveFallbackProvider(index, index - 1) },
                onMoveDown = { index -> viewModel.onMoveFallbackProvider(index, index + 1) }
            )

            HairlineDivider()

            // 4. Offline & Cache Settings
            GeneralSettingsSection(
                isOfflineMlKitEnabled = uiState.isOfflineMlKitEnabled,
                showModelCaption = uiState.showModelCaption,
                useCrossModelCache = uiState.useCrossModelCache,
                coloredArticlesEnabled = uiState.coloredArticlesEnabled,
                onToggleOfflineMlKit = { viewModel.onToggleOfflineMlKit(it) },
                onToggleShowModelCaption = { viewModel.onToggleShowModelCaption(it) },
                onToggleUseCrossModelCache = { viewModel.onToggleUseCrossModelCache(it) },
                onToggleColoredArticles = { viewModel.onToggleColoredArticles(it) },
                onClearCache = { viewModel.onClearCacheClicked() }
            )

            HairlineDivider()

            // 5. Speech-to-Text Settings
            SttSettingsSection(
                preferOffline = uiState.sttPreferOnDevice,
                autoDetect = uiState.sttAutoDetect,
                translateImmediately = uiState.sttTranslateImmediately,
                onTogglePreferOffline = { viewModel.onToggleSttPreferOnDevice(it) },
                onToggleAutoDetect = { viewModel.onToggleSttAutoDetect(it) },
                onToggleTranslateImmediately = { viewModel.onToggleSttTranslateImmediately(it) }
            )

            // 6. App Updates (sideloaded builds only)
            if (BuildConfig.ENABLE_SELF_UPDATE) {
                HairlineDivider()

                UpdateSection(
                    viewModel = updateViewModel,
                    onShowSnackbar = { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KeystoreRecoveryBanner(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.settings_keystore_recovery_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.settings_keystore_recovery_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = stringResource(R.string.settings_action_understood), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PrivacyNoticeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ActiveProvidersSection(
    uiState: SettingsUiState,
    onSelectWordProvider: (String) -> Unit,
    onSelectTextProvider: (String) -> Unit,
    onSelectCheckProvider: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.settings_section_active_providers),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Word Provider Picker
        ProviderDropdownField(
            label = stringResource(R.string.settings_provider_word),
            selectedId = uiState.selectedWordProviderId,
            providers = uiState.providers.filter { it.supportsWordMode },
            onSelected = onSelectWordProvider
        )

        // Text Provider Picker
        ProviderDropdownField(
            label = stringResource(R.string.settings_provider_text),
            selectedId = uiState.selectedTextProviderId,
            providers = uiState.providers.filter { it.supportsTextMode },
            onSelected = onSelectTextProvider
        )

        // Check Provider Picker
        ProviderDropdownField(
            label = stringResource(R.string.settings_provider_check),
            selectedId = uiState.selectedCheckProviderId,
            providers = uiState.providers.filter { it.id != "deepl" && it.id != "mlkit" },
            onSelected = onSelectCheckProvider
        )
        Text(
            text = stringResource(R.string.settings_provider_check_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProviderDropdownField(
    label: String,
    selectedId: String,
    providers: List<ProviderUiModel>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentProvider = providers.find { it.id == selectedId }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentProvider?.displayName ?: selectedId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                providers.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.displayName)
                                if (p.id == selectedId) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        onClick = {
                            onSelected(p.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigCard(
    provider: ProviderUiModel,
    customBaseUrl: String,
    customModel: String,
    openRouterShowAll: Boolean,
    onApiKeyChanged: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onPasteKey: () -> Unit,
    onModelChanged: (String) -> Unit,
    onLoadModels: () -> Unit,
    onTestConnection: () -> Unit,
    onCustomConfigChanged: (String, String) -> Unit,
    onToggleOpenRouterShowAll: (Boolean) -> Unit,
    onDownloadMlKit: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Header: Provider Title, badging, toggle expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (provider.isOffline) {
                        BadgeLabel(stringResource(R.string.settings_badge_offline))
                    } else if (!provider.supportsWordMode) {
                        BadgeLabel(stringResource(R.string.settings_badge_text))
                    }
                }
                Text(
                    text = stringResource(R.string.settings_model_prefix, provider.selectedModel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expanded Configuration Body
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HairlineDivider()

                // Free tier note and links
                if (provider.freeTierNote.isNotBlank()) {
                    Text(
                        text = provider.freeTierNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    provider.keyUrl?.let { url ->
                        TextButton(onClick = { onOpenUrl(url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.settings_action_get_key), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    provider.rateLimitDocsUrl?.let { url ->
                        TextButton(onClick = { onOpenUrl(url) }) {
                            Text(text = stringResource(R.string.settings_rate_limits), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // API Key Field (if not offline)
                if (!provider.isOffline) {
                    OutlinedTextField(
                        value = provider.apiKey,
                        onValueChange = onApiKeyChanged,
                        label = { Text(stringResource(R.string.settings_api_key_label)) },
                        placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                        visualTransformation = if (provider.isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onToggleKeyVisibility) {
                                    Icon(
                                        imageVector = if (provider.isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                                IconButton(onClick = onPasteKey) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                // Custom provider HTTPS URL
                if (provider.id == "custom") {
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { onCustomConfigChanged(it, customModel) },
                        label = { Text(stringResource(R.string.settings_custom_base_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // DeepL usage info
                if (provider.id == "deepl" && provider.deeplUsage != null) {
                    Text(
                        text = stringResource(
                            R.string.settings_deepl_usage,
                            provider.deeplUsage.characterCount,
                            provider.deeplUsage.characterLimit
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Model field & selection
                if (!provider.isOffline && provider.id != "deepl") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = provider.selectedModel,
                                onValueChange = onModelChanged,
                                label = { Text(stringResource(R.string.settings_model_label)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedButton(
                                onClick = onLoadModels,
                                enabled = !provider.isLoadingModels,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (provider.isLoadingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(text = stringResource(R.string.settings_action_list_models), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // OpenRouter show all models toggle
                        if (provider.id == "openrouter") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_openrouter_show_all),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = openRouterShowAll,
                                    onCheckedChange = onToggleOpenRouterShowAll
                                )
                            }
                        }

                        // Loaded models picker dropdown
                        if (provider.availableModels.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .clickable { showModelMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_select_from_models, provider.availableModels.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = showModelMenu,
                                    onDismissRequest = { showModelMenu = false }
                                ) {
                                    provider.availableModels.take(40).forEach { modelId ->
                                        DropdownMenuItem(
                                            text = { Text(modelId, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                onModelChanged(modelId)
                                                showModelMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ML Kit model download button
                if (provider.id == "mlkit") {
                    Button(
                        onClick = onDownloadMlKit,
                        enabled = !provider.isMlKitDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (provider.isMlKitDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_mlkit_downloading))
                        } else if (provider.isMlKitDownloaded) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_mlkit_ready))
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_mlkit_download))
                        }
                    }
                }

                // Test Connection Button & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        enabled = !provider.isTesting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (provider.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.settings_action_test))
                        }
                    }

                    provider.testStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.contains("Работает", ignoreCase = true) || status.contains("загружен", ignoreCase = true))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoFallbackSection(
    isEnabled: Boolean,
    fallbackOrder: List<String>,
    providers: List<ProviderUiModel>,
    onToggleFallback: (Boolean) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.settings_auto_fallback_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleFallback
            )
        }

        if (isEnabled) {
            Text(
                text = stringResource(R.string.settings_fallback_order_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            fallbackOrder.forEachIndexed { index, providerId ->
                val p = providers.find { it.id == providerId }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${p?.displayName ?: providerId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row {
                        IconButton(
                            onClick = { onMoveUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onMoveDown(index) },
                            enabled = index < fallbackOrder.lastIndex,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsSection(
    isOfflineMlKitEnabled: Boolean,
    showModelCaption: Boolean,
    useCrossModelCache: Boolean,
    coloredArticlesEnabled: Boolean,
    onToggleOfflineMlKit: (Boolean) -> Unit,
    onToggleShowModelCaption: (Boolean) -> Unit,
    onToggleUseCrossModelCache: (Boolean) -> Unit,
    onToggleColoredArticles: (Boolean) -> Unit,
    onClearCache: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Colored articles toggle
        SettingSwitchRow(
            title = stringResource(R.string.settings_colored_articles),
            subtitle = stringResource(R.string.settings_colored_articles_desc),
            checked = coloredArticlesEnabled,
            onCheckedChange = onToggleColoredArticles
        )

        // ML Kit offline toggle
        SettingSwitchRow(
            title = stringResource(R.string.settings_offline_mlkit),
            subtitle = stringResource(R.string.settings_offline_mlkit_desc),
            checked = isOfflineMlKitEnabled,
            onCheckedChange = onToggleOfflineMlKit
        )

        // Show model attribution caption
        SettingSwitchRow(
            title = stringResource(R.string.settings_show_model_caption),
            subtitle = stringResource(R.string.settings_show_model_caption_desc),
            checked = showModelCaption,
            onCheckedChange = onToggleShowModelCaption
        )

        // Cross-model cache
        SettingSwitchRow(
            title = stringResource(R.string.settings_use_cross_model_cache),
            subtitle = stringResource(R.string.settings_use_cross_model_cache_desc),
            checked = useCrossModelCache,
            onCheckedChange = onToggleUseCrossModelCache
        )

        // Clear cache button
        OutlinedButton(
            onClick = onClearCache,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_clear_cache))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun BadgeLabel(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AppLanguageSection(
    selectedLanguageCode: String,
    onSelectLanguage: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLanguage = AppLanguage.fromCode(selectedLanguageCode)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_app_language_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val label = if (currentLanguage == AppLanguage.SYSTEM) {
                        stringResource(R.string.lang_system)
                    } else {
                        currentLanguage.nativeName
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_app_language_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.action_select_language),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 280.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val itemTitle = if (lang == AppLanguage.SYSTEM) {
                                        stringResource(R.string.lang_system)
                                    } else {
                                        lang.nativeName
                                    }
                                    Text(
                                        text = itemTitle,
                                        fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (lang != AppLanguage.SYSTEM && lang.displayName != lang.nativeName) {
                                        Text(
                                            text = lang.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (lang == currentLanguage) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectLanguage(lang.code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SttSettingsSection(
    preferOffline: Boolean,
    autoDetect: Boolean,
    translateImmediately: Boolean,
    onTogglePreferOffline: (Boolean) -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onToggleTranslateImmediately: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.settings_stt_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_stt_prefer_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_stt_prefer_offline_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferOffline,
                onCheckedChange = onTogglePreferOffline
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_stt_auto_detect),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_stt_auto_detect_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoDetect,
                onCheckedChange = onToggleAutoDetect
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_stt_translate_immediately),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_stt_translate_immediately_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = translateImmediately,
                onCheckedChange = onToggleTranslateImmediately
            )
        }
    }
}

