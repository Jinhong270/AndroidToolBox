package com.jh270.toolbox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal data class SoftWrap(val text: String, val map: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoftWrap) return false
        return text == other.text && map.contentEquals(other.map)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = (31 * result) + map.contentHashCode()
        return result
    }
}

internal fun softWrap(text: String): SoftWrap {
    val map = IntArray(text.length + 1)
    val display = StringBuilder(text.length * 2)
    for (index in text.indices) {
        map[index] = display.length
        display.append(text[index])
        if (text[index] != '\n') display.append('\u200B')
    }
    map[text.length] = display.length
    return SoftWrap(display.toString(), map)
}

internal fun logicalLineStartOffsets(text: String): IntArray {
    var count = 1
    for (ch in text) if (ch == '\n') count++
    val starts = IntArray(count)
    var line = 1
    text.forEachIndexed { index, ch ->
        if (ch == '\n') {
            starts[line] = index + 1
            line++
        }
    }
    return starts
}

internal fun lineTopsPx(text: String, layout: TextLayoutResult): FloatArray {
    val wrapped = softWrap(text)
    val starts = logicalLineStartOffsets(text)
    val tops = FloatArray(starts.size)
    val last = if (layout.lineCount == 0) 0 else layout.getLineEnd(layout.lineCount - 1, false)
    for (index in starts.indices) {
        val transformed = wrapped.map[starts[index].coerceIn(0, wrapped.map.lastIndex)]
        val line = layout.getLineForOffset(transformed.coerceIn(0, last))
        tops[index] = layout.getLineTop(line)
    }
    return tops
}

internal class SoftWrapOffsetMapping(private val map: IntArray) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        if (map.isEmpty()) return 0
        return map[offset.coerceIn(0, map.lastIndex)]
    }

    override fun transformedToOriginal(offset: Int): Int {
        if (map.isEmpty()) return 0
        val last = map.lastIndex
        val target = offset.coerceAtLeast(0)
        if (target >= map[last]) return last
        var low = 0
        var high = last
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (map[mid] <= target) low = mid else high = mid - 1
        }
        return low
    }
}

internal class EditorVisualTransformation(
    private val comment: Color,
    private val string: Color,
    private val number: Color,
    private val keyword: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val wrapped = softWrap(raw)
        val builder = AnnotatedString.Builder(wrapped.text)
        for (span in sourceSpans(raw)) {
            val color = when (span.kind) {
                SourceKind.COMMENT -> comment
                SourceKind.STRING -> string
                SourceKind.NUMBER -> number
                SourceKind.KEYWORD -> keyword
            }
            val start = wrapped.map[span.start.coerceIn(0, raw.length)]
            val end = wrapped.map[span.end.coerceIn(0, raw.length)]
            if (start < end) builder.addStyle(SpanStyle(color = color), start, end)
        }
        return TransformedText(builder.toAnnotatedString(), SoftWrapOffsetMapping(wrapped.map))
    }
}
