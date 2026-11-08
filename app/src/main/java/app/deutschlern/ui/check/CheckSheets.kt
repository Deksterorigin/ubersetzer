package app.deutschlern.ui.check

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.deutschlern.R
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.PrimaryActionButton
import app.deutschlern.ui.components.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HunkDetailBottomSheet(
    hunk: CheckEditHunk,
    isAddingToVocab: Boolean,
    onDismiss: () -> Unit,
    onToggleAccepted: () -> Unit,
    onAddToVocab: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = ControlShape
                ) {
                    Text(
                        text = categoryLabel(hunk.category),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = stringResource(
                        if (hunk.isAccepted) R.string.check_hunk_accepted else R.string.check_hunk_rejected
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hunk.isAccepted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = hunkDiffLine(hunk),
                    style = MaterialTheme.typography.titleMedium.germanText(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            hunk.rule?.takeIf { it.isNotBlank() }?.let { rule ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rule,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = hunk.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hunk.isAccepted) {
                    OutlinedButton(
                        onClick = onToggleAccepted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = ControlShape
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.check_hunk_keep_original))
                    }
                } else {
                    PrimaryActionButton(
                        text = stringResource(R.string.check_hunk_apply),
                        onClick = onToggleAccepted,
                        icon = Icons.Default.Check
                    )
                }

                if (hunk.isSingleWord && (hunk.category == CheckCategory.SPELLING || hunk.category == CheckCategory.WORD_CHOICE)) {
                    TextButton(
                        onClick = onAddToVocab,
                        enabled = !isAddingToVocab,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ControlShape
                    ) {
                        if (isAddingToVocab) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
            Button(onClick = onConfirm, shape = ControlShape) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ControlShape) {
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.ocr_source_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            PhotoSourceCard(
                icon = Icons.Default.DocumentScanner,
                title = stringResource(R.string.ocr_source_scanner_title),
                description = stringResource(R.string.ocr_source_scanner_desc),
                onClick = onSelectScanner
            )
            PhotoSourceCard(
                icon = Icons.Default.PhotoCamera,
                title = stringResource(R.string.ocr_source_camera_title),
                description = stringResource(R.string.ocr_source_camera_desc),
                onClick = onSelectCamera
            )
            PhotoSourceCard(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.ocr_source_gallery_title),
                description = stringResource(R.string.ocr_source_gallery_desc),
                onClick = onSelectGallery
            )
        }
    }
}

@Composable
private fun PhotoSourceCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = ControlShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.ocr_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

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
                textStyle = MaterialTheme.typography.bodyLarge.germanText(),
                keyboardOptions = KeyboardOptions(hintLocales = LocaleList("de")),
                shape = ControlShape,
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
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = ControlShape
                ) {
                    Text(stringResource(R.string.action_back))
                }

                PrimaryActionButton(
                    text = stringResource(R.string.ocr_preview_insert_action),
                    onClick = onInsert,
                    enabled = previewText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
