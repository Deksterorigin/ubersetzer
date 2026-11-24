package app.deutschlern.ui.exam

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.domain.exam.ExamSegment
import app.deutschlern.domain.exam.ExamScoring
import app.deutschlern.domain.exam.ExamVariant
import app.deutschlern.domain.exam.GapText
import app.deutschlern.domain.exam.GapToken
import app.deutschlern.domain.exam.Option
import app.deutschlern.domain.exam.Passage
import app.deutschlern.domain.exam.PhoneNote
import app.deutschlern.domain.exam.Question
import app.deutschlern.domain.exam.QuestionResult
import app.deutschlern.domain.exam.WritingTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamVariantScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ExamVariantViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timerState by viewModel.timer.state.collectAsStateWithLifecycle()
    var gapSheetQuestion by rememberSaveable { mutableStateOf<Int?>(null) }

    ExamScaffold(
        title = state.variant?.title.orEmpty(),
        subtitle = state.variant?.part?.germanName(),
        onBack = onBack,
        wordLookup = true
    ) { padding ->
        val variant = state.variant
        when {
            state.loading -> ExamLoading(padding)
            variant == null -> ExamEmpty(padding, stringResource(R.string.exam_variant_missing))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                variant.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                variant.audioUrl?.takeIf { it.isNotBlank() }?.let { AudioLinkButton(it) }

                variant.segments.forEachIndexed { index, segment ->
                    SegmentContent(
                        index = index,
                        segment = segment,
                        state = state,
                        onPlayToggle = { viewModel.onPlayToggle(index) },
                        onRateSelected = viewModel::onRateSelected,
                        onAnswer = viewModel::onAnswer,
                        onOpenGap = { gapSheetQuestion = it }
                    )
                }

                variant.phoneNote?.let { note ->
                    PhoneNoteCard(note = note, inputs = state.noteInputs, results = state.noteResults, onInput = viewModel::onNoteInput)
                }

                if (state.hasObjectivePart) {
                    CheckControls(state = state, onCheck = viewModel::onCheck, onReset = viewModel::onReset)
                }

                variant.writing?.let { writing ->
                    WritingSection(
                        writing = writing,
                        state = state,
                        timerState = timerState,
                        onTimerToggle = viewModel.timer::toggle,
                        onTimerReset = { viewModel.timer.reset() },
                        onDraftChange = viewModel::onDraftChange,
                        onEvaluate = viewModel::onEvaluateWriting,
                        onToggleSample = viewModel::onToggleSample,
                        onDone = viewModel::onWritingDone,
                        onOpenSettings = onOpenSettings
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    val variant = state.variant
    val sheetNumber = gapSheetQuestion
    if (variant != null && sheetNumber != null) {
        val found = variant.findQuestion(sheetNumber)
        if (found == null) {
            gapSheetQuestion = null
        } else {
            val (segment, question) = found
            ModalBottomSheet(onDismissRequest = { gapSheetQuestion = null }) {
                GapOptionsSheet(
                    question = question,
                    options = ExamScoring.optionsFor(segment, question),
                    selected = state.answers[question.number],
                    onSelect = { key ->
                        viewModel.onAnswer(question.number, key)
                        gapSheetQuestion = null
                    }
                )
            }
        }
    }
}

private fun ExamVariant.findQuestion(number: Int): Pair<ExamSegment, Question>? {
    for (segment in segments) {
        segment.questions.find { it.number == number }?.let { return segment to it }
    }
    return null
}

@Composable
private fun AudioLinkButton(url: String) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.exam_audio_link))
    }
}

