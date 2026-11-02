package app.deutschlern.domain.trainer

import app.deutschlern.domain.model.WordEntry

/** Trainer types offered on the review tab. Order = order on screen (most effective first). */
enum class TrainerMode {
    MIXED,
    RECALL,
    CLOZE,
    FORMS,
    ARTICLES,
    LISTENING,
    SPEAKING,
    CHOICE
}

enum class WordPool {
    SMART,
    DIFFICULT
}

enum class TypingKind {
    /** Russian meaning shown, German word typed (with article for nouns). */
    TRANSLATE_TO_GERMAN,
    /** Example sentence with a gap, the exact form from the sentence typed. */
    CLOZE,
    PLURAL,
    PRAETERITUM,
    /** "hat gemacht" / "ist gegangen": auxiliary plus Partizip II. */
    PERFEKT,
    /** Word is spoken by TTS and typed. */
    DICTATION
}

sealed interface Exercise {
    val word: WordEntry

    data class Article(
        override val word: WordEntry,
        val correctArticle: String
    ) : Exercise

    data class Choice(
        override val word: WordEntry,
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int
    ) : Exercise

    data class Typing(
        override val word: WordEntry,
        val kind: TypingKind,
        /** Text shown as the task (meaning, sentence with gap, base form). Hidden for dictation. */
        val prompt: String,
        /** Secondary line: translation of the sentence, part of speech, etc. */
        val hint: String?,
        val expected: String,
        /** Answers counted as "almost" (e.g. the lemma instead of the inflected form in a cloze). */
        val nearMisses: List<String> = emptyList(),
        /** Article that must precede a noun answer; null when no article is expected. */
        val requiredArticle: String? = null,
        /** Text to pronounce (dictation, or after answering). */
        val audioText: String? = null
    ) : Exercise

    data class Speaking(
        override val word: WordEntry,
        val prompt: String,
        val expected: String,
        val requiredArticle: String?
    ) : Exercise
}

enum class AnswerResult { CORRECT, ALMOST, WRONG }

enum class AnswerNote {
    NONE,
    /** ae/oe/ue/ss typed instead of ä/ö/ü/ß: accepted, correct spelling shown. */
    SPELLING_VARIANT,
    TYPO,
    MISSING_ARTICLE,
    WRONG_ARTICLE,
    MISSING_AUXILIARY,
    /** Base form typed where an inflected form was asked (cloze). */
    BASE_FORM
}

data class AnswerVerdict(
    val result: AnswerResult,
    val note: AnswerNote = AnswerNote.NONE
)
