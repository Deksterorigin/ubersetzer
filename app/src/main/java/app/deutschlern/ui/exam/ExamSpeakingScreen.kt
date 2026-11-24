package app.deutschlern.ui.exam

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.domain.exam.SpeakingPrompt

@Composable
fun ExamSpeakingScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ExamSpeakingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timerState by viewModel.timer.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    val onRecord = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (state.recording || granted) viewModel.onRecordToggle() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    ExamScaffold(
        title = speakingName(state.teil),
        subtitle = speakingDescription(state.teil),
        onBack = onBack,
        wordLookup = true,
        actions = {
            if (state.prompts.isNotEmpty()) {
                IconButton(onClick = viewModel::onToggleListMode) {
                    Icon(
                        if (state.listMode) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.exam_speaking_list)
                    )
                }
            }
        }
    ) { padding ->
        val prompt = state.current
        when {
            state.loading -> ExamLoading(padding)
            prompt == null -> ExamEmpty(padding)
            state.listMode -> PromptList(padding, state, onSelect = viewModel::select)
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PromptNavigation(
                    position = state.index + 1,
                    total = state.prompts.size,
                    onPrevious = viewModel::onPrevious,
                    onNext = viewModel::onNext,
                    onRandom = viewModel::onRandom
                )
                PromptCard(prompt = prompt, showRu = state.showRu, done = prompt.id in state.done, onToggleRu = viewModel::onToggleRu)

                ExamCard {
                    SpeakingTimerChips(selected = state.timerMinutes, onSelect = viewModel::onTimerMinutes)
                    TimerRow(state = timerState, onToggle = viewModel.timer::toggle, onReset = { viewModel.timer.reset() })
                }

                RecordCard(state = state, onRecord = onRecord, onTranscriptEdit = viewModel::onTranscriptEdit)

                AiEvaluationSection(
                    state = state.ai,
                    buttonText = stringResource(R.string.exam_ai_evaluate_speaking),
                    enabled = state.transcript.isNotBlank() && !state.recording,
                    onEvaluate = viewModel::onEvaluate,
                    onOpenSettings = onOpenSettings
                )

                prompt.sample?.takeIf { it.isNotBlank() }?.let { sample ->
                    SampleAnswerCard(sample = sample, visible = state.showSample, onToggle = viewModel::onToggleSample)
                }
                if (prompt.id !in state.done) {
                    TextButton(onClick = viewModel::onMarkDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.exam_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptNavigation(position: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit, onRandom: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.exam_previous))
        }
        Text(
            "$position / $total",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onRandom) {
            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.exam_random))
        }
        OutlinedButton(onClick = onNext) {
            Text(stringResource(R.string.exam_next))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PromptCard(prompt: SpeakingPrompt, showRu: Boolean, done: Boolean, onToggleRu: () -> Unit) {
    ExamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(prompt.number?.let { "№$it" }, prompt.title).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (done) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ExamCorrectColor, modifier = Modifier.size(18.dp))
        }
        SelectionContainer {
            GermanText(prompt.de, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (prompt.tasks.isNotEmpty()) BulletList(prompt.tasks)
        prompt.ru?.takeIf { it.isNotBlank() }?.let { ru ->
            TextButton(onClick = onToggleRu) {
                Text(stringResource(if (showRu) R.string.exam_hide_translation else R.string.exam_show_translation))
            }
            if (showRu) {
                Text(ru, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeakingTimerChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { minutes ->
            FilterChip(
                selected = selected == minutes,
                onClick = { onSelect(minutes) },
                label = { Text(stringResource(R.string.exam_minutes, minutes)) }
            )
        }
    }
}

@Composable
private fun RecordCard(state: ExamSpeakingUiState, onRecord: () -> Unit, onTranscriptEdit: (String) -> Unit) {
    ExamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = onRecord,
                modifier = Modifier.size(56.dp),
                colors = if (state.recording) {
                    IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    IconButtonDefaults.filledIconButtonColors()
                }
            ) {
                Icon(
                    if (state.recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = stringResource(if (state.recording) R.string.exam_record_stop else R.string.exam_record_start)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(if (state.recording) R.string.exam_recording else R.string.exam_record_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (!state.hasMicPermission) {
            Text(stringResource(R.string.exam_mic_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        state.sttError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.partial.isNotBlank()) {
            Text(state.partial, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        OutlinedTextField(
            value = state.transcript,
            onValueChange = onTranscriptEdit,
            readOnly = state.recording,
            label = { Text(stringResource(R.string.exam_transcript_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            supportingText = { Text(stringResource(R.string.exam_word_count, app.deutschlern.domain.exam.ExamScoring.wordCount(state.transcript))) }
        )
    }
}

@Composable
private fun PromptList(padding: PaddingValues, state: ExamSpeakingUiState, onSelect: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(state.prompts, key = { _, p -> p.id }) { index, prompt ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(index) }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(prompt.number?.let { "№$it" }, prompt.title).joinToString(" · ").ifBlank { prompt.id },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(prompt.de, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (prompt.id in state.done) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ExamCorrectColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