@Composable
private fun SegmentContent(
    index: Int,
    segment: ExamSegment,
    state: ExamVariantUiState,
    onPlayToggle: () -> Unit,
    onRateSelected: (Float) -> Unit,
    onAnswer: (Int, String) -> Unit,
    onOpenGap: (Int) -> Unit
) {
    val gapNumbers = remember(segment) { segment.passages.flatMap { GapText.gapNumbers(it.body) }.toSet() }
    val resultsByNumber = state.result?.results?.associateBy { it.question.number }.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segment.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        segment.instruction?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (segment.transcript.isNotEmpty()) {
            ListeningCard(
                segment = segment,
                isThisPlaying = state.playback.playing && state.playingSegment == index,
                currentLine = if (state.playingSegment == index) state.playback.lineIndex else -1,
                voiceMissing = state.playback.voiceMissing,
                plays = state.plays[index] ?: 0,
                rate = state.rate,
                transcriptVisible = state.checked,
                onPlayToggle = onPlayToggle,
                onRateSelected = onRateSelected
            )
        }

        segment.passages.forEach { passage ->
            if (GapText.hasGaps(passage.body)) {
                GapPassageCard(
                    passage = passage,
                    segment = segment,
                    answers = state.answers,
                    results = resultsByNumber,
                    onOpenGap = onOpenGap
                )
            } else {
                PassageCard(passage)
            }
        }

        if (segment.sharedOptions.isNotEmpty()) {
            if (gapNumbers.isNotEmpty()) {
                WordBank(options = segment.sharedOptions, used = state.answers.values.toSet())
            } else {
                SectionLabel(stringResource(R.string.exam_shared_options))
                segment.sharedOptions.forEach { option ->
                    ExpandableCard(title = option.text, leading = option.key) {
                        GermanText(option.text)
                    }
                }
            }
        }

        segment.questions.forEach { question ->
            val result = resultsByNumber[question.number]
            if (question.number in gapNumbers) {
                if (result != null) GapResultRow(question, segment, result)
            } else {
                QuestionCard(
                    question = question,
                    options = ExamScoring.optionsFor(segment, question),
                    shared = question.options.isEmpty(),
                    selected = state.answers[question.number],
                    result = result,
                    onSelect = { onAnswer(question.number, it) }
                )
            }
        }
    }
}

