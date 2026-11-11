package com.jh270.toolbox.ui

internal enum class SourceKind {
    COMMENT, STRING, NUMBER, KEYWORD, TYPE, FUNCTION, PROPERTY, OPERATOR, HEADING,
}

internal enum class SourceLanguage(val label: String) {
    GENERIC("Code"), PLAIN("Text"), KOTLIN("Kotlin"), JAVA("Java"), JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"), PYTHON("Python"), SHELL("Shell"), POWERSHELL("PowerShell"),
    JSON("JSON"), YAML("YAML"), XML("XML"), HTML("HTML"), CSS("CSS"), SQL("SQL"),
    C("C / C++"), CSHARP("C#"), GO("Go"), RUST("Rust"), PHP("PHP"), RUBY("Ruby"),
    MARKDOWN("Markdown"), TOML("TOML"), INI("INI"),
}

internal data class SourceSpan(val start: Int, val end: Int, val kind: SourceKind)

internal fun detectSourceLanguage(path: String?, text: String = ""): SourceLanguage {
    val name = path.orEmpty().substringAfterLast('/').substringAfterLast('\\').lowercase()
    if (name in setOf("dockerfile", "makefile", ".bashrc", ".zshrc", ".profile", ".bash_profile")) return SourceLanguage.SHELL
    if (name == ".gitconfig" || name == ".editorconfig") return SourceLanguage.INI
    return when (name.substringAfterLast('.', "")) {
        "kt", "kts" -> SourceLanguage.KOTLIN
        "java", "gradle" -> SourceLanguage.JAVA
        "js", "jsx", "mjs", "cjs" -> SourceLanguage.JAVASCRIPT
        "ts", "tsx", "mts", "cts" -> SourceLanguage.TYPESCRIPT
        "py", "pyw" -> SourceLanguage.PYTHON
        "sh", "bash", "zsh", "fish" -> SourceLanguage.SHELL
        "ps1", "psm1", "psd1" -> SourceLanguage.POWERSHELL
        "json", "jsonc", "json5" -> SourceLanguage.JSON
        "yml", "yaml" -> SourceLanguage.YAML
        "xml", "svg", "xsl", "xsd", "plist" -> SourceLanguage.XML
        "html", "htm", "xhtml", "vue" -> SourceLanguage.HTML
        "css", "scss", "less" -> SourceLanguage.CSS
        "sql" -> SourceLanguage.SQL
        "c", "h", "cpp", "hpp", "cc", "cxx" -> SourceLanguage.C
        "cs" -> SourceLanguage.CSHARP
        "go", "mod" -> SourceLanguage.GO
        "rs" -> SourceLanguage.RUST
        "php" -> SourceLanguage.PHP
        "rb", "rake" -> SourceLanguage.RUBY
        "md", "markdown", "mdown" -> SourceLanguage.MARKDOWN
        "toml" -> SourceLanguage.TOML
        "ini", "conf", "cfg", "properties", "env" -> SourceLanguage.INI
        "txt", "log", "csv", "tsv" -> SourceLanguage.PLAIN
        else -> {
            val first = text.take(160).substringBefore('\n').trim()
            when {
                first.startsWith("#!") && first.contains("python") -> SourceLanguage.PYTHON
                first.startsWith("#!") && first.contains("node") -> SourceLanguage.JAVASCRIPT
                first.startsWith("#!") && first.contains("ruby") -> SourceLanguage.RUBY
                first.startsWith("#!") && (first.contains("sh") || first.contains("bash")) -> SourceLanguage.SHELL
                first.startsWith("<?xml") -> SourceLanguage.XML
                else -> SourceLanguage.GENERIC
            }
        }
    }
}

private fun words(value: String) = value.split(' ').toSet()

