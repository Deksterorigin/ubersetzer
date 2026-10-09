package app.deutschlern.data.local.mapper

import app.deutschlern.data.local.dto.GrammarJsonDto
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.data.remote.dto.CollocationDto
import app.deutschlern.data.remote.dto.MeaningDto
import app.deutschlern.data.remote.dto.VerbFormsDto
import app.deutschlern.domain.model.Collocation
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.Session
import app.deutschlern.domain.model.VerbForms
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.model.toDomain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WordEntryMapper {

    private val defaultJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun VerbForms.toDto(): VerbFormsDto {
        return VerbFormsDto(
            praesens_er = praesensEr,
            praeteritum = praeteritum,
            partizip2 = partizip2,
            auxiliary = auxiliary,
            separable = separable,
            prefix = prefix
        )
    }

    fun Meaning.toDto(): MeaningDto {
        return MeaningDto(
            translation = translation,
            note = note,
            register = register,
            example_de = exampleDe,
            example_ru = exampleRu,
            best_match = bestMatch
        )
    }

    fun Collocation.toDto(): CollocationDto {
        return CollocationDto(
            de = de,
            ru = ru
        )
    }

    fun entityToDomain(entity: WordEntryEntity, json: Json = defaultJson): WordEntry {
        val grammar: GrammarJsonDto? = entity.grammarJson?.let {
            runCatching { json.decodeFromString<GrammarJsonDto>(it) }.getOrNull()
        }

        val meanings: List<Meaning> = runCatching {
            json.decodeFromString<List<MeaningDto>>(entity.meaningsJson).map { it.toDomain() }
        }.getOrDefault(emptyList())

        val synonyms: List<String> = runCatching {
            json.decodeFromString<List<String>>(entity.synonymsJson)
        }.getOrDefault(emptyList())

        val collocations: List<Collocation> = runCatching {
            json.decodeFromString<List<CollocationDto>>(entity.collocationsJson).map { it.toDomain() }
        }.getOrDefault(emptyList())

        return WordEntry(
            id = entity.id,
            lemma = entity.lemma,
            article = entity.article,
            pos = entity.pos,
            plural = entity.plural,
            genitive = entity.genitive,
            verbForms = grammar?.verb_forms?.toDomain(),
            government = grammar?.government,
            comparative = grammar?.comparative,
            superlative = grammar?.superlative,
            meanings = meanings,
            synonyms = synonyms,
            collocations = collocations,
            selectedMeaningIndex = entity.selectedMeaningIndex,
            timesTranslated = entity.timesTranslated,
            firstSeenAt = entity.firstSeenAt,
            lastSeenAt = entity.lastSeenAt,
            sessionId = entity.sessionId,
            status = WordStatus.fromString(entity.status),
            partnerLanguage = entity.partnerLanguage
        )
    }

    fun wordTranslationToEntity(
        word: WordTranslation,
        sessionId: Long,
        timesTranslated: Int = 1,
        firstSeenAt: Long = System.currentTimeMillis(),
        lastSeenAt: Long = System.currentTimeMillis(),
        status: String = WordStatus.NEW.name,
        json: Json = defaultJson
    ): WordEntryEntity {
        val grammar = if (word.verbForms != null || word.government != null || word.comparative != null || word.superlative != null) {
            GrammarJsonDto(
                verb_forms = word.verbForms?.toDto(),
                government = word.government,
                comparative = word.comparative,
                superlative = word.superlative
            )
        } else {
            null
        }

        val grammarJson = grammar?.let { json.encodeToString(it) }
        val meaningsJson = json.encodeToString(word.meanings.map { it.toDto() })
        val synonymsJson = json.encodeToString(word.synonyms)
        val collocationsJson = json.encodeToString(word.collocations.map { it.toDto() })

        return WordEntryEntity(
            lemma = word.lemma,
            article = word.article,
            pos = word.pos,
            plural = word.plural,
            genitive = word.genitive,
            grammarJson = grammarJson,
            meaningsJson = meaningsJson,
            synonymsJson = synonymsJson,
            collocationsJson = collocationsJson,
            selectedMeaningIndex = word.selectedMeaningIndex,
            timesTranslated = timesTranslated,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            sessionId = sessionId,
            status = status,
            partnerLanguage = word.partnerLanguage
        )
    }

    fun SessionEntity.toDomain(): Session {
        return Session(
            id = id,
            title = title,
            date = date,
            isManual = isManual
        )
    }

    fun Session.toEntity(): SessionEntity {
        return SessionEntity(
            id = id,
            title = title,
            date = date,
            isManual = isManual
        )
    }
}
