package com.jh270.toolbox.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun editorIndent(text: String, language: SourceLanguage): String {
    val indents = text.lineSequence().take(200).map { it.takeWhile { ch -> ch == ' ' || ch == '\t' } }.filter { it.isNotEmpty() }.toList()
    if (indents.count { it.startsWith('\t') } > indents.size / 2) return "\t"
    val width = indents.filter { '\t' !in it }.minOfOrNull { it.length }?.takeIf { it in 1..8 }
    return " ".repeat(width ?: if (language in setOf(SourceLanguage.JSON, SourceLanguage.YAML, SourceLanguage.HTML, SourceLanguage.CSS)) 2 else 4)
}

internal fun replaceEditorSelection(value: TextFieldValue, insertion: String, caret: Int = insertion.length): TextFieldValue {
    val start = value.selection.min
    return TextFieldValue(
        value.text.replaceRange(start, value.selection.max, insertion),
        TextRange(start + caret.coerceIn(0, insertion.length)),
    )
}

internal fun assistEditorChange(
    old: TextFieldValue,
    next: TextFieldValue,
    language: SourceLanguage,
    indent: String,
    spans: List<SourceSpan> = sourceSpans(old.text, language),
): TextFieldValue {
    if (old.text == next.text || next.composition != null || old.composition != null || language == SourceLanguage.PLAIN) return next
    val start = old.selection.min
    val end = old.selection.max
    val insertedLength = next.text.length - (old.text.length - (end - start))
    if (insertedLength == 1 && next.selection.collapsed && next.selection.start == start + 1 &&
        next.text.regionMatches(0, old.text, 0, start) && next.text.substring(start + 1) == old.text.substring(end)) {
        val char = next.text[start]
        val protected = spans.any { start > it.start && start < it.end && it.kind in setOf(SourceKind.COMMENT, SourceKind.STRING) } ||
            spans.any { start == it.end && it.kind == SourceKind.COMMENT && old.text.getOrNull(start - 1) != '\n' }
        if (char == '\n') {
            val lineStart = old.text.lastIndexOf('\n', (start - 1).coerceAtLeast(-1)) + 1
            val before = old.text.substring(lineStart, start)
            val base = before.takeWhile { it == ' ' || it == '\t' }
            val opener = before.trimEnd().lastOrNull()
            val extra = !protected && (opener in listOf('{', '[', '(') || language == SourceLanguage.PYTHON && opener == ':')
            val newline = if ("\r\n" in old.text) "\r\n" else "\n"
            val close = mapOf('{' to '}', '[' to ']', '(' to ')')[opener]
            val suffix = if (extra && close != null && old.text.getOrNull(end) == close) newline + base else ""
            val prefix = newline + base + if (extra) indent else ""
            return replaceEditorSelection(old, prefix + suffix, prefix.length)
        }
        if (protected) {
            if (old.selection.collapsed && char in "'\"" && old.text.getOrNull(start) == char &&
                spans.any { it.kind == SourceKind.STRING && it.end == start + 1 }) {
                return old.copy(selection = TextRange(start + 1), composition = null)
            }
            return next
        }
        val pair = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')
        val closer = pair[char]
        if (old.selection.collapsed && char in ")]}'\"" && old.text.getOrNull(start) == char) {
            return old.copy(selection = TextRange(start + 1), composition = null)
        }
        if (closer != null) {
            val following = old.text.getOrNull(end)
            val preceding = old.text.getOrNull(start - 1)
            val quote = char == '\'' || char == '"'
            if (quote && (preceding?.isLetterOrDigit() == true || preceding == '\\' || following?.isLetterOrDigit() == true)) return next
            if (old.selection.collapsed && following != null && !following.isWhitespace() && following !in ")]},;:") return next
            val selected = old.text.substring(start, end)
            val text = "$char$selected$closer"
            val result = replaceEditorSelection(old, text, text.length - 1)
            return if (selected.isEmpty()) result else result.copy(selection = TextRange(start + 1, start + 1 + selected.length))
        }
    }
    if (old.selection.collapsed && start > 0 && next.selection == TextRange(start - 1) && next.text == old.text.removeRange(start - 1, start)) {
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')
        val closer = pairs[old.text[start - 1]]
        if (closer != null && closer == old.text.getOrNull(start)) {
            return TextFieldValue(old.text.removeRange(start - 1, start + 1), TextRange(start - 1))
        }
    }
    return next
}

internal fun indentEditorSelection(value: TextFieldValue, indent: String, outdent: Boolean = false): TextFieldValue {
    if (!outdent && value.selection.collapsed) return replaceEditorSelection(value, indent)
    val start = value.text.lastIndexOf('\n', value.selection.min - 1) + 1
    val endPosition = if (!value.selection.collapsed && value.selection.max > start && value.text.getOrNull(value.selection.max - 1) == '\n') value.selection.max - 1 else value.selection.max
    val end = value.text.indexOf('\n', endPosition).let { if (it < 0) value.text.length else it }
    val edits = ArrayList<Pair<Int, Int>>()
    var offset = start
    val replacement = value.text.substring(start, end).split('\n').map { line ->
        val removed = if (!outdent) 0 else if (line.startsWith('\t')) 1 else line.takeWhile { it == ' ' }.length.coerceAtMost(indent.length)
        edits.add(offset to if (outdent) -removed else indent.length)
        offset += line.length + 1
        if (outdent) line.drop(removed) else indent + line
    }.joinToString("\n")
    fun mapped(position: Int): Int {
        var change = 0
        for ((offset, delta) in edits) {
            if (position < offset) break
            change += if (delta < 0) -((position - offset).coerceAtMost(-delta)) else delta
        }
        return position + change
    }
    return TextFieldValue(value.text.replaceRange(start, end, replacement), TextRange(mapped(value.selection.start), mapped(value.selection.end)))
}

