package app.deutschlern.ui.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.domain.model.dialog.DialogSegment
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogRecordingScreen(
    onNavigateBack: () -> Unit,
    onRecordingSaved: (dialogId: Long) -> Unit,
    viewModel: DialogRecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.savedDialogId) {
        val id = uiState.savedDialogId
        if (id != null) {
            onRecordingSaved(id)
        }
    }

    LaunchedEffect(uiState.isDismissed) {
        if (uiState.isDismissed) {
            onNavigateBack()
        }
    }

    BackHandler {
        if (uiState.segments.isNotEmpty() || uiState.elapsedSeconds > 3) {
            showCancelDialog = true
        } else {
            viewModel.cancelRecording()
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(text = stringResource(R.string.dialog_cancel_confirm_title)) },
            text = { Text(text = stringResource(R.string.dialog_cancel_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelRecording()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = stringResource(R.string.dialog_cancel_confirm_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(text = stringResource(R.string.dialog_cancel_confirm_continue))
                }
            }
        )
    }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.segments.size, uiState.currentPartialText) {
        val count = uiState.segments.size + if (uiState.currentPartialText.isNotBlank()) 1 else 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.dialog_recording_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.segments.isNotEmpty() || uiState.elapsedSeconds > 3) {
                                showCancelDialog = true
                            } else {
                                viewModel.cancelRecording()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header timer and status
            HeaderTimerSection(
                elapsedSeconds = uiState.elapsedSeconds,
                isRecording = uiState.isRecording
            )

            // Speaker selector bar
            SpeakerSelectionBar(
                activeSpeaker = uiState.activeSpeaker,
                onSpeakerSelect = { viewModel.setSpeaker(it) },
                onToggleSpeaker = { viewModel.toggleSpeaker() }
            )

            // Live Transcript list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (uiState.segments.isEmpty() && uiState.currentPartialText.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isRecording) {
                                stringResource(R.string.dialog_recording_status_listening)
                            } else {
                                stringResource(R.string.dialog_recording_status_paused)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.segments, key = { it.id }) { segment ->
                            SegmentBubble(segment = segment)
                        }

                        if (uiState.currentPartialText.isNotBlank()) {
                            item(key = "provisional_partial") {
                                ProvisionalSegmentBubble(
                                    speaker = uiState.activeSpeaker,
                                    partialText = uiState.currentPartialText
                                )
                            }
                        }
                    }
                }
            }

            // Bottom control actions
            BottomControlsBar(
                isRecording = uiState.isRecording,
                isSaving = uiState.isSaving,
                onPauseToggle = {
                    if (uiState.isRecording) viewModel.pauseRecording() else viewModel.resumeRecording()
                },
                onSave = { viewModel.stopAndSave() },
                onCancel = {
                    if (uiState.segments.isNotEmpty() || uiState.elapsedSeconds > 3) {
                        showCancelDialog = true
                    } else {
                        viewModel.cancelRecording()
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderTimerSection(
    elapsedSeconds: Long,
    isRecording: Boolean
) {
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .alpha(alphaAnim)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = formattedTime,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SpeakerSelectionBar(
    activeSpeaker: String,
    onSpeakerSelect: (String) -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = activeSpeaker == "A",
                onClick = { onSpeakerSelect("A") },
                label = { Text(text = stringResource(R.string.dialog_speaker_a)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            FilterChip(
                selected = activeSpeaker == "B",
                onClick = { onSpeakerSelect("B") },
                label = { Text(text = stringResource(R.string.dialog_speaker_b)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        OutlinedButton(
            onClick = onToggleSpeaker,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.dialog_speaker_toggle),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SegmentBubble(
    segment: DialogSegment,
    modifier: Modifier = Modifier
) {
    val isSpeakerA = segment.speakerLabel == "A"
    val containerColor = if (isSpeakerA) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    }
    val speakerColor = if (isSpeakerA) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    val offsetSecs = segment.startOffsetMs / 1000
    val offsetMins = offsetSecs / 60
    val offsetSec = offsetSecs % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", offsetMins, offsetSec)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSpeakerA) stringResource(R.string.dialog_speaker_a) else stringResource(R.string.dialog_speaker_b),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = speakerColor
                )

                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ProvisionalSegmentBubble(
    speaker: String,
    partialText: String
) {
    val isSpeakerA = speaker == "A"
    val speakerColor = if (isSpeakerA) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (isSpeakerA) stringResource(R.string.dialog_speaker_a) else stringResource(R.string.dialog_speaker_b),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = speakerColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BottomControlsBar(
    isRecording: Boolean,
    isSaving: Boolean,
    onPauseToggle: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dialog_record_cancel),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            IconButton(
                onClick = onPauseToggle,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRecording) stringResource(R.string.dialog_record_pause) else stringResource(R.string.dialog_record_resume),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Button(
                onClick = onSave,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dialog_record_stop),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
