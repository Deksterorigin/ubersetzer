package app.deutschlern.ui.check

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.deutschlern.R
import app.deutschlern.ui.components.AppCard
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.ui.components.PrimaryActionButton
import app.deutschlern.ui.components.SkeletonLines
import app.deutschlern.ui.components.pressScale

/** German is shown left to right with German typography rules whatever the interface language. */
internal fun TextStyle.germanText(): TextStyle =
    copy(textDirection = TextDirection.Ltr, localeList = LocaleList("de"))

@Composable
fun CheckInputSection(
    uiState: CheckUiState,
    onInputChanged: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onScan: () -> Unit,
    onCheck: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!uiState.hasProviderKey) {
            MissingKeyCard(onOpenSettings)
        }

        CheckInputField(
            text = uiState.inputText,
            charCount = uiState.charCount,
            isLoading = uiState.isLoading,
            onInputChanged = onInputChanged,
            onClear = onClear,
            onPaste = onPaste
        )

        ScanTextButton(
            isRecognizing = uiState.isOcrLoading,
            enabled = !uiState.isLoading,
            onClick = onScan
        )

        PrimaryActionButton(
            text = stringResource(R.string.check_action_button),
            onClick = onCheck,
            enabled = uiState.canCheck,
            isLoading = uiState.isLoading
        )

        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(tween(200)) + expandVertically(tween(250)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
        ) {
            AppCard {
                Text(
                    text = stringResource(R.string.check_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SkeletonLines()
            }
        }

        uiState.errorMessage?.let { message ->
            CheckErrorCard(message, onRetry)
        }

        HairlineDivider(modifier = Modifier.padding(top = 12.dp))

        Text(
            text = stringResource(R.string.check_duden_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun MissingKeyCard(onOpenSettings: () -> Unit) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = stringResource(R.string.translate_add_key_card_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.check_no_key_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenSettings, shape = ControlShape) {
            Text(stringResource(R.string.translate_open_settings))
        }
    }
}

@Composable
private fun CheckInputField(
    text: String,
    charCount: Int,
    isLoading: Boolean,
    onInputChanged: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(200),
        label = "checkInputBorder"
    )

    AppCard(borderColor = borderColor, contentPadding = PaddingValues(0.dp)) {
        BasicTextField(
            value = text,
            onValueChange = onInputChanged,
            readOnly = isLoading,
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge.germanText()
                .copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(hintLocales = LocaleList("de")),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(16.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.check_input_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    innerTextField()
                }
            }
        )

        HairlineDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.char_counter_format, charCount, MAX_CHECK_INPUT_CHARS),
                style = MaterialTheme.typography.bodySmall,
                color = if (charCount >= MAX_CHECK_INPUT_CHARS) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (text.isNotEmpty()) {
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
}

@Composable
private fun ScanTextButton(
    isRecognizing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled && !isRecognizing,
        interactionSource = interactionSource,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecognizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(
                        if (isRecognizing) R.string.ocr_processing else R.string.check_camera_action
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isRecognizing) {
                    Text(
                        text = stringResource(R.string.check_scan_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
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
