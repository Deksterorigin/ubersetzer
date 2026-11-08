package app.deutschlern.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.domain.model.AppLanguage
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.ui.components.AppCard
import app.deutschlern.ui.components.PrimaryActionButton
import app.deutschlern.ui.components.appearOnce
import kotlinx.coroutines.launch

private const val PAGE_WELCOME = 0
private const val PAGE_FEATURES = 1
private const val PAGE_SETUP = 2
private const val PAGE_KEY = 3
private const val PAGE_COUNT = 4

@Composable
fun OnboardingScreen(
    onFinished: (OnboardingExit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.exit) {
        uiState.exit?.let(onFinished)
    }

    BackHandler(enabled = pagerState.currentPage > PAGE_WELCOME) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .displayCutoutPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.finish(OnboardingExit.TRANSLATE) }) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                PAGE_WELCOME -> WelcomePage()
                PAGE_FEATURES -> FeaturesPage()
                PAGE_SETUP -> SetupPage(
                    appLanguage = uiState.appLanguage,
                    partnerLanguage = uiState.partnerLanguage,
                    onSelectAppLanguage = viewModel::onSelectAppLanguage,
                    onSelectPartnerLanguage = viewModel::onSelectPartnerLanguage
                )
                else -> KeyPage()
            }
        }

        BottomActions(
            pagerState = pagerState,
            onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onAddKey = { viewModel.finish(OnboardingExit.SETTINGS) },
            onLater = { viewModel.finish(OnboardingExit.TRANSLATE) }
        )
    }
}

@Composable
private fun BottomActions(
    pagerState: PagerState,
    onNext: () -> Unit,
    onAddKey: () -> Unit,
    onLater: () -> Unit
) {
    val page = pagerState.currentPage
    val isLastPage = page == PAGE_KEY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageDots(currentPage = page, pageCount = PAGE_COUNT)

        PrimaryActionButton(
            text = stringResource(
                when (page) {
                    PAGE_WELCOME -> R.string.onboarding_start
                    PAGE_KEY -> R.string.onboarding_add_key
                    else -> R.string.onboarding_next
                }
            ),
            onClick = if (isLastPage) onAddKey else onNext
        )

        AnimatedVisibility(
            visible = isLastPage,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.update_action_later))
            }
        }
    }
}

@Composable
private fun PageDots(currentPage: Int, pageCount: Int) {
    val description = stringResource(R.string.onboarding_step_of, currentPage + 1, pageCount)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(pageCount) { index ->
            val isCurrent = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isCurrent) 20.dp else 8.dp,
                animationSpec = tween(250),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                animationSpec = tween(250),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

/** Fills the pager page so short content can be centred, and scrolls when big fonts make it overflow. */
@Composable
private fun OnboardingPage(
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(
                space = 16.dp,
                alignment = if (centered) Alignment.CenterVertically else Alignment.Top
            ),
            content = content
        )
    }
}

@Composable
private fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
private fun WelcomePage() {
    OnboardingPage(centered = true) {
        AppMark()
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .appearOnce(order = 3)
                .semantics { heading() }
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_promise),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.appearOnce(order = 5)
        )
    }
}

/** Two opposed arrows on a soft tile: the tile settles in, then the arrows draw themselves. */
@Composable
private fun AppMark() {
    val hasPlayed = rememberSaveable { mutableStateOf(false) }
    val tile = remember { Animatable(if (hasPlayed.value) 1f else 0f) }
    val arrows = remember { Animatable(if (hasPlayed.value) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasPlayed.value) {
            tile.animateTo(1f, tween(300))
            arrows.animateTo(1f, tween(350))
            hasPlayed.value = true
        }
    }

    val tileColor = MaterialTheme.colorScheme.primaryContainer
    val arrowColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                alpha = tile.value
                val scale = 0.92f + 0.08f * tile.value
                scaleX = scale
                scaleY = scale
            }
    ) {
        drawRoundRect(color = tileColor, cornerRadius = CornerRadius(24.dp.toPx()))
        val left = size.width * 0.28f
        val right = size.width * 0.72f
        drawArrow(arrowColor, y = size.height * 0.4f, fromX = left, toX = right, progress = arrows.value)
        drawArrow(arrowColor, y = size.height * 0.6f, fromX = right, toX = left, progress = arrows.value)
    }
}

