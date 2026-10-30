package com.jh270.toolbox

import com.jh270.toolbox.domain.DomainNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainNamesTest {
    @Test
    fun stripsUrlCaseAndTrailingDot() {
        val parsed = DomainNames.parse(" HTTPS://WWW.Example.COM./path?q=1 ")
        assertTrue(parsed is DomainNames.Parse.Ok)
        parsed as DomainNames.Parse.Ok
        assertEquals("www.example.com", parsed.asciiName)
        assertEquals("www.example.com", parsed.unicodeName)
    }

    @Test
    fun stripsPort() {
        val parsed = DomainNames.parse("example.org:8443")
        assertTrue(parsed is DomainNames.Parse.Ok)
        assertEquals("example.org", (parsed as DomainNames.Parse.Ok).asciiName)
    }

    @Test
    fun encodesInternationalNames() {
        val parsed = DomainNames.parse("例子.中国")
        assertTrue(parsed is DomainNames.Parse.Ok)
        parsed as DomainNames.Parse.Ok
        assertEquals("xn--fsqu00a.xn--fiqs8s", parsed.asciiName)
        assertEquals("例子.中国", parsed.unicodeName)
    }

    @Test
    fun rejectsIncompleteAndIp() {
        assertTrue(DomainNames.parse("   ") is DomainNames.Parse.Invalid)
        assertTrue(DomainNames.parse("com") is DomainNames.Parse.Invalid)
        assertTrue(DomainNames.parse("1.1.1.1") is DomainNames.Parse.Invalid)
        assertTrue(DomainNames.parse("not a domain") is DomainNames.Parse.Invalid)
    }
}
