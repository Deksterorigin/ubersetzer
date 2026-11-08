package app.deutschlern.ui.translate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deutschlern.R
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.ui.components.AppCard
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.ui.components.SkeletonLines
import app.deutschlern.ui.components.WordCard
import app.deutschlern.ui.components.pressScale
import kotlinx.coroutines.delay

private const val COPIED_FEEDBACK_MILLIS = 1500L

// German on purpose: these are what the learner taps to try the app, whatever the UI language is.
private val ExampleInputs = listOf("Fernweh", "Guten Morgen", "Ich hätte gern einen Kaffee.")

/** What occupies the area under the translate button. Holds its own data so a card can fade out intact. */
private sealed interface ResultSlot {
    data object Empty : ResultSlot
    data object Examples : ResultSlot
    data object Loading : ResultSlot
    data class Word(
        val word: WordTranslation,
        val direction: TranslationDirection,
        val selectedMeaningIndex: Int?,
        val attribution: String?
    ) : ResultSlot
    data class Translation(
        val text: String,
        val isRtl: Boolean,
        val attribution: String?
    ) : ResultSlot
}

// Selecting a meaning updates the word in place; only a different result should replay the entrance.
private val ResultSlot.animationKey: Any
    get() = when (this) {
        is ResultSlot.Word -> word.lemma
        is ResultSlot.Translation -> text
        else -> this
    }

private fun TranslateUiState.toResultSlot(): ResultSlot {
    val attribution = sourceModelAttribution.takeIf { showModelCaption }
    val word = wordTranslation
    val translation = translatedText
    return when {
        isLoading -> ResultSlot.Loading
        word != null -> ResultSlot.Word(word, direction, selectedMeaningIndex, attribution)
        translation != null -> ResultSlot.Translation(translation, isResultRtl, attribution)
        canOfferExamples -> ResultSlot.Examples
        else -> ResultSlot.Empty
    }
}

// A direction the user pinned to partner-to-German would translate a German example the wrong way round.
private val TranslateUiState.canOfferExamples: Boolean
    get() = inputText.isEmpty() && !isListening && !(hasManualDirectionOverride && !direction.isFromGerman)

@Composable
fun TranslateResultArea(
    state: TranslateUiState,
    onEvent: (TranslateUiEvent) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val slideOffsetPx = with(LocalDensity.current) { 12.dp.roundToPx() }

    AnimatedContent(
        targetState = state.toResultSlot(),
        contentKey = { it.animationKey },
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(280)) { slideOffsetPx })
                .togetherWith(fadeOut(tween(120)))
                .using(SizeTransform { _, _ -> tween(260) })
        },
        modifier = modifier.fillMaxWidth(),
        label = "translateResult"
    ) { slot ->
        when (slot) {
            ResultSlot.Empty -> Box(modifier = Modifier.fillMaxWidth())
            ResultSlot.Examples -> ExamplesHint(
                canTranslateWords = state.hasAnyKeyConfigured,
                onExampleClicked = { onEvent(TranslateUiEvent.OnInputTextChanged(it)) }
            )
            ResultSlot.Loading -> AppCard {
                SkeletonLines()
            }
            is ResultSlot.Word -> Column {
                WordCard(
                    word = slot.word,
                    direction = slot.direction,
                    selectedMeaningIndex = slot.selectedMeaningIndex,
                    onMeaningClicked = { onEvent(TranslateUiEvent.OnSelectMeaning(it)) },
                    onSaveEquivalent = { onEvent(TranslateUiEvent.OnSaveEquivalent(it)) },
                    onSpeak = { onEvent(TranslateUiEvent.OnSpeakWord(it)) }
                )
                ModelRow(
                    attribution = slot.attribution,
                    onRefresh = { onEvent(TranslateUiEvent.OnRefreshWithDifferentModelClicked) }
                )
            }
            is ResultSlot.Translation -> Column {
                TranslationCard(
                    text = slot.text,
                    isRtl = slot.isRtl,
                    onCopy = { onCopy(slot.text) },
                    onShare = { onShare(slot.text) }
                )
                ModelRow(
                    attribution = slot.attribution,
                    onRefresh = { onEvent(TranslateUiEvent.OnRefreshWithDifferentModelClicked) }
                )
            }
        }
    }
}

@Composable
private fun TranslationCard(
    text: String,
    isRtl: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        SelectionContainer(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Start,
                    textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                ),
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }

        HairlineDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CopyButton(onCopy = onCopy)
            IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
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
private fun CopyButton(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MILLIS)
            copied = false
        }
    }

    IconButton(
        onClick = {
            onCopy()
            copied = true
        },
        modifier = Modifier.size(48.dp)
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.6f))
                    .togetherWith(fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.6f))
            },
            label = "copyFeedback"
        ) { showCheck ->
            if (showCheck) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.copied_to_clipboard),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    attribution: String?,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (attribution != null) {
            Text(
                text = attribution,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        TextButton(
            onClick = onRefresh,
            shape = ControlShape,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.translate_refresh_different_model),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamplesHint(
    canTranslateWords: Boolean,
    onExampleClicked: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.translate_examples_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A single word needs a key, and a first try should not end in an error
            ExampleInputs.filter { canTranslateWords || ' ' in it }.forEach { example ->
                ExampleChip(text = example, onClick = { onExampleClicked(example) })
            }
        }
    }
}

@Composable
private fun ExampleChip(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier.pressScale(interactionSource),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interactionSource
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        TextButton(
            onClick = onRetry,
            shape = ControlShape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.action_retry),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissingKeyCard(
    isWordMode: Boolean,
    isMlKitDownloaded: Boolean,
    isDownloadingMlKit: Boolean,
    onOpenSettings: () -> Unit,
    onDownloadMlKit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = Color.Transparent,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedContent(
            targetState = isWordMode,
            transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(120))) },
            label = "missingKeyMessage"
        ) { wordMode ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = if (wordMode) Icons.Default.Key else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(
                            if (wordMode) R.string.translate_add_key_card_title else R.string.translate_download_mlkit_prompt
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Text(
                        text = stringResource(
                            if (wordMode) R.string.translate_add_key_card_desc else R.string.translate_offline_mlkit_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(onClick = onOpenSettings, shape = ControlShape) {
                Text(
                    text = stringResource(R.string.translate_open_settings),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (!isWordMode && !isMlKitDownloaded) {
                OutlinedButton(
                    onClick = onDownloadMlKit,
                    enabled = !isDownloadingMlKit,
                    shape = ControlShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.4f))
                ) {
                    if (isDownloadingMlKit) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(
                            if (isDownloadingMlKit) R.string.translate_downloading else R.string.translate_download_mlkit_btn
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
