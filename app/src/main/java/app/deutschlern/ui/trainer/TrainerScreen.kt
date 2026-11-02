package app.deutschlern.ui.trainer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.trainer.AnswerNote
import app.deutschlern.domain.trainer.AnswerResult
import app.deutschlern.domain.trainer.Exercise
import app.deutschlern.domain.trainer.TypingKind
import app.deutschlern.ui.theme.ArticleColors

private val CorrectColor = Color(0xFF2E7D32)
private val AlmostColor = Color(0xFFB26A00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerScreen(
    onBack: () -> Unit,
    viewModel: TrainerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trainerTitle(state.mode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (!state.isLoading && !state.isEmpty && !state.isFinished) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.isEmpty -> EmptyTrainer(onBack = onBack)
                    state.isFinished -> TrainerResults(
                        state = state,
                        onRepeatMistakes = viewModel::restartWithMistakes,
                        onRestart = viewModel::restart,
                        onDone = onBack
                    )
                    else -> state.current?.let { exercise ->
                        ExerciseContent(exercise = exercise, state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseContent(
    exercise: Exercise,
    state: TrainerUiState,
    viewModel: TrainerViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (exercise) {
            is Exercise.Typing -> TypingExercise(exercise, state, viewModel)
            is Exercise.Choice -> ChoiceExercise(exercise, state, viewModel)
            is Exercise.Article -> ArticleExercise(exercise, state, viewModel)
            is Exercise.Speaking -> SpeakingExercise(exercise, state, viewModel)
        }

        state.verdict?.let { verdict ->
            FeedbackPanel(exercise = exercise, result = verdict.result, note = verdict.note, onSpeak = viewModel::onSpeak)
            Button(
                onClick = viewModel::onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.trainer_next))
            }
        }
    }
}

@Composable
private fun TaskLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ColumnScope.TypingExercise(exercise: Exercise.Typing, state: TrainerUiState, viewModel: TrainerViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(exercise, state.index) {
        runCatching { focusRequester.requestFocus() }
    }

    TaskLabel(
        stringResource(
            when (exercise.kind) {
                TypingKind.TRANSLATE_TO_GERMAN -> R.string.trainer_task_recall
                TypingKind.CLOZE -> R.string.trainer_task_cloze
                TypingKind.PLURAL -> R.string.trainer_task_plural
                TypingKind.PRAETERITUM -> R.string.trainer_task_praeteritum
                TypingKind.PERFEKT -> R.string.trainer_task_perfekt
                TypingKind.DICTATION -> R.string.trainer_task_dictation
            }
        )
    )

    if (exercise.kind == TypingKind.DICTATION) {
        FilledIconButton(
            onClick = { viewModel.onSpeak(exercise.audioText.orEmpty()) },
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.trainer_listen), modifier = Modifier.size(40.dp))
        }
    } else {
        Text(
            text = exercise.prompt,
            style = if (exercise.kind == TypingKind.CLOZE) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }

    exercise.hint?.let { hint ->
        // In dictation the meaning is the answer's translation: show it only after answering
        if (exercise.kind != TypingKind.DICTATION || state.verdict != null) {
            Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (state.hintLevel > 0 && state.verdict == null) {
        Text(
            text = hintText(exercise, state.hintLevel),
            style = MaterialTheme.typography.titleMedium,
            color = AlmostColor,
            letterSpacing = 2.sp
        )
    }

    OutlinedTextField(
        value = state.input,
        onValueChange = viewModel::onInputChanged,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        enabled = state.verdict == null,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.trainer_input_hint)) },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.onSubmitTyped() })
    )

    if (state.verdict == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = viewModel::onHint, enabled = state.hintLevel < TrainerViewModel.MAX_HINT_LEVEL) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.trainer_hint))
            }
            TextButton(onClick = viewModel::onDontKnow) { Text(stringResource(R.string.trainer_dont_know)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = viewModel::onSubmitTyped, enabled = state.input.isNotBlank()) {
                Text(stringResource(R.string.trainer_check))
            }
        }
    }
}

/** Hint ladder: first letter, then the length of every word. */
private fun hintText(exercise: Exercise.Typing, level: Int): String {
    val answer = listOfNotNull(exercise.requiredArticle, exercise.expected).joinToString(" ")
    return if (level <= 1) {
        listOfNotNull(exercise.requiredArticle, exercise.expected.take(1) + "…").joinToString(" ")
    } else {
        answer.split(" ").joinToString("  ") { token ->
            if (token == exercise.requiredArticle) token else token.first() + "_".repeat(token.length - 1)
        }
    }
}

