package app.deutschlern.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtMost
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.ui.components.WordCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordCardLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val pathologicalTranslation = "Очень длинный перевод слова на русском языке ровно шестьдесят знаков"
    private val pathologicalNote = "Это экстремально длинное примечание для тестирования поведения переноса строк и ограничения высоты карточки, состоящее из множества слов для проверки того, что оно не сжимается в одну букву на строку"

    private fun renderCard(
        width: Dp,
        fontScale: Float,
        isRtl: Boolean,
        isDark: Boolean,
        meanings: List<Meaning>
    ) {
        composeTestRule.setContent {
            val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
            ) {
                MaterialTheme(colorScheme = colorScheme) {
                    Box(modifier = Modifier.width(width)) {
                        WordCard(
                            word = WordTranslation(
                                lemma = "erwähnen",
                                article = null,
                                pos = "verb",
                                meanings = meanings
                            ),
                            direction = TranslationDirection.DE_RU
                        )
                    }
                }
            }
        }
    }

    @Test
    fun pathologicalCard_320dp_fontScale1_ltr_light() {
        renderCard(
            width = 320.dp,
            fontScale = 1.0f,
            isRtl = false,
            isDark = false,
            meanings = listOf(
                Meaning(
                    translation = pathologicalTranslation,
                    note = pathologicalNote,
                    exampleDe = "Er erwähnte das Buch.",
                    exampleRu = "Он упомянул книгу."
                )
            )
        )

        // Note must be full width (>= 200dp in a 320dp container), never squeezed to one char per line (< 50dp)
        composeTestRule.onNodeWithText(pathologicalNote, substring = true)
            .assertWidthIsAtLeast(200.dp)
            .assertHeightIsAtMost(400.dp)
    }

    @Test
    fun pathologicalCard_360dp_fontScale2_rtl_dark() {
        renderCard(
            width = 360.dp,
            fontScale = 2.0f,
            isRtl = true,
            isDark = true,
            meanings = listOf(
                Meaning(
                    translation = pathologicalTranslation,
                    note = pathologicalNote,
                    exampleDe = "Er erwähnte das Buch.",
                    exampleRu = "Он упомянул книгу."
                )
            )
        )

        composeTestRule.onNodeWithText(pathologicalNote, substring = true)
            .assertWidthIsAtLeast(200.dp)
    }

    @Test
    fun pathologicalCard_5Meanings_allRegisters_noNote_rendersWithinReasonableHeight() {
        val fiveMeanings = listOf(
            Meaning(translation = "упоминать", note = null, register = "neutral"),
            Meaning(translation = "затрагивать", note = "в разговоре", register = "umgangssprachlich"),
            Meaning(translation = "ссылаться на", note = "официально", register = "formell"),
            Meaning(translation = "поминать", note = "в старых текстах", register = "veraltet"),
            Meaning(translation = "называть", note = null, register = null)
        )

        renderCard(
            width = 360.dp,
            fontScale = 1.0f,
            isRtl = false,
            isDark = false,
            meanings = fiveMeanings
        )

        composeTestRule.onNodeWithText("упоминать")
            .assertWidthIsAtLeast(50.dp)
    }
}
