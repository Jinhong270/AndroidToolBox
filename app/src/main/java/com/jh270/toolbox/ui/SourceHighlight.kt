package com.jh270.toolbox.ui

internal enum class SourceKind {
    COMMENT,
    STRING,
    NUMBER,
    KEYWORD,
}

internal data class SourceSpan(val start: Int, val end: Int, val kind: SourceKind)

private val sourceKeywords = setOf(
    "if", "else", "elif", "fi", "then", "for", "while", "do", "done", "in",
    "return", "fun", "function", "func", "class", "def", "import", "from",
    "package", "val", "var", "const", "let", "public", "private", "protected",
    "static", "void", "int", "long", "true", "false", "null", "nil", "none",
    "and", "or", "not", "try", "catch", "finally", "switch", "case", "break",
    "continue", "new", "this", "super", "async", "await", "yield", "interface",
    "object", "enum", "struct", "typedef", "include", "esac", "when", "echo",
    "local", "readonly", "until", "export", "default", "extends", "throw",
)

internal fun sourceSpans(text: String): List<SourceSpan> {
    val spans = ArrayList<SourceSpan>()
    val length = text.length
    var index = 0
    while (index < length) {
        val current = text[index]
        if (current == '/' && index + 1 < length && text[index + 1] == '*') {
            val start = index
            index += 2
            while (index + 1 < length && !(text[index] == '*' && text[index + 1] == '/')) index++
            index = if (index + 1 < length) index + 2 else length
            spans.add(SourceSpan(start, index, SourceKind.COMMENT))
            continue
        }
        if (current == '/' && index + 1 < length && text[index + 1] == '/') {
            val start = index
            index += 2
            while (index < length && text[index] != '\n') index++
            spans.add(SourceSpan(start, index, SourceKind.COMMENT))
            continue
        }
        if (current == '#' || current == '＃') {
            val start = index
            index++
            while (index < length && text[index] != '\n') index++
            spans.add(SourceSpan(start, index, SourceKind.COMMENT))
            continue
        }
        if (current == '"' || current == '\'') {
            val quote = current
            val start = index
            index++
            while (index < length && text[index] != '\n') {
                if (text[index] == '\\' && index + 1 < length && text[index + 1] != '\n') {
                    index += 2
                    continue
                }
                if (text[index] == quote) {
                    index++
                    break
                }
                index++
            }
            spans.add(SourceSpan(start, index, SourceKind.STRING))
            continue
        }
        if (current.isDigit()) {
            val start = index
            if (current == '0' && index + 1 < length && (text[index + 1] == 'x' || text[index + 1] == 'X')) {
                index += 2
                while (index < length && isHex(text[index])) index++
            } else {
                index++
                while (index < length && (text[index].isDigit() || text[index] == '.')) index++
            }
            spans.add(SourceSpan(start, index, SourceKind.NUMBER))
            continue
        }
        if (current.isLetter() || current == '_') {
            val start = index
            index++
            while (index < length && (text[index].isLetterOrDigit() || text[index] == '_')) index++
            val word = text.substring(start, index)
            if (word in sourceKeywords) spans.add(SourceSpan(start, index, SourceKind.KEYWORD))
            continue
        }
        index++
    }
    return spans
}

private fun isHex(char: Char): Boolean {
    return char.isDigit() || char.lowercaseChar() in 'a'..'f'
}