@Composable
private fun ColumnScope.ChoiceExercise(exercise: Exercise.Choice, state: TrainerUiState, viewModel: TrainerViewModel) {
    TaskLabel(stringResource(R.string.trainer_task_choice))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(exercise.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = { viewModel.onSpeak(exercise.prompt) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.trainer_listen))
        }
    }
    exercise.options.forEachIndexed { index, option ->
        val answered = state.verdict != null
        val color = when {
            !answered -> null
            index == exercise.correctIndex -> CorrectColor
            index == state.selectedOption -> MaterialTheme.colorScheme.error
            else -> null
        }
        OutlinedButton(
            onClick = { viewModel.onOptionSelected(index) },
            enabled = !answered || color != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = if (color != null) {
                ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color, disabledContentColor = color)
            } else {
                ButtonDefaults.outlinedButtonColors()
            }
        ) {
            Text(option, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ColumnScope.ArticleExercise(exercise: Exercise.Article, state: TrainerUiState, viewModel: TrainerViewModel) {
    TaskLabel(stringResource(R.string.trainer_task_article))
    Text(
        text = exercise.word.lemma,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    exercise.word.selectedMeaning?.translation?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    val isDark = isSystemInDarkTheme()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("der", "die", "das").forEach { article ->
            val articleColor = ArticleColors.getColor(article, isDark) ?: MaterialTheme.colorScheme.primary
            val answered = state.verdict != null
            val highlight = answered && (article == exercise.correctArticle || article == state.input)
            Button(
                onClick = { viewModel.onArticleSelected(article) },
                enabled = !answered || highlight,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = articleColor,
                    contentColor = if (isDark) Color.Black else Color.White,
                    disabledContainerColor = articleColor.copy(alpha = 0.6f),
                    disabledContentColor = if (isDark) Color.Black else Color.White
                )
            ) {
                Text(article, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColumnScope.SpeakingExercise(exercise: Exercise.Speaking, state: TrainerUiState, viewModel: TrainerViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onToggleListening() else viewModel.onPermissionDenied()
    }

    TaskLabel(stringResource(R.string.trainer_task_speaking))
    Text(exercise.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

    if (state.verdict == null) {
        FilledIconButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onToggleListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.CenterHorizontally),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (state.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (state.isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = stringResource(R.string.trainer_speak),
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = state.input.ifBlank { stringResource(if (state.isListening) R.string.trainer_listening else R.string.trainer_tap_to_speak) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        state.speechError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = viewModel::onDontKnow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.trainer_dont_know))
        }
    }
}

@Composable
private fun FeedbackPanel(exercise: Exercise, result: AnswerResult, note: AnswerNote, onSpeak: (String) -> Unit) {
    val color = when (result) {
        AnswerResult.CORRECT -> CorrectColor
        AnswerResult.ALMOST -> AlmostColor
        AnswerResult.WRONG -> MaterialTheme.colorScheme.error
    }
    val answer = correctAnswer(exercise)

    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(
                    when (result) {
                        AnswerResult.CORRECT -> R.string.trainer_result_correct
                        AnswerResult.ALMOST -> R.string.trainer_result_almost
                        AnswerResult.WRONG -> R.string.trainer_result_wrong
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            noteText(note)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(answer, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onSpeak(answer) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.trainer_listen))
                }
            }
            exercise.word.selectedMeaning?.let { meaning ->
                Text(meaning.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (exercise !is Exercise.Typing || exercise.kind != TypingKind.CLOZE) {
                    meaning.exampleDe.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun correctAnswer(exercise: Exercise): String = when (exercise) {
    is Exercise.Typing -> when (exercise.kind) {
        TypingKind.CLOZE -> exercise.audioText ?: exercise.expected
        TypingKind.PLURAL -> "die ${exercise.expected}"
        else -> listOfNotNull(exercise.requiredArticle, exercise.expected).joinToString(" ")
    }
    is Exercise.Article -> "${exercise.correctArticle} ${exercise.word.lemma}"
    is Exercise.Choice -> exercise.options[exercise.correctIndex]
    is Exercise.Speaking -> listOfNotNull(exercise.requiredArticle, exercise.expected).joinToString(" ")
}

@Composable
private fun noteText(note: AnswerNote): String? = when (note) {
    AnswerNote.NONE -> null
    AnswerNote.SPELLING_VARIANT -> stringResource(R.string.trainer_note_spelling)
    AnswerNote.TYPO -> stringResource(R.string.trainer_note_typo)
    AnswerNote.MISSING_ARTICLE -> stringResource(R.string.trainer_note_missing_article)
    AnswerNote.WRONG_ARTICLE -> stringResource(R.string.trainer_note_wrong_article)
    AnswerNote.MISSING_AUXILIARY -> stringResource(R.string.trainer_note_missing_auxiliary)
    AnswerNote.BASE_FORM -> stringResource(R.string.trainer_note_base_form)
}

@Composable
private fun TrainerResults(
    state: TrainerUiState,
    onRepeatMistakes: () -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.trainer_finished_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.trainer_finished_score, state.firstTryCorrect, state.totalWords),
            style = MaterialTheme.typography.titleMedium
        )

        if (state.mistakes.isNotEmpty()) {
            Text(stringResource(R.string.trainer_mistakes_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            state.mistakes.forEach { WordLine(it) }
        }
        if (state.autoMarkedDifficult.isNotEmpty()) {
            Text(
                stringResource(R.string.trainer_auto_difficult, state.autoMarkedDifficult.size),
                style = MaterialTheme.typography.bodyMedium,
                color = AlmostColor
            )
        }

        Spacer(Modifier.height(8.dp))
        if (state.mistakes.isNotEmpty()) {
            Button(onClick = onRepeatMistakes, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.trainer_repeat_mistakes))
            }
        }
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.trainer_new_session))
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.trainer_done))
        }
    }
}

@Composable
private fun WordLine(word: WordEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(listOfNotNull(word.article, word.lemma).joinToString(" "), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(word.selectedMeaning?.translation.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyTrainer(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.trainer_empty), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.trainer_done)) }
    }
}
