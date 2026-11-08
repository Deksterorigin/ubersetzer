package app.deutschlern.ui.check

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.deutschlern.R
import app.deutschlern.domain.ocr.OcrPaintStroke
import app.deutschlern.domain.ocr.OcrPoint
import app.deutschlern.domain.ocr.OcrSelection
import app.deutschlern.domain.ocr.OcrWord
import app.deutschlern.ui.components.ControlShape
import app.deutschlern.ui.components.HairlineDivider
import app.deutschlern.ui.components.PrimaryActionButton
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val DragSlop = 4.dp
private val TapSlop = 14.dp
private const val STROKE_REVERT_WINDOW_MS = 250L

/** Full-screen word picker over the photo. A dialog so that it also covers the bottom navigation. */
@Composable
fun OcrSelectionDialog(
    selection: OcrSelectionState,
    onEvent: (CheckUiEvent) -> Unit
) {
    Dialog(
        onDismissRequest = { onEvent(CheckUiEvent.OnOcrDiscardSelection) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        EdgeToEdgeDialogWindow()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            SelectionTopBar(selection, onEvent)
            key(selection.currentPageIndex) {
                OcrPageCanvas(
                    page = selection.currentPage,
                    showHint = selection.selectedWordCount == 0,
                    onSelectionChanged = { onEvent(CheckUiEvent.OnOcrSelectionChanged(it)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            SelectionBottomPanel(selection, onEvent)
        }
    }
}

@Composable
private fun EdgeToEdgeDialogWindow() {
    val view = LocalView.current
    val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        window.setDimAmount(0f)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}

@Composable
private fun SelectionTopBar(
    selection: OcrSelectionState,
    onEvent: (CheckUiEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onEvent(CheckUiEvent.OnOcrDiscardSelection) }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel)
            )
        }
        Text(
            text = stringResource(R.string.ocr_select_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }

    if (selection.pages.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selection.pages.forEachIndexed { index, page ->
                val count = page.selectedIds.size
                FilterChip(
                    selected = index == selection.currentPageIndex,
                    onClick = { onEvent(CheckUiEvent.OnOcrSelectPage(index)) },
                    label = {
                        val label = stringResource(R.string.ocr_page_tab, index + 1)
                        Text(if (count > 0) "$label · $count" else label, maxLines = 1)
                    },
                    shape = ControlShape
                )
            }
        }
    }

    if (selection.failedPageCount > 0) {
        Text(
            text = stringResource(R.string.ocr_pages_failed, selection.failedPageCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, ControlShape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }

    val page = selection.currentPage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = { onEvent(CheckUiEvent.OnOcrClearSelection) },
            enabled = page.selectedIds.isNotEmpty()
        ) {
            Text(stringResource(R.string.action_clear))
        }
        TextButton(
            onClick = { onEvent(CheckUiEvent.OnOcrSelectAll) },
            enabled = page.layout.words.isNotEmpty() && page.selectedIds.size < page.layout.words.size
        ) {
            Text(stringResource(R.string.ocr_select_all))
        }
    }
}

@Composable
private fun SelectionBottomPanel(
    selection: OcrSelectionState,
    onEvent: (CheckUiEvent) -> Unit
) {
    HairlineDivider()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val text = selection.selectedText
        val scrollState = rememberScrollState()
        LaunchedEffect(text) { scrollState.animateScrollTo(scrollState.maxValue) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 72.dp)
                .verticalScroll(scrollState)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.ocr_select_empty_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.germanText(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        PrimaryActionButton(
            text = stringResource(R.string.ocr_select_next, selection.selectedWordCount),
            onClick = { onEvent(CheckUiEvent.OnOcrConfirmSelection) },
            enabled = selection.selectedWordCount > 0
        )
    }
}

@Composable
private fun OcrPageCanvas(
    page: OcrSelectionPage,
    showHint: Boolean,
    onSelectionChanged: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = page.layout
    val image = remember(page.bitmap) { page.bitmap.asImageBitmap() }
    val markerPaths = remember(layout) { layout.words.map { markerPath(it.corners) } }
    var transform by remember(layout) { mutableStateOf(OcrViewTransform(layout.width, layout.height)) }

    val selectedIds by rememberUpdatedState(page.selectedIds)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val gestures = remember(layout) {
        OcrPageGestures(
            words = layout.words,
            dragSlopPx = with(density) { DragSlop.toPx() },
            tapSlopPx = with(density) { TapSlop.toPx() },
            readTransform = { transform },
            writeTransform = { transform = it },
            animateTransform = { target ->
                scope.launch {
                    val start = transform
                    animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                        transform = start.interpolatedTo(target, fraction)
                    }
                }
            },
            readSelection = { selectedIds },
            writeSelection = { currentOnSelectionChanged(it) },
            onWordChanged = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onBlockChanged = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        )
    }

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val markerColor = MaterialTheme.colorScheme.primary

    // Box coordinates are physical pixels, and an RTL interface must not mirror the photo
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.background(trackColor)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        transform = transform.resized(it.width.toFloat(), it.height.toFloat())
                    }
                    // The whole photo is a drawing surface: a stroke from the edge must not go back
                    .systemGestureExclusion()
                    .pointerInput(layout) { ocrSelectionGestures(gestures) }
            ) {
                val current = transform
                val outline = Stroke(width = 1.dp.toPx() / current.scale)
                withTransform({
                    translate(current.originX, current.originY)
                    scale(current.scale, current.scale, pivot = Offset.Zero)
                }) {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(image.width, image.height),
                        filterQuality = FilterQuality.Medium
                    )
                    layout.words.forEachIndexed { index, word ->
                        val path = markerPaths[index]
                        if (word.id in selectedIds) {
                            // Multiply keeps dark print dark under the marker, like ink on paper
                            drawPath(path, markerColor.copy(alpha = 0.45f), blendMode = BlendMode.Multiply)
                            drawPath(path, markerColor.copy(alpha = 0.7f), style = outline)
                        } else {
                            drawPath(path, markerColor.copy(alpha = 0.3f), style = outline)
                        }
                    }
                }
            }

            // Plain boxes without pointer input: a drag starting under them still reaches the photo
            AnimatedVisibility(
                visible = showHint && layout.words.isNotEmpty(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                OverlayMessage(stringResource(R.string.ocr_select_hint))
            }
            if (layout.words.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    OverlayMessage(stringResource(R.string.ocr_page_no_text))
                }
            }
        }
    }
}

