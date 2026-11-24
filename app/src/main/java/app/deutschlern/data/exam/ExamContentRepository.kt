package app.deutschlern.data.exam

import android.content.Context
import app.deutschlern.domain.exam.ExamFile
import app.deutschlern.domain.exam.ExamPart
import app.deutschlern.domain.exam.ExamVariant
import app.deutschlern.domain.exam.GuideContent
import app.deutschlern.domain.exam.GuideSection
import app.deutschlern.domain.exam.SpeakingContent
import app.deutschlern.domain.exam.SpeakingPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only B2 Beruf exam material bundled in assets/b2beruf, parsed once and kept in memory. */
@Singleton
class ExamContentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val parts = mutableMapOf<ExamPart, List<ExamVariant>>()
    private var speaking: List<SpeakingPrompt>? = null
    private var guide: List<GuideSection>? = null

    suspend fun variants(part: ExamPart): List<ExamVariant> = mutex.withLock {
        parts.getOrPut(part) {
            readAsset(part.fileName)?.let { JSON.decodeFromString<ExamFile>(it).variants }.orEmpty()
        }
    }

    suspend fun variant(id: String): ExamVariant? =
        ExamPart.entries.firstNotNullOfOrNull { part -> variants(part).find { it.id == id } }

    suspend fun speakingPrompts(): List<SpeakingPrompt> = mutex.withLock {
        speaking ?: (readAsset(SPEAKING_FILE)?.let { JSON.decodeFromString<SpeakingContent>(it).prompts }.orEmpty())
            .also { speaking = it }
    }

    /** All guide*.json files merged, in file-name order. */
    suspend fun guideSections(): List<GuideSection> = mutex.withLock {
        guide ?: withContext(Dispatchers.IO) {
            context.assets.list(DIR).orEmpty()
                .filter { it.startsWith("guide") && it.endsWith(".json") }
                .sorted()
                .flatMap { name -> readAsset(name)?.let { JSON.decodeFromString<GuideContent>(it).sections }.orEmpty() }
        }.also { guide = it }
    }

    private suspend fun readAsset(name: String): String? = withContext(Dispatchers.IO) {
        runCatching { context.assets.open("$DIR/$name").bufferedReader().use { it.readText() } }.getOrNull()
    }

    companion object {
        const val DIR = "b2beruf"
        const val SPEAKING_FILE = "sprechen.json"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
