package app.deutschlern.ui.words

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.deutschlern.R
import app.deutschlern.domain.model.WordTopic
import app.deutschlern.domain.sanitizer.PosNormalizer
import app.deutschlern.ui.components.formatPosLabel

@Composable
fun topicLabel(topic: WordTopic): String = stringResource(
    when (topic) {
        WordTopic.WORK -> R.string.topic_work
        WordTopic.BUREAUCRACY -> R.string.topic_bureaucracy
        WordTopic.FINANCE -> R.string.topic_finance
        WordTopic.EVERYDAY -> R.string.topic_everyday
        WordTopic.HOUSING -> R.string.topic_housing
        WordTopic.FOOD_SHOPPING -> R.string.topic_food_shopping
        WordTopic.HEALTH -> R.string.topic_health
        WordTopic.TRAVEL -> R.string.topic_travel
        WordTopic.EDUCATION -> R.string.topic_education
        WordTopic.FEELINGS -> R.string.topic_feelings
        WordTopic.NATURE -> R.string.topic_nature
        WordTopic.TECHNOLOGY -> R.string.topic_technology
    }
)

/** Plural form for filter chips ("Глаголы"), unlike the singular word-card label ("Глагол"). */
@Composable
fun posFilterLabel(pos: String): String = when (pos) {
    PosNormalizer.POS_NOUN -> stringResource(R.string.filter_pos_noun)
    PosNormalizer.POS_VERB -> stringResource(R.string.filter_pos_verb)
    PosNormalizer.POS_ADJECTIVE -> stringResource(R.string.filter_pos_adjective)
    PosNormalizer.POS_ADVERB -> stringResource(R.string.filter_pos_adverb)
    PosNormalizer.POS_PHRASE -> stringResource(R.string.filter_pos_phrase)
    else -> formatPosLabel(pos)
}
