package com.jh270.toolbox

import com.jh270.toolbox.domain.DomainLookupClient
import com.jh270.toolbox.domain.HttpFetch
import com.jh270.toolbox.domain.LookupOutcome
import com.jh270.toolbox.domain.LookupTransport
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLookupClientTest {
    @Test
    fun usesLongestRdapZoneAndFollowsRegistrar() {
        val transport = ScriptedTransport()
        transport.onGet = { url ->
            when (url) {
                "https://data.iana.org/rdap/dns.json" -> HttpFetch(200, BOOTSTRAP, url)
                "https://rdap.verisign.com/com/v1/domain/example.com" -> HttpFetch(200, registryJson(), url)
                "https://rdap.registrar.example/rdap/domain/example.com" -> HttpFetch(200, registrarJson(), url)
                else -> error("unexpected $url")
            }
        }
        val client = DomainLookupClient(transport)
        val outcome = client.lookup("https://Example.com/path")
        assertTrue(outcome is LookupOutcome.Success)
        val record = (outcome as LookupOutcome.Success).record
        assertEquals("example.com", record.unicodeName)
        assertEquals("Thin Registrar", record.registrar?.name)
        assertEquals("376", record.registrar?.ianaId)
        assertEquals("abuse@example-registrar.test", record.registrar?.abuseEmail)
        assertEquals("Ada", record.contacts.single().name)
        assertEquals("1995-08-14T04:00:00Z", record.registeredAt)
        assertTrue(transport.whoisCalls.isEmpty())
        client.lookup("example.com")
        assertEquals(1, transport.gets.count { it == "https://data.iana.org/rdap/dns.json" })
    }

    @Test
    fun choosesCountryZoneOverParent() {
        val transport = ScriptedTransport()
        transport.onGet = { url ->
            when (url) {
                "https://data.iana.org/rdap/dns.json" -> HttpFetch(200, BOOTSTRAP, url)
                "https://rdap.nic.uk/co.uk/domain/example.co.uk" -> HttpFetch(200, simpleDomain("example.co.uk"), url)
                else -> error("unexpected $url")
            }
        }
        val outcome = DomainLookupClient(transport).lookup("example.co.uk")
        assertTrue(outcome is LookupOutcome.Success)
        assertEquals("example.co.uk", (outcome as LookupOutcome.Success).record.asciiName)
    }

    @Test
    fun rdap404DoesNotFallBackToWhois() {
        val transport = ScriptedTransport()
        transport.onGet = { url ->
            when (url) {
                "https://data.iana.org/rdap/dns.json" -> HttpFetch(200, BOOTSTRAP, url)
                else -> HttpFetch(404, """{"errorCode":404,"title":"Not Found"}""", url)
            }
        }
        val outcome = DomainLookupClient(transport).lookup("missing.com")
        assertTrue(outcome is LookupOutcome.NotFound)
        assertTrue(transport.whoisCalls.isEmpty())
    }

    @Test
    fun rateLimitStaysOnTheRegistry() {
        val transport = ScriptedTransport()
        transport.onGet = { url ->
            when (url) {
                "https://data.iana.org/rdap/dns.json" -> HttpFetch(200, BOOTSTRAP, url)
                else -> HttpFetch(429, "", url)
            }
        }
        val outcome = DomainLookupClient(transport).lookup("example.com")
        assertTrue(outcome is LookupOutcome.Failure)
        assertTrue(transport.whoisCalls.isEmpty())
    }

    @Test
    fun fallsBackToWhoisWhenRdapIsDown() {
        val transport = ScriptedTransport()
        transport.onGet = { url ->
            if (url == "https://data.iana.org/rdap/dns.json") HttpFetch(200, BOOTSTRAP, url)
            else throw IOException("down")
        }
        transport.onWhois = { host, query ->
            when (host) {
                "whois.iana.org" -> "whois: whois.example-registry.test\n"
                "whois.example-registry.test" -> """
                    Domain Name: example.dev
                    Registrar: Dev Registrar
                    Creation Date: 2020-01-02T00:00:00Z
                    Registry Expiry Date: 2026-01-02T00:00:00Z
                    Registrar WHOIS Server: whois.example-registrar.test
                    Name Server: ns1.example.dev
                """.trimIndent()
                "whois.example-registrar.test" -> """
                    Domain Name: example.dev
                    Registrant Name: Ada Lovelace
                    Registrant Email: ada@example.dev
                """.trimIndent()
                else -> error(host)
            }.also { assertEquals("example.dev", query) }
        }
        val outcome = DomainLookupClient(transport).lookup("example.dev")
        assertTrue(outcome is LookupOutcome.Success)
        val record = (outcome as LookupOutcome.Success).record
        assertEquals("Dev Registrar", record.registrar?.name)
        assertEquals("Ada Lovelace", record.contacts.single().name)
        assertEquals("2020-01-02T00:00:00Z", record.registeredAt)
        assertEquals("WHOIS", record.source.name)
    }

    @Test
    fun invalidDomainDoesNotTouchTheNetwork() {
        val transport = ScriptedTransport()
        val outcome = DomainLookupClient(transport).lookup("com")
        assertTrue(outcome is LookupOutcome.Invalid)
        assertTrue(transport.gets.isEmpty())
        assertTrue(transport.whoisCalls.isEmpty())
    }

    private class ScriptedTransport : LookupTransport {
        val gets = mutableListOf<String>()
        val whoisCalls = mutableListOf<Pair<String, String>>()
        var onGet: (String) -> HttpFetch = { error("unexpected $it") }
        var onWhois: (String, String) -> String = { host, _ -> error("unexpected $host") }

        override fun httpsGet(url: String, headers: Map<String, String>): HttpFetch {
            gets += url
            return onGet(url)
        }

        override fun whois(host: String, query: String): String {
            whoisCalls += host to query
            return onWhois(host, query)
        }
    }

    private fun registryJson(): String {
        return """
            {
              "objectClassName": "domain",
              "ldhName": "EXAMPLE.COM",
              "status": ["client transfer prohibited"],
              "events": [
                {"eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z"},
                {"eventAction": "expiration", "eventDate": "2024-08-13T04:00:00Z"}
              ],
              "entities": [
                {
                  "roles": ["registrar"],
                  "publicIds": [{"type": "IANA Registrar ID", "identifier": "376"}],
                  "vcardArray": ["vcard", [["fn", {}, "text", "Thin Registrar"]]]
                }
              ],
              "links": [
                {
                  "rel": "related",
                  "href": "https://rdap.registrar.example/rdap/domain/example.com",
                  "type": "application/rdap+json"
                }
              ]
            }
        """.trimIndent()
    }

    private fun registrarJson(): String {
        return """
            {
              "objectClassName": "domain",
              "ldhName": "EXAMPLE.COM",
              "entities": [
                {
                  "roles": ["registrar"],
                  "vcardArray": ["vcard", [
                    ["fn", {}, "text", "Example Registrar"],
                    ["url", {}, "text", "https://registrar.example"]
                  ]],
                  "entities": [
                    {
                      "roles": ["abuse"],
                      "vcardArray": ["vcard", [["email", {}, "text", "abuse@example-registrar.test"]]]
                    }
                  ]
                },
                {
                  "roles": ["registrant"],
                  "vcardArray": ["vcard", [
                    ["fn", {}, "text", "Ada"],
                    ["email", {}, "text", "ada@example.com"]
                  ]]
                }
              ]
            }
        """.trimIndent()
    }

    private fun simpleDomain(name: String): String {
        return """
            {"objectClassName":"domain","ldhName":"$name","status":["active"]}
        """.trimIndent()
    }

    private companion object {
        const val BOOTSTRAP = """
            {
              "services": [
                [["uk"], ["https://rdap.nic.uk/"]],
                [["co.uk"], ["https://rdap.nic.uk/co.uk/"]],
                [["com", "dev"], ["https://rdap.verisign.com/com/v1"]],
                [["org"], ["http://insecure.example/rdap/"]]
              ]
            }
        """
    }
}
