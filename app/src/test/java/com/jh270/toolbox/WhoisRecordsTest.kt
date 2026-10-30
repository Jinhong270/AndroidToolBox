package com.jh270.toolbox

import com.jh270.toolbox.domain.DomainNames
import com.jh270.toolbox.domain.WhoisRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoisRecordsTest {
    @Test
    fun readsIanaReferralAndThinRecord() {
        val iana = """
            domain:       COM
            organisation: VeriSign Global Registry Services
            whois:        whois.verisign-grs.com
            status:       ACTIVE
        """.trimIndent()
        assertEquals("whois.verisign-grs.com", WhoisRecords.ianaServer(iana))
        val thin = """
            Domain Name: EXAMPLE.COM
            Registry Domain ID: 2336799_DOMAIN_COM-VRSN
            Registrar WHOIS Server: whois.godaddy.com
            Registrar URL: https://www.godaddy.com
            Updated Date: 2023-08-14T07:01:38Z
            Creation Date: 1995-08-14T04:00:00Z
            Registry Expiry Date: 2024-08-13T04:00:00Z
            Registrar: Example Registrar
            Registrar IANA ID: 146
            Registrar Abuse Contact Email: abuse@example-registrar.com
            Domain Status: clientTransferProhibited https://icann.org/epp#clientTransferProhibited
            Name Server: A.IANA-SERVERS.NET
            Name Server: B.IANA-SERVERS.NET
            DNSSEC: signedDelegation
            >>> Last update of whois database: 2024-01-01T00:00:00Z <<<
        """.trimIndent()
        assertEquals("whois.godaddy.com", WhoisRecords.referralServer(thin))
        assertFalse(WhoisRecords.isNotFound(thin, "example.com"))
        val domain = DomainNames.parse("example.com") as DomainNames.Parse.Ok
        val record = WhoisRecords.parse(domain, thin, "whois.verisign-grs.com")
        assertEquals("Example Registrar", record.registrar?.name)
        assertEquals("146", record.registrar?.ianaId)
        assertEquals("abuse@example-registrar.com", record.registrar?.abuseEmail)
        assertEquals("1995-08-14T04:00:00Z", record.registeredAt)
        assertEquals("2024-08-13T04:00:00Z", record.expiresAt)
        assertEquals("2024-01-01T00:00:00Z", record.databaseUpdatedAt)
        assertEquals("已签名", record.dnssec)
        assertEquals(listOf("A.IANA-SERVERS.NET", "B.IANA-SERVERS.NET"), record.nameservers.map { it.name })
        assertEquals("注册商禁止转移", record.statuses.single().label)
    }

    @Test
    fun readsRegistrarContactAndNotFound() {
        assertTrue(WhoisRecords.isNotFound("No match for domain \"MISSING.COM\".\n", "missing.com"))
        assertTrue(
            WhoisRecords.isNotFound(
                "Domain: missing.de\nStatus: free\n",
                "missing.de"
            )
        )
        val full = """
            Domain Name: example.com
            Registrant Name: REDACTED FOR PRIVACY
            Registrant Organization: Privacy Service
            Registrant Email: person@example.net
            Registrant Country: US
            Name servers:
                ns1.example.net
                ns2.example.net
        """.trimIndent()
        val domain = DomainNames.parse("example.com") as DomainNames.Parse.Ok
        val record = WhoisRecords.parse(domain, full, "whois.godaddy.com")
        val contact = record.contacts.single()
        assertEquals("注册人", contact.roleLabel)
        assertNull(contact.name)
        assertEquals("Privacy Service", contact.organization)
        assertEquals("person@example.net", contact.email)
        assertEquals("US", contact.country)
        assertEquals(listOf("ns1.example.net", "ns2.example.net"), record.nameservers.map { it.name })
    }

    @Test
    fun readsJprsBracketsAndSpecialQueries() {
        val text = """
            a. [Domain Name]                EXAMPLE.JP
            g. [Registrant]                 Example Inc.
            n. [Name Server]                ns1.example.jp
            [Created on]                    2020/01/01
            [Expires on]                    2021/01/31
            [Status]                        Active
        """.trimIndent()
        val domain = DomainNames.parse("example.jp") as DomainNames.Parse.Ok
        val record = WhoisRecords.parse(domain, text, "whois.jprs.jp")
        assertEquals("Example Inc.", record.contacts.single().name)
        assertEquals("ns1.example.jp", record.nameservers.single().name)
        assertEquals("2020/01/01", record.registeredAt)
        assertEquals("2021/01/31", record.expiresAt)
        assertEquals("正常", record.statuses.single().label)
        assertEquals("example.jp/e", WhoisRecords.queryFor("whois.jprs.jp", "example.jp"))
        assertEquals("-T dn,ace example.de", WhoisRecords.queryFor("whois.denic.de", "example.de"))
    }
}
