package app.deutschlern.domain.model

data class WordEntry(
    val id: Long = 0,
    val lemma: String,
    val article: String? = null,
    val pos: String,
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
    val timesTranslated: Int = 1,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val sessionId: Long,
    val status: WordStatus = WordStatus.NEW,
    val partnerLanguage: String = "ru"
) {
    val selectedMeaning: Meaning?
        get() = selectedMeaningIndex?.let { meanings.getOrNull(it) } ?: meanings.firstOrNull()

    fun toWordTranslation(): WordTranslation {
        return WordTranslation(
            lemma = lemma,
            article = article,
            pos = pos,
            plural = plural,
            genitive = genitive,
            verbForms = verbForms,
            government = government,
            comparative = comparative,
            superlative = superlative,
            meanings = meanings,
            synonyms = synonyms,
            collocations = collocations,
            selectedMeaningIndex = selectedMeaningIndex,
            partnerLanguage = partnerLanguage
        )
    }
}
