package app.deutschlern.domain.trainer

import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.sanitizer.ArticleSanitizer
import app.deutschlern.domain.sanitizer.PosNormalizer
import kotlin.random.Random

/** Turns words into exercises. Pure and deterministic for a given [random], so it is unit-testable. */
class ExerciseFactory(private val random: Random = Random.Default) {

    fun canBuild(word: WordEntry, mode: TrainerMode): Boolean = when (mode) {
        TrainerMode.MIXED, TrainerMode.RECALL, TrainerMode.LISTENING, TrainerMode.SPEAKING, TrainerMode.CHOICE ->
            meaning(word).isNotBlank()
        TrainerMode.ARTICLES -> article(word) != null
        TrainerMode.CLOZE -> findCloze(word) != null
        TrainerMode.FORMS -> availableForms(word).isNotEmpty()
    }

    /**
     * Builds one exercise per word. [MIXED] interleaves formats: new words start with recognition,
     * known ones rotate through recall, gap-fill, forms, articles and dictation, never repeating
     * the previous format twice in a row.
     */
    fun build(words: List<WordEntry>, mode: TrainerMode, distractorPool: List<WordEntry>): List<Exercise> {
        var previous: TrainerMode? = null
        return words.mapNotNull { word ->
            val concreteMode = if (mode == TrainerMode.MIXED) pickMixedMode(word, previous) else mode
            previous = concreteMode
            // Recall works for every word, so it is the fallback when e.g. distractors are missing
            build(word, concreteMode, distractorPool) ?: build(word, TrainerMode.RECALL, distractorPool)
        }
    }

    fun build(word: WordEntry, mode: TrainerMode, distractorPool: List<WordEntry>): Exercise? = when (mode) {
        TrainerMode.MIXED -> build(word, pickMixedMode(word, null), distractorPool)
        TrainerMode.RECALL -> recall(word)
        TrainerMode.CLOZE -> cloze(word)
        TrainerMode.FORMS -> forms(word)
        TrainerMode.ARTICLES -> article(word)?.let { Exercise.Article(word, it) }
        TrainerMode.LISTENING -> dictation(word)
        TrainerMode.SPEAKING -> speaking(word)
        TrainerMode.CHOICE -> choice(word, distractorPool)
    }

    private fun pickMixedMode(word: WordEntry, previous: TrainerMode?): TrainerMode {
        if (word.status == WordStatus.NEW && !word.isDifficult) return TrainerMode.CHOICE
        val options = listOf(TrainerMode.RECALL, TrainerMode.CLOZE, TrainerMode.FORMS, TrainerMode.ARTICLES, TrainerMode.LISTENING)
            .filter { canBuild(word, it) && it != previous }
        // Recall is the strongest format, so it gets double weight
        val weighted = options + options.filter { it == TrainerMode.RECALL }
        return weighted.randomOrNull(random) ?: TrainerMode.RECALL
    }

    private fun recall(word: WordEntry): Exercise.Typing = Exercise.Typing(
        word = word,
        kind = TypingKind.TRANSLATE_TO_GERMAN,
        prompt = meaning(word),
        hint = word.selectedMeaning?.exampleRu?.takeIf { it.isNotBlank() },
        expected = lemma(word),
        requiredArticle = article(word),
        audioText = displayGerman(word)
    )

    private fun dictation(word: WordEntry): Exercise.Typing = Exercise.Typing(
        word = word,
        kind = TypingKind.DICTATION,
        prompt = "",
        hint = meaning(word),
        expected = lemma(word),
        requiredArticle = article(word),
        audioText = displayGerman(word)
    )

    private fun speaking(word: WordEntry): Exercise.Speaking = Exercise.Speaking(
        word = word,
        prompt = meaning(word),
        expected = lemma(word),
        requiredArticle = article(word)
    )

    private fun cloze(word: WordEntry): Exercise.Typing? {
        val (sentence, translation, found) = findCloze(word) ?: return null
        return Exercise.Typing(
            word = word,
            kind = TypingKind.CLOZE,
            prompt = sentence.replaceFirst(found, GAP),
            hint = translation.ifBlank { meaning(word) },
            expected = found,
            nearMisses = listOf(lemma(word)).filter { !it.equals(found, ignoreCase = true) },
            audioText = sentence
        )
    }

    private fun forms(word: WordEntry): Exercise.Typing? {
        val kind = availableForms(word).randomOrNull(random) ?: return null
        val forms = word.verbForms
        return when (kind) {
            TypingKind.PLURAL -> Exercise.Typing(
                word = word,
                kind = kind,
                prompt = displayGerman(word),
                hint = meaning(word),
                expected = stripArticle(word.plural.orEmpty()),
                audioText = "die ${stripArticle(word.plural.orEmpty())}"
            )
            TypingKind.PRAETERITUM -> Exercise.Typing(
                word = word,
                kind = kind,
                prompt = lemma(word),
                hint = meaning(word),
                expected = forms?.praeteritum.orEmpty(),
                audioText = "er ${forms?.praeteritum}"
            )
            else -> {
                val auxiliary = if (forms?.auxiliary.equals("sein", ignoreCase = true)) "ist" else "hat"
                Exercise.Typing(
                    word = word,
                    kind = TypingKind.PERFEKT,
                    prompt = lemma(word),
                    hint = meaning(word),
                    expected = "$auxiliary ${forms?.partizip2}",
                    audioText = "er $auxiliary ${forms?.partizip2}"
                )
            }
        }
    }

