package app.deutschlern.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.deutschlern.R
import app.deutschlern.update.UpdateUiState
import app.deutschlern.update.UpdateViewModel
import app.deutschlern.update.model.UpdateError
import app.deutschlern.update.model.UpdateState

@Composable
fun UpdateSection(
    viewModel: UpdateViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Launch user action confirmation intent if pending from PackageInstaller
    LaunchedEffect(uiState.confirmationIntentToLaunch) {
        uiState.confirmationIntentToLaunch?.let { intent ->
            context.startActivity(intent)
            viewModel.onConfirmationIntentLaunched()
        }
    }

    // On resume, re-check unknown sources permission if pending
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val state = uiState.updateState
                if (state is UpdateState.PermissionRequired) {
                    viewModel.onResumeCheckPermissions(state.release)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Title
        Text(
            text = stringResource(R.string.update_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Version Info
        Text(
            text = stringResource(
                R.string.update_current_version,
                uiState.currentVersionName,
                uiState.currentVersionCode
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Wi-Fi Only Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.update_wifi_only_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.update_wifi_only_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = uiState.isWifiOnly,
                onCheckedChange = { viewModel.onToggleWifiOnly(it) }
            )
        }

        // Active State Display
        when (val state = uiState.updateState) {
            is UpdateState.Checking -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.update_status_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is UpdateState.UpToDate -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.update_status_up_to_date),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            is UpdateState.Downloading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_progress_downloading, state.progressPercent),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onCancelDownload() },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(text = stringResource(R.string.update_action_cancel), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            is UpdateState.Verifying -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.update_status_verifying),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is UpdateState.Installing -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.update_status_installing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is UpdateState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = resolveUpdateErrorMessage(state.error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(
                            onClick = { viewModel.onDismissDialog() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_action_understood),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            else -> Unit
        }

        // Check for updates button
        val isChecking = uiState.updateState is UpdateState.Checking
        val isDownloading = uiState.updateState is UpdateState.Downloading
        val isInstalling = uiState.updateState is UpdateState.Installing

        Button(
            onClick = { viewModel.onCheckForUpdates() },
            enabled = !isChecking && !isDownloading && !isInstalling,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.update_action_check))
        }
    }

    // Dialog: Update Available
    val availableState = uiState.updateState as? UpdateState.UpdateAvailable
    if (availableState != null) {
        val release = availableState.release
        val emptyVocabMsg = stringResource(R.string.words_export_empty)

        AlertDialog(
            onDismissRequest = { viewModel.onDismissDialog() },
            title = {
                Text(
                    text = stringResource(R.string.update_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_version_available, release.tagName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.update_size_format, release.formattedSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Release notes as PLAIN TEXT only (never HTML/Markdown)
                    if (release.releaseNotes.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.update_release_notes_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Backup Reminder Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.update_backup_reminder_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.onExportVocabularyBackup(context) {
                                        onShowSnackbar(emptyVocabMsg)
                                    }
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.update_backup_action_export_csv),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onDownloadAndInstall(release) },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(stringResource(R.string.update_action_download_install))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onDismissDialog() }
                ) {
                    Text(stringResource(R.string.update_action_later))
                }
            }
        )
    }

    // Dialog: Permission Required (Unknown Sources)
    val permissionState = uiState.updateState as? UpdateState.PermissionRequired
    if (permissionState != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDialog() },
            title = {
                Text(
                    text = stringResource(R.string.update_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.update_permission_explanation),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(stringResource(R.string.update_action_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onDismissDialog() }
                ) {
                    Text(stringResource(R.string.update_action_cancel))
                }
            }
        )
    }
}

@Composable
private fun resolveUpdateErrorMessage(error: UpdateError): String {
    return when (error) {
        is UpdateError.NetworkError -> stringResource(R.string.update_error_network)
        is UpdateError.RateLimitExceeded -> stringResource(R.string.update_error_rate_limit)
        is UpdateError.NoReleasesFound -> stringResource(R.string.update_error_not_found)
        is UpdateError.ParseVersionError -> stringResource(R.string.update_error_parse_version, error.rawTag)
        is UpdateError.NoApkAsset -> stringResource(R.string.update_error_no_apk_asset)
        is UpdateError.MultipleApkAssets -> stringResource(R.string.update_error_multiple_apk_assets)
        is UpdateError.WifiRequired -> stringResource(R.string.update_error_wifi_required)
        is UpdateError.InsufficientStorage -> stringResource(R.string.update_error_insufficient_space)
        is UpdateError.HashMismatch -> stringResource(R.string.update_error_hash_mismatch)
        is UpdateError.InvalidPackageName -> stringResource(R.string.update_error_invalid_package)
        is UpdateError.DowngradeAttempt -> stringResource(R.string.update_error_downgrade)
        is UpdateError.SignatureMismatch -> stringResource(R.string.update_error_signature_mismatch)
        is UpdateError.FileTooLarge -> stringResource(R.string.update_error_file_too_large)
        is UpdateError.InstallFailed -> stringResource(
            R.string.update_error_install_failed,
            error.statusCode.toString(),
            error.statusMessage ?: ""
        )
        is UpdateError.GenericError -> error.message
    }
}
