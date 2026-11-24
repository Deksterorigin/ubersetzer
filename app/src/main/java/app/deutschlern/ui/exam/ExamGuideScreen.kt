package app.deutschlern.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.data.exam.ExamContentRepository
import app.deutschlern.domain.exam.GuideCategory
import app.deutschlern.domain.exam.GuideSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamGuideState(
    val loading: Boolean = true,
    val groups: List<Pair<GuideCategory, List<GuideSection>>> = emptyList()
)

@HiltViewModel
class ExamGuideViewModel @Inject constructor(
    private val repository: ExamContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExamGuideState())
    val state: StateFlow<ExamGuideState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sections = runCatching { repository.guideSections() }.getOrDefault(emptyList())
            val grouped = sections.groupBy { it.category }
            _state.value = ExamGuideState(
                loading = false,
                groups = GuideCategory.entries.mapNotNull { category -> grouped[category]?.let { category to it } }
            )
        }
    }
}

@Composable
fun ExamGuideScreen(
    onBack: () -> Unit,
    viewModel: ExamGuideViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ExamScaffold(title = stringResource(R.string.exam_guide_title), onBack = onBack, wordLookup = true) { padding ->
        when {
            state.loading -> ExamLoading(padding)
            state.groups.isEmpty() -> ExamEmpty(padding)
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.groups.forEach { (category, sections) ->
                    item(key = "cat_$category") {
                        Text(
                            category.label(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    items(sections, key = { it.id }) { section ->
                        ExpandableCard(title = section.title) { GuideSectionBody(section) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSectionBody(section: GuideSection) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            section.items.forEachIndexed { index, item ->
                if (index > 0 || section.body != null) HorizontalDivider()
                Column {
                    item.de?.takeIf { it.isNotBlank() }?.let {
                        GermanText(it, fontWeight = FontWeight.SemiBold)
                    }
                    item.ru?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
