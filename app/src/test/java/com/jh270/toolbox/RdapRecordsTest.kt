package com.jh270.toolbox

import com.jh270.toolbox.domain.DomainNames
import com.jh270.toolbox.domain.DomainStatus
import com.jh270.toolbox.domain.RdapRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdapRecordsTest {
    @Test
    fun readsRegistryRecordAndRegistrarLink() {
        val domain = DomainNames.parse("example.com") as DomainNames.Parse.Ok
        val parsed = RdapRecords.parse(
            """
            {
              "objectClassName": "domain",
              "handle": "2336799_DOMAIN_COM-VRSN",
              "ldhName": "EXAMPLE.COM",
              "status": ["client delete prohibited", "client transfer prohibited"],
              "nameservers": [
                {"objectClassName": "nameserver", "ldhName": "A.IANA-SERVERS.NET"}
              ],
              "secureDNS": {"delegationSigned": true},
              "entities": [
                {
                  "objectClassName": "entity",
                  "roles": ["registrar"],
                  "publicIds": [{"type": "IANA Registrar ID", "identifier": "376"}],
                  "vcardArray": ["vcard", [
                    ["fn", {}, "text", "Reserved-Internet Assigned Numbers Authority"]
                  ]]
                }
              ],
              "events": [
                {"eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z"},
                {"eventAction": "expiration", "eventDate": "2024-08-13T04:00:00Z"},
                {"eventAction": "last changed", "eventDate": "2023-08-14T07:01:38Z"},
                {"eventAction": "last update of RDAP database", "eventDate": "2024-01-01T00:00:00Z"}
              ],
              "links": [
                {
                  "rel": "related",
                  "href": "https://rdap.registrar.example/rdap/domain/example.com",
                  "type": "application/rdap+json"
                }
              ]
            }
            """.trimIndent(),
            "https://rdap.verisign.com/com/v1/domain/example.com",
            domain
        )
        assertTrue(parsed is RdapRecords.Parsed.Found)
        val found = parsed as RdapRecords.Parsed.Found
        assertEquals("https://rdap.registrar.example/rdap/domain/example.com", found.relatedUrl)
        assertEquals("376", found.record.registrar?.ianaId)
        assertEquals("1995-08-14T04:00:00Z", found.record.registeredAt)
        assertEquals("2024-01-01T00:00:00Z", found.record.databaseUpdatedAt)
        assertEquals("2023-08-14T07:01:38Z", found.record.changedAt)
        assertEquals("已签名", found.record.dnssec)
        assertEquals(listOf("注册商禁止删除", "注册商禁止转移"), found.record.statuses.map { it.label })
        assertEquals("A.IANA-SERVERS.NET", found.record.nameservers.single().name)
    }

    @Test
    fun readsRedactedRegistrant() {
        val domain = DomainNames.parse("example.org") as DomainNames.Parse.Ok
        val parsed = RdapRecords.parse(
            """
            {
              "objectClassName": "domain",
              "ldhName": "example.org",
              "entities": [
                {
                  "roles": ["registrant"],
                  "vcardArray": ["vcard", [
                    ["fn", {}, "text", "REDACTED FOR PRIVACY"],
                    ["org", {}, "text", ["Example Privacy"]],
                    ["email", {}, "text", "abuse@example.org"],
                    ["adr", {}, "text", ["", "", "", "", "", "", "US"]]
                  ]]
                }
              ]
            }
            """.trimIndent(),
            "https://rdap.example/domain/example.org",
            domain
        ) as RdapRecords.Parsed.Found
        val contact = parsed.record.contacts.single()
        assertEquals("注册人", contact.roleLabel)
        assertEquals(null, contact.name)
        assertEquals("Example Privacy", contact.organization)
        assertEquals("abuse@example.org", contact.email)
        assertEquals("US", contact.country)
        assertTrue(contact.redacted)
    }

    @Test
    fun treatsRdap404AsMissing() {
        val domain = DomainNames.parse("missing.com") as DomainNames.Parse.Ok
        val parsed = RdapRecords.parse(
            """{"errorCode":404,"title":"Not Found"}""",
            "https://rdap.verisign.com/com/v1/domain/missing.com",
            domain
        )
        assertTrue(parsed is RdapRecords.Parsed.Missing)
    }

    @Test
    fun splitsCamelCaseStatusCodes() {
        assertEquals("注册商禁止转移", DomainStatus.from("clientTransferProhibited https://icann.org/epp#clientTransferProhibited")?.label)
    }
}
