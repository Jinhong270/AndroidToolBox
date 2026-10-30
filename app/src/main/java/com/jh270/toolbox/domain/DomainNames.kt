package com.jh270.toolbox.domain

import java.net.IDN
import java.net.URI
import java.util.Locale

object DomainNames {
    sealed class Parse {
        data class Ok(val unicodeName: String, val asciiName: String) : Parse()
        data class Invalid(val message: String) : Parse()
    }

    fun parse(raw: String): Parse {
        val host = extractHost(raw)
        if (host.isBlank()) return Parse.Invalid("请输入域名")
        if (host.any { it.isWhitespace() }) return Parse.Invalid("域名不能包含空格")
        if (isIpAddress(host)) return Parse.Invalid("请输入域名，IP 地址不在查询范围内")
        val ascii = try {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).trim().trimEnd('.').lowercase(Locale.ROOT)
        } catch (e: IllegalArgumentException) {
            return Parse.Invalid("这个域名包含无法使用的字符")
        }
        if (ascii.isEmpty()) return Parse.Invalid("请输入域名")
        if (ascii.length > 253) return Parse.Invalid("域名过长")
        val labels = ascii.split('.')
        if (labels.size < 2) return Parse.Invalid("请输入完整域名，例如 example.com")
        if (labels.any { !validLabel(it) }) return Parse.Invalid("这个域名包含无法使用的字符")
        val unicode = IDN.toUnicode(ascii, IDN.USE_STD3_ASCII_RULES).trim().trimEnd('.').lowercase(Locale.ROOT)
        return Parse.Ok(unicode, ascii)
    }

    private fun validLabel(label: String): Boolean {
        if (label.length !in 1..63) return false
        if (label.startsWith('-') || label.endsWith('-')) return false
        return label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
    }

    private fun extractHost(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("://") || trimmed.startsWith("//")) {
            val withScheme = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
            val host = runCatching { URI(withScheme).host }.getOrNull()
            if (!host.isNullOrBlank()) return host.trim().trimEnd('.')
        }
        var text = trimmed.substringBefore('#').substringBefore('?').substringBefore('/')
        text = text.substringAfter('@')
        if (text.startsWith("[")) return text
        val colon = text.lastIndexOf(':')
        if (colon > 0 && text.indexOf(':') == colon && text.substring(colon + 1).all { it.isDigit() }) {
            text = text.substring(0, colon)
        }
        return text.trim().trimEnd('.')
    }

    private fun isIpAddress(host: String): Boolean {
        if (host.startsWith("[")) return true
        if (host.count { it == ':' } >= 2) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val number = part.toIntOrNull()
            part.isNotEmpty() && part.all { it.isDigit() } && number != null && number in 0..255
        }
    }
}
