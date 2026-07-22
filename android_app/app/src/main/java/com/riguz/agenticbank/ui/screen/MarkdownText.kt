package com.riguz.agenticbank.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val annotatedString = parseMarkdown(text)
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        color = color,
    )
}

private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            when {
                // Header
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                        append(line.removePrefix("### "))
                    }
                    append("\n")
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                        append(line.removePrefix("## "))
                    }
                    append("\n")
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removePrefix("# "))
                    }
                    append("\n")
                }
                // Horizontal rule
                line.trim() == "---" || line.trim() == "***" -> {
                    withStyle(SpanStyle(color = Color.Gray)) {
                        append("────────────────────────────────")
                    }
                    append("\n")
                }
                // List items
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    append("  • ")
                    appendInlineMarkdown(line.trimStart().removePrefix("- ").removePrefix("* "))
                    append("\n")
                }
                line.trimStart().matches(Regex("^\\d+\\. .*")) -> {
                    val num = line.trimStart().substringBefore(".")
                    append("  $num. ")
                    appendInlineMarkdown(line.trimStart().substringAfter(". "))
                    append("\n")
                }
                // Code block
                line.trim().startsWith("```") -> {
                    append("\n")
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )) {
                            append(lines[i])
                        }
                        append("\n")
                        i++
                    }
                    append("\n")
                }
                // Regular text
                else -> {
                    appendInlineMarkdown(line)
                    append("\n")
                }
            }
            i++
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold **text**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic *text*
            text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*') -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Inline code `text`
            text[i] == '`' -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Link [text](url)
            text[i] == '[' -> {
                val closeBracket = text.indexOf("]", i + 1)
                if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(")", closeBracket + 2)
                    if (closeParen != -1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        withStyle(SpanStyle(
                            color = Color(0xFF1976D2),
                            textDecoration = TextDecoration.Underline,
                        )) {
                            append(linkText)
                        }
                        i = closeParen + 1
                    } else {
                        append(text[i])
                        i++
                    }
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