private fun DrawScope.drawArrow(
    color: Color,
    y: Float,
    fromX: Float,
    toX: Float,
    progress: Float
) {
    if (progress <= 0f) return
    val strokeWidth = 3.dp.toPx()
    val tipX = fromX + (toX - fromX) * progress
    val headSize = 7.dp.toPx() * progress
    val headX = if (toX > fromX) tipX - headSize else tipX + headSize
    val tip = Offset(tipX, y)
    drawLine(color, Offset(fromX, y), tip, strokeWidth, StrokeCap.Round)
    drawLine(color, tip, Offset(headX, y - headSize), strokeWidth, StrokeCap.Round)
    drawLine(color, tip, Offset(headX, y + headSize), strokeWidth, StrokeCap.Round)
}

@Composable
private fun FeaturesPage() {
    OnboardingPage {
        PageTitle(stringResource(R.string.onboarding_features_title))
        FeatureRow(
            icon = Icons.Outlined.Translate,
            title = stringResource(R.string.onboarding_feature_translate_title),
            description = stringResource(R.string.onboarding_feature_translate_desc),
            modifier = Modifier.appearOnce(order = 0)
        )
        FeatureRow(
            icon = Icons.Outlined.CollectionsBookmark,
            title = stringResource(R.string.onboarding_feature_words_title),
            description = stringResource(R.string.onboarding_feature_words_desc),
            modifier = Modifier.appearOnce(order = 1)
        )
        FeatureRow(
            icon = Icons.Outlined.Forum,
            title = stringResource(R.string.onboarding_feature_dialogues_title),
            description = stringResource(R.string.onboarding_feature_dialogues_desc),
            modifier = Modifier.appearOnce(order = 2)
        )
        FeatureRow(
            icon = Icons.Outlined.Spellcheck,
            title = stringResource(R.string.onboarding_feature_check_title),
            description = stringResource(R.string.onboarding_feature_check_desc),
            modifier = Modifier.appearOnce(order = 3)
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SetupPage(
    appLanguage: AppLanguage,
    partnerLanguage: PartnerLanguage,
    onSelectAppLanguage: (AppLanguage) -> Unit,
    onSelectPartnerLanguage: (PartnerLanguage) -> Unit
) {
    OnboardingPage {
        PageTitle(stringResource(R.string.onboarding_setup_title))

        LanguageGroup(
            title = stringResource(R.string.settings_app_language_title),
            options = AppLanguage.entries,
            selected = appLanguage,
            label = { language ->
                if (language == AppLanguage.SYSTEM) stringResource(R.string.lang_system) else language.nativeName
            },
            onSelect = onSelectAppLanguage,
            modifier = Modifier.appearOnce(order = 0)
        )

        LanguageGroup(
            title = stringResource(R.string.onboarding_partner_language_title),
            hint = stringResource(R.string.onboarding_partner_language_hint),
            options = PartnerLanguage.entries,
            selected = partnerLanguage,
            label = { language -> language.displayName },
            onSelect = onSelectPartnerLanguage,
            modifier = Modifier.appearOnce(order = 1)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> LanguageGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun KeyPage() {
    OnboardingPage {
        PageTitle(stringResource(R.string.onboarding_key_title))
        KeyFact(
            icon = Icons.Outlined.Key,
            text = stringResource(R.string.onboarding_key_need),
            modifier = Modifier.appearOnce(order = 0)
        )
        KeyFact(
            icon = Icons.Outlined.PrivacyTip,
            text = stringResource(R.string.onboarding_key_privacy),
            modifier = Modifier.appearOnce(order = 1)
        )
        KeyFact(
            icon = Icons.Outlined.CloudOff,
            text = stringResource(R.string.onboarding_key_offline),
            modifier = Modifier.appearOnce(order = 2)
        )
        Text(
            text = stringResource(R.string.settings_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.appearOnce(order = 3)
        )
    }
}

@Composable
private fun KeyFact(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
    }
}
