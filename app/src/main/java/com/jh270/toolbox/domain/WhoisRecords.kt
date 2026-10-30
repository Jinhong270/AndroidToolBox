package com.jh270.toolbox.domain

import java.util.Locale

object WhoisRecords {
    private val registeredKeys = listOf(
        "creation date", "created", "created on", "registered", "registered on",
        "registration time", "domain registration date", "record created", "registered date"
    )
    private val expiryKeys = listOf(
        "registry expiry date", "registrar registration expiration date", "expiry date",
        "expiration date", "expiration time", "expire date", "expires on", "expires",
        "expire", "paid-till", "paid till", "renewal date"
    )
    private val changedKeys = listOf(
        "updated date", "last updated", "changed", "last modified", "modification date"
    )
    private val nameServerKeys = listOf(
        "name server", "nameserver", "nserver", "name servers", "nameservers"
    )
    private val statusKeys = listOf("domain status", "status", "state")
    private val roles = listOf(
        RoleSpec("registrant", "注册人", listOf("registrant")),
        RoleSpec("administrative", "管理联系人", listOf("admin", "administrative", "admin contact", "administrative contact")),
        RoleSpec("technical", "技术联系人", listOf("tech", "technical", "tech contact", "technical contact")),
        RoleSpec("billing", "账单联系人", listOf("billing", "billing contact"))
    )

    fun queryFor(host: String, asciiDomain: String): String {
        return when (host.lowercase(Locale.ROOT)) {
            "whois.jprs.jp" -> "$asciiDomain/e"
            "whois.denic.de" -> "-T dn,ace $asciiDomain"
            else -> asciiDomain
        }
    }

    fun ianaServer(text: String): String? {
        val value = firstOf(fields(text), listOf("whois", "refer")) ?: return null
        return RegistryHosts.whoisHost(value)
    }

    fun referralServer(text: String): String? {
        val value = firstOf(fields(text), listOf("registrar whois server", "referralserver", "referral server"))
            ?: return null
        return RegistryHosts.whoisHost(value)
    }

    fun isNotFound(text: String, asciiDomain: String): Boolean {
        if (isFree(text)) return true
        if (hasDomainIdentity(text, asciiDomain)) return false
        val sample = text.lowercase(Locale.ROOT)
        val patterns = listOf(
            "no match for",
            "no match found",
            "not found",
            "no data found",
            "no entries found",
            "no such domain",
            "domain not found",
            "nothing found",
            "the queried object does not exist",
            "object does not exist",
            "%error:101:",
            "no object found",
            "not registered"
        )
        return patterns.any { sample.contains(it) }
    }

    fun parse(domain: DomainNames.Parse.Ok, text: String, host: String): DomainRecord {
        val parsed = fields(text)
        val contacts = roles.mapNotNull { contact(parsed, it) }
        return DomainRecord(
            unicodeName = domain.unicodeName,
            asciiName = domain.asciiName,
            handle = firstOf(parsed, listOf("registry domain id", "domain id", "roid")),
            statuses = all(parsed, statusKeys).mapNotNull { DomainStatus.from(it) }.distinctBy { it.code },
            registrar = registrar(parsed),
            registeredAt = firstOf(parsed, registeredKeys),
            expiresAt = firstOf(parsed, expiryKeys),
            changedAt = firstOf(parsed, changedKeys),
            databaseUpdatedAt = firstOf(parsed, listOf("database updated")),
            nameservers = all(parsed, nameServerKeys).mapNotNull { nameServer(it) }.distinctBy { it.name.lowercase(Locale.ROOT) },
            dnssec = dnssec(firstOf(parsed, listOf("dnssec", "signing key"))),
            contacts = contacts,
            source = RecordSource.WHOIS,
            sourceUrl = "whois://$host",
            raw = text.trim()
        )
    }

    private fun isFree(text: String): Boolean {
        val free = Regex("""(?im)^\s*status:\s*(free|available)\s*$""").containsMatchIn(text)
        if (!free) return false
        val sample = text.lowercase(Locale.ROOT)
        return !sample.contains("name server") && !sample.contains("nserver:") && !sample.contains("creation date")
    }

    private fun hasDomainIdentity(text: String, asciiDomain: String): Boolean {
        val domain = asciiDomain.lowercase(Locale.ROOT)
        return text.lines().any { raw ->
            val line = raw.trim().lowercase(Locale.ROOT)
            val keyed = line.startsWith("domain name:") || line.startsWith("domain:") ||
                line.startsWith("[domain name]")
            keyed && line.contains(domain)
        }
    }

