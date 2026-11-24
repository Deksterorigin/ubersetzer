package app.deutschlern.data.exam

import app.deutschlern.domain.exam.ExamFile
import app.deutschlern.domain.exam.ExamPart
import app.deutschlern.domain.exam.ExamScoring
import app.deutschlern.domain.exam.GapText
import app.deutschlern.domain.exam.GuideContent
import app.deutschlern.domain.exam.SpeakingContent
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Validates whatever B2 Beruf content files are bundled; missing files are skipped. */
class ExamContentTest {

    private val dir = listOf(File("src/main/assets/b2beruf"), File("app/src/main/assets/b2beruf")).firstOrNull { it.isDirectory }

    private fun read(name: String): String? = dir?.resolve(name)?.takeIf { it.isFile }?.readText()

    @Test
    fun `exam part files parse and are consistent`() {
        val problems = mutableListOf<String>()
        val ids = mutableMapOf<String, String>()
        for (part in ExamPart.entries) {
            val text = read(part.fileName) ?: continue
            val parsed = runCatching { ExamContentRepository.JSON.decodeFromString<ExamFile>(text) }
            val file = parsed.getOrNull()
            if (file == null) {
                problems += "${part.fileName}: parse error ${parsed.exceptionOrNull()?.message}"
                continue
            }
            if (file.part != part) problems += "${part.fileName}: part is ${file.part}"
            for (variant in file.variants) {
                val where = "${part.fileName}/${variant.id}"
                ids.put(variant.id, part.fileName)?.let { problems += "$where: duplicate id (also in $it)" }
                if (variant.part != part) problems += "$where: variant part ${variant.part}"
                val numbers = variant.questions.map { it.number }
                numbers.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { problems += "$where: question $it repeated" }
                for (segment in variant.segments) {
                    for (question in segment.questions) {
                        val options = ExamScoring.optionsFor(segment, question)
                        val answer = question.answer
                        if (options.isEmpty()) problems += "$where: question ${question.number} has no options"
                        if (answer != null && options.none { it.key == answer }) {
                            problems += "$where: question ${question.number} answer '$answer' not in options ${options.map { it.key }}"
                        }
                    }
                    val gaps = segment.passages.flatMap { GapText.gapNumbers(it.body) }
                    if (gaps.isNotEmpty()) {
                        val segmentNumbers = segment.questions.map { it.number }
                        gaps.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { problems += "$where: gap {{$it}} repeated" }
                        (gaps - segmentNumbers.toSet()).forEach { problems += "$where: gap {{$it}} has no question" }
                        (segmentNumbers - gaps.toSet()).forEach { problems += "$where: question $it has no gap marker" }
                    }
                }
                variant.phoneNote?.fields?.forEach { field ->
                    // A blank answer is allowed: the source sometimes misses it, and the app leaves it ungraded
                    if (field.label.isBlank()) problems += "$where: phone-note field without label"
                }
                variant.writing?.let { if (it.situation.isBlank()) problems += "$where: empty writing situation" }
            }
        }
        assertTrue(problems.take(50).joinToString("\n", prefix = "${problems.size} problems:\n"), problems.isEmpty())
    }

    @Test
    fun `speaking prompts parse with unique ids`() {
        val text = read(ExamContentRepository.SPEAKING_FILE) ?: return
        val prompts = ExamContentRepository.JSON.decodeFromString<SpeakingContent>(text).prompts
        val duplicates = prompts.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate ids: $duplicates", duplicates.isEmpty())
        assertTrue("blank prompt", prompts.none { it.de.isBlank() || it.teil.isBlank() })
    }

    @Test
    fun `guide files parse with unique section ids`() {
        val files = dir?.listFiles { f -> f.name.startsWith("guide") && f.name.endsWith(".json") }.orEmpty()
        val sections = files.flatMap { ExamContentRepository.JSON.decodeFromString<GuideContent>(it.readText()).sections }
        val duplicates = sections.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate guide ids: $duplicates", duplicates.isEmpty())
    }
}
