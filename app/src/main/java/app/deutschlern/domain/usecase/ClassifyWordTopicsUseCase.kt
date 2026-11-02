package app.deutschlern.domain.usecase

import app.deutschlern.data.remote.dto.WordTopicsResponseDto
import app.deutschlern.domain.model.WordTopic
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.util.Prompts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files words that have no topics yet under [WordTopic]s, in batches, with one LLM call per batch.
 * Runs quietly in the background: without a key or network it simply leaves words unclassified
 * and they are picked up on the next run.
 */
@Singleton
class ClassifyWordTopicsUseCase @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository,
    private val vocabularyRepository: VocabularyRepository
) {
    private val mutex = Mutex()

    /** Returns how many words were classified. */
    suspend operator fun invoke(maxBatches: Int = 5): Result<Int> {
        if (mutex.isLocked) return Result.success(0)
        return mutex.withLock {
            var classified = 0
            repeat(maxBatches) {
                val batch = vocabularyRepository.getWordsWithoutTopics(BATCH_SIZE)
                if (batch.isEmpty()) return@withLock Result.success(classified)

                val topicsById = requestTopics(batch.map { word ->
                    val translation = word.selectedMeaning?.translation.orEmpty()
                    val lemma = listOfNotNull(word.article, word.lemma).joinToString(" ")
                    "${word.id} | $lemma (${word.pos}) | $translation"
                }).getOrElse { return@withLock if (classified > 0) Result.success(classified) else Result.failure(it) }

                batch.forEach { word ->
                    // Words the model skipped get an empty list so they are not re-sent forever
                    vocabularyRepository.updateWordTopics(word.id, topicsById[word.id].orEmpty())
                }
                classified += batch.size
            }
            Result.success(classified)
        }
    }

    private suspend fun requestTopics(lines: List<String>): Result<Map<Long, List<String>>> {
        val settings = settingsRepository.getSettings()
        val provider = registry.getProvider(settings.selectedCheckProviderId) as? StructuredGenerationProvider
            ?: registry.geminiProvider as StructuredGenerationProvider
        val defaultModel = if (provider.id == "gemini") "gemini-2.5-flash" else "default"
        val model = settings.getSelectedModel(provider.id, isWordMode = false, defaultModel)

        val raw = provider.generateStructured(
            prompt = Prompts.buildWordTopicsUserPrompt(lines),
            systemPrompt = Prompts.wordTopicsSystemPrompt(WordTopic.entries.map { it.key }),
            schemaJson = Prompts.WORD_TOPICS_SCHEMA_SPEC,
            model = model
        ).getOrElse { return Result.failure(it) }

        val dto = StructuredJsonParser.parse(raw, WordTopicsResponseDto.serializer(), provider.id)
            .getOrElse { return Result.failure(it) }

        return Result.success(
            dto.items.associate { item ->
                item.id to item.topics.filter { WordTopic.fromKey(it) != null }.distinct().take(2)
            }
        )
    }

    private companion object {
        const val BATCH_SIZE = 40
    }
}
