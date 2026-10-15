package app.deutschlern.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

import androidx.compose.runtime.compositionLocalOf

val LocalColoredArticlesEnabled = compositionLocalOf { true }

/**
 * Semantic article colors for German genders (der, die, das):
 * - der: Blue
 * - die (including plural): Red
 * - das: Yellow / Amber
 *
 * Designed with separate light and dark theme values strictly satisfying WCAG 2.1 AA
 * contrast ratio requirements (>= 4.5:1 for normal text against #FAFAFA and #0E0E0E).
 * Documented exception to the single-accent design rule.
 */
object ArticleColors {
    // Light theme values (evaluated against #FAFAFA background)
    val LightDer = Color(0xFF1565C0) // Deep Blue (5.51:1 contrast)
    val LightDie = Color(0xFFC62828) // Crimson Red (5.39:1 contrast)
    val LightDas = Color(0xFF7F6000) // Deep Golden Amber (5.62:1 contrast, distinct from die under protanopia)

    // Dark theme values (evaluated against #0E0E0E background)
    val DarkDer = Color(0xFF64B5F6) // Sky Blue (8.72:1 contrast)
    val DarkDie = Color(0xFFEF5350) // Bright Red (5.54:1 contrast)
    val DarkDas = Color(0xFFFDD835) // Warm Yellow (13.84:1 contrast)

    /**
     * Resolves the semantic color for a given German article.
     * Returns null if uncolored or article is null/unrecognized.
     */
    fun getColor(
        article: String?,
        isDark: Boolean,
        isEnabled: Boolean = true
    ): Color? {
        if (!isEnabled || article.isNullOrBlank()) return null

        return when (article.trim().lowercase()) {
            "der" -> if (isDark) DarkDer else LightDer
            "die" -> if (isDark) DarkDie else LightDie
            "das" -> if (isDark) DarkDas else LightDas
            else -> null
        }
    }
}

/**
 * Renders an article with semantic color (if enabled) or standard text color.
 */
@Composable
fun ArticleLabel(
    article: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight = FontWeight.Normal,
    isColoredEnabled: Boolean = LocalColoredArticlesEnabled.current
) {
    if (article.isNullOrBlank()) return

    val isDark = isSystemInDarkTheme()
    val resolvedColor = ArticleColors.getColor(article, isDark = isDark, isEnabled = isColoredEnabled)
        ?: MaterialTheme.colorScheme.onSurface

    Text(
        text = article,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = resolvedColor
    )
}

/**
 * Builds an AnnotatedString for combined "article lemma" display where only the article is colored.
 */
@Composable
fun buildArticleLemmaAnnotatedString(
    article: String?,
    lemma: String,
    isColoredEnabled: Boolean = LocalColoredArticlesEnabled.current,
    articleWeight: FontWeight = FontWeight.Normal,
    lemmaWeight: FontWeight = FontWeight.Bold,
    defaultColor: Color = MaterialTheme.colorScheme.onSurface
): AnnotatedString {
    val isDark = isSystemInDarkTheme()
    val articleColor = ArticleColors.getColor(article, isDark = isDark, isEnabled = isColoredEnabled)
        ?: defaultColor

    return buildAnnotatedString {
        if (!article.isNullOrBlank()) {
            withStyle(SpanStyle(color = articleColor, fontWeight = articleWeight)) {
                append(article)
            }
            append(" ")
        }
        withStyle(SpanStyle(color = defaultColor, fontWeight = lemmaWeight)) {
            append(lemma)
        }
    }
}
