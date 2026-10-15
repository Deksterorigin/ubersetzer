package app.deutschlern.domain.model

import app.deutschlern.data.remote.dto.CollocationDto
import app.deutschlern.data.remote.dto.MeaningDto
import app.deutschlern.data.remote.dto.VerbFormsDto
import app.deutschlern.data.remote.dto.WordTranslationDto
import kotlinx.serialization.Serializable

@Serializable
data class WordTranslation(
    val lemma: String,
    val article: String? = null,
    val pos: String = "other",
    val plural: String? = null,
    val genitive: String? = null,
    val verbForms: VerbForms? = null,
    val government: String? = null,
    val comparative: String? = null,
    val superlative: String? = null,
    val meanings: List<Meaning> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val collocations: List<Collocation> = emptyList(),
    val selectedMeaningIndex: Int? = null,
    val partnerLanguage: String = "ru"
)

@Serializable
data class VerbForms(
    val praesensEr: String,
    val praeteritum: String,
    val partizip2: String,
    val auxiliary: String,
    val separable: Boolean,
    val prefix: String? = null
)

@Serializable
data class Meaning(
    val translation: String,
    val note: String? = null,
    val register: String? = null,
    val exampleDe: String = "",
    val exampleRu: String = "",
    val bestMatch: Boolean = false
)

@Serializable
data class Collocation(
    val de: String,
    val ru: String
)

fun WordTranslationDto.toDomain(partnerLanguage: String = "ru"): WordTranslation {
    val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(
        rawLemma = lemma,
        rawPos = pos,
        rawArticle = article
    )
    val rawMeanings = meanings.orEmpty().map { it.toDomain() }
    val deduplicatedMeanings = app.deutschlern.domain.sanitizer.MeaningDeduplicator.deduplicate(rawMeanings)

    return WordTranslation(
        lemma = sanitized.lemma,
        article = sanitized.article,
        pos = sanitized.pos,
        plural = plural,
        genitive = genitive,
        verbForms = verb_forms?.toDomain(),
        government = government,
        comparative = comparative,
        superlative = superlative,
        meanings = deduplicatedMeanings,
        synonyms = synonyms.orEmpty(),
        collocations = common_collocations.orEmpty().map { it.toDomain() },
        selectedMeaningIndex = null,
        partnerLanguage = partnerLanguage
    )
}

fun VerbFormsDto.toDomain(): VerbForms {
    return VerbForms(
        praesensEr = praesens_er.orEmpty(),
        praeteritum = praeteritum.orEmpty(),
        partizip2 = partizip2.orEmpty(),
        auxiliary = auxiliary.orEmpty(),
        separable = separable ?: false,
        prefix = prefix
    )
}

fun MeaningDto.toDomain(): Meaning {
    return Meaning(
        translation = translation,
        note = note,
        register = register,
        exampleDe = example_de,
        exampleRu = example_ru,
        bestMatch = best_match ?: false
    )
}

fun CollocationDto.toDomain(): Collocation {
    return Collocation(
        de = de,
        ru = ru
    )
}
