package com.jh270.toolbox.json

class JSONException(message: String) : Exception(message)

class JSONObject(map: Map<String, Any?>) {
    private val pairs: LinkedHashMap<String, Any> = wrapMap(map)

    constructor(source: String) : this(Parser(source).parseTopMap())

    fun has(name: String): Boolean = pairs.containsKey(name)

    fun opt(name: String): Any? = pairs[name]

    fun optString(name: String): String = optString(name, "")

    fun optString(name: String, fallback: String): String = stringValue(opt(name), fallback)

    fun optBoolean(name: String, fallback: Boolean): Boolean {
        return when (val value = opt(name)) {
            is Boolean -> value
            is String -> when {
                value.equals("true", ignoreCase = true) -> true
                value.equals("false", ignoreCase = true) -> false
                else -> fallback
            }
            else -> fallback
        }
    }

    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject

    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray

    fun keys(): Iterator<String> = pairs.keys.iterator()

    fun toString(indentFactor: Int): String {
        val builder = StringBuilder()
        writeTo(builder, indentFactor, 0)
        return builder.toString()
    }

    override fun toString(): String = toString(0)

    internal fun writeTo(builder: StringBuilder, indentFactor: Int, indent: Int) {
        builder.append('{')
        if (pairs.isEmpty()) {
            builder.append('}')
            return
        }
        val pretty = indentFactor > 0
        val next = if (pretty) indent + indentFactor else 0
        var first = true
        for ((key, value) in pairs) {
            if (!first) builder.append(',')
            first = false
            if (pretty) {
                builder.append('\n')
                appendIndent(builder, next)
            }
            builder.append(quote(key))
            if (pretty) builder.append(": ") else builder.append(':')
            writeJson(builder, value, indentFactor, next)
        }
        if (pretty) {
            builder.append('\n')
            appendIndent(builder, indent)
        }
        builder.append('}')
    }

    companion object {
        val NULL: Any = Any()
    }

    private class Parser(private val source: String) {
        private var index = 0
        private var depth = 0

        fun parseTopMap(): LinkedHashMap<String, Any?> {
            if (source.isNotEmpty() && source[0] == '\uFEFF') index = 1
            skipWs()
            if (index >= source.length || source[index] != '{') fail("Expected object")
            val map = parseObjectMap()
            skipWs()
            if (index != source.length) fail("Trailing data")
            return map
        }

        private fun parseValue(): Any? {
            skipWs()
            if (index >= source.length) fail("Unexpected end of input")
            return when (source[index]) {
                '{' -> JSONObject(parseObjectMap())
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> {
                    if (source[index] == '-' || source[index] in '0'..'9') parseNumber()
                    else fail("Unexpected character")
                }
            }
        }

        private fun parseObjectMap(): LinkedHashMap<String, Any?> {
            expect('{')
            enter()
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (match('}')) {
                leave()
                return map
            }
            while (true) {
                skipWs()
                if (index >= source.length || source[index] != '"') fail("Expected name")
                val name = parseString()
                skipWs()
                expect(':')
                map[name] = parseValue()
                skipWs()
                if (match(',')) continue
                if (match('}')) {
                    leave()
                    return map
                }
                fail("Expected ',' or '}'")
            }
        }

        private fun parseArray(): JSONArray {
            expect('[')
            enter()
            val items = ArrayList<Any?>()
            skipWs()
            if (match(']')) {
                leave()
                return JSONArray(items)
            }
            while (true) {
                items.add(parseValue())
                skipWs()
                if (match(',')) continue
                if (match(']')) {
                    leave()
                    return JSONArray(items)
                }
                fail("Expected ',' or ']'")
            }
        }

        private fun parseLiteral(text: String, value: Any?): Any? {
            if (index + text.length > source.length || !source.regionMatches(index, text, 0, text.length)) {
                fail("Expected $text")
            }
            val end = index + text.length
            if (end < source.length) {
                val next = source[end]
                if (next.isLetterOrDigit() || next == '_') fail("Expected $text")
            }
            index = end
            return value
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < source.length) {
                val c = source[index]
                when {
                    c == '"' -> {
                        index++
                        return builder.toString()
                    }
                    c == '\\' -> {
                        index++
                        if (index >= source.length) fail("Unterminated escape")
                        when (source[index]) {
                            '"', '\\', '/' -> {
                                builder.append(source[index])
                                index++
                            }
                            'b' -> {
                                builder.append('\b')
                                index++
                            }
                            'f' -> {
                                builder.append('\u000C')
                                index++
                            }
                            'n' -> {
                                builder.append('\n')
                                index++
                            }
                            'r' -> {
                                builder.append('\r')
                                index++
                            }
                            't' -> {
                                builder.append('\t')
                                index++
                            }
                            'u' -> {
                                index++
                                if (index + 4 > source.length) fail("Bad unicode escape")
                                var code = 0
                                repeat(4) {
                                    val hex = hexValue(source[index])
                                    if (hex < 0) fail("Bad unicode escape")
                                    code = (code shl 4) or hex
                                    index++
                                }
                                builder.append(code.toChar())
                            }
                            else -> fail("Invalid escape")
                        }
                    }
                    c.code < 0x20 -> fail("Unescaped control character")
                    else -> {
                        builder.append(c)
                        index++
                    }
                }
            }
            fail("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = index
            if (match('-') && index >= source.length) fail("Invalid number")
            if (index >= source.length) fail("Invalid number")
            if (source[index] == '0') {
                index++
            } else if (source[index] in '1'..'9') {
                while (index < source.length && source[index] in '0'..'9') index++
            } else {
                fail("Invalid number")
            }
            var real = false
            if (index < source.length && source[index] == '.') {
                real = true
                index++
                if (index >= source.length || source[index] !in '0'..'9') fail("Invalid number")
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                real = true
                index++
                if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
                if (index >= source.length || source[index] !in '0'..'9') fail("Invalid number")
                while (index < source.length && source[index] in '0'..'9') index++
            }
            val literal = source.substring(start, index)
            if (!real) {
                val asLong = literal.toLongOrNull() ?: return JsonNumber(literal)
                if (asLong >= Int.MIN_VALUE.toLong() && asLong <= Int.MAX_VALUE.toLong()) return asLong.toInt()
                return asLong
            }
            val asDouble = try {
                literal.toDouble()
            } catch (_: NumberFormatException) {
                throw JSONException("Invalid number")
            }
            if (!asDouble.isFinite()) return JsonNumber(literal)
            return asDouble
        }

