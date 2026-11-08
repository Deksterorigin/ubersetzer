package app.deutschlern.ui.check

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.deutschlern.R
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckComment
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.ui.components.AppCard
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.ui.components.PrimaryActionButton
import app.deutschlern.ui.components.appearOnce
import app.deutschlern.ui.components.pressScale

/** Marks an edit that inserts nothing or removes everything, which has no text of its own to show. */
private const val EMPTY_SIDE_MARK = "—"
private const val REJECTED_INSERTION_MARK = "‸"

@Composable
fun CheckResultSection(
    uiState: CheckUiState,
    onSelectMode: (CheckViewMode) -> Unit,
    onSelectCategory: (CheckCategory?) -> Unit,
    onHunkClick: (CheckEditHunk) -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNewCheck: () -> Unit
) {
    val result = uiState.result ?: return
    val hasEdits = uiState.totalEditsCount > 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (result.isOverEditWarning) {
            OverEditBanner()
        }

        if (hasEdits) {
            CheckSummaryCard(uiState, onSelectCategory, onAcceptAll, onRejectAll)
            ModeSelector(uiState.viewMode, onSelectMode)
        } else {
            NoErrorsCard()
        }

        CheckTextCard(uiState, result, onHunkClick)

        if (hasEdits && uiState.viewMode == CheckViewMode.CORRECTED) {
            Text(
                text = stringResource(R.string.check_hunk_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        uiState.filteredHunks.forEach { hunk ->
            EditRow(hunk = hunk, onClick = { onHunkClick(hunk) })
        }

        if (result.comments.isNotEmpty()) {
            Text(
                text = stringResource(R.string.check_comments_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            result.comments.forEach { CommentCard(it) }
        }

        ResultActions(onCopy, onShare, onNewCheck)

        HairlineDivider(modifier = Modifier.padding(top = 4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            uiState.providerModelAttribution?.takeIf { it.isNotBlank() }?.let { attribution ->
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.check_duden_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OverEditBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.check_overedit_warning),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckSummaryCard(
    uiState: CheckUiState,
    onSelectCategory: (CheckCategory?) -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit
) {
    AppCard(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.check_edits_count, uiState.totalEditsCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.check_accepted_count,
                        uiState.acceptedEditsCount,
                        uiState.totalEditsCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.allEditsAccepted) {
                TextButton(onClick = onRejectAll) {
                    Text(stringResource(R.string.check_action_reject_all))
                }
            } else {
                TextButton(onClick = onAcceptAll) {
                    Text(stringResource(R.string.check_action_accept_all))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryChip(
                label = stringResource(R.string.check_filter_all),
                count = uiState.totalEditsCount,
                selected = uiState.categoryFilter == null,
                onClick = { onSelectCategory(null) }
            )
            CheckCategory.entries.forEach { category ->
                val count = uiState.categoryCounts[category] ?: 0
                if (count > 0) {
                    CategoryChip(
                        label = categoryLabel(category),
                        count = count,
                        selected = uiState.categoryFilter == category,
                        onClick = { onSelectCategory(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label $count") },
        shape = ControlShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
internal fun categoryLabel(category: CheckCategory): String = when (category) {
    CheckCategory.SPELLING -> stringResource(R.string.check_category_spelling)
    CheckCategory.GRAMMAR -> stringResource(R.string.check_category_grammar)
    CheckCategory.PUNCTUATION -> stringResource(R.string.check_category_punctuation)
    CheckCategory.WORD_CHOICE -> stringResource(R.string.check_category_word_choice)
}

@Composable
private fun ModeSelector(
    mode: CheckViewMode,
    onSelectMode: (CheckViewMode) -> Unit
) {
    val modes = listOf(
        CheckViewMode.CORRECTED to stringResource(R.string.check_mode_corrected),
        CheckViewMode.ORIGINAL to stringResource(R.string.check_mode_original)
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(ControlShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / modes.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * modes.indexOfFirst { it.first == mode },
            animationSpec = tween(220),
            label = "checkModeIndicator"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface, ControlShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, ControlShape)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            modes.forEach { (itemMode, label) ->
                val selected = itemMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ControlShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelectMode(itemMode) }
                        )
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoErrorsCard() {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f))
    }
    AppCard(modifier = Modifier.appearOnce()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.check_empty_no_errors),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.check_empty_no_errors_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CheckTextCard(
    uiState: CheckUiState,
    result: TextCheckResult,
    onHunkClick: (CheckEditHunk) -> Unit
) {
    AppCard {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val showDiff = uiState.viewMode == CheckViewMode.CORRECTED && uiState.totalEditsCount > 0
            Crossfade(targetState = showDiff, animationSpec = tween(180), label = "checkTextMode") { diff ->
                if (diff) {
                    InlineDiffContent(
                        originalText = result.originalText,
                        hunks = result.hunks,
                        categoryFilter = uiState.categoryFilter,
                        onHunkClick = onHunkClick
                    )
                } else {
                    Text(
                        text = result.originalText,
                        style = MaterialTheme.typography.bodyLarge.germanText(),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineDiffContent(
    originalText: String,
    hunks: List<CheckEditHunk>,
    categoryFilter: CheckCategory?,
    onHunkClick: (CheckEditHunk) -> Unit
) {
    val colors = InlineDiffColors(
        accent = MaterialTheme.colorScheme.primary,
        accentTint = MaterialTheme.colorScheme.primaryContainer,
        text = MaterialTheme.colorScheme.onSurface,
        rejectedMark = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
    val currentOnHunkClick by rememberUpdatedState(onHunkClick)
    val annotated = remember(originalText, hunks, categoryFilter, colors) {
        buildInlineDiff(originalText, hunks, categoryFilter, colors) { currentOnHunkClick(it) }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.germanText(),
        color = colors.text,
        modifier = Modifier.fillMaxWidth()
    )
}

private data class InlineDiffColors(
    val accent: Color,
    val accentTint: Color,
    val text: Color,
    val rejectedMark: Color,
    val dimmed: Color
)

private fun buildInlineDiff(
    originalText: String,
    hunks: List<CheckEditHunk>,
    categoryFilter: CheckCategory?,
    colors: InlineDiffColors,
    onClick: (CheckEditHunk) -> Unit
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (hunk in hunks.sortedBy { it.startOffset }) {
        if (hunk.startOffset > cursor && hunk.startOffset <= originalText.length) {
            append(originalText.substring(cursor, hunk.startOffset))
        }
        val dimmed = categoryFilter != null && hunk.category != categoryFilter
        withLink(LinkAnnotation.Clickable(hunk.id) { onClick(hunk) }) {
            when {
                dimmed -> withStyle(SpanStyle(color = colors.dimmed)) {
                    append(
                        if (hunk.isAccepted) {
                            hunk.corrected.ifEmpty { hunk.original }
                        } else {
                            hunk.original.ifEmpty { REJECTED_INSERTION_MARK }
                        }
                    )
                }
                // An accepted deletion and a rejected insertion leave no text behind, yet each
                // must stay visible and tappable or the choice could never be changed again
                hunk.isAccepted && hunk.corrected.isEmpty() -> withStyle(
                    SpanStyle(
                        color = colors.accent,
                        background = colors.accentTint,
                        textDecoration = TextDecoration.LineThrough
                    )
                ) { append(hunk.original) }
                hunk.isAccepted -> withStyle(
                    SpanStyle(
                        color = colors.accent,
                        background = colors.accentTint,
                        fontWeight = FontWeight.SemiBold
                    )
                ) { append(hunk.corrected) }
                hunk.original.isEmpty() -> withStyle(
                    SpanStyle(color = colors.accent, background = colors.rejectedMark)
                ) { append(REJECTED_INSERTION_MARK) }
                else -> withStyle(SpanStyle(background = colors.rejectedMark)) {
                    append(hunk.original)
                }
            }
        }
        cursor = maxOf(cursor, hunk.endOffset)
    }
    if (cursor < originalText.length) {
        append(originalText.substring(cursor))
    }
}

/** "original → corrected" for a hunk, emphasising whichever side currently applies. */
@Composable
internal fun hunkDiffLine(hunk: CheckEditHunk): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(hunk.original, hunk.corrected, hunk.isAccepted, accent, text, muted) {
        buildAnnotatedString {
            val appliedStyle = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
            withStyle(
                if (hunk.isAccepted) {
                    SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough)
                } else {
                    SpanStyle(color = text, fontWeight = FontWeight.SemiBold)
                }
            ) {
                append(hunk.original.trim().ifEmpty { EMPTY_SIDE_MARK })
            }
            withStyle(SpanStyle(color = muted)) { append("  →  ") }
            withStyle(if (hunk.isAccepted) appliedStyle else SpanStyle(color = muted)) {
                append(hunk.corrected.trim().ifEmpty { EMPTY_SIDE_MARK })
            }
        }
    }
}

@Composable
private fun EditRow(
    hunk: CheckEditHunk,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = hunkDiffLine(hunk),
                        style = MaterialTheme.typography.bodyMedium.germanText(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = categoryLabel(hunk.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (hunk.isAccepted) Icons.Default.Check else Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(
                    if (hunk.isAccepted) R.string.check_hunk_accepted else R.string.check_hunk_rejected
                ),
                tint = if (hunk.isAccepted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CommentCard(comment: CheckComment) {
    AppCard(contentPadding = PaddingValues(14.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = comment.fragment,
                style = MaterialTheme.typography.bodyMedium.germanText(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = comment.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultActions(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNewCheck: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.weight(1f),
            shape = ControlShape
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy), maxLines = 1)
        }
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.weight(1f),
            shape = ControlShape
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_share), maxLines = 1)
        }
    }
    PrimaryActionButton(
        text = stringResource(R.string.check_action_new),
        onClick = onNewCheck
    )
}
