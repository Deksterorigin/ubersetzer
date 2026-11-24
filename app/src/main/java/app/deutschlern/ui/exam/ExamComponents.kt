package app.deutschlern.ui.exam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.deutschlern.R
import app.deutschlern.domain.exam.ExamEvaluation
import app.deutschlern.domain.provider.ProviderError

internal val ExamCorrectColor = Color(0xFF2E7D32)
internal val ExamWrongColor = Color(0xFFC62828)
internal val ExamWarnColor = Color(0xFFB26A00)

/** Progress of one AI grading request, shared by writing and speaking screens. */
data class AiEvalState(
    val running: Boolean = false,
    val result: ExamEvaluation? = null,
    val error: String? = null,
    /** No usable LLM provider: the screen points to Settings instead of showing a raw error. */
    val noProvider: Boolean = false
)

internal fun Throwable.toAiEvalState(): AiEvalState = AiEvalState(
    error = message ?: toString(),
    noProvider = this is ProviderError.NoKeyConfigured || this is ProviderError.NoProvidersConfigured ||
        this is ProviderError.InvalidKey
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    /** Lets the user tap German words in the content to translate them and save them to their words. */
    wordLookup: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    if (wordLookup) {
        ExamWordLookupHost { ExamScaffold(title, onBack, subtitle, actions, wordLookup = false, content = content) }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { actions() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
internal fun ExamLoading(padding: PaddingValues) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
internal fun ExamEmpty(padding: PaddingValues, text: String = stringResource(R.string.exam_empty_content)) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ExamCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
        tonalElevation = 1.dp,
        border = border,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
internal fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row {
                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Card with a title row that hides or shows its body. */
@Composable
internal fun ExpandableCard(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    leading: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) content()
        }
    }
}

@Composable
internal fun TimerRow(state: TimerState, onToggle: () -> Unit, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Timer,
            contentDescription = null,
            tint = if (state.finished) ExamWrongColor else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatSeconds(state.remainingSeconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (state.finished) ExamWrongColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (state.finished) {
            Text(
                stringResource(R.string.exam_timer_finished),
                style = MaterialTheme.typography.labelMedium,
                color = ExamWrongColor
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (state.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (state.running) R.string.exam_timer_pause else R.string.exam_timer_start)
            )
        }
        IconButton(onClick = onReset) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.exam_timer_reset))
        }
    }
}

/** The AI grading block: button, progress, error or "set up a provider" hint, and the result. */
@Composable
internal fun AiEvaluationSection(
    state: AiEvalState,
    buttonText: String,
    enabled: Boolean,
    onEvaluate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onEvaluate,
            enabled = enabled && !state.running,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.running) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.exam_ai_running))
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonText)
            }
        }
        when {
            state.noProvider -> ExamCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringResource(R.string.exam_ai_no_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.exam_open_settings))
                }
            }
            state.error != null -> Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        state.result?.let { EvaluationCard(it) }
    }
}

@Composable
internal fun EvaluationCard(evaluation: ExamEvaluation) {
    val scoreColor = when {
        evaluation.score >= 75 -> ExamCorrectColor
        evaluation.score >= 60 -> ExamWarnColor
        else -> ExamWrongColor
    }
    ExamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.exam_ai_score, evaluation.score),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
        }
        if (evaluation.summary.isNotBlank()) {
            Text(evaluation.summary, style = MaterialTheme.typography.bodyMedium)
        }
        if (evaluation.criteria.isNotEmpty()) {
            SectionLabel(stringResource(R.string.exam_ai_criteria))
            evaluation.criteria.forEach { criterion ->
                Column {
                    Text(
                        buildString {
                            append(criterion.name)
                            criterion.score?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (criterion.comment.isNotBlank()) {
                        Text(criterion.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.exam_ai_corrections))
        if (evaluation.corrections.isEmpty()) {
            Text(stringResource(R.string.exam_ai_no_corrections), style = MaterialTheme.typography.bodySmall)
        }
        evaluation.corrections.forEach { correction ->
            Column {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ExamWrongColor, textDecoration = TextDecoration.LineThrough)) {
                            append(correction.original)
                        }
                        append("  →  ")
                        withStyle(SpanStyle(color = ExamCorrectColor, fontWeight = FontWeight.SemiBold)) {
                            append(correction.corrected)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (correction.reason.isNotBlank()) {
                    Text(correction.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (evaluation.correctedText.isNotBlank()) {
            ExpandableCard(title = stringResource(R.string.exam_ai_corrected_text)) {
                GermanText(evaluation.correctedText)
            }
        }
    }
}

@Composable
internal fun SampleAnswerCard(sample: String, visible: Boolean, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (visible) R.string.exam_hide_sample else R.string.exam_show_sample))
    }
    if (visible) {
        ExamCard(color = MaterialTheme.colorScheme.surfaceVariant) {
            GermanText(sample)
        }
    }
}
