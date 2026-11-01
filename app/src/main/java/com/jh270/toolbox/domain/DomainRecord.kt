package com.jh270.toolbox.domain

import java.net.URI
import java.util.Locale

enum class RecordSource { RDAP, WHOIS }

data class DomainStatus(val code: String, val label: String) {
    companion object {
        private val labels = mapOf(
            "ok" to "Active",
            "active" to "Active",
            "inactive" to "Inactive",
            "associated" to "Associated",
            "locked" to "Locked",
            "client delete prohibited" to "Registrar delete prohibited",
            "client transfer prohibited" to "Registrar transfer prohibited",
            "client update prohibited" to "Registrar update prohibited",
            "client renew prohibited" to "Registrar renew prohibited",
            "client hold" to "Registrar hold",
            "server delete prohibited" to "Registry delete prohibited",
            "server transfer prohibited" to "Registry transfer prohibited",
            "server update prohibited" to "Registry update prohibited",
            "server renew prohibited" to "Registry renew prohibited",
            "server hold" to "Registry hold",
            "pending delete" to "Pending delete",
            "pending transfer" to "Pending transfer",
            "pending update" to "Pending update",
            "pending create" to "Pending create",
            "pending renew" to "Pending renew",
            "pending restore" to "Pending restore",
            "redemption period" to "Redemption period",
            "add period" to "Add grace period",
            "auto renew period" to "Auto-renew grace period",
            "renew period" to "Renew grace period",
            "transfer period" to "Transfer grace period"
        )

        fun from(raw: String): DomainStatus? {
            val token = raw.trim()
                .substringBefore(" http")
                .substringBefore(" https")
                .substringBefore(" (")
                .trim()
            if (token.isEmpty() || token.startsWith("http", ignoreCase = true)) return null
            val spaced = token
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
                .trim()
            if (spaced.isEmpty()) return null
            return DomainStatus(spaced, labels[spaced] ?: spaced)
        }
    }
}

data class RegistrarInfo(
    val name: String?,
    val ianaId: String?,
    val url: String?,
    val abuseEmail: String?,
    val abusePhone: String?
)

data class NameServerInfo(
    val name: String,
    val addresses: List<String>
)

data class ContactInfo(
    val role: String,
    val name: String?,
    val organization: String?,
    val email: String?,
    val phone: String?,
    val country: String?,
    val redacted: Boolean
) {
    val visible: Boolean
        get() = listOf(name, organization, email, phone, country).any { !it.isNullOrBlank() }
}

data class DomainRecord(
    val unicodeName: String,
    val asciiName: String,
    val handle: String?,
    val statuses: List<DomainStatus>,
    val registrar: RegistrarInfo?,
    val registeredAt: String?,
    val expiresAt: String?,
    val changedAt: String?,
    val databaseUpdatedAt: String?,
    val nameservers: List<NameServerInfo>,
    val dnssec: String?,
    val contacts: List<ContactInfo>,
    val source: RecordSource,
    val sourceUrl: String,
    val raw: String
) {
    val sparse: Boolean
        get() = registrar?.name.isNullOrBlank() && registeredAt.isNullOrBlank() && nameservers.isEmpty()
}

fun DomainRecord.sourceLabel(): String {
    val hosts = sourceUrl.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when (source) {
            RecordSource.RDAP -> runCatching { URI(line).host }.getOrNull() ?: line
            RecordSource.WHOIS -> line.removePrefix("whois://")
        }
    }.distinct()
    val prefix = if (source == RecordSource.RDAP) "RDAP" else "WHOIS"
    if (hosts.isEmpty()) return prefix
    return prefix + " · " + hosts.joinToString(" → ")
}

fun mergeRecords(primary: DomainRecord, extra: DomainRecord): DomainRecord {
    return primary.copy(
        handle = primary.handle ?: extra.handle,
        statuses = primary.statuses.ifEmpty { extra.statuses },
        registrar = mergeRegistrar(primary.registrar, extra.registrar),
        registeredAt = primary.registeredAt ?: extra.registeredAt,
        expiresAt = primary.expiresAt ?: extra.expiresAt,
        changedAt = primary.changedAt ?: extra.changedAt,
        databaseUpdatedAt = primary.databaseUpdatedAt ?: extra.databaseUpdatedAt,
        nameservers = primary.nameservers.ifEmpty { extra.nameservers },
        dnssec = primary.dnssec ?: extra.dnssec,
        contacts = extra.contacts.ifEmpty { primary.contacts },
        sourceUrl = listOf(primary.sourceUrl, extra.sourceUrl)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n"),
        raw = listOf(primary.raw.trim(), extra.raw.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    )
}

fun mergeRegistrar(primary: RegistrarInfo?, extra: RegistrarInfo?): RegistrarInfo? {
    if (primary == null) return extra
    if (extra == null) return primary
    return RegistrarInfo(
        name = primary.name ?: extra.name,
        ianaId = primary.ianaId ?: extra.ianaId,
        url = extra.url ?: primary.url,
        abuseEmail = extra.abuseEmail ?: primary.abuseEmail,
        abusePhone = extra.abusePhone ?: primary.abusePhone
    )
}

fun looksRedacted(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val text = value.trim().lowercase(Locale.ROOT)
    return text == "redacted" ||
        text == "redacted for privacy" ||
        text.contains("redacted for privacy") ||
        text == "data redacted" ||
        text == "not disclosed" ||
        text == "privacy redacted" ||
        text.contains("statutory masking") ||
        text == "hidden" ||
        text == "withheld for privacy" ||
        text == "gdpr masked" ||
        text == "not available"
}

fun publishedText(value: String?): String? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (looksRedacted(text)) return null
    return text
}
