package app.deutschlern.ui.words

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.deutschlern.R
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.util.CsvExporter
import kotlinx.coroutines.launch

@Composable
fun WordsScreen(
    modifier: Modifier = Modifier,
    viewModel: WordsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportEmptyMessage = stringResource(R.string.words_export_empty)
    val undoLabel = stringResource(R.string.action_undo)

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onEvent(WordsUiEvent.OnAddWordMicClicked)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (uiState.showUndoAction) undoLabel else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onEvent(WordsUiEvent.OnUndoDelete)
            }
            viewModel.onEvent(WordsUiEvent.OnDismissSnackbar)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title + Add Word button + "Новый урок" button + Sort selector + Export CSV
            HeaderRow(
                sort = uiState.sort,
                onSortChanged = { viewModel.onEvent(WordsUiEvent.OnSortChanged(it)) },
                onCreateNewSession = { viewModel.onEvent(WordsUiEvent.OnCreateNewSession) },
                onOpenAddWord = { viewModel.onEvent(WordsUiEvent.OnOpenAddWordClicked) },
                onExportCsv = {
                    val allWords = uiState.rawSessionsWithWords.flatMap { it.words }
                    if (allWords.isEmpty()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(exportEmptyMessage)
                        }
                    } else {
                        CsvExporter.shareAnkiCsv(context, allWords)
                    }
                }
            )

            // Search Bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChanged = { viewModel.onEvent(WordsUiEvent.OnSearchQueryChanged(it)) }
            )

            // Segmented Filter: «Все» / «Новые» / «Учу» / «Выучено»
            SegmentedFilterRow(
                currentFilter = uiState.filter,
                onFilterChanged = { viewModel.onEvent(WordsUiEvent.OnFilterChanged(it)) }
            )

            HairlineDivider()

            // Main Content: Loading, Empty, or Grouped List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (uiState.isLibraryEmpty) {
                EmptyStateView(
                    title = stringResource(R.string.words_empty_title),
                    subtitle = stringResource(R.string.words_empty_desc)
                )
            } else if (uiState.isSearchOrFilterEmpty) {
                EmptyStateView(
                    title = stringResource(R.string.words_search_empty_title),
                    subtitle = stringResource(R.string.words_search_empty_desc)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.sessionsWithWords.forEach { sessionGroup ->
                        item(key = "header_${sessionGroup.session.id}") {
                            SessionHeader(title = sessionGroup.session.title)
                        }

                        items(
                            items = sessionGroup.words,
                            key = { "word_${sessionGroup.session.id}_${it.id}" }
                        ) { word ->
                            SwipeableWordRow(
                                word = word,
                                onClick = { viewModel.onEvent(WordsUiEvent.OnWordClicked(word)) },
                                onDelete = { viewModel.onEvent(WordsUiEvent.OnDeleteWord(word)) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    uiState.selectedWordForDetail?.let { selectedWord ->
        WordDetailBottomSheet(
            word = selectedWord,
            onDismiss = { viewModel.onEvent(WordsUiEvent.OnDismissBottomSheet) },
            onStatusChanged = { newStatus ->
                viewModel.onEvent(WordsUiEvent.OnStatusChanged(selectedWord.id, newStatus))
            },
            onSelectMeaning = { meaningIndex ->
                viewModel.onEvent(WordsUiEvent.OnSelectMeaning(selectedWord.id, meaningIndex))
            },
            onDelete = {
                viewModel.onEvent(WordsUiEvent.OnDeleteWord(selectedWord))
            },
            onSpeak = { text ->
                viewModel.onEvent(WordsUiEvent.OnSpeakWord(text))
            }
        )
    }

    // Add Word Bottom Sheet
    if (uiState.isAddWordSheetVisible) {
        AddWordBottomSheet(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onMicClicked = {
                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    viewModel.onEvent(WordsUiEvent.OnAddWordMicClicked)
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
    }

    // Duplicate Word Dialog
    uiState.duplicateWordDialogData?.let { dupData ->
        DuplicateWordDialog(
            data = dupData,
            onOpenWord = { viewModel.onEvent(WordsUiEvent.OnOpenDuplicateWord(it)) },
            onAddToLesson = { viewModel.onEvent(WordsUiEvent.OnAddDuplicateToLesson(it)) },
            onDismiss = { viewModel.onEvent(WordsUiEvent.OnDismissDuplicateDialog) }
        )
    }
}

@Composable
private fun HeaderRow(
    sort: WordSort,
    onSortChanged: (WordSort) -> Unit,
    onCreateNewSession: () -> Unit,
    onOpenAddWord: () -> Unit,
    onExportCsv: () -> Unit
) {
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.tab_words),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.words_placeholder_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Add Word button
            IconButton(
                onClick = onOpenAddWord,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_word_title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // "Новый урок" button
            OutlinedButton(
                onClick = onCreateNewSession,
                modifier = Modifier.heightIn(min = 40.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_new_session),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { isSortMenuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.sort_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = isSortMenuExpanded,
                    onDismissRequest = { isSortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.sort_by_date),
                                fontWeight = if (sort == WordSort.BY_DATE) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortChanged(WordSort.BY_DATE)
                            isSortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.sort_alphabetical),
                                fontWeight = if (sort == WordSort.ALPHABETICAL) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortChanged(WordSort.ALPHABETICAL)
                            isSortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.sort_most_translated),
                                fontWeight = if (sort == WordSort.MOST_TRANSLATED) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortChanged(WordSort.MOST_TRANSLATED)
                            isSortMenuExpanded = false
                        }
                    )
                }
            }

            // Export Anki CSV button
            IconButton(
                onClick = onExportCsv,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_export_csv),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(R.string.words_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun SegmentedFilterRow(
    currentFilter: WordFilter,
    onFilterChanged: (WordFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        val filters = listOf(
            WordFilter.ALL to stringResource(R.string.filter_all),
            WordFilter.NEW to stringResource(R.string.filter_new),
            WordFilter.LEARNING to stringResource(R.string.filter_learning),
            WordFilter.KNOWN to stringResource(R.string.filter_known)
        )

        filters.forEachIndexed { index, (filter, title) ->
            val isSelected = currentFilter == filter
            val background = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(background)
                    .clickable { onFilterChanged(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            }

            if (index < filters.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

@Composable
private fun SessionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableWordRow(
    word: WordEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        WordRowContent(
            word = word,
            onClick = onClick
        )
    }
}

@Composable
private fun WordRowContent(
    word: WordEntry,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Article + Lemma
                val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(
                    rawLemma = word.lemma,
                    rawPos = word.pos,
                    rawArticle = word.article
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!sanitized.article.isNullOrBlank()) {
                        app.deutschlern.ui.theme.ArticleLabel(
                            article = sanitized.article,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    Text(
                        text = sanitized.lemma,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Counter badge if timesTranslated > 1
                    if (word.timesTranslated > 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "×${word.timesTranslated}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Translation (selected or first meaning)
                val meaningText = word.selectedMeaning?.translation ?: word.meanings.firstOrNull()?.translation
                if (!meaningText.isNullOrBlank()) {
                    Text(
                        text = meaningText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Badge
            StatusBadge(status = word.status)
        }
    }
}

@Composable
private fun StatusBadge(status: WordStatus) {
    val (label, bg, fg) = when (status) {
        WordStatus.NEW -> Triple(
            stringResource(R.string.status_new),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        WordStatus.LEARNING -> Triple(
            stringResource(R.string.status_learning),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary
        )
        WordStatus.KNOWN -> Triple(
            stringResource(R.string.status_known),
            Color(0xFF2E7D32).copy(alpha = 0.12f),
            Color(0xFF2E7D32)
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun EmptyStateView(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