internal data class EditorCompletion(val label: String, val insertion: String, val caret: Int = insertion.length, val snippet: Boolean = false)

internal fun editorDocumentWords(text: String): List<String> =
    Regex("[\\p{L}_$][\\p{L}\\p{N}_$]{1,63}").findAll(text).map { it.value }.distinct().take(1500).toList()

internal fun editorCompletions(value: TextFieldValue, language: SourceLanguage, words: List<String>, spans: List<SourceSpan>): List<EditorCompletion> {
    if (!value.selection.collapsed || value.composition != null || language in setOf(SourceLanguage.PLAIN, SourceLanguage.MARKDOWN)) return emptyList()
    val cursor = value.selection.start
    if (spans.any { cursor > it.start && cursor <= it.end && it.kind in setOf(SourceKind.STRING, SourceKind.COMMENT) }) return emptyList()
    var start = cursor
    while (start > 0 && (value.text[start - 1].isLetterOrDigit() || value.text[start - 1] in "_$")) start--
    val prefix = value.text.substring(start, cursor)
    if (prefix.length < 2 || prefix.first().isDigit()) return emptyList()
    val candidates = (sourceKeywords(language) + words).asSequence()
        .filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }
        .sortedWith(compareBy<String> { !it.startsWith(prefix) }.thenBy { it.length }.thenBy { it })
        .take(7).map { EditorCompletion(it, it) }.toList()
    val indent = editorIndent(value.text, language)
    val template = when (language) {
        SourceLanguage.PYTHON -> mapOf("def" to "def name():\n${indent}|", "if" to "if condition:\n${indent}|", "for" to "for item in items:\n${indent}|")
        SourceLanguage.KOTLIN -> mapOf("fun" to "fun name() {\n${indent}|\n}", "if" to "if (condition) {\n${indent}|\n}")
        SourceLanguage.JAVASCRIPT, SourceLanguage.TYPESCRIPT -> mapOf("function" to "function name() {\n${indent}|\n}", "if" to "if (condition) {\n${indent}|\n}")
        SourceLanguage.SHELL -> mapOf("if" to "if condition; then\n${indent}|\nfi", "for" to "for item in items; do\n${indent}|\ndone")
        else -> emptyMap()
    }
    val snippets = template.filterKeys { it.startsWith(prefix) }.map { (label, body) ->
        val lineStart = value.text.lastIndexOf('\n', start - 1) + 1
        val base = value.text.substring(lineStart, start).takeWhile { it == ' ' || it == '\t' }
        val newline = if ("\r\n" in value.text) "\r\n" else "\n"
        val adjusted = body.replace("\n", newline + base)
        EditorCompletion("$label …", adjusted.replace("|", ""), adjusted.indexOf('|'), true)
    }
    return (snippets + candidates).take(8)
}

internal fun applyEditorCompletion(value: TextFieldValue, completion: EditorCompletion): TextFieldValue {
    var start = value.selection.min
    var end = value.selection.max
    while (start > 0 && (value.text[start - 1].isLetterOrDigit() || value.text[start - 1] in "_$")) start--
    while (end < value.text.length && (value.text[end].isLetterOrDigit() || value.text[end] in "_$")) end++
    return replaceEditorSelection(value.copy(selection = TextRange(start, end)), completion.insertion, completion.caret)
}

internal fun editorMatches(text: String, query: String, matchCase: Boolean): List<TextRange> {
    if (query.isEmpty()) return emptyList()
    val result = ArrayList<TextRange>()
    var start = 0
    while (start <= text.length - query.length && result.size < 10_000) {
        val found = text.indexOf(query, start, ignoreCase = !matchCase)
        if (found < 0) break
        result.add(TextRange(found, found + query.length))
        start = found + query.length
    }
    return result
}

internal fun replaceAllEditorMatches(value: TextFieldValue, query: String, replacement: String, matchCase: Boolean): TextFieldValue {
    if (query.isEmpty()) return value
    val updated = value.text.replace(query, replacement, ignoreCase = !matchCase)
    return TextFieldValue(updated, TextRange(value.selection.end.coerceAtMost(updated.length)))
}

internal class EditorHistory(private val maxEntries: Int = 100, private val maxCharacters: Int = 4_000_000) {
    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(previous: TextFieldValue, next: TextFieldValue) {
        if (previous.text == next.text) return
        undo.addLast(previous.copy(composition = null))
        redo.clear()
        trim(undo)
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        if (undo.isEmpty()) return null
        redo.addLast(current.copy(composition = null))
        trim(redo)
        return undo.removeLast()
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        if (redo.isEmpty()) return null
        undo.addLast(current.copy(composition = null))
        trim(undo)
        return redo.removeLast()
    }

    private fun trim(stack: ArrayDeque<TextFieldValue>) {
        var size = stack.sumOf { it.text.length }
        while (stack.size > 1 && (stack.size > maxEntries || size > maxCharacters)) size -= stack.removeFirst().text.length
    }
}