private val commonKeywords = words("if else for while do return break continue switch case default try catch finally throw new class interface enum public private protected static import package true false null this super async await")
private val keywordsByLanguage = mapOf(
    SourceLanguage.KOTLIN to words("as break by catch class companion const constructor continue crossinline data delegate do dynamic else enum expect external false field file final finally for fun get if import in infix init inline inner interface internal is lateinit noinline null object open operator out override package param private property protected public receiver reified return sealed set setparam super suspend tailrec this throw true try typealias typeof val var vararg when where while actual"),
    SourceLanguage.JAVA to commonKeywords + words("abstract assert boolean byte char double extends final float implements instanceof int long native short strictfp synchronized throws transient void volatile record sealed permits var"),
    SourceLanguage.JAVASCRIPT to commonKeywords + words("const let var function of in export extends typeof instanceof delete void undefined NaN yield debugger get set"),
    SourceLanguage.TYPESCRIPT to commonKeywords + words("const let var function of in export extends typeof instanceof delete void undefined yield type namespace declare abstract readonly keyof infer never unknown any implements as satisfies"),
    SourceLanguage.PYTHON to words("False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case"),
    SourceLanguage.SHELL to words("if then else elif fi case esac for select while until do done in function time coproc return break continue export local readonly declare typeset unset shift source echo printf exit"),
    SourceLanguage.POWERSHELL to words("begin break catch class continue data define do dynamicparam else elseif end enum exit filter finally for foreach from function if in param process return switch throw trap try until using var while workflow"),
    SourceLanguage.JSON to words("true false null"),
    SourceLanguage.YAML to words("true false null yes no on off"),
    SourceLanguage.SQL to words("select from where insert into values update set delete create table alter drop join inner outer left right full on as and or not null is in exists between like group by order having limit offset union all distinct primary key foreign references index view with recursive case when then else end begin commit rollback true false asc desc returning"),
    SourceLanguage.C to commonKeywords + words("auto bool char const constexpr double explicit extern float friend inline int long namespace nullptr operator short signed sizeof struct template typedef typename union unsigned using virtual void volatile include define ifdef ifndef endif pragma"),
    SourceLanguage.CSHARP to commonKeywords + words("abstract as base bool byte char checked const decimal delegate double event explicit extern fixed float foreach implicit in int internal is lock long namespace operator out override params readonly ref sbyte sealed short sizeof stackalloc string struct typeof uint ulong unchecked unsafe ushort using virtual void volatile partial var get set value"),
    SourceLanguage.GO to words("break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var true false nil"),
    SourceLanguage.RUST to words("as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while"),
    SourceLanguage.PHP to commonKeywords + words("abstract array as callable clone const declare die echo elseif empty endfor endforeach endif endwhile eval exit extends final fn foreach function global implements include include_once instanceof isset list namespace print require require_once trait unset use var yield"),
    SourceLanguage.RUBY to words("BEGIN END alias and begin break case class def defined do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield"),
    SourceLanguage.TOML to words("true false inf nan"),
    SourceLanguage.GENERIC to commonKeywords + words("elif fi then done in fun function func def from val var const let void int long nil none and or not yield object struct typedef include esac when echo local readonly until export extends"),
)

internal fun sourceKeywords(language: SourceLanguage): Set<String> = keywordsByLanguage[language].orEmpty()

