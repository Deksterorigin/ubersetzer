package app.deutschlern.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.TranslateWordUseCase
import app.deutschlern.ui.components.WordCard
import app.deutschlern.util.TextNormalizer
import app.deutschlern.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Set by [ExamWordLookupHost]: (word, sentence it came from). Null outside the exam trainer. */
val LocalWordLookup = staticCompositionLocalOf<((String, String) -> Unit)?> { null }

data class WordLookupState(
    val loadingWord: String? = null,
    val word: WordTranslation? = null,
    val partnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN,
    /** Id of the word this lookup added to the vocabulary; null when it was already there. */
    val addedWordId: Long? = null,
    val alreadyKnown: Boolean = false,
    val removed: Boolean = false,
    val error: String? = null
)

/** Tap on a German word in an exam text: translate it and put it straight into the words to learn. */
@HiltViewModel
class ExamWordLookupViewModel @Inject constructor(
    private val translateWordUseCase: TranslateWordUseCase,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WordLookupState())
    val state: StateFlow<WordLookupState> = _state.asStateFlow()
    private var lookupJob: Job? = null

    fun lookup(word: String, context: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val partner = settingsRepository.getSettings().selectedPartnerLanguage
            _state.value = WordLookupState(loadingWord = word, partnerLanguage = partner)
            val direction = TranslationDirection.getDirection(partner = partner, fromGerman = true)
            translateWordUseCase(word = word, direction = direction, context = context)
                .onSuccess { result -> save(result.data, partner) }
                .onFailure { err -> _state.value = WordLookupState(partnerLanguage = partner, error = err.message ?: word) }
        }
    }

    private suspend fun save(word: WordTranslation, partner: PartnerLanguage) {
        runSuspendCatching {
            val existed = vocabularyRepository.findWord(word.lemma, word.pos, word.partnerLanguage.ifBlank { "ru" }) != null
            val saved = vocabularyRepository.saveWordTranslation(word)
            _state.value = WordLookupState(
                word = word,
                partnerLanguage = partner,
                addedWordId = saved.id.takeUnless { existed },
                alreadyKnown = existed
            )
        }.onFailure { err ->
            _state.value = WordLookupState(word = word, partnerLanguage = partner, error = err.message)
        }
    }

    /** Undo for a word added by mistake; words that were in the vocabulary before stay untouched. */
    fun removeAddedWord() {
        val id = _state.value.addedWordId ?: return
        viewModelScope.launch {
            runSuspendCatching { vocabularyRepository.deleteWord(id) }
                .onSuccess { _state.update { it.copy(addedWordId = null, removed = true) } }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }

    fun dismiss() {
        lookupJob?.cancel()
        _state.value = WordLookupState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamWordLookupHost(
    viewModel: ExamWordLookupViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWordLookup provides viewModel::lookup) { content() }

        state.loadingWord?.let { word ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.exam_word_translating, word), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    val showSheet = state.word != null || (state.error != null && state.loadingWord == null)
    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::dismiss, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val status = when {
                    state.removed -> stringResource(R.string.exam_word_removed)
                    state.alreadyKnown -> stringResource(R.string.exam_word_already_saved)
                    state.addedWordId != null -> stringResource(R.string.exam_word_saved)
                    else -> null
                }
                status?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.removed) MaterialTheme.colorScheme.onSurfaceVariant else ExamCorrectColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (state.addedWordId != null) {
                            TextButton(onClick = viewModel::removeAddedWord) { Text(stringResource(R.string.exam_word_undo)) }
                        }
                    }
                }
                state.error?.let {
                    Text(stringResource(R.string.exam_word_error, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                state.word?.let { word ->
                    WordCard(
                        word = word,
                        direction = TranslationDirection.getDirection(partner = state.partnerLanguage, fromGerman = true)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private val WORD = Regex("""[\p{L}\p{M}]+(?:-[\p{L}\p{M}]+)*""")
private val SENTENCE_END = Regex("""(?<=[.!?…])\s+|\n+""")

/**
 * Appends German text whose words open a translation when tapped. Each word carries its own
 * sentence as context, so the translator can pick the right meaning.
 */
fun AnnotatedString.Builder.appendGermanText(text: String, onWord: ((String, String) -> Unit)?) {
    if (onWord == null) {
        append(text)
        return
    }
    var cursor = 0
    findGermanWords(text).forEach { found ->
        append(text.substring(cursor, found.range.first))
        withLink(LinkAnnotation.Clickable(tag = "word") { onWord(TextNormalizer.normalize(found.word).trim(), found.sentence) }) {
            append(found.word)
        }
        cursor = found.range.last + 1
    }
    append(text.substring(cursor))
}

data class GermanWord(val word: String, val range: IntRange, val sentence: String)

/** Words of [text] (hyphenated compounds stay whole) with the sentence each one stands in. */
fun findGermanWords(text: String): List<GermanWord> {
    val sentenceBreaks = SENTENCE_END.findAll(text).map { it.range.last + 1 }.toList() + text.length
    return WORD.findAll(text).map { match ->
        val start = sentenceBreaks.lastOrNull { it <= match.range.first } ?: 0
        val end = sentenceBreaks.first { it > match.range.first }
        GermanWord(match.value, match.range, text.substring(start, end).trim())
    }.toList()
}

/** German exam text with tap-to-translate words inside the trainer; plain text elsewhere. */
@Composable
fun GermanText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    prefix: AnnotatedString? = null
) {
    val onWord = LocalWordLookup.current
    val annotated = remember(text, prefix, onWord) {
        buildAnnotatedString {
            prefix?.let { append(it) }
            appendGermanText(text, onWord)
        }
    }
    Text(annotated, modifier = modifier, style = style, color = color, fontWeight = fontWeight)
}