    private fun choice(word: WordEntry, pool: List<WordEntry>): Exercise.Choice? {
        val correct = meaning(word)
        if (correct.isBlank()) return null
        val pos = PosNormalizer.normalize(word.pos)
        // Same part of speech first: distractors that are obviously different teach nothing
        val candidates = pool.asSequence()
            .filter { it.id != word.id }
            .sortedBy { if (PosNormalizer.normalize(it.pos) == pos) 0 else 1 }
            .map { meaning(it) }
            .filter { it.isNotBlank() && !it.equals(correct, ignoreCase = true) }
            .distinct()
            .take(DISTRACTOR_CANDIDATES)
            .toList()
            .shuffled(random)
            .take(CHOICE_OPTIONS - 1)
        if (candidates.isEmpty()) return null
        val options = (candidates + correct).shuffled(random)
        return Exercise.Choice(
            word = word,
            prompt = displayGerman(word),
            options = options,
            correctIndex = options.indexOf(correct)
        )
    }

    private fun availableForms(word: WordEntry): List<TypingKind> {
        val list = mutableListOf<TypingKind>()
        val plural = stripArticle(word.plural.orEmpty())
        if (article(word) != null && plural.isNotBlank() && plural != "-" && !plural.startsWith("-")) {
            list += TypingKind.PLURAL
        }
        val forms = word.verbForms
        if (PosNormalizer.normalize(word.pos) == PosNormalizer.POS_VERB && forms != null) {
            if (!forms.praeteritum.isNullOrBlank()) list += TypingKind.PRAETERITUM
            if (!forms.partizip2.isNullOrBlank() && !forms.partizip2.contains(' ')) list += TypingKind.PERFEKT
        }
        return list
    }

    /** Sentence, its translation and the exact substring that stands for the word. */
    private fun findCloze(word: WordEntry): Triple<String, String, String>? {
        val lemma = lemma(word)
        val meanings = listOfNotNull(word.selectedMeaning) + word.meanings
        for (meaning in meanings) {
            val sentence = meaning.exampleDe.trim()
            if (sentence.isBlank()) continue

            if (lemma.contains(' ')) {
                val index = sentence.indexOf(lemma, ignoreCase = true)
                if (index >= 0) return Triple(sentence, meaning.exampleRu, sentence.substring(index, index + lemma.length))
                continue
            }

            val stem = stem(lemma)
            if (stem.length < MIN_STEM_LENGTH) continue
            val token = Regex("[\\p{L}-]+").findAll(sentence)
                .map { it.value }
                .firstOrNull { it.lowercase().contains(stem) }
            if (token != null) return Triple(sentence, meaning.exampleRu, token)
        }
        return null
    }

    private fun stem(lemma: String): String {
        val lower = lemma.lowercase()
        val trimmed = when {
            lower.endsWith("en") -> lower.dropLast(2)
            lower.endsWith("n") || lower.endsWith("e") -> lower.dropLast(1)
            else -> lower
        }
        return trimmed.takeIf { it.length >= MIN_STEM_LENGTH } ?: lower
    }

    private fun article(word: WordEntry): String? {
        val sanitized = ArticleSanitizer.sanitize(word.lemma, word.pos, word.article)
        return sanitized.article?.lowercase()?.takeIf { it in setOf("der", "die", "das") }
    }

    private fun lemma(word: WordEntry): String = ArticleSanitizer.sanitize(word.lemma, word.pos, word.article).lemma

    private fun displayGerman(word: WordEntry): String = listOfNotNull(article(word), lemma(word)).joinToString(" ")

    private fun meaning(word: WordEntry): String = word.selectedMeaning?.translation.orEmpty().trim()

    private fun stripArticle(text: String): String =
        text.trim().replace(Regex("^(der|die|das)\\s+", RegexOption.IGNORE_CASE), "")

    companion object {
        const val GAP = "_____"
        private const val CHOICE_OPTIONS = 4
        private const val DISTRACTOR_CANDIDATES = 8
        private const val MIN_STEM_LENGTH = 4
    }
}

/**
 * Chooses which words a session trains: forgotten and difficult words first, then due ones,
 * then weak ones, with a cap on brand-new words so a session never overwhelms.
 */
object TrainerSessionPlanner {

    const val SESSION_SIZE = 15
    private const val MAX_NEW_WORDS = 5

    fun select(
        cards: List<ReviewCard>,
        pool: WordPool,
        now: Long,
        random: Random = Random.Default,
        size: Int = SESSION_SIZE
    ): List<WordEntry> {
        val candidates = cards.filter { pool == WordPool.SMART || it.word.isDifficult }
        val (newCards, practiced) = candidates.partition { it.word.status == WordStatus.NEW && it.sm2State.repetitions == 0 }

        fun score(card: ReviewCard): Double {
            var score = random.nextDouble() * 5
            if (card.word.isDifficult) score += 100
            if (card.sm2State.dueAt <= now) score += 50
            score += (2.5 - card.sm2State.easeFactor).coerceAtLeast(0.0) * 40
            if (card.word.status == WordStatus.LEARNING) score += 10
            return score
        }

        val chosenNew = newCards.sortedByDescending(::score).take(MAX_NEW_WORDS)
        val chosenPracticed = practiced.sortedByDescending(::score).take(size - chosenNew.size)
        val chosen = chosenPracticed + chosenNew
        // Top up with more new words when there is not enough practiced material
        val topUp = newCards.filterNot { it in chosenNew }.take((size - chosen.size).coerceAtLeast(0))
        return (chosen + topUp).map { it.word }.shuffled(random)
    }
}
