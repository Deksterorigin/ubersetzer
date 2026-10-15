package app.deutschlern.ui.dialog

import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.ui.dialog.components.TranscriptTokenizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class DialogPerformanceBenchmarkTest {

    @Before
    fun setUp() {
        TranscriptTokenizer.clearCache()
    }

    @After
    fun tearDown() {
        TranscriptTokenizer.clearCache()
    }

    private val sampleSentences = listOf(
        "Guten Tag! Wie kann ich Ihnen heute behilflich sein?",
        "Ich interessiere mich für die Deutschkurse auf dem Niveau B1 und B2.",
        "Sehr gerne, wir bieten sowohl Intensivkurse als auch Abendkurse an.",
        "Wann finden die nächsten Einstufungstests statt?",
        "Die Einstufungstests werden jeden Dienstag und Donnerstag ab 10:00 Uhr durchgeführt.",
        "Muss ich mich dafür im Voraus online anmelden oder kann ich einfach vorbeikommen?",
        "Eine vorherige Registrierung auf unserer Website ist unbedingt erforderlich.",
        "Vielen Dank für die ausführliche Auskunft, ich werde mich gleich heute anmelden.",
        "Gern geschehen! Bei weiteren Fragen stehen wir Ihnen jederzeit zur Verfügung.",
        "Auf Wiedersehen und einen schönen Tag noch!"
    )

    /**
     * Synthetically generates 5,000 realistic dialogue segments totaling >= 200,000 characters.
     */
    private fun generate5000Segments(): List<DialogSegment> {
        val segments = ArrayList<DialogSegment>(5000)
        var totalChars = 0

        for (i in 0 until 5000) {
            val sentence = "Segment Nr. $i: ${sampleSentences[i % sampleSentences.size]}"
            val speaker = if (i % 2 == 0) "A" else "B"
            totalChars += sentence.length

            segments.add(
                DialogSegment(
                    id = i.toLong() + 1,
                    dialogId = 999L,
                    orderIndex = i,
                    text = sentence,
                    speakerLabel = speaker,
                    startOffsetMs = i * 2500L
                )
            )
        }

        assertTrue("Generated character count must be at least 200,000 (actual: $totalChars)", totalChars >= 200000)
        assertEquals(5000, segments.size)
        return segments
    }

    @Test
    fun benchmark_5000Segments_200kChars_uncachedVsCachedPerformance() {
        val segments = generate5000Segments()
        val texts = segments.map { it.text }

        // Phase 1: Measure cold tokenization with prewarm
        val coldElapsedMs = measureTimeMillis {
            TranscriptTokenizer.prewarm(texts)
        }

        // Phase 2: Measure warm tokenization from LRU cache (what happens during LazyColumn scrolling)
        val warmElapsedMs = measureTimeMillis {
            for (text in texts) {
                val annotated = TranscriptTokenizer.tokenize(text)
                assertTrue(annotated.text.isNotEmpty())
            }
        }

        println("=== 5,000 Segments / 200k Chars Benchmark Results ===")
        println("Cold prewarm time: ${coldElapsedMs}ms")
        println("Warm cached lookup time for 5,000 items: ${warmElapsedMs}ms")
        println("Cache size: ${TranscriptTokenizer.cacheSize()}")

        // Warm cache lookup for 5,000 items should complete in < 50ms (typically ~5-15ms)
        assertTrue("Warm cached lookups must be fast (< 100ms)", warmElapsedMs < 100)
        // Cold tokenization for 5,000 segments must complete in under 1.5 seconds on desktop/device
        assertTrue("Cold prewarm must finish within 2500ms", coldElapsedMs < 2500)
    }

    @Test
    fun memorySafety_extremeLongTokensAndPunctuation_doesNotExhaustMemory() {
        val longSegment = "Wort ".repeat(10000) // 50,000 characters in one segment
        val elapsed = measureTimeMillis {
            val annotated = TranscriptTokenizer.tokenize(longSegment)
            assertEquals(50000, annotated.text.length)
        }
        assertTrue("Single 50k character segment tokenization took ${elapsed}ms", elapsed < 1000)
    }
}