@Composable
private fun PassageCard(passage: Passage) {
    ExamCard {
        val header = listOfNotNull(passage.key?.takeIf { it.isNotBlank() }, passage.title?.takeIf { it.isNotBlank() })
            .joinToString("  ")
        if (header.isNotEmpty()) {
            Text(header, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        GermanText(passage.body)
    }
}

@Composable
private fun GapPassageCard(
    passage: Passage,
    segment: ExamSegment,
    answers: Map<Int, String>,
    results: Map<Int, QuestionResult>,
    onOpenGap: (Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val chipBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val tokens = remember(passage.body) { GapText.parse(passage.body) }
    val checked = results.isNotEmpty()
    val onWord = LocalWordLookup.current
    val text = buildAnnotatedString {
        tokens.forEach { token ->
            when (token) {
                is GapToken.Text -> appendGermanText(token.text, onWord)
                is GapToken.Gap -> {
                    val question = segment.questions.find { it.number == token.number }
                    val options = question?.let { ExamScoring.optionsFor(segment, it) }.orEmpty()
                    val given = answers[token.number]
                    val givenText = options.find { it.key == given }?.let(::gapLabel)
                    val result = results[token.number]
                    val color = when (result?.correct) {
                        true -> ExamCorrectColor
                        false -> ExamWrongColor
                        null -> primary
                    }
                    val style = SpanStyle(color = color, background = color.copy(alpha = 0.12f).takeIf { checked } ?: chipBackground, fontWeight = FontWeight.SemiBold)
                    val label = buildString {
                        append(" ").append(token.number).append(": ")
                        append(givenText ?: "______")
                        if (result?.correct == false) {
                            options.find { it.key == question?.answer }?.let { append(" → ").append(gapLabel(it)) }
                        }
                        append(" ")
                    }
                    if (checked) {
                        withStyle(style) { append(label) }
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "gap_${token.number}",
                                styles = TextLinkStyles(style = style),
                                linkInteractionListener = { onOpenGap(token.number) }
                            )
                        ) { append(label) }
                    }
                }
            }
        }
    }
    ExamCard {
        passage.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (!checked) {
            Text(
                stringResource(R.string.exam_gap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f))
    }
}

/** Word-bank options are single words; lettered options keep their key so a/b/c stay recognisable. */
private fun gapLabel(option: Option): String = option.text.lineSequence().firstOrNull().orEmpty().ifBlank { option.key }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordBank(options: List<Option>, used: Set<String>) {
    ExamCard(color = MaterialTheme.colorScheme.surfaceVariant) {
        SectionLabel(stringResource(R.string.exam_word_bank))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val isUsed = option.key in used
                Text(
                    "${option.key}) ${option.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUsed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun GapOptionsSheet(question: Question, options: List<Option>, selected: String?, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.exam_gap_title, question.number),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        question.prompt.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        options.forEach { option ->
            OptionButton(option = option, selected = option.key == selected, feedbackColor = null, enabled = true, onClick = { onSelect(option.key) })
        }
    }
}

@Composable
private fun OptionButton(option: Option, selected: Boolean, feedbackColor: Color?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = feedbackColor ?: if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val background = when {
        feedbackColor != null -> feedbackColor.copy(alpha = 0.10f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(if (selected || feedbackColor != null) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val showKey = option.text.isNotBlank() && option.key.length <= 2 && !option.text.equals(option.key, ignoreCase = true) &&
                option.key !in setOf("r", "f")
            if (showKey) {
                Text("${option.key})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
            }
            Text(option.text.ifBlank { option.key }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(
    question: Question,
    options: List<Option>,
    shared: Boolean,
    selected: String?,
    result: QuestionResult?,
    onSelect: (String) -> Unit
) {
    val border = when (result?.correct) {
        true -> BorderStroke(1.5.dp, ExamCorrectColor)
        false -> BorderStroke(1.5.dp, ExamWrongColor)
        null -> null
    }
    ExamCard(border = border) {
        Row {
            Text("${question.number}.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (question.prompt.isNotBlank()) {
                GermanText(question.prompt)
            }
        }
        val checked = result != null
        if (shared) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    val isAnswer = checked && option.key == question.answer
                    val isWrongPick = checked && option.key == selected && result?.correct == false
                    FilterChip(
                        selected = option.key == selected || isAnswer,
                        onClick = { onSelect(option.key) },
                        enabled = !checked || option.key == selected || isAnswer,
                        label = {
                            Text(
                                option.key,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isAnswer -> ExamCorrectColor
                                    isWrongPick -> ExamWrongColor
                                    else -> Color.Unspecified
                                }
                            )
                        }
                    )
                }
            }
            options.find { it.key == selected }?.let {
                Text(
                    "${it.key}) ${it.text.lineSequence().firstOrNull().orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val compact = options.size <= 2 && options.all { it.text.length <= 12 }
            if (compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        OptionButton(
                            option = option,
                            selected = option.key == selected,
                            feedbackColor = feedbackFor(option, question, selected, result),
                            enabled = !checked,
                            onClick = { onSelect(option.key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                options.forEach { option ->
                    OptionButton(
                        option = option,
                        selected = option.key == selected,
                        feedbackColor = feedbackFor(option, question, selected, result),
                        enabled = !checked,
                        onClick = { onSelect(option.key) }
                    )
                }
            }
        }
        result?.let { QuestionFeedback(question, options, it) }
    }
}

private fun feedbackFor(option: Option, question: Question, selected: String?, result: QuestionResult?): Color? = when {
    result == null || question.answer == null -> null
    option.key == question.answer -> ExamCorrectColor
    option.key == selected -> ExamWrongColor
    else -> null
}

@Composable
private fun QuestionFeedback(question: Question, options: List<Option>, result: QuestionResult) {
    when (result.correct) {
        null -> Text(stringResource(R.string.exam_no_key), style = MaterialTheme.typography.labelMedium, color = ExamWarnColor)
        true -> Text(stringResource(R.string.exam_correct), style = MaterialTheme.typography.labelMedium, color = ExamCorrectColor)
        false -> {
            val answer = options.find { it.key == question.answer }
            Text(
                stringResource(
                    R.string.exam_wrong_answer_is,
                    answer?.let { "${it.key}) ${it.text.lineSequence().firstOrNull().orEmpty()}" } ?: question.answer.orEmpty()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = ExamWrongColor
            )
        }
    }
    question.evidence?.takeIf { it.isNotBlank() }?.let {
        Text(
            "«$it»",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GapResultRow(question: Question, segment: ExamSegment, result: QuestionResult) {
    val options = ExamScoring.optionsFor(segment, question)
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        val given = options.find { it.key == result.given }?.let(::gapLabel) ?: "—"
        Text(
            "${question.number}: $given",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (result.correct) {
                true -> ExamCorrectColor
                false -> ExamWrongColor
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
        QuestionFeedback(question, options, result)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListeningCard(
    segment: ExamSegment,
    isThisPlaying: Boolean,
    currentLine: Int,
    voiceMissing: Boolean,
    plays: Int,
    rate: Float,
    transcriptVisible: Boolean,
    onPlayToggle: () -> Unit,
    onRateSelected: (Float) -> Unit
) {
    ExamCard(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = onPlayToggle, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isThisPlaying) R.string.exam_stop else R.string.exam_play)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(if (isThisPlaying) R.string.exam_playing else R.string.exam_listen),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.exam_plays_count, plays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.8f, 1.0f, 1.2f).forEach { value ->
                FilterChip(
                    selected = rate == value,
                    onClick = { onRateSelected(value) },
                    label = { Text("×$value") }
                )
            }
        }
        if (voiceMissing) {
            Text(stringResource(R.string.exam_voice_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (transcriptVisible) {
            ExpandableCard(title = stringResource(R.string.exam_transcript), initiallyExpanded = isThisPlaying) {
                segment.transcript.forEachIndexed { i, line ->
                    val highlighted = i == currentLine
                    GermanText(
                        text = line.text,
                        prefix = line.speaker?.takeIf { it.isNotBlank() }?.let {
                            buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it); append(": ") } }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.exam_transcript_hidden),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhoneNoteCard(note: PhoneNote, inputs: List<String>, results: List<Boolean?>?, onInput: (Int, String) -> Unit) {
    ExamCard {
        Text(
            note.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.exam_phone_note),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        note.fields.forEachIndexed { index, field ->
            val ok = results?.getOrNull(index)
            OutlinedTextField(
                value = inputs.getOrNull(index).orEmpty(),
                onValueChange = { onInput(index, it) },
                label = { Text(field.label) },
                readOnly = results != null,
                isError = ok == false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (ok == true) {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = ExamCorrectColor) }
                } else null,
                supportingText = if (results != null) {
                    {
                        if (field.answer.isBlank()) {
                            Text(stringResource(R.string.exam_no_key), color = ExamWarnColor)
                        } else {
                            Text(
                                stringResource(R.string.exam_expected, field.answer),
                                color = if (ok == true) ExamCorrectColor else ExamWrongColor
                            )
                        }
                    }
                } else null
            )
        }
    }
}

@Composable
private fun CheckControls(state: ExamVariantUiState, onCheck: () -> Unit, onReset: () -> Unit) {
    if (!state.checked) {
        Button(onClick = onCheck, modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)) {
            Text(stringResource(R.string.exam_check))
        }
        return
    }
    ExamCard(color = MaterialTheme.colorScheme.surfaceVariant) {
        val result = state.result
        val noteResults = state.noteResults
        val gradable = (result?.gradable ?: 0) + (noteResults?.count { it != null } ?: 0)
        val correct = (result?.correct ?: 0) + (noteResults?.count { it == true } ?: 0)
        val percent = state.percent
        Text(
            if (percent != null) stringResource(R.string.exam_result_summary, correct, gradable, percent)
            else stringResource(R.string.exam_result_no_keys),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                percent == null -> MaterialTheme.colorScheme.onSurface
                percent >= 60 -> ExamCorrectColor
                else -> ExamWrongColor
            }
        )
        val ungraded = (result?.results?.count { it.correct == null } ?: 0) + (noteResults?.count { it == null } ?: 0)
        if (ungraded > 0) {
            Text(
                stringResource(R.string.exam_result_ungraded, ungraded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exam_reset))
        }
    }
}

@Composable
private fun WritingSection(
    writing: WritingTask,
    state: ExamVariantUiState,
    timerState: TimerState,
    onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit,
    onDraftChange: (String) -> Unit,
    onEvaluate: () -> Unit,
    onToggleSample: () -> Unit,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ExamCard {
        SectionLabel(stringResource(R.string.exam_writing_task))
        GermanText(writing.situation)
        if (writing.points.isNotEmpty()) {
            Text(stringResource(R.string.exam_writing_points), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            BulletList(writing.points)
        }
        HorizontalDivider()
        TimerRow(state = timerState, onToggle = onTimerToggle, onReset = onTimerReset)
    }

    OutlinedTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(R.string.exam_writing_placeholder)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        supportingText = {
            val min = writing.minWords
            Text(
                if (min != null) stringResource(R.string.exam_word_count_min, state.wordCount, min)
                else stringResource(R.string.exam_word_count, state.wordCount),
                color = if (min != null && state.wordCount < min) ExamWarnColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    AiEvaluationSection(
        state = state.ai,
        buttonText = stringResource(R.string.exam_ai_check_text),
        enabled = state.draft.isNotBlank(),
        onEvaluate = onEvaluate,
        onOpenSettings = onOpenSettings
    )

    if (state.ai.noProvider && writing.points.isNotEmpty()) {
        SelfCheckList(writing.points)
    }

    writing.sampleAnswer?.takeIf { it.isNotBlank() }?.let { sample ->
        SampleAnswerCard(sample = sample, visible = state.showSample, onToggle = onToggleSample)
    }

    if (state.writingDone) {
        Text(stringResource(R.string.exam_writing_marked_done), style = MaterialTheme.typography.labelMedium, color = ExamCorrectColor)
    } else {
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exam_done))
        }
    }
}

@Composable
private fun SelfCheckList(points: List<String>) {
    val checks = remember(points) { mutableStateListOf(*Array(points.size + SELF_CHECK_EXTRA.size) { false }) }
    ExamCard {
        SectionLabel(stringResource(R.string.exam_self_check))
        val labels = points + SELF_CHECK_EXTRA.map { stringResource(it) }
        labels.forEachIndexed { i, label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { checks[i] = !checks[i] }
            ) {
                Checkbox(checked = checks[i], onCheckedChange = { checks[i] = it })
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val SELF_CHECK_EXTRA = listOf(
    R.string.exam_self_check_greeting,
    R.string.exam_self_check_connectors,
    R.string.exam_self_check_closing
)
