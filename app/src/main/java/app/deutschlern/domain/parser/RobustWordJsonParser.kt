package app.deutschlern.domain.parser

import app.deutschlern.data.remote.dto.WordTranslationDto
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.model.toDomain
import app.deutschlern.domain.provider.ProviderError
import kotlinx.serialization.json.Json

object RobustWordJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val cyrillicRegex = Regex("[\\u0400-\\u04FF]")
    private val latinRegex = Regex("[a-zA-ZäöüÄÖÜß]")
    private val arabicRegex = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]")

    /**
     * Extracts and validates WordTranslationDto from model output.
     */
    fun parse(
        rawText: String,
        direction: TranslationDirection,
        providerName: String
    ): Result<WordTranslation> {
        val jsonString = extractJsonString(rawText)
            ?: return Result.failure(ProviderError.MalformedJson(providerName))

        val dto = try {
            json.decodeFromString<WordTranslationDto>(jsonString)
        } catch (e: Exception) {
            return Result.failure(ProviderError.MalformedJson(providerName))
        }

        val meanings = dto.meanings

        // Sanity Check 1: Non-empty lemma and valid meanings range
        if (dto.lemma.isBlank() || meanings.isNullOrEmpty() || meanings.size > 5) {
            return Result.failure(ProviderError.MalformedJson(providerName))
        }

        // Sanity Check 2: Each meaning has non-blank translation and examples
        for (meaning in meanings) {
            if (meaning.translation.isBlank() || meaning.example_de.isNullOrBlank() || meaning.example_ru.isNullOrBlank()) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
        }

        // Sanity Check 3: Script validation dynamically adapted to the partner language
        val partner = direction.partnerLanguage
        val partnerRegex = when (partner) {
            PartnerLanguage.RUSSIAN, PartnerLanguage.UKRAINIAN -> cyrillicRegex
            PartnerLanguage.ARABIC, PartnerLanguage.DARI -> arabicRegex
            PartnerLanguage.ENGLISH, PartnerLanguage.ROMANIAN -> latinRegex
        }

        if (direction.isFromGerman) {
            // German lemma must contain Latin
            if (!latinRegex.containsMatchIn(dto.lemma)) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
            // If partner is non-Latin, German lemma should not contain partner script
            if (partnerRegex !== latinRegex && partnerRegex.containsMatchIn(dto.lemma)) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
            // Partner translations and partner examples must contain partner script
            val hasPartnerScript = meanings.all {
                partnerRegex.containsMatchIn(it.translation) && partnerRegex.containsMatchIn(it.example_ru.orEmpty())
            }
            if (!hasPartnerScript) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
        } else {
            // Partner query to German: German example must contain Latin
            val hasLatinExample = meanings.all { latinRegex.containsMatchIn(it.example_de.orEmpty()) }
            if (!hasLatinExample) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
            // German translation or lemma must contain Latin
            val hasLatinGerman = meanings.all { latinRegex.containsMatchIn(it.translation) } || latinRegex.containsMatchIn(dto.lemma)
            if (!hasLatinGerman) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
            // Partner example must contain partner script
            val hasPartnerScript = meanings.all { partnerRegex.containsMatchIn(it.example_ru.orEmpty()) }
            if (!hasPartnerScript) {
                return Result.failure(ProviderError.MalformedJson(providerName))
            }
        }

        return Result.success(dto.toDomain())
    }

    /**
     * Strips markdown fences and extracts the outer JSON object boundaries.
     */
    fun extractJsonString(raw: String): String? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
            return null
        }

        return trimmed.substring(firstBrace, lastBrace + 1)
    }

    /**
     * Constructs a targeted repair instruction for a retry request.
     */
    fun createRepairPrompt(direction: TranslationDirection): String {
        val targetName = direction.targetDisplayName
        val sourceName = direction.sourceDisplayName
        return "Your previous output failed JSON validation. Please output ONLY raw valid JSON adhering strictly to the schema. " +
            if (direction.isFromGerman) {
                "Ensure lemma is Latin German script and translations are in ${targetName}. Include 1 to 5 meanings with example_de and example_ru."
            } else {
                "Ensure lemma is in German and example translations are in ${sourceName}. Include 1 to 5 meanings with example_de and example_ru."
            }
    }
}
