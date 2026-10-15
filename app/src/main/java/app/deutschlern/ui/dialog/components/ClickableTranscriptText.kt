package app.deutschlern.ui.dialog.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.deutschlern.util.TextNormalizer

@Composable
fun ClickableTranscriptText(
    text: String,
    onWordClick: (word: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // German text is always rendered strictly LTR
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val annotatedString = androidx.compose.runtime.remember(text) {
            TranscriptTokenizer.tokenize(text)
        }

        val defaultStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        )

        ClickableText(
            text = annotatedString,
            modifier = modifier,
            style = defaultStyle,
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "WORD", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        val cleaned = TextNormalizer.normalize(annotation.item).trim()
                        if (cleaned.isNotBlank()) {
                            onWordClick(cleaned)
                        }
                    }
            }
        )
    }
}
