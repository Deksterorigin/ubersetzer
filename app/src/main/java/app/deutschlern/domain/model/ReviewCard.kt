package app.deutschlern.domain.model

import app.deutschlern.domain.sm2.Sm2State

data class ReviewCard(
    val word: WordEntry,
    val sm2State: Sm2State = Sm2State()
) {
    val displayMeaning: Meaning?
        get() = word.selectedMeaning

    val frontDeText: String
        get() {
            val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(
                rawLemma = word.lemma,
                rawPos = word.pos,
                rawArticle = word.article
            )
            return if (!sanitized.article.isNullOrBlank()) "${sanitized.article} ${sanitized.lemma}" else sanitized.lemma
        }

    val backDeText: String
        get() = frontDeText

    val frontRuText: String
        get() = displayMeaning?.translation.orEmpty()

    val backRuText: String
        get() = displayMeaning?.translation.orEmpty()

    val exampleDe: String
        get() = displayMeaning?.exampleDe.orEmpty()

    val exampleRu: String
        get() = displayMeaning?.exampleRu.orEmpty()
}

enum class FlashcardDirection {
    DE_RU,
    RU_DE
}
