package app.deutschlern.util

import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import java.lang.Character.UnicodeBlock

object LanguageDetector {

    /**
     * Detects translation direction based on script and vocabulary analysis:
     * - Cyrillic script: RU_DE or UK_DE depending on partner language.
     * - Arabic script: AR_DE or FA_DE depending on partner language.
     * - Latin script: DE_{partner} or {partner}_DE.
     *
     * Calling without [partner] defaults to [PartnerLanguage.RUSSIAN] for 100% backward compatibility.
     */
    fun detectLanguage(
        text: CharSequence?,
        partner: PartnerLanguage = PartnerLanguage.RUSSIAN
    ): TranslationDirection {
        if (text.isNullOrBlank()) {
            return TranslationDirection.getDirection(partner, fromGerman = true)
        }

        var cyrillicCount = 0
        var latinCount = 0
        var arabicCount = 0
        var germanUmlautCount = 0
        var romanianDiacriticCount = 0
        var firstDetectedIsGerman: Boolean? = null

        for (i in 0 until text.length) {
            val ch = text[i]
            if (!ch.isLetter()) continue

            val block = UnicodeBlock.of(ch)
            when {
                block == UnicodeBlock.CYRILLIC ||
                block == UnicodeBlock.CYRILLIC_SUPPLEMENTARY ||
                block == UnicodeBlock.CYRILLIC_EXTENDED_A ||
                block == UnicodeBlock.CYRILLIC_EXTENDED_B -> {
                    cyrillicCount++
                    if (firstDetectedIsGerman == null) {
                        firstDetectedIsGerman = false
                    }
                }
                block == UnicodeBlock.ARABIC ||
                block == UnicodeBlock.ARABIC_SUPPLEMENT ||
                block == UnicodeBlock.ARABIC_EXTENDED_A -> {
                    arabicCount++
                    if (firstDetectedIsGerman == null) {
                        firstDetectedIsGerman = false
                    }
                }
                block == UnicodeBlock.BASIC_LATIN ||
                block == UnicodeBlock.LATIN_1_SUPPLEMENT ||
                block == UnicodeBlock.LATIN_EXTENDED_A ||
                block == UnicodeBlock.LATIN_EXTENDED_B ||
                block == UnicodeBlock.LATIN_EXTENDED_ADDITIONAL -> {
                    latinCount++
                    when (ch) {
                        'ä', 'ö', 'ü', 'Ä', 'Ö', 'Ü', 'ß', 'ẞ' -> germanUmlautCount++
                        'ă', 'â', 'î', 'ș', 'ț', 'Ă', 'Â', 'Î', 'Ș', 'Ț' -> romanianDiacriticCount++
                    }
                    if (firstDetectedIsGerman == null) {
                        firstDetectedIsGerman = true
                    }
                }
            }
        }

        // Script-based routing
        return when (partner) {
            PartnerLanguage.RUSSIAN, PartnerLanguage.UKRAINIAN -> {
                if (cyrillicCount > latinCount) {
                    TranslationDirection.getDirection(partner, fromGerman = false)
                } else if (latinCount > cyrillicCount) {
                    TranslationDirection.getDirection(partner, fromGerman = true)
                } else if (cyrillicCount > 0) {
                    TranslationDirection.getDirection(partner, fromGerman = firstDetectedIsGerman ?: false)
                } else {
                    TranslationDirection.getDirection(partner, fromGerman = true)
                }
            }
            PartnerLanguage.ARABIC, PartnerLanguage.DARI -> {
                if (arabicCount > latinCount) {
                    TranslationDirection.getDirection(partner, fromGerman = false)
                } else if (latinCount > arabicCount) {
                    TranslationDirection.getDirection(partner, fromGerman = true)
                } else if (arabicCount > 0) {
                    TranslationDirection.getDirection(partner, fromGerman = firstDetectedIsGerman ?: false)
                } else {
                    TranslationDirection.getDirection(partner, fromGerman = true)
                }
            }
            PartnerLanguage.ROMANIAN -> {
                if (romanianDiacriticCount > germanUmlautCount) {
                    TranslationDirection.RO_DE
                } else {
                    TranslationDirection.DE_RO
                }
            }
            PartnerLanguage.ENGLISH -> {
                if (germanUmlautCount > 0) {
                    TranslationDirection.DE_EN
                } else {
                    val words = text.split(Regex("\\s+")).map { it.lowercase().trim('.', ',', '!', '?', ';', ':') }
                    val englishStopWords = setOf("the", "is", "are", "and", "in", "to", "of", "it", "you", "that", "he", "was", "for", "on", "are", "as", "with", "his", "they", "at", "be", "this", "have", "from", "or", "one", "had", "by", "word", "but", "not", "what", "all", "were", "we", "when", "your", "can", "said")
                    val hasEnglishWords = words.any { it in englishStopWords }
                    if (hasEnglishWords) {
                        TranslationDirection.EN_DE
                    } else {
                        TranslationDirection.DE_EN
                    }
                }
            }
        }
    }
}
