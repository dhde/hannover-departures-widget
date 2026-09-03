package de.dhde.hannover.departures.widget.debug

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * Hebt JSON-Blöcke in Debug-Log-Zeilen farblich hervor.
 * Erkennt den ersten gepaarten `{…}`- oder `[…]`-Block, pretty-printed
 * ihn und einfärbt Keys/Strings/Zahlen/Booleans/null.
 */
object DebugLogFormatter {
    private val KEY = Color(0xFF64B5F6)         // hellblau
    private val STRING = Color(0xFF81C784)      // hellgrün
    private val NUMBER = Color(0xFFFFA726)      // orange
    private val KEYWORD = Color(0xFFCE93D8)     // lila (true/false)
    private val NULL_COLOR = Color(0xFFEF5350)  // rot
    private val PUNCT = Color(0xFF9E9E9E)       // grau (Braces, Kommas)

    /** Entry-Point für den DebugScreen. */
    fun format(message: String): AnnotatedString {
        val range = findJsonRange(message) ?: return AnnotatedString(message)
        val (start, end) = range
        val prefix = message.substring(0, start)
        val jsonText = message.substring(start, end)
        val suffix = message.substring(end)

        val pretty = tryPrettyPrint(jsonText) ?: return AnnotatedString(message)

        return buildAnnotatedString {
            append(prefix)
            if (prefix.isNotEmpty() && !prefix.endsWith("\n")) append('\n')
            appendJson(pretty)
            if (suffix.isNotEmpty()) {
                append(suffix)
            }
        }
    }

    /** Findet erste balancierte `{…}` oder `[…]`-Range oder null. */
    private fun findJsonRange(text: String): Pair<Int, Int>? {
        val openIdx = text.indexOfAny(charArrayOf('{', '['))
        if (openIdx < 0) return null
        val opener = text[openIdx]
        val closer = if (opener == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in openIdx until text.length) {
            val c = text[i]
            if (escaped) { escaped = false; continue }
            if (inString) {
                if (c == '\\') { escaped = true; continue }
                if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                opener -> depth++
                closer -> {
                    depth--
                    if (depth == 0) return openIdx to (i + 1)
                }
            }
        }
        return null
    }

    private fun tryPrettyPrint(json: String): String? = try {
        val parsed = Gson().fromJson(json, JsonElement::class.java)
        com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(parsed)
    } catch (_: Exception) { null }

    /** Einfacher Line-Scan-Tokenizer für JSON: erkennt Key vs. String-Wert am Doppelpunkt danach. */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendJson(pretty: String) {
        var i = 0
        while (i < pretty.length) {
            val c = pretty[i]
            when {
                c == '"' -> {
                    // String bis nächstes unescaped "
                    val end = findStringEnd(pretty, i)
                    val token = pretty.substring(i, end + 1)
                    val isKey = isKeyPosition(pretty, end + 1)
                    withStyle(SpanStyle(color = if (isKey) KEY else STRING)) { append(token) }
                    i = end + 1
                }
                c.isDigit() || (c == '-' && i + 1 < pretty.length && pretty[i + 1].isDigit()) -> {
                    var end = i + 1
                    while (end < pretty.length && (pretty[end].isDigit() || pretty[end] == '.' || pretty[end] == 'e' || pretty[end] == 'E' || pretty[end] == '+' || pretty[end] == '-')) end++
                    withStyle(SpanStyle(color = NUMBER)) { append(pretty.substring(i, end)) }
                    i = end
                }
                pretty.startsWith("true", i) -> {
                    withStyle(SpanStyle(color = KEYWORD)) { append("true") }; i += 4
                }
                pretty.startsWith("false", i) -> {
                    withStyle(SpanStyle(color = KEYWORD)) { append("false") }; i += 5
                }
                pretty.startsWith("null", i) -> {
                    withStyle(SpanStyle(color = NULL_COLOR)) { append("null") }; i += 4
                }
                c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == ':' -> {
                    withStyle(SpanStyle(color = PUNCT)) { append(c) }
                    i++
                }
                else -> { append(c); i++ }
            }
        }
    }

    private fun findStringEnd(text: String, startQuote: Int): Int {
        var i = startQuote + 1
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') { i += 2; continue }
            if (c == '"') return i
            i++
        }
        return text.length - 1
    }

    /** Nach dem schließenden Quote folgen ggf. Whitespace, dann `:` → das war ein Key. */
    private fun isKeyPosition(text: String, afterQuote: Int): Boolean {
        var i = afterQuote
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && text[i] == ':'
    }
}
