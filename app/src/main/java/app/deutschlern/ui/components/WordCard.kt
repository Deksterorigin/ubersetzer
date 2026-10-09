package app.deutschlern.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deutschlern.R
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.util.TextNormalizer

@Composable
fun WordCard(
    word: WordTranslation,
    direction: TranslationDirection,
    modifier: Modifier = Modifier,
    selectedMeaningIndex: Int? = word.selectedMeaningIndex,
    onMeaningClicked: (Int) -> Unit = {},
    onSaveEquivalent: (Int) -> Unit = {},
    onSpeak: (String) -> Unit = {}
) {
    var areSynonymsExpanded by remember { mutableStateOf(false) }
    var areCollocationsExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: [Lemma with article in bold] + [Speaker] + [POS Badge]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val (extractedArticle, baseLemma) = TextNormalizer.extractArticleAndLemma(word.lemma)
                    val article = word.article?.trim()?.ifBlank { null } ?: extractedArticle
                    val cleanLemma = if (extractedArticle != null) baseLemma else word.lemma.trim()

                    val displayLemma = if (!article.isNullOrBlank()) {
                        "$article $cleanLemma"
                    } else {
                        cleanLemma
                    }

                    Text(
                        text = displayLemma,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Speaker Button (Min 48dp touch target)
                    IconButton(
                        onClick = { onSpeak(displayLemma) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.action_speak),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // POS Badge
                if (word.pos.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatPosLabel(word.pos),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Small grey grammar line
            val grammarLine = buildGrammarLine(word)
            if (grammarLine.isNotBlank()) {
                Text(
                    text = grammarLine,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HairlineDivider()

            // List of Meanings
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                word.meanings.forEachIndexed { index, meaning ->
                    MeaningRow(
                        meaning = meaning,
                        index = index,
                        isSelected = (selectedMeaningIndex == index),
                        isRuDeMode = !direction.isFromGerman,
                        onMeaningClicked = { onMeaningClicked(index) },
                        onSaveEquivalent = { onSaveEquivalent(index) }
                    )
                }
            }

            // Expandable Synonyms Section
            if (word.synonyms.isNotEmpty()) {
                HairlineDivider()
                ExpandableHeader(
                    title = "${stringResource(R.string.section_synonyms)} (${word.synonyms.size})",
                    isExpanded = areSynonymsExpanded,
                    onToggle = { areSynonymsExpanded = !areSynonymsExpanded }
                )
                AnimatedVisibility(visible = areSynonymsExpanded) {
                    SynonymsContent(synonyms = word.synonyms)
                }
            }

            // Expandable Collocations Section
            if (word.collocations.isNotEmpty()) {
                HairlineDivider()
                ExpandableHeader(
                    title = "${stringResource(R.string.section_collocations)} (${word.collocations.size})",
                    isExpanded = areCollocationsExpanded,
                    onToggle = { areCollocationsExpanded = !areCollocationsExpanded }
                )
                AnimatedVisibility(visible = areCollocationsExpanded) {
                    CollocationsContent(collocations = word.collocations)
                }
            }
        }
    }
}

@Composable
private fun MeaningRow(
    meaning: Meaning,
    index: Int,
    isSelected: Boolean,
    isRuDeMode: Boolean,
    onMeaningClicked: () -> Unit,
    onSaveEquivalent: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        meaning.bestMatch -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        meaning.bestMatch -> MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .clickable(onClick = onMeaningClicked)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Translation + Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${meaning.translation}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (meaning.bestMatch) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.badge_best_match),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    meaning.register?.let { reg ->
                        Text(
                            text = reg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    meaning.note?.let { note ->
                        Text(
                            text = "($note)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action: In RU->DE show "Сохранить" button; show subtle check icon if selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRuDeMode) {
                        OutlinedButton(
                            onClick = onSaveEquivalent,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_save),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.badge_selected_meaning),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // German Example
            if (meaning.exampleDe.isNotBlank()) {
                Text(
                    text = meaning.exampleDe,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Russian Translation of Example
            if (meaning.exampleRu.isNotBlank()) {
                Text(
                    text = meaning.exampleRu,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpandableHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SynonymsContent(synonyms: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        synonyms.forEach { syn ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = syn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CollocationsContent(collocations: List<app.deutschlern.domain.model.Collocation>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        collocations.forEach { col ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = col.de,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " — ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = col.ru,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun formatPosLabel(pos: String): String {
    return when (pos.lowercase()) {
        "noun" -> stringResource(R.string.pos_noun)
        "verb" -> stringResource(R.string.pos_verb)
        "adjective" -> stringResource(R.string.pos_adjective)
        "adverb" -> stringResource(R.string.pos_adverb)
        "preposition" -> stringResource(R.string.pos_preposition)
        "conjunction" -> stringResource(R.string.pos_conjunction)
        "pronoun" -> stringResource(R.string.pos_pronoun)
        "particle" -> stringResource(R.string.pos_particle)
        "phrase" -> stringResource(R.string.pos_phrase)
        else -> pos
    }
}

private fun buildGrammarLine(word: WordTranslation): String {
    val parts = mutableListOf<String>()

    // Noun plural & genitive
    if (!word.plural.isNullOrBlank()) {
        parts.add("Pl.: ${word.plural}")
    }
    if (!word.genitive.isNullOrBlank()) {
        parts.add("Gen.: ${word.genitive}")
    }

    // Verb forms
    word.verbForms?.let { vf ->
        val forms = mutableListOf<String>()
        if (vf.praesensEr.isNotBlank()) forms.add(vf.praesensEr)
        if (vf.praeteritum.isNotBlank()) forms.add(vf.praeteritum)
        if (vf.partizip2.isNotBlank()) forms.add(vf.partizip2)
        val formsStr = forms.joinToString(", ")
        val auxStr = if (vf.auxiliary.isNotBlank()) " (${vf.auxiliary})" else ""
        val sepStr = if (vf.separable) " • trennbar" + (if (!vf.prefix.isNullOrBlank()) " [${vf.prefix}]" else "") else ""
        if (formsStr.isNotBlank()) {
            parts.add("$formsStr$auxStr$sepStr")
        }
    }

    // Adjective degrees
    if (!word.comparative.isNullOrBlank()) {
        parts.add("Komp.: ${word.comparative}")
    }
    if (!word.superlative.isNullOrBlank()) {
        parts.add("Sup.: ${word.superlative}")
    }

    // Government
    if (!word.government.isNullOrBlank()) {
        parts.add("Rektion: ${word.government}")
    }

    return parts.joinToString(" • ")
}
