package app.deutschlern.ui.translate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.deutschlern.R
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.ui.components.AppCard
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.ui.components.pressScale

@Composable
fun LanguageSelector(
    direction: TranslationDirection,
    selectedPartner: PartnerLanguage,
    onSwap: () -> Unit,
    onSelectPartner: (PartnerLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguagePill(
            name = direction.sourceDisplayName,
            isPartner = !direction.isFromGerman,
            selectedPartner = selectedPartner,
            slideTowards = SlideDirection.Start,
            onSelectPartner = onSelectPartner,
            modifier = Modifier.weight(1f)
        )
        SwapButton(onClick = onSwap)
        LanguagePill(
            name = direction.targetDisplayName,
            isPartner = direction.isFromGerman,
            selectedPartner = selectedPartner,
            slideTowards = SlideDirection.End,
            onSelectPartner = onSelectPartner,
            modifier = Modifier.weight(1f)
        )
    }
}

/** German is fixed, so only the pill currently holding the partner language opens the menu. */
@Composable
private fun LanguagePill(
    name: String,
    isPartner: Boolean,
    selectedPartner: PartnerLanguage,
    slideTowards: SlideDirection,
    onSelectPartner: (PartnerLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (isPartner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(240),
        label = "languagePillColor"
    )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clip(CircleShape)
                .then(
                    if (isPartner) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { menuExpanded = true }
                        )
                    } else {
                        Modifier
                    }
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = name,
                    transitionSpec = {
                        (slideIntoContainer(slideTowards, tween(240), initialOffset = { it / 2 }) + fadeIn(tween(240)))
                            .togetherWith(
                                slideOutOfContainer(slideTowards, tween(240), targetOffset = { it / 2 }) + fadeOut(tween(160))
                            )
                    },
                    modifier = Modifier.weight(1f, fill = false),
                    label = "languagePillName"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedVisibility(visible = isPartner) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.action_select_language),
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            PartnerLanguage.entries.forEach { language ->
                val isSelected = language == selectedPartner
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.displayName,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onSelectPartner(language)
                    }
                )
            }
        }
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    var halfTurns by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = halfTurns * 180f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "swapRotation"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = {
            halfTurns++
            onClick()
        },
        modifier = Modifier
            .size(48.dp)
            .pressScale(interactionSource, pressedScale = 0.92f),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.action_swap_direction),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
