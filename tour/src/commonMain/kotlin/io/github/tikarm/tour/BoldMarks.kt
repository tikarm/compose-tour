package io.github.tikarm.tour

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private const val BOLD_MARK = "**"

/**
 * Shows the parts of a tour text wrapped in `**` bold, so a translation can stress a button's
 * name. String resources are plain text, so this stands in for markup.
 */
internal fun String.withBoldMarks(): AnnotatedString = buildAnnotatedString {
    split(BOLD_MARK).forEachIndexed { index, part ->
        // Parts between an opening and a closing mark sit at odd positions
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        } else {
            append(part)
        }
    }
}
