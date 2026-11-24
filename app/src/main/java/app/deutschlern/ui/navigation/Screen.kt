package app.deutschlern.ui.navigation

import app.deutschlern.domain.trainer.TrainerMode
import app.deutschlern.domain.trainer.WordPool
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import app.deutschlern.R

import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Spellcheck

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Translate : Screen(
        route = "translate",
        titleRes = R.string.tab_translate,
        icon = Icons.Outlined.Translate
    )

    data object Words : Screen(
        route = "words",
        titleRes = R.string.tab_words,
        icon = Icons.Outlined.CollectionsBookmark
    )

    data object Dialogues : Screen(
        route = "dialogues",
        titleRes = R.string.tab_dialogues,
        icon = Icons.Outlined.Forum
    )

    data object Check : Screen(
        route = "check",
        titleRes = R.string.tab_check,
        icon = Icons.Outlined.Spellcheck
    )

    data object Review : Screen(
        route = "review",
        titleRes = R.string.tab_review,
        icon = Icons.Outlined.School
    )

    data object Settings : Screen(
        route = "settings",
        titleRes = R.string.settings_title,
        icon = Icons.Filled.Settings
    )

    data object CheckHistory : Screen(
        route = "check_history",
        titleRes = R.string.check_history_title,
        icon = Icons.Outlined.History
    )

    data object DialogRecording : Screen(
        route = "dialog_recording?dialogId={dialogId}",
        titleRes = R.string.dialog_recording_title,
        icon = Icons.Outlined.Forum
    ) {
        fun createRoute(dialogId: Long = 0L): String = if (dialogId > 0L) "dialog_recording?dialogId=$dialogId" else "dialog_recording"
    }

    data object DialogDetail : Screen(
        route = "dialog_detail/{dialogId}",
        titleRes = R.string.dialog_detail_title,
        icon = Icons.Outlined.Forum
    ) {
        fun createRoute(dialogId: Long): String = "dialog_detail/$dialogId"
    }

    data object DialogTask : Screen(
        route = "dialog_task/{dialogId}/{taskId}",
        titleRes = R.string.dialog_task_title,
        icon = Icons.Outlined.School
    ) {
        fun createRoute(dialogId: Long, taskId: Long): String = "dialog_task/$dialogId/$taskId"
    }

    data object Trainer : Screen(
        route = "trainer/{mode}/{pool}",
        titleRes = R.string.review_tab_trainers,
        icon = Icons.Outlined.School
    ) {
        fun createRoute(mode: TrainerMode, pool: WordPool): String = "trainer/${mode.name}/${pool.name}"
    }

    data object ExamHub : Screen(
        route = "exam_hub",
        titleRes = R.string.exam_hub_title,
        icon = Icons.Outlined.School
    )

    data object ExamPart : Screen(
        route = "exam_part/{part}",
        titleRes = R.string.exam_hub_title,
        icon = Icons.Outlined.School
    ) {
        fun createRoute(part: app.deutschlern.domain.exam.ExamPart): String = "exam_part/${part.name}"
    }

    data object ExamVariant : Screen(
        route = "exam_variant/{variantId}",
        titleRes = R.string.exam_hub_title,
        icon = Icons.Outlined.School
    ) {
        fun createRoute(variantId: String): String = "exam_variant/${android.net.Uri.encode(variantId)}"
    }

    data object ExamSpeaking : Screen(
        route = "exam_speaking/{teil}",
        titleRes = R.string.exam_hub_title,
        icon = Icons.Outlined.School
    ) {
        fun createRoute(teil: String): String = "exam_speaking/${android.net.Uri.encode(teil)}"
    }

    data object ExamGuide : Screen(
        route = "exam_guide",
        titleRes = R.string.exam_guide_title,
        icon = Icons.Outlined.School
    )

    data object Onboarding : Screen(
        route = "onboarding",
        titleRes = R.string.app_name,
        icon = Icons.Outlined.Translate
    )

    companion object {
        // Lazy: built in Screen's static init, this list would hold null for a screen object initialized first
        val bottomNavItems by lazy { listOf(Translate, Words, Dialogues, Check, Review) }
    }
}
