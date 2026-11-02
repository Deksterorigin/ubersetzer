package app.deutschlern.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupSection(
    onShowMessage: (String) -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onImport) }

    val exportedText = stringResource(R.string.backup_export_done)
    val exportFailedText = stringResource(R.string.backup_export_failed)
    val restoreFailedText = stringResource(R.string.backup_import_failed)
    val restoredResult = (message as? BackupMessage.Restored)?.result
    val restoredText = restoredResult?.let {
        stringResource(R.string.backup_import_done, it.wordsAdded, it.keysRestored)
    }

    LaunchedEffect(message) {
        val text = when (message) {
            BackupMessage.Exported -> exportedText
            BackupMessage.ExportFailed -> exportFailedText
            BackupMessage.RestoreFailed -> restoreFailedText
            is BackupMessage.Restored -> restoredText
            null -> null
        }
        if (text != null) {
            onShowMessage(text)
            viewModel.onMessageShown()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.backup_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.backup_section_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingSwitchRow(
            title = stringResource(R.string.backup_auto_title),
            subtitle = settings.lastAutoBackupAt?.let {
                stringResource(
                    R.string.backup_last_time,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                )
            } ?: stringResource(R.string.backup_auto_desc),
            checked = settings.autoBackupEnabled,
            onCheckedChange = viewModel::onAutoBackupChanged
        )

        SettingSwitchRow(
            title = stringResource(R.string.backup_include_keys_title),
            subtitle = stringResource(R.string.backup_include_keys_warning),
            checked = settings.includeApiKeys,
            onCheckedChange = viewModel::onIncludeKeysChanged
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("deutschlern-backup.json") },
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.backup_export_action))
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.backup_import_action))
            }
        }
    }
}
