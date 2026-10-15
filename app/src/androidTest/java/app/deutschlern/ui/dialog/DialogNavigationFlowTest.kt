package app.deutschlern.ui.dialog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.deutschlern.MainActivity
import app.deutschlern.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogNavigationFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAllDialogNavigationPaths_noCrashes() {
        val activity = composeTestRule.activity

        // Step 1: Open Dialogues Tab
        val dialoguesTabString = activity.getString(R.string.tab_dialogues)
        composeTestRule.onNodeWithText(dialoguesTabString).performClick()
        composeTestRule.waitForIdle()

        // Step 2: Path 1 - List to Detail
        // "Café Gespräch" was inserted into the database
        composeTestRule.onNodeWithText("Café Gespräch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Café Gespräch").performClick()
        composeTestRule.waitForIdle()

        // Confirm detail screen opened with segments
        composeTestRule.onNodeWithText("Guten Tag, ich möchte einen Kaffee bestellen.").assertIsDisplayed()

        // Step 3: Path 4 - Process death / configuration change / recreate on Detail screen
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Guten Tag, ich möchte einen Kaffee bestellen.").assertIsDisplayed()

        // Step 4: Path 3 - Detail to Tasks screen
        val tasksTabString = activity.getString(R.string.dialog_tab_tasks)
        composeTestRule.onNodeWithText(tasksTabString).performClick()
        composeTestRule.waitForIdle()

        val openTaskString = activity.getString(R.string.dialog_task_open_action)
        composeTestRule.onNodeWithText(openTaskString).assertIsDisplayed()
        composeTestRule.onNodeWithText(openTaskString).performClick()
        composeTestRule.waitForIdle()

        // Confirm task screen loaded successfully
        composeTestRule.onNodeWithText("Was möchte der Kunde?").assertIsDisplayed()

        // Step 5: Navigate back from Task to Detail, then from Detail to List
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Step 6: Path 2 - Start recording and Stop & Save to open Detail screen
        val newRecordingString = activity.getString(R.string.dialog_new_recording)
        composeTestRule.onNodeWithText(newRecordingString).performClick()
        composeTestRule.waitForIdle()

        val stopRecordString = activity.getString(R.string.dialog_record_stop)
        composeTestRule.onNodeWithText(stopRecordString).performClick()
        composeTestRule.waitForIdle()

        // Confirm detail screen opened for the saved recording
        val transcriptTabString = activity.getString(R.string.dialog_tab_transcript)
        composeTestRule.onNodeWithText(transcriptTabString).assertIsDisplayed()
    }
}