        private fun hexValue(c: Char): Int {
            return when (c) {
                in '0'..'9' -> c.code - '0'.code
                in 'a'..'f' -> c.code - 'a'.code + 10
                in 'A'..'F' -> c.code - 'A'.code + 10
                else -> -1
            }
        }

        private fun enter() {
            depth += 1
            if (depth > 512) fail("Nesting too deep")
        }

        private fun leave() {
            depth -= 1
        }

        private fun skipWs() {
            while (index < source.length) {
                val c = source[index]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') index++ else return
            }
        }

        private fun match(c: Char): Boolean {
            if (index < source.length && source[index] == c) {
                index++
                return true
            }
            return false
        }

        private fun expect(c: Char) {
            if (index >= source.length || source[index] != c) fail("Expected '$c'")
            index++
        }

        private fun fail(message: String): Nothing {
            throw JSONException(message + " at " + index)
        }
    }
}

class JSONArray internal constructor(items: List<Any?>) {
    constructor() : this(emptyList())
    private val values: List<Any> = items.map { wrapJsonValue(it) }

    fun length(): Int = values.size

    fun opt(index: Int): Any? {
        if (index < 0 || index >= values.size) return null
        return values[index]
    }

    fun optString(index: Int): String = stringValue(opt(index), "")

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    override fun toString(): String {
        val builder = StringBuilder()
        writeTo(builder, 0, 0)
        return builder.toString()
    }

    internal fun writeTo(builder: StringBuilder, indentFactor: Int, indent: Int) {
        builder.append('[')
        if (values.isEmpty()) {
            builder.append(']')
            return
        }
        val pretty = indentFactor > 0
        val next = if (pretty) indent + indentFactor else 0
        for (index in values.indices) {
            if (index > 0) builder.append(',')
            if (pretty) {
                builder.append('\n')
                appendIndent(builder, next)
            }
            writeJson(builder, values[index], indentFactor, next)
        }
        if (pretty) {
            builder.append('\n')
            appendIndent(builder, indent)
        }
        builder.append(']')
    }
}

private class JsonNumber(private val literal: String) : Number() {
    override fun toByte(): Byte = toInt().toByte()

    override fun toDouble(): Double = literal.toDouble()

    override fun toFloat(): Float = toDouble().toFloat()

    override fun toInt(): Int = toLong().toInt()

    override fun toLong(): Long = literal.toLongOrNull() ?: toDouble().toLong()

    override fun toShort(): Short = toInt().toShort()

    override fun toString(): String = literal
}

private fun wrapMap(map: Map<String, Any?>): LinkedHashMap<String, Any> {
    val dest = LinkedHashMap<String, Any>()
    for ((key, value) in map) {
        dest[key] = wrapJsonValue(value)
    }
    return dest
}

private fun wrapJsonValue(value: Any?): Any {
    return when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is JSONObject, is JSONArray, is String, is Boolean -> value
        is Number -> {
            if (value is Double && !value.isFinite()) throw JSONException("Non-finite number")
            if (value is Float && !value.isFinite()) throw JSONException("Non-finite number")
            value
        }
        is Map<*, *> -> JSONObject(stringKeyMap(value))
        is Iterable<*> -> JSONArray(value.toList())
        is Array<*> -> JSONArray(value.asList())
        else -> throw JSONException("Unsupported value type")
    }
}

private fun stringKeyMap(map: Map<*, *>): LinkedHashMap<String, Any?> {
    val nested = LinkedHashMap<String, Any?>()
    for ((key, value) in map) {
        if (key !is String) throw JSONException("Object key must be a string")
        nested[key] = value
    }
    return nested
}

private fun stringValue(value: Any?, fallback: String): String {
    if (value == null || value === JSONObject.NULL) return fallback
    return when (value) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> ""
    }
}

private fun writeJson(builder: StringBuilder, value: Any, indentFactor: Int, indent: Int) {
    when {
        value === JSONObject.NULL -> builder.append("null")
        value is String -> builder.append(quote(value))
        value is Boolean || value is Number -> builder.append(value.toString())
        value is JSONObject -> value.writeTo(builder, indentFactor, indent)
        value is JSONArray -> value.writeTo(builder, indentFactor, indent)
        else -> throw JSONException("Unsupported value")
    }
}

private fun quote(value: String): String {
    val builder = StringBuilder(value.length + 2)
    builder.append('"')
    for (c in value) {
        when (c) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> if (c.code < 0x20) appendHex(builder, c.code) else builder.append(c)
        }
    }
    builder.append('"')
    return builder.toString()
}

private fun appendHex(builder: StringBuilder, code: Int) {
    val hex = "0123456789abcdef"
    builder.append("\\u")
    builder.append(hex[(code shr 12) and 0xF])
    builder.append(hex[(code shr 8) and 0xF])
    builder.append(hex[(code shr 4) and 0xF])
    builder.append(hex[code and 0xF])
}

private fun appendIndent(builder: StringBuilder, count: Int) {
    repeat(count) { builder.append(' ') }
}
