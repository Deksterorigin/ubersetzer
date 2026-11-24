package app.deutschlern.domain.exam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Parts of the telc Deutsch-Test für den Beruf B2, in exam order. Each has its own asset file. */
@Serializable
enum class ExamPart(val fileName: String, val skill: ExamSkill) {
    LESEN_1("lesen_1.json", ExamSkill.LESEN),
    LESEN_2("lesen_2.json", ExamSkill.LESEN),
    LESEN_3("lesen_3.json", ExamSkill.LESEN),
    LESEN_4("lesen_4.json", ExamSkill.LESEN),
    BESCHWERDE("beschwerde.json", ExamSkill.SCHREIBEN),
    SPRACHBAUSTEINE_1("sprachbausteine_1.json", ExamSkill.SPRACHBAUSTEINE),
    SPRACHBAUSTEINE_2("sprachbausteine_2.json", ExamSkill.SPRACHBAUSTEINE),
    FORUM("forum.json", ExamSkill.SCHREIBEN),
    TELEFONNOTIZ("telefonnotiz.json", ExamSkill.HOEREN),
    HOEREN_1("hoeren_1.json", ExamSkill.HOEREN),
    HOEREN_2("hoeren_2.json", ExamSkill.HOEREN),
    HOEREN_3("hoeren_3.json", ExamSkill.HOEREN),
    HOEREN_4("hoeren_4.json", ExamSkill.HOEREN)
}

enum class ExamSkill { LESEN, HOEREN, SCHREIBEN, SPRACHBAUSTEINE, SPRECHEN }

@Serializable
data class ExamFile(
    val part: ExamPart,
    val variants: List<ExamVariant>
)

@Serializable
data class ExamVariant(
    val id: String,
    val part: ExamPart,
    val title: String,
    /** Source remarks such as "100%" or "новая версия от 08.2024", shown as a small caption. */
    val note: String? = null,
    val segments: List<ExamSegment> = emptyList(),
    val writing: WritingTask? = null,
    val phoneNote: PhoneNote? = null,
    val audioUrl: String? = null
) {
    val questions: List<Question> get() = segments.flatMap { it.questions }
}

/**
 * One block of an exam variant: texts or a dialogue plus the questions about them.
 * Gap-fill texts mark gaps inside [Passage.body] as {{46}}, matching [Question.number].
 */
@Serializable
data class ExamSegment(
    val title: String? = null,
    val instruction: String? = null,
    val passages: List<Passage> = emptyList(),
    val transcript: List<TranscriptLine> = emptyList(),
    /** Options shared by all questions of the segment (matching tasks, word banks). */
    val sharedOptions: List<Option> = emptyList(),
    val questions: List<Question> = emptyList()
)

@Serializable
data class Passage(
    val key: String? = null,
    val title: String? = null,
    val body: String
)

@Serializable
data class TranscriptLine(
    val speaker: String? = null,
    val gender: Gender? = null,
    val text: String
)

@Serializable
enum class Gender {
    @SerialName("m") MALE,
    @SerialName("f") FEMALE
}

@Serializable
data class Option(
    val key: String,
    val text: String
)

@Serializable
data class Question(
    val number: Int,
    val prompt: String = "",
    /** Own options; when empty the segment's shared options apply. */
    val options: List<Option> = emptyList(),
    /** Key of the correct option, or null when the source gives no answer. */
    val answer: String? = null,
    /** Sentence from the text that proves the answer. */
    val evidence: String? = null
)

@Serializable
enum class WritingKind { BESCHWERDE, FORUM }

@Serializable
data class WritingTask(
    val kind: WritingKind,
    val situation: String,
    val points: List<String> = emptyList(),
    val sampleAnswer: String? = null,
    val minWords: Int? = null,
    val timeMinutes: Int? = null
)

@Serializable
data class PhoneNote(
    val title: String? = null,
    val fields: List<NoteField>
)

@Serializable
data class NoteField(
    val label: String,
    val answer: String
)

@Serializable
data class SpeakingContent(
    val prompts: List<SpeakingPrompt>
)

@Serializable
data class SpeakingPrompt(
    val id: String,
    /** "1", "2" or "3": the Sprechen part the prompt belongs to. */
    val teil: String,
    val number: Int? = null,
    val title: String? = null,
    val de: String,
    val ru: String? = null,
    val tasks: List<String> = emptyList(),
    val sample: String? = null
)

@Serializable
data class GuideContent(
    val sections: List<GuideSection>
)

@Serializable
data class GuideSection(
    val id: String,
    val title: String,
    val category: GuideCategory,
    val body: String? = null,
    val items: List<GuideItem> = emptyList()
)

@Serializable
enum class GuideCategory { FORMAT, SCORING, LETTER, FORUM, PHONE_NOTE, SPEAKING, TIPS }

@Serializable
data class GuideItem(
    val de: String? = null,
    val ru: String? = null
)
