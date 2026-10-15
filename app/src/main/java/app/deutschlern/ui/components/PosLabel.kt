package app.deutschlern.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.deutschlern.R
import app.deutschlern.domain.sanitizer.PosNormalizer

@Composable
fun formatPosLabel(pos: String): String {
    val normalized = PosNormalizer.normalize(pos)
    return when (normalized) {
        PosNormalizer.POS_NOUN -> stringResource(R.string.pos_noun)
        PosNormalizer.POS_VERB -> stringResource(R.string.pos_verb)
        PosNormalizer.POS_ADJECTIVE -> stringResource(R.string.pos_adjective)
        PosNormalizer.POS_ADVERB -> stringResource(R.string.pos_adverb)
        PosNormalizer.POS_PREPOSITION -> stringResource(R.string.pos_preposition)
        PosNormalizer.POS_CONJUNCTION -> stringResource(R.string.pos_conjunction)
        PosNormalizer.POS_PRONOUN -> stringResource(R.string.pos_pronoun)
        PosNormalizer.POS_PARTICLE -> stringResource(R.string.pos_particle)
        PosNormalizer.POS_PHRASE -> stringResource(R.string.pos_phrase)
        else -> pos
    }
}
