package com.jh270.toolbox.domain

import java.net.URI
import java.util.Locale

enum class RecordSource { RDAP, WHOIS }

data class DomainStatus(val code: String, val label: String) {
    companion object {
        private val labels = mapOf(
            "ok" to "正常",
            "active" to "正常",
            "inactive" to "未激活",
            "associated" to "已关联",
            "locked" to "已锁定",
            "client delete prohibited" to "注册商禁止删除",
            "client transfer prohibited" to "注册商禁止转移",
            "client update prohibited" to "注册商禁止更新",
            "client renew prohibited" to "注册商禁止续费",
            "client hold" to "注册商暂停解析",
            "server delete prohibited" to "注册局禁止删除",
            "server transfer prohibited" to "注册局禁止转移",
            "server update prohibited" to "注册局禁止更新",
            "server renew prohibited" to "注册局禁止续费",
            "server hold" to "注册局暂停解析",
            "pending delete" to "等待删除",
            "pending transfer" to "等待转移",
            "pending update" to "等待更新",
            "pending create" to "等待创建",
            "pending renew" to "等待续费",
            "pending restore" to "等待恢复",
            "redemption period" to "赎回期",
            "add period" to "新注册宽限期",
            "auto renew period" to "自动续费宽限期",
            "renew period" to "续费宽限期",
            "transfer period" to "转移宽限期"
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
    val roleLabel: String,
    val name: String?,
    val organization: String?,
    val email: String?,
    val phone: String?,
    val country: String?,
    val redacted: Boolean
) {
    val visible: Boolean
        get() = listOf(name, organization, email, phone, country).any { !it.isNullOrBlank() }

    fun summary(): String {
        if (!visible) return "已隐藏"
        return listOfNotNull(name, organization, email, phone, country?.let { "国家/地区 $it" })
            .joinToString("，")
    }
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

    fun summary(now: Long = System.currentTimeMillis()): String {
        val lines = mutableListOf<String>()
        lines += "域名: $unicodeName"
        if (!asciiName.equals(unicodeName, ignoreCase = true)) lines += "Punycode: $asciiName"
        if (statuses.isNotEmpty()) lines += "状态: " + statuses.joinToString("；") { it.label }
        registrar?.name?.let { lines += "注册商: $it" }
        registrar?.ianaId?.let { lines += "注册商编号: $it" }
        registrar?.url?.let { lines += "注册商网址: $it" }
        registrar?.abuseEmail?.let { lines += "滥用联系邮箱: $it" }
        registrar?.abusePhone?.let { lines += "滥用联系电话: $it" }
        handle?.let { lines += "注册编号: $it" }
        registeredAt?.let { lines += "注册时间: ${DomainDates.format(it)}" }
        expiresAt?.let { rawDate ->
            val hint = DomainDates.expiryHint(rawDate, now)?.text
            val suffix = if (hint == null) "" else "（$hint）"
            lines += "到期时间: ${DomainDates.format(rawDate)}$suffix"
        }
        changedAt?.let { lines += "最近变更: ${DomainDates.format(it)}" }
        databaseUpdatedAt?.let { lines += "数据更新: ${DomainDates.format(it)}" }
        dnssec?.let { lines += "DNSSEC: $it" }
        if (nameservers.isEmpty()) {
            lines += "域名服务器: 未提供"
        } else {
            lines += "域名服务器:"
            nameservers.forEach { server ->
                val addresses = if (server.addresses.isEmpty()) "" else "  ${server.addresses.joinToString(" ")}"
                lines += "  ${server.name}$addresses"
            }
        }
        if (contacts.isEmpty()) {
            lines += "联系人: 未公开"
        } else {
            contacts.forEach { contact -> lines += "${contact.roleLabel}: ${contact.summary()}" }
        }
        lines += "来源: ${sourceLabel()}"
        return lines.joinToString("\n")
    }
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
