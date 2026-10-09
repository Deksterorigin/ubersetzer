package app.deutschlern.ui.review

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.deutschlern.R
import app.deutschlern.domain.model.FlashcardDirection
import app.deutschlern.domain.sm2.Sm2Grade
import app.deutschlern.ui.components.HairlineDivider

@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Counter / Title + Controls
            ReviewHeader(
                uiState = uiState,
                onToggleDirection = { viewModel.onEvent(ReviewUiEvent.OnToggleDirection) },
                onToggleLastLesson = { viewModel.onEvent(ReviewUiEvent.OnToggleLastLessonMode(it)) }
            )

            HairlineDivider()

            // Main Content Area
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (uiState.isQueueCompleted) {
                // Empty / Completed queue state
                CompletedReviewView(
                    isLastLessonMode = uiState.isLastLessonMode,
                    reviewedCount = uiState.reviewedInSessionCount,
                    onRestart = { viewModel.onEvent(ReviewUiEvent.OnRestartQueue) }
                )
            } else {
                val currentCard = uiState.currentCard
                if (currentCard != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Flashcard
                        FlashcardComponent(
                            card = currentCard,
                            isFlipped = uiState.isFlipped,
                            direction = uiState.direction,
                            onFlip = { viewModel.onEvent(ReviewUiEvent.OnFlipCard) },
                            onSpeak = { viewModel.onEvent(ReviewUiEvent.OnSpeakWord(it)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Grade Buttons (visible when flipped)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = uiState.isFlipped,
                                label = "GradeButtonsCrossfade"
                            ) { flipped ->
                                if (flipped) {
                                    GradeButtonsRow(
                                        onGrade = { grade ->
                                            viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(grade))
                                        }
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.flashcard_tap_to_flip),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewHeader(
    uiState: ReviewUiState,
    onToggleDirection: () -> Unit,
    onToggleLastLesson: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val title = if (uiState.isLastLessonMode) {
                    stringResource(R.string.review_last_lesson_header)
                } else {
                    stringResource(R.string.review_due_today, uiState.totalDueTodayCount)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (!uiState.isQueueCompleted && uiState.cards.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.review_cards_remaining,
                            uiState.currentIndex + 1,
                            uiState.cards.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Direction Toggle button (DE ⇄ RU)
            OutlinedButton(
                onClick = onToggleDirection,
                modifier = Modifier.heightIn(min = 40.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.direction == FlashcardDirection.DE_RU) "DE → RU" else "RU → DE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Toggle "Слова последнего урока"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.isLastLessonMode,
                onClick = { onToggleLastLesson(!uiState.isLastLessonMode) },
                label = {
                    Text(
                        text = stringResource(R.string.review_option_last_lesson),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    1.dp,
                    if (uiState.isLastLessonMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun GradeButtonsRow(
    onGrade: (Sm2Grade) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // «Снова» (Again)
        OutlinedButton(
            onClick = { onGrade(Sm2Grade.AGAIN) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = stringResource(R.string.grade_again),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // «Трудно» (Hard)
        OutlinedButton(
            onClick = { onGrade(Sm2Grade.HARD) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE65100)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFE65100)
            )
        ) {
            Text(
                text = stringResource(R.string.grade_hard),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // «Хорошо» (Good)
        OutlinedButton(
            onClick = { onGrade(Sm2Grade.GOOD) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.grade_good),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // «Легко» (Easy)
        OutlinedButton(
            onClick = { onGrade(Sm2Grade.EASY) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF2E7D32)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF2E7D32)
            )
        ) {
            Text(
                text = stringResource(R.string.grade_easy),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CompletedReviewView(
    isLastLessonMode: Boolean,
    reviewedCount: Int,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color(0xFF2E7D32)
            )

            Text(
                text = stringResource(R.string.review_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.review_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            if (reviewedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_restart_review),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
