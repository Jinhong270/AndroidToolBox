package com.jh270.toolbox.domain

import java.net.URI
import java.util.Locale
import com.jh270.toolbox.json.JSONArray
import com.jh270.toolbox.json.JSONException
import com.jh270.toolbox.json.JSONObject

object RdapRecords {
    sealed class Parsed {
        data class Found(val record: DomainRecord, val relatedUrl: String?) : Parsed()
        data object Missing : Parsed()
        data object Rejected : Parsed()
    }

    fun parse(body: String, sourceUrl: String, domain: DomainNames.Parse.Ok): Parsed {
        val payload = body.trim().trimStart('\uFEFF')
        if (payload.isEmpty()) return Parsed.Rejected
        return try {
            parseObject(JSONObject(payload), sourceUrl, domain, payload)
        } catch (e: JSONException) {
            Parsed.Rejected
        }
    }

    private fun parseObject(
        root: JSONObject,
        sourceUrl: String,
        domain: DomainNames.Parse.Ok,
        payload: String
    ): Parsed {
        val errorCode = errorCode(root)
        if (errorCode == "404") return Parsed.Missing
        if (errorCode.isNotEmpty()) return Parsed.Rejected
        val kind = root.optString("objectClassName")
        if (kind.equals("error", ignoreCase = true)) return Parsed.Rejected
        if (kind.isNotBlank() && !kind.equals("domain", ignoreCase = true)) return Parsed.Rejected
        val ldh = root.optString("ldhName").trim().trimEnd('.').lowercase(Locale.ROOT)
        val unicode = root.optString("unicodeName").trim().trimEnd('.').lowercase(Locale.ROOT)
        if (ldh.isNotEmpty() && ldh != domain.asciiName && unicode != domain.unicodeName && unicode != domain.asciiName) {
            return Parsed.Rejected
        }
        if (ldh.isEmpty() && unicode.isEmpty() && root.optJSONArray("nameservers") == null && root.optJSONArray("events") == null) {
            return Parsed.Rejected
        }
        val events = events(root)
        val entities = topEntities(root)
        var registrar: RegistrarInfo? = null
        val contacts = mutableListOf<ContactInfo>()
        entities.forEach { entity ->
            val roles = roles(entity)
            if ("registrar" in roles) {
                registrar = mergeRegistrar(registrar, registrarInfo(entity))
            }
            contactRole(roles)?.let { role ->
                contactInfo(entity, role.first, role.second)?.let { contacts += it }
            }
        }
        val record = DomainRecord(
            unicodeName = domain.unicodeName,
            asciiName = domain.asciiName,
            handle = root.optString("handle").trim().ifEmpty { null },
            statuses = statuses(root.optJSONArray("status")),
            registrar = registrar,
            registeredAt = eventDate(events, "registration", "registered"),
            expiresAt = eventDate(events, "expiration", "registrar expiration"),
            changedAt = eventDate(events, "last changed", "last changed by registrar"),
            databaseUpdatedAt = eventDate(events, "last update of rdap database"),
            nameservers = nameServers(root.optJSONArray("nameservers")),
            dnssec = dnssec(root.optJSONObject("secureDNS")),
            contacts = contacts,
            source = RecordSource.RDAP,
            sourceUrl = sourceUrl,
            raw = pretty(payload)
        )
        return Parsed.Found(record, relatedUrl(root, sourceUrl))
    }

    private fun errorCode(root: JSONObject): String {
        if (!root.has("errorCode")) return ""
        return when (val value = root.opt("errorCode")) {
            is Number -> value.toInt().toString()
            is String -> value.trim()
            else -> ""
        }
    }

    private fun events(root: JSONObject): List<Pair<String, String>> {
        val array = root.optJSONArray("events") ?: return emptyList()
        val values = mutableListOf<Pair<String, String>>()
        for (index in 0 until array.length()) {
            val event = array.optJSONObject(index) ?: continue
            val action = event.optString("eventAction").trim().lowercase(Locale.ROOT)
            val date = event.optString("eventDate").trim()
            if (action.isNotEmpty() && date.isNotEmpty()) values += action to date
        }
        return values
    }

    private fun eventDate(events: List<Pair<String, String>>, vararg actions: String): String? {
        actions.forEach { action ->
            val wanted = action.lowercase(Locale.ROOT)
            val date = events.firstOrNull { it.first == wanted }?.second
            if (date != null) return date
        }
        return null
    }

    private fun statuses(array: JSONArray?): List<DomainStatus> {
        if (array == null) return emptyList()
        val values = mutableListOf<DomainStatus>()
        for (index in 0 until array.length()) {
            val status = DomainStatus.from(array.optString(index)) ?: continue
            if (values.none { it.code == status.code }) values += status
        }
        return values
    }

