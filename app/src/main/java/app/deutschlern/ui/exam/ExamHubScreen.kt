package app.deutschlern.ui.exam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.data.exam.ExamContentRepository
import app.deutschlern.data.exam.ExamProgressStore
import app.deutschlern.domain.exam.ExamPart
import app.deutschlern.domain.exam.ExamSkill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExamHubTarget {
    data class Part(val part: ExamPart) : ExamHubTarget
    data class Speaking(val teil: String) : ExamHubTarget
    data object Guide : ExamHubTarget
}

data class ExamHubRow(val target: ExamHubTarget, val total: Int, val done: Int)

data class ExamHubSection(val skill: ExamSkill?, val rows: List<ExamHubRow>)

data class ExamHubState(
    val loading: Boolean = true,
    val sections: List<ExamHubSection> = emptyList()
)

private data class ExamCatalog(
    val partIds: Map<ExamPart, List<String>>,
    val speakingIds: Map<String, List<String>>,
    val guideCount: Int
)

@HiltViewModel
class ExamHubViewModel @Inject constructor(
    private val repository: ExamContentRepository,
    progressStore: ExamProgressStore
) : ViewModel() {

    private val catalog = MutableStateFlow<ExamCatalog?>(null)

    val state: StateFlow<ExamHubState> = combine(catalog, progressStore.bestScores) { catalog, scores ->
        catalog?.let { buildState(it, scores.keys) } ?: ExamHubState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamHubState())

    init {
        viewModelScope.launch {
            val parts = ExamPart.entries.associateWith { part ->
                runCatching { repository.variants(part) }.getOrDefault(emptyList()).map { it.id }
            }
            val speaking = runCatching { repository.speakingPrompts() }.getOrDefault(emptyList())
                .groupBy({ it.teil }, { it.id })
                .toSortedMap()
            val guide = runCatching { repository.guideSections() }.getOrDefault(emptyList()).size
            catalog.value = ExamCatalog(parts, speaking, guide)
        }
    }

    private fun buildState(catalog: ExamCatalog, done: Set<String>): ExamHubState {
        fun partRow(part: ExamPart): ExamHubRow {
            val ids = catalog.partIds[part].orEmpty()
            return ExamHubRow(ExamHubTarget.Part(part), ids.size, ids.count { it in done })
        }
        val skills = listOf(ExamSkill.LESEN, ExamSkill.HOEREN, ExamSkill.SCHREIBEN, ExamSkill.SPRACHBAUSTEINE)
        val partSections = skills.map { skill ->
            ExamHubSection(skill, ExamPart.entries.filter { it.skill == skill }.map(::partRow))
        }
        val speaking = ExamHubSection(
            ExamSkill.SPRECHEN,
            catalog.speakingIds.map { (teil, ids) ->
                ExamHubRow(ExamHubTarget.Speaking(teil), ids.size, ids.count { it in done })
            }
        )
        val guide = ExamHubSection(null, listOf(ExamHubRow(ExamHubTarget.Guide, catalog.guideCount, 0)))
        return ExamHubState(loading = false, sections = partSections + speaking + guide)
    }
}

@Composable
fun ExamHubScreen(
    onBack: () -> Unit,
    onOpenPart: (ExamPart) -> Unit,
    onOpenSpeaking: (String) -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: ExamHubViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ExamScaffold(
        title = stringResource(R.string.exam_hub_title),
        subtitle = "telc Deutsch-Test für den Beruf B2",
        onBack = onBack
    ) { padding ->
        if (state.loading) {
            ExamLoading(padding)
            return@ExamScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.sections.forEach { section ->
                item(key = "header_${section.skill}") {
                    Text(
                        section.skill?.let(::skillName) ?: stringResource(R.string.exam_guide_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (section.rows.isEmpty()) {
                    item(key = "empty_${section.skill}") {
                        Text(
                            stringResource(R.string.exam_empty_content),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(section.rows, key = { it.target.toString() }) { row ->
                    HubRow(
                        row = row,
                        icon = section.skill.icon(),
                        onClick = {
                            when (val target = row.target) {
                                is ExamHubTarget.Part -> onOpenPart(target.part)
                                is ExamHubTarget.Speaking -> onOpenSpeaking(target.teil)
                                ExamHubTarget.Guide -> onOpenGuide()
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun skillName(skill: ExamSkill): String = when (skill) {
    ExamSkill.LESEN -> "Lesen"
    ExamSkill.HOEREN -> "Hören"
    ExamSkill.SCHREIBEN -> "Schreiben"
    ExamSkill.SPRACHBAUSTEINE -> "Sprachbausteine"
    ExamSkill.SPRECHEN -> "Sprechen"
}

private fun ExamSkill?.icon(): ImageVector = when (this) {
    ExamSkill.LESEN -> Icons.AutoMirrored.Filled.MenuBook
    ExamSkill.HOEREN -> Icons.AutoMirrored.Filled.VolumeUp
    ExamSkill.SCHREIBEN -> Icons.Default.Edit
    ExamSkill.SPRACHBAUSTEINE -> Icons.Default.Extension
    ExamSkill.SPRECHEN -> Icons.Default.Mic
    null -> Icons.Default.Lightbulb
}

@Composable
private fun HubRow(row: ExamHubRow, icon: ImageVector, onClick: () -> Unit) {
    val enabled = row.total > 0
    val alpha = if (enabled) 1f else 0.45f
    val (title, description) = when (val target = row.target) {
        is ExamHubTarget.Part -> target.part.germanName() to target.part.description()
        is ExamHubTarget.Speaking -> speakingName(target.teil) to speakingDescription(target.teil)
        ExamHubTarget.Guide -> "Vorlagen & Redemittel" to stringResource(R.string.exam_desc_guide)
    }
    val counter = when (row.target) {
        is ExamHubTarget.Part -> stringResource(R.string.exam_variants_progress, row.total, row.done)
        is ExamHubTarget.Speaking -> stringResource(R.string.exam_prompts_progress, row.total, row.done)
        ExamHubTarget.Guide -> stringResource(R.string.exam_guide_sections_count, row.total)
    }
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                Text(
                    counter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.done > 0) ExamCorrectColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