@Composable
private fun OverlayMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, ControlShape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/** A word's quad with softened corners, so that the highlight reads as a marker stroke. */
private fun markerPath(corners: List<OcrPoint>): Path {
    val path = Path()
    val count = corners.size
    fun pointTowards(from: OcrPoint, to: OcrPoint, distance: Float): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = hypot(dx, dy).coerceAtLeast(1f)
        return Offset(from.x + dx / length * distance, from.y + dy / length * distance)
    }
    val shortestEdge = corners.indices.minOf { i ->
        val next = corners[(i + 1) % count]
        hypot(next.x - corners[i].x, next.y - corners[i].y)
    }
    val radius = shortestEdge * 0.2f
    corners.forEachIndexed { index, corner ->
        val previous = corners[(index + count - 1) % count]
        val next = corners[(index + 1) % count]
        val entry = pointTowards(corner, previous, radius)
        val exit = pointTowards(corner, next, radius)
        if (index == 0) path.moveTo(entry.x, entry.y) else path.lineTo(entry.x, entry.y)
        path.quadraticTo(corner.x, corner.y, exit.x, exit.y)
    }
    path.close()
    return path
}

private class OcrPageGestures(
    private val words: List<OcrWord>,
    private val dragSlopPx: Float,
    private val tapSlopPx: Float,
    private val readTransform: () -> OcrViewTransform,
    private val writeTransform: (OcrViewTransform) -> Unit,
    private val animateTransform: (OcrViewTransform) -> Unit,
    private val readSelection: () -> Set<Int>,
    private val writeSelection: (Set<Int>) -> Unit,
    private val onWordChanged: () -> Unit,
    private val onBlockChanged: () -> Unit
) {
    private class LastTap(val position: Offset, val timeMs: Long, val selectionBefore: Set<Int>)

    private var stroke: OcrPaintStroke? = null
    private var selectionBeforeStroke: Set<Int> = emptySet()
    private var strokeStartMs = 0L
    private var lastTap: LastTap? = null

    fun startStroke(timeMs: Long) {
        selectionBeforeStroke = readSelection()
        stroke = OcrPaintStroke(selectionBeforeStroke)
        strokeStartMs = timeMs
    }

    fun continueStroke(from: Offset, to: Offset) {
        val activeStroke = stroke ?: return
        val transform = readTransform()
        val touched = OcrSelection.wordsAlongSegment(
            words = words,
            from = transform.toImage(from.x, from.y),
            to = transform.toImage(to.x, to.y),
            slop = dragSlopPx / transform.scale
        )
        var changed = false
        touched.forEach { if (activeStroke.touch(it.id)) changed = true }
        if (changed) {
            writeSelection(activeStroke.selection)
            onWordChanged()
        }
    }

    fun endStroke() {
        stroke = null
    }

    /** The two fingers of a pinch rarely land together; words the first one brushed were accidental. */
    fun interruptStroke(timeMs: Long) {
        if (stroke != null && timeMs - strokeStartMs < STROKE_REVERT_WINDOW_MS) {
            writeSelection(selectionBeforeStroke)
        }
        stroke = null
    }

    fun tap(position: Offset, timeMs: Long, doubleTapTimeoutMs: Long, doubleTapSlopPx: Float) {
        val previous = lastTap
        if (previous != null &&
            timeMs - previous.timeMs <= doubleTapTimeoutMs &&
            (position - previous.position).getDistance() <= doubleTapSlopPx
        ) {
            lastTap = null
            writeSelection(previous.selectionBefore)
            toggleZoom(position)
            return
        }

        val selectionBefore = readSelection()
        wordAt(position, tapSlopPx)?.let { word ->
            writeSelection(OcrSelection.toggleWord(selectionBefore, word.id))
            onWordChanged()
        }
        lastTap = LastTap(position, timeMs, selectionBefore)
    }

    fun longPress(position: Offset) {
        val word = wordAt(position, tapSlopPx) ?: return
        writeSelection(OcrSelection.toggleBlock(words, readSelection(), word.blockIndex))
        onBlockChanged()
    }

    fun transform(centroid: Offset, zoomChange: Float, pan: Offset) {
        writeTransform(readTransform().transformed(centroid.x, centroid.y, zoomChange, pan.x, pan.y))
    }

    private fun toggleZoom(position: Offset) {
        val current = readTransform()
        val target = if (current.zoom > OcrViewTransform.MIN_ZOOM) {
            current.reset()
        } else {
            current.transformed(position.x, position.y, OcrViewTransform.DOUBLE_TAP_ZOOM)
        }
        animateTransform(target)
    }

    private fun wordAt(position: Offset, slopPx: Float): OcrWord? {
        val transform = readTransform()
        val point = transform.toImage(position.x, position.y)
        return OcrSelection.wordAt(words, point.x, point.y, slopPx / transform.scale)
    }
}

