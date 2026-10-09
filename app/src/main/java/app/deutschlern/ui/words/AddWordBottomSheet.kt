package app.deutschlern.ui.words

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deutschlern.R
import app.deutschlern.domain.model.WordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordBottomSheet(
    uiState: WordsUiState,
    onEvent: (WordsUiEvent) -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onEvent(WordsUiEvent.OnDismissAddWordSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.add_word_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Tabs: Translate / Manual / Bulk
            TabRow(
                selectedTabIndex = uiState.addWordTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = uiState.addWordTab == AddWordTab.TRANSLATE,
                    onClick = { onEvent(WordsUiEvent.OnSelectAddWordTab(AddWordTab.TRANSLATE)) },
                    text = { Text(stringResource(R.string.add_word_tab_translate)) }
                )
                Tab(
                    selected = uiState.addWordTab == AddWordTab.MANUAL,
                    onClick = { onEvent(WordsUiEvent.OnSelectAddWordTab(AddWordTab.MANUAL)) },
                    text = { Text(stringResource(R.string.add_word_tab_manual)) }
                )
                Tab(
                    selected = uiState.addWordTab == AddWordTab.BULK,
                    onClick = { onEvent(WordsUiEvent.OnSelectAddWordTab(AddWordTab.BULK)) },
                    text = { Text(stringResource(R.string.add_word_tab_bulk)) }
                )
            }

            when (uiState.addWordTab) {
                AddWordTab.TRANSLATE -> {
                    TranslateTabContent(
                        uiState = uiState,
                        onEvent = onEvent,
                        onMicClicked = onMicClicked
                    )
                }
                AddWordTab.MANUAL -> {
                    ManualTabContent(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
                AddWordTab.BULK -> {
                    BulkTabContent(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslateTabContent(
    uiState: WordsUiState,
    onEvent: (WordsUiEvent) -> Unit,
    onMicClicked: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.addWordTranslateInput,
            onValueChange = { onEvent(WordsUiEvent.OnAddWordInputChange(it)) },
            label = { Text(stringResource(R.string.add_word_german_word_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onMicClicked) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.stt_mic_button_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        Button(
            onClick = { onEvent(WordsUiEvent.OnAddWordTranslateClicked) },
            enabled = uiState.addWordTranslateInput.isNotBlank() && !uiState.isAddWordTranslating,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (uiState.isAddWordTranslating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.action_translate))
        }

        uiState.addWordTranslateError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        uiState.addWordTranslatedResult?.let { result ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildString {
                        if (!result.article.isNullOrBlank()) append("${result.article} ")
                        append(result.lemma)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = result.pos,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.add_word_translation_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                result.meanings.forEachIndexed { index, meaning ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(WordsUiEvent.OnAddWordSelectMeaning(index)) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.addWordSelectedMeaningIndex == index,
                            onClick = { onEvent(WordsUiEvent.OnAddWordSelectMeaning(index)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = meaning.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (!meaning.exampleDe.isNullOrBlank()) {
                                Text(
                                    text = meaning.exampleDe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onEvent(WordsUiEvent.OnAddWordSaveClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.add_word_save_button))
                }
            }
        }
    }
}

@Composable
private fun ManualTabContent(
    uiState: WordsUiState,
    onEvent: (WordsUiEvent) -> Unit
) {
    val articles = listOf(null, "der", "die", "das")
    val poses = listOf("noun", "verb", "adjective", "adverb", "other")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = uiState.manualGermanWord,
            onValueChange = {
                onEvent(
                    WordsUiEvent.OnManualWordChange(
                        word = it,
                        article = uiState.manualArticle,
                        pos = uiState.manualPos,
                        translation = uiState.manualTranslation,
                        example = uiState.manualExample
                    )
                )
            },
            label = { Text(stringResource(R.string.add_word_german_word_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Article Selector Chips
        Text(
            text = stringResource(R.string.add_word_article_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            articles.forEach { art ->
                val label = art ?: "—"
                FilterChip(
                    selected = uiState.manualArticle == art,
                    onClick = {
                        onEvent(
                            WordsUiEvent.OnManualWordChange(
                                word = uiState.manualGermanWord,
                                article = art,
                                pos = uiState.manualPos,
                                translation = uiState.manualTranslation,
                                example = uiState.manualExample
                            )
                        )
                    },
                    label = { Text(label) }
                )
            }
        }

        // POS Selector Chips
        Text(
            text = stringResource(R.string.add_word_pos_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            poses.forEach { posOption ->
                FilterChip(
                    selected = uiState.manualPos == posOption,
                    onClick = {
                        onEvent(
                            WordsUiEvent.OnManualWordChange(
                                word = uiState.manualGermanWord,
                                article = uiState.manualArticle,
                                pos = posOption,
                                translation = uiState.manualTranslation,
                                example = uiState.manualExample
                            )
                        )
                    },
                    label = { Text(posOption, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        OutlinedTextField(
            value = uiState.manualTranslation,
            onValueChange = {
                onEvent(
                    WordsUiEvent.OnManualWordChange(
                        word = uiState.manualGermanWord,
                        article = uiState.manualArticle,
                        pos = uiState.manualPos,
                        translation = it,
                        example = uiState.manualExample
                    )
                )
            },
            label = { Text(stringResource(R.string.add_word_translation_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.manualExample,
            onValueChange = {
                onEvent(
                    WordsUiEvent.OnManualWordChange(
                        word = uiState.manualGermanWord,
                        article = uiState.manualArticle,
                        pos = uiState.manualPos,
                        translation = uiState.manualTranslation,
                        example = it
                    )
                )
            },
            label = { Text(stringResource(R.string.add_word_example_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        uiState.manualError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onEvent(WordsUiEvent.OnManualEnhanceWithAiClicked) },
                enabled = uiState.manualGermanWord.isNotBlank() && !uiState.isEnhancingWithAi,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (uiState.isEnhancingWithAi) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_word_ai_enhancing))
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_word_ai_enhance))
                }
            }

            Button(
                onClick = { onEvent(WordsUiEvent.OnManualSaveClicked) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.add_word_save_button))
            }
        }
    }
}

@Composable
private fun BulkTabContent(
    uiState: WordsUiState,
    onEvent: (WordsUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.bulkInputText,
            onValueChange = { onEvent(WordsUiEvent.OnBulkInputChange(it)) },
            label = { Text(stringResource(R.string.bulk_add_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            enabled = !uiState.isBulkTranslating
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.bulk_add_count, uiState.bulkWordsCount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.bulkWordsCount > 30) {
                Text(
                    text = stringResource(R.string.bulk_add_limit_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (uiState.isBulkTranslating) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = {
                        if (uiState.bulkProgressTotal > 0)
                            uiState.bulkProgressCurrent.toFloat() / uiState.bulkProgressTotal.toFloat()
                        else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(
                        R.string.bulk_add_progress,
                        uiState.bulkProgressCurrent,
                        uiState.bulkProgressTotal
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = { onEvent(WordsUiEvent.OnBulkStopClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.bulk_add_stop))
                }
            }
        } else {
            Button(
                onClick = { onEvent(WordsUiEvent.OnBulkStartClicked) },
                enabled = uiState.bulkWordsCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.bulk_add_start))
            }
        }
    }
}

@Composable
fun DuplicateWordDialog(
    data: DuplicateWordData,
    onOpenWord: (WordEntry) -> Unit,
    onAddToLesson: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duplicate_word_title)) },
        text = {
            Text(stringResource(R.string.duplicate_word_message, data.existingWord.lemma))
        },
        confirmButton = {
            Button(
                onClick = { onAddToLesson(data.existingWord.id) },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(stringResource(R.string.duplicate_word_add_to_lesson))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onOpenWord(data.existingWord) }) {
                    Text(stringResource(R.string.duplicate_word_open))
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.duplicate_word_cancel))
                }
            }
        }
    )
}