private val slashComments = setOf(SourceLanguage.GENERIC, SourceLanguage.KOTLIN, SourceLanguage.JAVA, SourceLanguage.JAVASCRIPT, SourceLanguage.TYPESCRIPT, SourceLanguage.JSON, SourceLanguage.C, SourceLanguage.CSHARP, SourceLanguage.GO, SourceLanguage.RUST, SourceLanguage.PHP)
private val hashComments = setOf(SourceLanguage.GENERIC, SourceLanguage.PYTHON, SourceLanguage.SHELL, SourceLanguage.POWERSHELL, SourceLanguage.YAML, SourceLanguage.TOML, SourceLanguage.INI, SourceLanguage.RUBY, SourceLanguage.PHP)
private val markupLanguages = setOf(SourceLanguage.XML, SourceLanguage.HTML)
private val dataLanguages = setOf(SourceLanguage.JSON, SourceLanguage.YAML, SourceLanguage.TOML, SourceLanguage.INI)
private val noCalls = dataLanguages + markupLanguages + setOf(SourceLanguage.PLAIN, SourceLanguage.MARKDOWN, SourceLanguage.CSS)
private val multilineLanguages = setOf(SourceLanguage.SHELL, SourceLanguage.POWERSHELL, SourceLanguage.SQL, SourceLanguage.RUBY, SourceLanguage.PHP)
private val tripleLanguages = setOf(SourceLanguage.PYTHON, SourceLanguage.KOTLIN, SourceLanguage.JAVA, SourceLanguage.TOML)
private val backtickLanguages = setOf(SourceLanguage.JAVASCRIPT, SourceLanguage.TYPESCRIPT, SourceLanguage.GO, SourceLanguage.MARKDOWN, SourceLanguage.GENERIC)
private val dashedWords = markupLanguages + dataLanguages + SourceLanguage.CSS
private val noOperators = markupLanguages + setOf(SourceLanguage.MARKDOWN, SourceLanguage.YAML, SourceLanguage.INI)

