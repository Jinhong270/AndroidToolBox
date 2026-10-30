package com.jh270.toolbox

import com.jh270.toolbox.domain.RdapBootstrap
import com.jh270.toolbox.domain.RegistryHosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdapBootstrapTest {
    @Test
    fun prefersLongestZoneAndHttps() {
        val bootstrap = RdapBootstrap.parse(
            """
            {
              "services": [
                [["uk"], ["https://rdap.nic.uk/"]],
                [["co.uk"], ["https://rdap.nic.uk/co.uk/"]],
                [["com"], ["http://insecure.example/rdap/", "https://rdap.verisign.com/com/v1"]],
                [["internal"], ["https://rdap.localhost/x/"]]
              ]
            }
            """.trimIndent()
        )
        assertEquals(listOf("https://rdap.nic.uk/co.uk/"), bootstrap.basesFor("www.example.co.uk"))
        assertEquals(listOf("https://rdap.nic.uk/"), bootstrap.basesFor("example.uk"))
        assertEquals(listOf("https://rdap.verisign.com/com/v1"), bootstrap.basesFor("example.com"))
        assertTrue(bootstrap.basesFor("printer.internal").isEmpty())
        assertEquals(
            "https://rdap.verisign.com/com/v1/domain/example.com",
            RdapBootstrap.domainUrl("https://rdap.verisign.com/com/v1", "example.com")
        )
    }

    @Test
    fun rejectsPrivateAndPlainHttpTargets() {
        assertTrue(RegistryHosts.isAllowedRegistryUrl("https://data.iana.org/rdap/dns.json"))
        assertFalse(RegistryHosts.isAllowedRegistryUrl("http://rdap.verisign.com/com/v1/"))
        assertFalse(RegistryHosts.isAllowedRegistryUrl("https://user:pass@rdap.verisign.com/"))
        assertFalse(RegistryHosts.isAllowedRegistryUrl("https://rdap.localhost/x"))
        assertFalse(RegistryHosts.isAllowedRegistryUrl("https://10.1.1.1/rdap"))
        assertFalse(RegistryHosts.isPublicHost("whois.local"))
        assertEquals("whois.verisign-grs.com", RegistryHosts.whoisHost("whois://whois.verisign-grs.com:43"))
        assertEquals(null, RegistryHosts.whoisHost("whois://127.0.0.1"))
    }
}
