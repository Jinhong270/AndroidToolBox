package com.jh270.toolbox.domain

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class RdapBootstrap private constructor(
    private val zones: Map<String, List<String>>
) {
    fun basesFor(asciiDomain: String): List<String> {
        val labels = asciiDomain.lowercase(Locale.ROOT).split('.')
        var best: List<String> = emptyList()
        var bestLength = -1
        for (start in labels.indices) {
            val suffix = labels.subList(start, labels.size).joinToString(".")
            val bases = zones[suffix] ?: continue
            val length = labels.size - start
            if (length > bestLength) {
                bestLength = length
                best = bases
            }
        }
        return best
    }

    companion object {
        const val IANA_DNS_URL = "https://data.iana.org/rdap/dns.json"

        fun parse(json: String): RdapBootstrap {
            val root = JSONObject(json.trim().trimStart('\uFEFF'))
            val services = root.optJSONArray("services") ?: JSONArray()
            val zones = linkedMapOf<String, List<String>>()
            for (index in 0 until services.length()) {
                val entry = services.optJSONArray(index) ?: continue
                if (entry.length() < 2) continue
                val names = entry.optJSONArray(0) ?: continue
                val urls = entry.optJSONArray(1) ?: continue
                val bases = mutableListOf<String>()
                for (urlIndex in 0 until urls.length()) {
                    val url = urls.optString(urlIndex).trim()
                    if (RegistryHosts.isAllowedRegistryUrl(url)) bases += url
                }
                if (bases.isEmpty()) continue
                for (nameIndex in 0 until names.length()) {
                    val zone = names.optString(nameIndex).trim().trim('.').lowercase(Locale.ROOT)
                    if (zone.isEmpty() || zone.contains('*') || zone.contains(' ') || zone.contains('/')) continue
                    zones.putIfAbsent(zone, bases.toList())
                }
            }
            return RdapBootstrap(zones)
        }

        fun domainUrl(base: String, asciiDomain: String): String {
            val trimmed = base.trim()
            val prefix = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return prefix + "domain/" + asciiDomain
        }
    }
}
