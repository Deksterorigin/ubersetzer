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
import androidx.compose.ui.text.withStyle
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        ),
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
                    val rawLemma = if (extractedArticle != null) baseLemma else word.lemma
                    val candidateArticle = word.article?.trim()?.ifBlank { null } ?: extractedArticle
                    val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(
                        rawLemma = rawLemma,
                        rawPos = word.pos,
                        rawArticle = candidateArticle
                    )
                    val article = sanitized.article
                    val cleanLemma = sanitized.lemma
                    val displayLemma = if (!article.isNullOrBlank()) "$article $cleanLemma" else cleanLemma

                    val annotatedLemma = app.deutschlern.ui.theme.buildArticleLemmaAnnotatedString(
                        article = article,
                        lemma = cleanLemma,
                        articleWeight = FontWeight.Normal,
                        lemmaWeight = FontWeight.Bold,
                        defaultColor = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = annotatedLemma,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp
                        ),
                        modifier = Modifier.weight(1f, fill = false)
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
            // Line 1: Translation (weighted) + Best match + Register label (non-neutral only) + Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${meaning.translation}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
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
                                fontWeight = FontWeight.Medium,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }

                    val registerResId = formatRegisterLabel(meaning.register)
                    if (registerResId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(registerResId),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
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

            // Line 2: Note (if present, on its own line, full width, secondary style, maxLines 3 with expand)
            val cleanNote = app.deutschlern.domain.sanitizer.MeaningDeduplicator.cleanNote(meaning.note)
            if (!cleanNote.isNullOrBlank()) {
                var isNoteExpanded by remember { mutableStateOf(false) }
                Text(
                    text = cleanNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isNoteExpanded) Int.MAX_VALUE else 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNoteExpanded = !isNoteExpanded }
                )
            }

            // Line 3+: German Example
            if (meaning.exampleDe.isNotBlank()) {
                Text(
                    text = meaning.exampleDe,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Line 4+: Partner Translation of Example
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
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        append(col.de)
                    }
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        append(" — ")
                        append(col.ru)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


private fun formatRegisterLabel(register: String?): Int? {
    if (register.isNullOrBlank()) return null
    val reg = register.trim().lowercase()
    return when {
        reg == "neutral" -> null
        reg == "umgangssprachlich" || reg == "colloquial" || reg == "разг" || reg == "разговорное" -> R.string.register_colloquial
        reg == "formell" || reg == "formal" || reg == "офиц" || reg == "официальное" -> R.string.register_formal
        reg == "veraltet" || reg == "dated" || reg == "устар" || reg == "устаревшее" -> R.string.register_dated
        else -> null
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

@androidx.compose.ui.tooling.preview.Preview(name = "Pathological 320dp FontScale 1.0", widthDp = 320, showBackground = true)
@Composable
fun WordCardPathologicalNarrowPreview() {
    MaterialTheme {
        WordCard(
            word = WordTranslation(
                lemma = "erwähnen",
                article = null,
                pos = "verb",
                meanings = listOf(
                    Meaning(
                        translation = "Очень длинный перевод слова на русском языке ровно шестьдесят знаков",
                        note = "Это экстремально длинное примечание для тестирования поведения переноса строк и ограничения высоты карточки, состоящее из множества слов для проверки того, что оно не сжимается в одну букву на строку",
                        exampleDe = "Er erwähnte dieses Thema nur kurz.",
                        exampleRu = "Он упомянул эту тему лишь вскользь."
                    )
                )
            ),
            direction = TranslationDirection.DE_RU
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Pathological 360dp FontScale 2.0", widthDp = 360, fontScale = 2.0f, showBackground = true)
@Composable
fun WordCardPathologicalFontScale2Preview() {
    MaterialTheme {
        WordCard(
            word = WordTranslation(
                lemma = "erwähnen",
                article = null,
                pos = "verb",
                meanings = listOf(
                    Meaning(
                        translation = "Очень длинный перевод слова на русском языке ровно шестьдесят знаков",
                        note = "Это экстремально длинное примечание для тестирования поведения переноса строк и ограничения высоты карточки, состоящее из множества слов для проверки того, что оно не сжимается в одну букву на строку",
                        exampleDe = "Er erwähnte dieses Thema nur kurz.",
                        exampleRu = "Он упомянул эту тему лишь вскользь."
                    )
                )
            ),
            direction = TranslationDirection.DE_RU
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "All Registers and 5 Meanings", widthDp = 360, showBackground = true)
@Composable
fun WordCardAllRegistersAndMeaningsPreview() {
    MaterialTheme {
        WordCard(
            word = WordTranslation(
                lemma = "erwähnen",
                article = null,
                pos = "verb",
                meanings = listOf(
                    Meaning(
                        translation = "упоминать",
                        note = null,
                        register = "neutral",
                        exampleDe = "Er erwähnte das Buch.",
                        exampleRu = "Он упомянул книгу."
                    ),
                    Meaning(
                        translation = "затрагивать",
                        note = "в разговоре",
                        register = "umgangssprachlich",
                        exampleDe = "Das hat er kurz erwähnt.",
                        exampleRu = "Он обмолвился об этом."
                    ),
                    Meaning(
                        translation = "ссылаться на",
                        note = "в протоколе",
                        register = "formell",
                        exampleDe = "Der Bericht erwähnt die Fakten.",
                        exampleRu = "В отчете упомянуты факты."
                    ),
                    Meaning(
                        translation = "поминать",
                        note = "в старых текстах",
                        register = "veraltet",
                        exampleDe = "Man erwähnte seiner mit Lob.",
                        exampleRu = "Его поминали с похвалой."
                    ),
                    Meaning(
                        translation = "называть по имени",
                        note = null,
                        register = null,
                        exampleDe = "Namen wurden nicht erwähnt.",
                        exampleRu = "Имена не назывались."
                    )
                )
            ),
            direction = TranslationDirection.DE_RU
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "RTL Dark Theme", widthDp = 360, showBackground = true)
@Composable
fun WordCardRtlDarkPreview() {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme()
        ) {
            WordCard(
                word = WordTranslation(
                    lemma = "Apfel",
                    article = "der",
                    pos = "noun",
                    meanings = listOf(
                        Meaning(
                            translation = "تفاحة (فاكهة لذيذة ومفيدة جداً للصحة)",
                            note = "فاكهة",
                            register = "neutral",
                            exampleDe = "Ich esse einen Apfel.",
                            exampleRu = "أنا آكل تفاحة."
                        )
                    )
                ),
                direction = TranslationDirection.DE_RU
            )
        }
    }
}