    private fun nameServers(array: JSONArray?): List<NameServerInfo> {
        if (array == null) return emptyList()
        val values = mutableListOf<NameServerInfo>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("unicodeName").trim().ifEmpty { item.optString("ldhName").trim() }
            if (name.isEmpty()) continue
            val addresses = mutableListOf<String>()
            val ip = item.optJSONObject("ipAddresses")
            if (ip != null) {
                addresses += stringList(ip.optJSONArray("v4"))
                addresses += stringList(ip.optJSONArray("v6"))
            }
            if (values.none { it.name.equals(name, ignoreCase = true) }) {
                values += NameServerInfo(name.trimEnd('.'), addresses)
            }
        }
        return values
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) values += value
        }
        return values
    }

    private fun dnssec(secure: JSONObject?): String? {
        if (secure == null) return null
        val signed = secure.optBoolean("delegationSigned", false) ||
            (secure.optJSONArray("dsData")?.length() ?: 0) > 0
        if (signed) return "已签名"
        if (secure.has("delegationSigned")) return "未签名"
        return null
    }

    private fun topEntities(root: JSONObject): List<JSONObject> {
        val array = root.optJSONArray("entities") ?: return emptyList()
        val values = mutableListOf<JSONObject>()
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { values += it }
        }
        return values
    }

    private fun roles(entity: JSONObject): Set<String> {
        val array = entity.optJSONArray("roles") ?: return emptySet()
        val values = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val role = array.optString(index).trim().lowercase(Locale.ROOT)
            if (role.isNotEmpty()) values += role
        }
        return values
    }

    private fun contactRole(roles: Set<String>): Pair<String, String>? {
        val order = listOf(
            "registrant" to "注册人",
            "administrative" to "管理联系人",
            "technical" to "技术联系人",
            "billing" to "账单联系人"
        )
        return order.firstOrNull { it.first in roles }
    }

    private fun registrarInfo(entity: JSONObject): RegistrarInfo? {
        val fields = vcard(entity)
        var abuseEmail: String? = null
        var abusePhone: String? = null
        val nested = entity.optJSONArray("entities")
        if (nested != null) {
            for (index in 0 until nested.length()) {
                val child = nested.optJSONObject(index) ?: continue
                if ("abuse" !in roles(child)) continue
                val childFields = vcard(child)
                abuseEmail = abuseEmail ?: fieldText(childFields, "email")
                abusePhone = abusePhone ?: fieldText(childFields, "tel")
            }
        }
        val info = RegistrarInfo(
            name = fieldText(fields, "fn") ?: fieldText(fields, "org"),
            ianaId = ianaId(entity),
            url = fieldText(fields, "url"),
            abuseEmail = abuseEmail,
            abusePhone = abusePhone
        )
        if (info.name == null && info.ianaId == null && info.url == null && info.abuseEmail == null && info.abusePhone == null) {
            return null
        }
        return info
    }

    private fun ianaId(entity: JSONObject): String? {
        val ids = entity.optJSONArray("publicIds") ?: return null
        for (index in 0 until ids.length()) {
            val item = ids.optJSONObject(index) ?: continue
            val type = item.optString("type").lowercase(Locale.ROOT)
            if (!type.contains("iana") || !type.contains("registrar")) continue
            val identifier = item.optString("identifier").trim()
            if (identifier.isNotEmpty()) return identifier
        }
        return null
    }

    private fun contactInfo(entity: JSONObject, role: String, label: String): ContactInfo? {
        val fields = vcard(entity)
        val name = fieldText(fields, "fn")
        val organization = fieldText(fields, "org")
        val email = fieldText(fields, "email")
        val phone = fieldText(fields, "tel")
        val country = country(fields)
        val redacted = fields.any { looksRedacted(plain(it.value)) }
        if (name == null && organization == null && email == null && phone == null && country == null && !redacted) {
            return null
        }
        val shownOrganization = organization?.takeUnless { it.equals(name, ignoreCase = true) }
        return ContactInfo(role, label, name, shownOrganization, email, phone, country, redacted)
    }

    private fun country(fields: List<VcardField>): String? {
        val address = fields.firstOrNull { it.name == "adr" } ?: return null
        val array = address.value as? JSONArray ?: return null
        if (array.length() < 7) return null
        return publishedText(array.optString(6))
    }

    private data class VcardField(val name: String, val value: Any?)

    private fun vcard(entity: JSONObject): List<VcardField> {
        val card = entity.optJSONArray("vcardArray") ?: return emptyList()
        if (card.length() < 2) return emptyList()
        val fields = card.optJSONArray(1) ?: return emptyList()
        val values = mutableListOf<VcardField>()
        for (index in 0 until fields.length()) {
            val field = fields.optJSONArray(index) ?: continue
            if (field.length() < 4) continue
            val name = field.optString(0).trim().lowercase(Locale.ROOT)
            if (name.isEmpty()) continue
            values += VcardField(name, field.opt(3))
        }
        return values
    }

    private fun fieldText(fields: List<VcardField>, name: String): String? {
        return fields.filter { it.name == name }.firstNotNullOfOrNull { publishedText(plain(it.value)) }
    }

    private fun plain(value: Any?): String? {
        return when (value) {
            null, JSONObject.NULL -> null
            is String -> value.trim().ifEmpty { null }
            is JSONArray -> {
                val parts = mutableListOf<String>()
                for (index in 0 until value.length()) {
                    plain(value.opt(index))?.let { parts += it }
                }
                parts.joinToString(" ").ifEmpty { null }
            }
            else -> null
        }
    }

    private fun relatedUrl(root: JSONObject, sourceUrl: String): String? {
        val links = root.optJSONArray("links") ?: return null
        for (index in 0 until links.length()) {
            val link = links.optJSONObject(index) ?: continue
            if (!link.optString("rel").equals("related", ignoreCase = true)) continue
            val href = link.optString("href").trim()
            if (!RegistryHosts.isAllowedRegistryUrl(href)) continue
            val type = link.optString("type")
            if (type.isNotEmpty() && !type.contains("json", ignoreCase = true) && !type.contains("rdap", ignoreCase = true)) {
                continue
            }
            val path = runCatching { URI(href).path.orEmpty() }.getOrDefault("")
            if (!path.contains("/domain/", ignoreCase = true)) continue
            if (href.trimEnd('/') == sourceUrl.trimEnd('/')) continue
            return href
        }
        return null
    }

    private fun pretty(payload: String): String {
        return runCatching { JSONObject(payload).toString(2) }.getOrDefault(payload)
    }
}
