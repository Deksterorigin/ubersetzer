package app.deutschlern.ui.navigation

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
        route = "dialog_recording",
        titleRes = R.string.dialog_recording_title,
        icon = Icons.Outlined.Forum
    )

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

    companion object {
        val bottomNavItems = listOf(Translate, Words, Dialogues, Check, Review)
    }
}