fun InputCard(
    state: TranslateUiState,
    onEvent: (TranslateUiEvent) -> Unit,
    onPaste: () -> Unit,
    onTranslate: () -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isListeningHere = state.isListening && !state.isListeningContext

    AppCard(
        modifier = modifier,
        borderColor = focusBorderColor(isFocused || isListeningHere),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(isWordMode = state.isWordMode)
            CharCounter(count = state.charCount, isOverLimit = state.isOverLimit)
        }

        PlainTextArea(
            value = state.inputText,
            onValueChange = { onEvent(TranslateUiEvent.OnInputTextChanged(it)) },
            placeholder = stringResource(R.string.translate_input_hint),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 24.sp,
                textAlign = if (state.isInputRtl) TextAlign.Right else TextAlign.Start,
                textDirection = if (state.isInputRtl) TextDirection.Rtl else TextDirection.Ltr
            ),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { onTranslate() },
                onDone = { onTranslate() },
                onSend = { onTranslate() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        PartialSpeechText(
            text = state.speechPartialText,
            visible = isListeningHere,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        AnimatedVisibility(visible = state.speechAlternatives.isNotEmpty()) {
            SpeechAlternatives(
                alternatives = state.speechAlternatives,
                onSelect = { onEvent(TranslateUiEvent.OnSelectSpeechAlternative(it)) }
            )
        }

        HairlineDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicButton(isListening = isListeningHere, onClick = onMicClicked)
            SpeechLanguageToggle(
                activeCode = state.activeSpeechLanguageCode,
                partnerCode = state.selectedPartnerLanguage.code,
                onSelect = { onEvent(TranslateUiEvent.OnSelectSpeechLanguage(it)) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Crossfade(
                targetState = state.inputText.isEmpty(),
                animationSpec = tween(150),
                label = "pasteOrClear"
            ) { isEmpty ->
                if (isEmpty) {
                    IconButton(onClick = onPaste, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.action_paste),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onEvent(TranslateUiEvent.OnClearInputClicked) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(isWordMode: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        AnimatedContent(
            targetState = isWordMode,
            transitionSpec = {
                (slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(200)))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(120)))
            },
            label = "translationModeChip"
        ) { wordMode ->
            Text(
                text = stringResource(if (wordMode) R.string.translate_mode_word else R.string.translate_mode_text),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun CharCounter(count: Int, isOverLimit: Boolean) {
    val color by animateColorAsState(
        targetValue = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "charCounterColor"
    )
    AnimatedVisibility(
        visible = count > 0,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150))
    ) {
        Text(
            text = stringResource(R.string.char_counter_format, count, MAX_INPUT_LENGTH),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun SpeechAlternatives(
    alternatives: List<String>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.stt_alternatives_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            alternatives.forEach { alternative ->
                AssistChip(
                    onClick = { onSelect(alternative) },
                    label = { Text(alternative, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
    }
}

@Composable
private fun SpeechLanguageToggle(
    activeCode: String?,
    partnerCode: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeechLanguageOption(code = "de", selected = activeCode == "de", onSelect = onSelect)
        SpeechLanguageOption(code = partnerCode, selected = activeCode == partnerCode, onSelect = onSelect)
    }
}

@Composable
private fun SpeechLanguageOption(
    code: String,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(200),
        label = "speechLanguageBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "speechLanguageText"
    )

    Box(
        modifier = Modifier
            .size(44.dp, 48.dp)
            .clip(ControlShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(code) }),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 36.dp)
                .height(28.dp)
                .background(background, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

@Composable
private fun MicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "micTint"
    )

    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (isListening) {
            PulsingRing(color = tint)
        }
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(
                    if (isListening) R.string.stt_stop_listening else R.string.stt_mic_button_desc
                ),
                tint = tint
            )
        }
    }
}

@Composable
private fun PulsingRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "micPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "micPulseProgress"
    )

    Canvas(modifier = Modifier.size(48.dp)) {
        val startRadius = 14.dp.toPx()
        val endRadius = size.minDimension / 2
        drawCircle(color = color.copy(alpha = 0.12f), radius = startRadius)
        drawCircle(
            color = color.copy(alpha = 0.5f * (1f - progress)),
            radius = startRadius + (endRadius - startRadius) * progress,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
fun ContextCard(
    contextText: String,
    isExpanded: Boolean,
    isListening: Boolean,
    partialText: String?,
    onContextChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(220),
        label = "contextChevron"
    )

    AppCard(
        modifier = modifier,
        borderColor = focusBorderColor(isFocused || isListening),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.context_field_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AnimatedVisibility(
                visible = contextText.isNotBlank() && !isExpanded,
                enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
        ) {
            Column {
                HairlineDivider()
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PlainTextArea(
                        value = contextText,
                        onValueChange = onContextChanged,
                        placeholder = stringResource(R.string.context_field_hint),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp)
                            .padding(vertical = 12.dp)
                    )
                    MicButton(isListening = isListening, onClick = onMicClicked)
                }
                PartialSpeechText(
                    text = partialText,
                    visible = isListening,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PartialSpeechText(
    text: String?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible && !text.isNullOrBlank()) {
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = modifier
        )
    }
}

@Composable
private fun focusBorderColor(isActive: Boolean): Color {
    val color by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "cardFocusBorder"
    )
    return color
}

/** The cards draw their own border, so the field itself stays bare instead of an OutlinedTextField. */
@Composable
private fun PlainTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = textStyle.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}
