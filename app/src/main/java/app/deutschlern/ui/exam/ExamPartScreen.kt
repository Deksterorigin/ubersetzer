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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.data.exam.ExamContentRepository
import app.deutschlern.data.exam.ExamProgressStore
import app.deutschlern.domain.exam.ExamPart
import app.deutschlern.domain.exam.ExamVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamPartState(
    val part: ExamPart? = null,
    val loading: Boolean = true,
    val variants: List<ExamVariant> = emptyList(),
    val best: Map<String, Int> = emptyMap()
)

@HiltViewModel
class ExamPartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExamContentRepository,
    progressStore: ExamProgressStore
) : ViewModel() {

    private val part: ExamPart? = savedStateHandle.get<String>(ARG_PART)
        ?.let { name -> ExamPart.entries.find { it.name == name } }
    private val variants = MutableStateFlow<List<ExamVariant>?>(null)

    val state: StateFlow<ExamPartState> = combine(variants, progressStore.bestScores) { variants, best ->
        ExamPartState(part = part, loading = variants == null, variants = variants.orEmpty(), best = best)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamPartState(part = part))

    init {
        viewModelScope.launch {
            variants.value = part?.let { runCatching { repository.variants(it) }.getOrDefault(emptyList()) }.orEmpty()
        }
    }

    companion object {
        const val ARG_PART = "part"
    }
}

@Composable
fun ExamPartScreen(
    onBack: () -> Unit,
    onOpenVariant: (String) -> Unit,
    viewModel: ExamPartViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ExamScaffold(
        title = state.part?.germanName().orEmpty(),
        subtitle = state.part?.description(),
        onBack = onBack
    ) { padding ->
        when {
            state.loading -> ExamLoading(padding)
            state.variants.isEmpty() -> ExamEmpty(padding)
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.variants, key = { it.id }) { variant ->
                    VariantRow(variant = variant, best = state.best[variant.id], onClick = { onOpenVariant(variant.id) })
                }
            }
        }
    }
}

@Composable
private fun VariantRow(variant: ExamVariant, best: Int?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(variant.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                variant.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            ScoreChip(best)
        }
    }
}

@Composable
internal fun ScoreChip(best: Int?) {
    val color = when {
        best == null -> MaterialTheme.colorScheme.onSurfaceVariant
        best >= 75 -> ExamCorrectColor
        best >= 60 -> ExamWarnColor
        else -> ExamWrongColor
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            if (best == null) stringResource(R.string.exam_not_started) else "$best%",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