internal fun sourceSpans(text: String, language: SourceLanguage = SourceLanguage.GENERIC): List<SourceSpan> {
    if (language == SourceLanguage.PLAIN || text.isEmpty()) return emptyList()
    val spans = ArrayList<SourceSpan>()
    val keywords = sourceKeywords(language)
    var index = 0
    var inTag = false
    fun add(start: Int, end: Int, kind: SourceKind) { if (start < end) spans.add(SourceSpan(start, end, kind)) }
    fun at(value: String) = text.startsWith(value, index)
    fun lineEnd(start: Int) = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    fun nextNonSpace(start: Int): Int {
        var next = start
        while (next < text.length && text[next].isWhitespace()) next++
        return next
    }
    while (index < text.length) {
        val start = index
        val char = text[index]
        if (char.isWhitespace()) { index++; continue }
        val lineStart = index == 0 || text[index - 1] == '\n'
        if ((language in markupLanguages || language == SourceLanguage.MARKDOWN) && at("<!--")) {
            val close = text.indexOf("-->", index + 4)
            index = if (close < 0) text.length else close + 3
            add(start, index, SourceKind.COMMENT)
        } else if ((language in slashComments || language == SourceLanguage.CSS || language == SourceLanguage.SQL) && at("/*")) {
            index += 2
            var depth = 1
            val nested = language == SourceLanguage.KOTLIN || language == SourceLanguage.RUST
            while (index < text.length && depth > 0) {
                when {
                    at("*/") -> { depth--; index += 2 }
                    nested && at("/*") -> { depth++; index += 2 }
                    else -> index++
                }
            }
            add(start, index, SourceKind.COMMENT)
        } else if (language == SourceLanguage.POWERSHELL && at("<#")) {
            val close = text.indexOf("#>", index + 2)
            index = if (close < 0) text.length else close + 2
            add(start, index, SourceKind.COMMENT)
        } else if ((language in slashComments && at("//")) || (language == SourceLanguage.SQL && at("--")) ||
            (language in hashComments && (char == '#' || (language == SourceLanguage.GENERIC && char == '＃'))) ||
            (language == SourceLanguage.INI && char == ';')) {
            index = lineEnd(index)
            add(start, index, SourceKind.COMMENT)
        } else if (language == SourceLanguage.MARKDOWN && lineStart && (at("```") || at("~~~"))) {
            val fence = text.substring(index, index + 3)
            var end = lineEnd(index)
            index = text.length
            while (end < text.length) {
                val next = end + 1
                if (text.startsWith(fence, next)) { index = lineEnd(next); break }
                end = lineEnd(next)
            }
            add(start, index, SourceKind.STRING)
        } else if (language == SourceLanguage.MARKDOWN && lineStart && char == '#') {
            index = lineEnd(index)
            add(start, index, SourceKind.HEADING)
        } else if (language in markupLanguages && char == '<') {
            index++
            if (index < text.length && text[index] in "/!?") index++
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] in "-:_")) index++
            inTag = true
            add(start, index, SourceKind.KEYWORD)
        } else if (language in markupLanguages && char == '>') {
            inTag = false
            index++
            add(start, index, SourceKind.KEYWORD)
        } else if (language in markupLanguages && !inTag && char != '&') {
            while (index < text.length && text[index] != '<' && text[index] != '&') index++
        } else if (language in markupLanguages && char == '&') {
            index++
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '#')) index++
            if (index < text.length && text[index] == ';') index++
            add(start, index, SourceKind.NUMBER)
        } else if (char == '"' || char == '\'' || (char == '`' && language in backtickLanguages)) {
            val triple = language in tripleLanguages && text.startsWith(char.toString().repeat(3), index)
            val delimiter = char.toString().repeat(if (triple) 3 else 1)
            val multiline = triple || char == '`' || language in multilineLanguages
            index += delimiter.length
            while (index < text.length) {
                if (!multiline && text[index] == '\n') break
                if (text[index] == '\\' && !(triple && language == SourceLanguage.KOTLIN) && !(language == SourceLanguage.GO && char == '`')) {
                    index = (index + 2).coerceAtMost(text.length)
                } else if (text.startsWith(delimiter, index)) {
                    index += delimiter.length
                    if (language == SourceLanguage.SQL && index < text.length && text[index] == char) index++ else break
                } else index++
            }
            val next = nextNonSpace(index)
            val property = language in dataLanguages && next < text.length && text[next] == ':'
            add(start, index, if (property) SourceKind.PROPERTY else SourceKind.STRING)
        } else if (char.isDigit() && (index == 0 || !text[index - 1].isLetterOrDigit())) {
            index++
            if (char == '0' && index < text.length && text[index].lowercaseChar() in "xbo") {
                index++
                while (index < text.length && (text[index].isDigit() || text[index].lowercaseChar() in 'a'..'f' || text[index] == '_')) index++
            } else {
                while (index < text.length && (text[index].isDigit() || text[index] == '_')) index++
                if (index + 1 < text.length && text[index] == '.' && text[index + 1].isDigit()) {
                    index++
                    while (index < text.length && (text[index].isDigit() || text[index] == '_')) index++
                }
                if (index < text.length && text[index].lowercaseChar() == 'e') {
                    var exponent = index + 1
                    if (exponent < text.length && text[exponent] in "+-") exponent++
                    if (exponent < text.length && text[exponent].isDigit()) {
                        index = exponent + 1
                        while (index < text.length && text[index].isDigit()) index++
                    }
                }
            }
            add(start, index, SourceKind.NUMBER)
        } else if (char.isLetter() || char == '_' || char == '$' || (language == SourceLanguage.CSS && char == '#')) {
            index++
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] in "_$" || (language in dashedWords && text[index] == '-'))) index++
            val word = text.substring(start, index)
            val next = nextNonSpace(index)
            val normalized = if (language == SourceLanguage.SQL || language == SourceLanguage.POWERSHELL) word.lowercase() else word
            val kind = when {
                normalized in keywords -> SourceKind.KEYWORD
                language in markupLanguages && inTag -> SourceKind.PROPERTY
                language in dataLanguages && next < text.length && text[next] in ":=" -> SourceKind.PROPERTY
                language == SourceLanguage.CSS && next < text.length && text[next] == ':' -> SourceKind.PROPERTY
                language !in noCalls && next < text.length && text[next] == '(' -> SourceKind.FUNCTION
                language !in noCalls && char.isUpperCase() -> SourceKind.TYPE
                char == '$' -> SourceKind.PROPERTY
                else -> null
            }
            if (kind != null) add(start, index, kind)
        } else {
            index++
            if (char in "+-*/%=!<>|&?:~^" && language !in noOperators) add(start, index, SourceKind.OPERATOR)
        }
    }
    return spans
}