private enum class GesturePhase { PENDING, PAINTING, TRANSFORMING, LONG_PRESSED }

/**
 * One finger always selects and two always zoom, so nothing here is delegated to a scrollable
 * parent. A touch stays pending until it moves or a second finger lands, which is what keeps the
 * start of a pinch from painting a word.
 */
private suspend fun PointerInputScope.ocrSelectionGestures(gestures: OcrPageGestures) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
    val doubleTapSlop = TapSlop.toPx() * 2

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var phase = GesturePhase.PENDING
        var lastPosition = down.position
        var lastTimeMs = down.uptimeMillis

        while (true) {
            val event = if (phase == GesturePhase.PENDING) {
                val remaining = down.uptimeMillis + longPressMs - lastTimeMs
                val next = withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
                if (next == null) {
                    phase = GesturePhase.LONG_PRESSED
                    gestures.longPress(down.position)
                    continue
                }
                next
            } else {
                awaitPointerEvent()
            }

            val pressed = event.changes.filter { it.pressed }
            val timeMs = event.changes.first().uptimeMillis
            if (pressed.isEmpty()) {
                when (phase) {
                    GesturePhase.PENDING -> gestures.tap(down.position, timeMs, doubleTapMs, doubleTapSlop)
                    GesturePhase.PAINTING -> gestures.endStroke()
                    else -> Unit
                }
                event.changes.forEach { it.consume() }
                break
            }

            if (pressed.size > 1 && phase != GesturePhase.TRANSFORMING) {
                if (phase == GesturePhase.PAINTING) gestures.interruptStroke(timeMs)
                phase = GesturePhase.TRANSFORMING
            }

            val finger = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
            when (phase) {
                GesturePhase.PENDING -> {
                    lastTimeMs = timeMs
                    if ((finger.position - down.position).getDistance() > touchSlop) {
                        phase = GesturePhase.PAINTING
                        gestures.startStroke(timeMs)
                        gestures.continueStroke(down.position, finger.position)
                        lastPosition = finger.position
                    }
                }
                GesturePhase.PAINTING -> {
                    gestures.continueStroke(lastPosition, finger.position)
                    lastPosition = finger.position
                }
                GesturePhase.TRANSFORMING -> if (pressed.size > 1) {
                    gestures.transform(event.calculateCentroid(), event.calculateZoom(), event.calculatePan())
                }
                GesturePhase.LONG_PRESSED -> Unit
            }
            event.changes.forEach { it.consume() }
        }
    }
}
