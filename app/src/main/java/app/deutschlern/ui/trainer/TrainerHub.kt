package app.deutschlern.ui.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.data.trainer.TrainerStats
import app.deutschlern.data.trainer.TrainerStatsStore
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.trainer.ExerciseFactory
import app.deutschlern.domain.trainer.TrainerMode
import app.deutschlern.domain.trainer.WordPool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainerHubState(
    val pool: WordPool = WordPool.SMART,
    /** Words usable by each trainer for the selected pool. */
    val wordCounts: Map<TrainerMode, Int> = emptyMap(),
    val stats: TrainerStats = TrainerStats()
)

@HiltViewModel
class TrainerHubViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    statsStore: TrainerStatsStore
) : ViewModel() {

    private val factory = ExerciseFactory()
    private val pool = MutableStateFlow(WordPool.SMART)
    private val counts = MutableStateFlow<Map<WordPool, Map<TrainerMode, Int>>>(emptyMap())

    val state: StateFlow<TrainerHubState> = combine(pool, counts, statsStore.stats) { pool, counts, stats ->
        TrainerHubState(pool = pool, wordCounts = counts[pool].orEmpty(), stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainerHubState())

    init {
        refresh()
    }

    /** Called whenever the hub becomes visible: words may have been added or marked meanwhile. */
    fun refresh() {
        viewModelScope.launch {
            val words = reviewRepository.getAllCards().map { it.word }
            counts.value = WordPool.entries.associateWith { pool ->
                val poolWords = if (pool == WordPool.DIFFICULT) words.filter { it.isDifficult } else words
                TrainerMode.entries.associateWith { mode -> poolWords.count { factory.canBuild(it, mode) } }
            }
        }
    }

    fun onPoolSelected(selected: WordPool) {
        pool.value = selected
    }
}

@Composable
fun TrainerHub(
    onOpenTrainer: (TrainerMode, WordPool) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainerHubViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StreakCard(stats = state.stats)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.pool == WordPool.SMART,
                    onClick = { viewModel.onPoolSelected(WordPool.SMART) },
                    label = { Text(stringResource(R.string.trainer_pool_smart)) }
                )
                FilterChip(
                    selected = state.pool == WordPool.DIFFICULT,
                    onClick = { viewModel.onPoolSelected(WordPool.DIFFICULT) },
                    label = { Text(stringResource(R.string.trainer_pool_difficult)) },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
        items(TrainerMode.entries.toList(), key = { it.name }) { mode ->
            val count = state.wordCounts[mode] ?: 0
            TrainerCard(
                mode = mode,
                wordCount = count,
                enabled = count >= minimumWords(mode),
                onClick = { onOpenTrainer(mode, state.pool) }
            )
        }
    }
}

private fun minimumWords(mode: TrainerMode): Int = if (mode == TrainerMode.CHOICE) 4 else 1

@Composable
private fun StreakCard(stats: TrainerStats) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (stats.streakDays > 0) Color(0xFFF57C00) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.trainer_streak_days, stats.streakDays),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.trainer_answers_today, stats.answersToday),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrainerCard(mode: TrainerMode, wordCount: Int, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = trainerIcon(mode),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trainerTitle(mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    trainerDescription(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            Text(
                stringResource(R.string.trainer_words_available, wordCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun trainerIcon(mode: TrainerMode): ImageVector = when (mode) {
    TrainerMode.MIXED -> Icons.Default.Shuffle
    TrainerMode.RECALL -> Icons.Default.Edit
    TrainerMode.CLOZE -> Icons.Default.TextFields
    TrainerMode.FORMS -> Icons.Default.Spellcheck
    TrainerMode.ARTICLES -> Icons.AutoMirrored.Filled.Chat
    TrainerMode.LISTENING -> Icons.AutoMirrored.Filled.VolumeUp
    TrainerMode.SPEAKING -> Icons.Default.Mic
    TrainerMode.CHOICE -> Icons.Default.Quiz
}

@Composable
fun trainerTitle(mode: TrainerMode): String = stringResource(
    when (mode) {
        TrainerMode.MIXED -> R.string.trainer_mixed_title
        TrainerMode.RECALL -> R.string.trainer_recall_title
        TrainerMode.CLOZE -> R.string.trainer_cloze_title
        TrainerMode.FORMS -> R.string.trainer_forms_title
        TrainerMode.ARTICLES -> R.string.trainer_articles_title
        TrainerMode.LISTENING -> R.string.trainer_listening_title
        TrainerMode.SPEAKING -> R.string.trainer_speaking_title
        TrainerMode.CHOICE -> R.string.trainer_choice_title
    }
)

@Composable
private fun trainerDescription(mode: TrainerMode): String = stringResource(
    when (mode) {
        TrainerMode.MIXED -> R.string.trainer_mixed_desc
        TrainerMode.RECALL -> R.string.trainer_recall_desc
        TrainerMode.CLOZE -> R.string.trainer_cloze_desc
        TrainerMode.FORMS -> R.string.trainer_forms_desc
        TrainerMode.ARTICLES -> R.string.trainer_articles_desc
        TrainerMode.LISTENING -> R.string.trainer_listening_desc
        TrainerMode.SPEAKING -> R.string.trainer_speaking_desc
        TrainerMode.CHOICE -> R.string.trainer_choice_desc
    }
)
