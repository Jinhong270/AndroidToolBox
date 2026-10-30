package com.jh270.toolbox.domain

import java.net.URI
import java.util.Locale

object RegistryHosts {
    fun isAllowedRegistryUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.rawUserInfo.isNullOrBlank()) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.trim()?.trimEnd('.') ?: return false
        return isPublicHost(host)
    }

    fun isPublicHost(host: String): Boolean {
        val normalized = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalized.isEmpty() || normalized.length > 253) return false
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized.endsWith(".local")) return false
        if (normalized.endsWith(".internal") || normalized.endsWith(".home") || normalized.endsWith(".lan")) return false
        if (isIpv4(normalized)) return isPublicIpv4(normalized)
        if (normalized.contains(':')) return false
        if (!normalized.matches(Regex("[a-z0-9.-]+"))) return false
        if (normalized.contains("..") || normalized.startsWith('.')) return false
        val labels = normalized.split('.')
        if (labels.size < 2) return false
        return labels.all { label ->
            label.isNotEmpty() && label.length <= 63 && !label.startsWith('-') && !label.endsWith('-')
        }
    }

    fun whoisHost(value: String): String? {
        var text = value.trim()
        if (text.isEmpty()) return null
        text = text.removePrefix("whois://").removePrefix("rwhois://").removePrefix("WHOIS://")
        text = text.substringBefore('/').substringBefore(' ').trim()
        if (text.endsWith(":43")) text = text.removeSuffix(":43")
        text = text.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (!isPublicHost(text)) return null
        return text
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }
    }

    private fun isPublicIpv4(host: String): Boolean {
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val first = parts[0]
        val second = parts[1]
        if (first == 0 || first == 10 || first == 127) return false
        if (first == 169 && second == 254) return false
        if (first == 172 && second in 16..31) return false
        if (first == 192 && second == 168) return false
        if (first == 100 && second in 64..127) return false
        if (first >= 224) return false
        return true
    }
}
