package com.jh270.toolbox.domain

import java.io.IOException

sealed class LookupOutcome {
    data class Success(val record: DomainRecord) : LookupOutcome()
    data class NotFound(val domain: String) : LookupOutcome()
    data class Invalid(val message: String) : LookupOutcome()
    data class Failure(val message: String) : LookupOutcome()
}

class DomainLookupClient(
    private val transport: LookupTransport,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gate = Any()
    private var bootstrap: RdapBootstrap? = null
    private var bootstrapAt = 0L

    fun lookup(raw: String): LookupOutcome {
        val domain = when (val parsed = DomainNames.parse(raw)) {
            is DomainNames.Parse.Invalid -> return LookupOutcome.Invalid(parsed.message)
            is DomainNames.Parse.Ok -> parsed
        }
        val bases = try {
            loadBootstrap()?.basesFor(domain.asciiName).orEmpty()
        } catch (e: IOException) {
            emptyList()
        }
        if (bases.isNotEmpty()) {
            val rdap = lookupRdap(domain, bases)
            if (rdap != null) return rdap
        }
        return lookupWhois(domain)
    }

    private fun loadBootstrap(): RdapBootstrap? {
        synchronized(gate) {
            val cached = bootstrap
            if (cached != null && clock() - bootstrapAt < BOOTSTRAP_TTL_MS) return cached
        }
        val fetched = try {
            val response = transport.httpsGet(RdapBootstrap.IANA_DNS_URL, emptyMap())
            if (response.code !in 200..299) null else RdapBootstrap.parse(response.body)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        if (fetched != null) {
            synchronized(gate) {
                bootstrap = fetched
                bootstrapAt = clock()
            }
            return fetched
        }
        return synchronized(gate) { bootstrap }
    }

    private fun lookupRdap(domain: DomainNames.Parse.Ok, bases: List<String>): LookupOutcome? {
        var rateLimited = false
        var forbidden = false
        bases.forEach { base ->
            val url = RdapBootstrap.domainUrl(base, domain.asciiName)
            val response = try {
                transport.httpsGet(url, emptyMap())
            } catch (e: IOException) {
                return@forEach
            }
            when (response.code) {
                404 -> {
                    when (val missing = RdapRecords.parse(response.body, response.finalUrl, domain)) {
                        is RdapRecords.Parsed.Found -> return LookupOutcome.Success(followRelated(domain, missing))
                        is RdapRecords.Parsed.Missing -> return LookupOutcome.NotFound(domain.unicodeName)
                        is RdapRecords.Parsed.Rejected -> {
                            if (response.body.isBlank()) return LookupOutcome.NotFound(domain.unicodeName)
                            return@forEach
                        }
                    }
                }
                429 -> {
                    rateLimited = true
                    return@forEach
                }
                401, 403 -> {
                    forbidden = true
                    return@forEach
                }
            }
            if (response.code !in 200..299) return@forEach
            when (val parsed = RdapRecords.parse(response.body, response.finalUrl, domain)) {
                is RdapRecords.Parsed.Found -> return LookupOutcome.Success(followRelated(domain, parsed))
                is RdapRecords.Parsed.Missing -> return LookupOutcome.NotFound(domain.unicodeName)
                is RdapRecords.Parsed.Rejected -> return@forEach
            }
        }
        if (rateLimited) return LookupOutcome.Failure("注册局限制了查询频率，请稍后再试")
        if (forbidden) return LookupOutcome.Failure("注册局拒绝了这次查询")
        return null
    }

    private fun followRelated(domain: DomainNames.Parse.Ok, found: RdapRecords.Parsed.Found): DomainRecord {
        val related = found.relatedUrl ?: return found.record
        val response = try {
            transport.httpsGet(related, emptyMap())
        } catch (e: IOException) {
            return found.record
        }
        if (response.code !in 200..299) return found.record
        val extra = RdapRecords.parse(response.body, response.finalUrl, domain)
        if (extra !is RdapRecords.Parsed.Found) return found.record
        if (extra.record.sourceUrl.trimEnd('/') == found.record.sourceUrl.trimEnd('/')) return found.record
        return mergeRecords(found.record, extra.record)
    }

    private fun lookupWhois(domain: DomainNames.Parse.Ok): LookupOutcome {
        val iana = try {
            transport.whois("whois.iana.org", domain.asciiName)
        } catch (e: IOException) {
            return LookupOutcome.Failure("无法连接 IANA 的 WHOIS 服务，请检查网络")
        }
        val server = WhoisRecords.ianaServer(iana)
            ?: return LookupOutcome.Failure("IANA 没有公布这个后缀的 WHOIS 服务器")
        return queryRegistry(domain, server)
    }

    private fun queryRegistry(domain: DomainNames.Parse.Ok, server: String): LookupOutcome {
        val text = try {
            transport.whois(server, WhoisRecords.queryFor(server, domain.asciiName))
        } catch (e: IOException) {
            return LookupOutcome.Failure("无法连接 $server")
        }
        if (WhoisRecords.isNotFound(text, domain.asciiName)) return LookupOutcome.NotFound(domain.unicodeName)
        val registry = WhoisRecords.parse(domain, text, server)
        val referral = WhoisRecords.referralServer(text)
        if (referral == null || referral == server || referral == "whois.iana.org") {
            return LookupOutcome.Success(registry)
        }
        val registrarText = try {
            transport.whois(referral, WhoisRecords.queryFor(referral, domain.asciiName))
        } catch (e: IOException) {
            return LookupOutcome.Success(registry)
        }
        if (WhoisRecords.isNotFound(registrarText, domain.asciiName)) return LookupOutcome.Success(registry)
        val registrar = WhoisRecords.parse(domain, registrarText, referral)
        return LookupOutcome.Success(mergeRecords(registry, registrar))
    }

    private companion object {
        const val BOOTSTRAP_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