    private fun registrar(fields: List<Pair<String, String>>): RegistrarInfo? {
        val info = RegistrarInfo(
            name = firstOf(fields, listOf("registrar name", "sponsoring registrar", "registrar")),
            ianaId = firstOf(fields, listOf("registrar iana id")),
            url = firstOf(fields, listOf("registrar url")),
            abuseEmail = firstOf(fields, listOf("registrar abuse contact email")),
            abusePhone = firstOf(fields, listOf("registrar abuse contact phone"))
        )
        if (info.name == null && info.ianaId == null && info.url == null && info.abuseEmail == null && info.abusePhone == null) {
            return null
        }
        return info
    }

    private fun contact(fields: List<Pair<String, String>>, spec: RoleSpec): ContactInfo? {
        fun pick(suffixes: List<String>): String? {
            val keys = spec.prefixes.flatMap { prefix -> suffixes.map { suffix -> "$prefix $suffix" } }
            return firstOf(fields, keys)
        }
        val exactName = spec.prefixes.firstNotNullOfOrNull { prefix ->
            fields.firstOrNull { it.first == prefix && it.second.isNotBlank() }?.second
        }
        val name = pick(listOf("name")) ?: exactName
        val organization = pick(listOf("organization", "organisation", "org"))
        val email = pick(listOf("email", "e-mail"))
        val phone = pick(listOf("phone", "telephone"))
        val country = pick(listOf("country", "country code"))
        val rawValues = listOf(name, organization, email, phone, country)
        if (rawValues.all { it.isNullOrBlank() }) return null
        val redacted = rawValues.any { looksRedacted(it) }
        return ContactInfo(
            role = spec.role,
            roleLabel = spec.label,
            name = publishedText(name),
            organization = publishedText(organization),
            email = publishedText(email),
            phone = publishedText(phone),
            country = publishedText(country),
            redacted = redacted
        )
    }

    private fun nameServer(value: String): NameServerInfo? {
        val tokens = value.split(Regex("\\s+")).filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (tokens.isEmpty()) return null
        val name = tokens.first().trimEnd('.').trim()
        if (!name.contains('.') || name.startsWith("http", ignoreCase = true)) return null
        val addresses = tokens.drop(1).filter { token -> token.any { it.isDigit() } && !token.startsWith("http", ignoreCase = true) }
        return NameServerInfo(name, addresses)
    }

    private fun dnssec(value: String?): String? {
        val text = publishedText(value) ?: return null
        val normalized = text.lowercase(Locale.ROOT)
        return when {
            normalized.contains("unsigned") || normalized == "no" || normalized == "false" -> "未签名"
            normalized.contains("signed") || normalized == "yes" || normalized == "true" -> "已签名"
            else -> text
        }
    }

    private fun fields(text: String): List<Pair<String, String>> {
        val values = mutableListOf<Pair<String, String>>()
        var pending: String? = null
        text.lines().forEach { raw ->
            if (raw.isBlank()) {
                pending = null
                return@forEach
            }
            val update = Regex("""(?i)last update of (?:whois|rdap) database:\s*(\S+)""").find(raw)
            if (update != null) values += "database updated" to update.groupValues[1].trim('<', '>', ' ')
            var content = raw.trim().replace(Regex("""^[a-z]\.\s+"""), "")
            if (content.startsWith("%") || content.startsWith("#") || content.startsWith(">")) {
                pending = null
                return@forEach
            }
            val bracket = Regex("""^\[([^]]{1,80})]\s*(.*)$""").matchEntire(content)
            val colon = Regex("""^([^:]{1,80}):\s*(.*)$""").matchEntire(content)
            val key = when {
                bracket != null -> normalize(bracket.groupValues[1])
                colon != null && !colon.groupValues[1].contains('/') -> normalize(colon.groupValues[1])
                else -> null
            }
            val fieldValue = when {
                bracket != null -> bracket.groupValues[2].trim()
                colon != null && key != null -> colon.groupValues[2].trim()
                else -> null
            }
            if (key != null && fieldValue != null) {
                if (fieldValue.isNotEmpty()) values += key to fieldValue
                pending = if (fieldValue.isEmpty() && key in nameServerKeys) key else null
                return@forEach
            }
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && pending != null && content.isNotEmpty()) {
                values += pending!! to content
            } else {
                pending = null
            }
        }
        return values
    }

    private fun normalize(raw: String): String {
        return raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun firstOf(fields: List<Pair<String, String>>, keys: List<String>): String? {
        keys.forEach { key ->
            val value = fields.firstOrNull { it.first == key && it.second.isNotBlank() }?.second?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun all(fields: List<Pair<String, String>>, keys: List<String>): List<String> {
        return fields.filter { it.first in keys && it.second.isNotBlank() }.map { it.second.trim() }
    }

    private data class RoleSpec(val role: String, val label: String, val prefixes: List<String>)
}
