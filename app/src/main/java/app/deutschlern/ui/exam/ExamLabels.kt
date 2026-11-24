package app.deutschlern.ui.exam

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.deutschlern.R
import app.deutschlern.domain.exam.ExamPart
import app.deutschlern.domain.exam.GuideCategory

/** Official German names of the exam parts; they stay German in every app language. */
fun ExamPart.germanName(): String = when (this) {
    ExamPart.LESEN_1 -> "Lesen Teil 1"
    ExamPart.LESEN_2 -> "Lesen Teil 2"
    ExamPart.LESEN_3 -> "Lesen Teil 3"
    ExamPart.LESEN_4 -> "Lesen Teil 4"
    ExamPart.BESCHWERDE -> "Schreiben: Beschwerde"
    ExamPart.SPRACHBAUSTEINE_1 -> "Sprachbausteine Teil 1"
    ExamPart.SPRACHBAUSTEINE_2 -> "Sprachbausteine Teil 2"
    ExamPart.FORUM -> "Schreiben: Forumsbeitrag"
    ExamPart.TELEFONNOTIZ -> "Hören: Telefonnotiz"
    ExamPart.HOEREN_1 -> "Hören Teil 1"
    ExamPart.HOEREN_2 -> "Hören Teil 2"
    ExamPart.HOEREN_3 -> "Hören Teil 3"
    ExamPart.HOEREN_4 -> "Hören Teil 4"
}

fun speakingName(teil: String): String = "Sprechen Teil $teil"

@Composable
fun ExamPart.description(): String = stringResource(
    when (this) {
        ExamPart.LESEN_1 -> R.string.exam_desc_lesen_1
        ExamPart.LESEN_2 -> R.string.exam_desc_lesen_2
        ExamPart.LESEN_3 -> R.string.exam_desc_lesen_3
        ExamPart.LESEN_4 -> R.string.exam_desc_lesen_4
        ExamPart.BESCHWERDE -> R.string.exam_desc_beschwerde
        ExamPart.SPRACHBAUSTEINE_1 -> R.string.exam_desc_sprachbausteine_1
        ExamPart.SPRACHBAUSTEINE_2 -> R.string.exam_desc_sprachbausteine_2
        ExamPart.FORUM -> R.string.exam_desc_forum
        ExamPart.TELEFONNOTIZ -> R.string.exam_desc_telefonnotiz
        ExamPart.HOEREN_1 -> R.string.exam_desc_hoeren_1
        ExamPart.HOEREN_2 -> R.string.exam_desc_hoeren_2
        ExamPart.HOEREN_3 -> R.string.exam_desc_hoeren_3
        ExamPart.HOEREN_4 -> R.string.exam_desc_hoeren_4
    }
)

@Composable
fun speakingDescription(teil: String): String = stringResource(
    when (teil) {
        "1" -> R.string.exam_desc_sprechen_1
        "2" -> R.string.exam_desc_sprechen_2
        "3" -> R.string.exam_desc_sprechen_3
        else -> R.string.exam_desc_sprechen_other
    }
)

@Composable
fun GuideCategory.label(): String = stringResource(
    when (this) {
        GuideCategory.FORMAT -> R.string.exam_guide_cat_format
        GuideCategory.SCORING -> R.string.exam_guide_cat_scoring
        GuideCategory.LETTER -> R.string.exam_guide_cat_letter
        GuideCategory.FORUM -> R.string.exam_guide_cat_forum
        GuideCategory.PHONE_NOTE -> R.string.exam_guide_cat_phone_note
        GuideCategory.SPEAKING -> R.string.exam_guide_cat_speaking
        GuideCategory.TIPS -> R.string.exam_guide_cat_tips
    }
)
