package com.jh270.toolbox

import com.jh270.toolbox.json.JSONException
import com.jh270.toolbox.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test
    fun parsesNestedRdapDocument() {
        val root = JSONObject(
            "\uFEFF" + """
            {
              "objectClassName": "domain",
              "ldhName": "EXAMPLE.COM",
              "unicodeName": "\u4f8b\u5b50.\u6d4b\u8bd5",
              "country": "中国",
              "emoji": "\uD83D\uDE00",
              "handle": "2336799_DOMAIN_COM-VRSN",
              "errorCode": 404,
              "bigSerial": 3000000000,
              "ratio": 1.5,
              "active": false,
              "transferLocked": "true",
              "nameservers": [
                {
                  "objectClassName": "nameserver",
                  "ldhName": "A.IANA-SERVERS.NET",
                  "ipAddresses": {"v4": ["192.0.2.1"], "v6": null}
                }
              ],
              "status": ["client delete prohibited", "client transfer prohibited"],
              "secureDNS": {
                "delegationSigned": true,
                "signedText": "false",
                "dsData": []
              },
              "entities": [
                {
                  "objectClassName": "entity",
                  "roles": ["registrar"],
                  "publicIds": [{"type": "IANA Registrar ID", "identifier": "376"}],
                  "vcardArray": ["vcard", [
                    ["fn", {}, "text", "Hello \"World\"\nLine\tA"],
                    ["url", {}, "uri", "https:\/\/rdap.example\/domain\/\u0041"]
                  ]]
                }
              ],
              "events": [
                {"eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z"}
              ],
              "remarks": null,
              "notice": "quote:\" slash:\\ slash2:\/ back:\b form:\f line:\n cr:\r tab:\t uni:\u0041"
            }
            """.trimIndent()
        )
        assertTrue(root.has("ldhName"))
        assertTrue(root.has("remarks"))
        assertFalse(root.has("missing"))
        assertTrue(root.keys().asSequence().contains("objectClassName"))
        assertEquals("EXAMPLE.COM", root.optString("ldhName"))
        assertEquals("例子.测试", root.optString("unicodeName"))
        assertEquals("中国", root.optString("country"))
        assertEquals("\uD83D\uDE00", root.optString("emoji"))
        assertEquals("", root.optString("remarks"))
        assertEquals("fallback", root.optString("remarks", "fallback"))
        assertEquals("", root.optString("missing"))
        assertEquals("fallback", root.optString("missing", "fallback"))
        assertEquals("", root.optString("nameservers"))
        assertEquals("", root.optString("nameservers", "fallback"))
        assertEquals("", root.optString("secureDNS", "fallback"))
        assertEquals("404", root.optString("errorCode"))
        assertEquals("3000000000", root.optString("bigSerial"))
        assertEquals("1.5", root.optString("ratio"))
        assertEquals("false", root.optString("active"))
        assertNull(root.opt("missing"))
        assertTrue(root.opt("remarks") != null)
        assertTrue(root.opt("remarks") === JSONObject.NULL)
        assertTrue(root.opt("errorCode") is Int)
        assertTrue(root.opt("errorCode") is Number)
        assertEquals(404, (root.opt("errorCode") as Number).toInt())
        assertTrue(root.opt("bigSerial") is Long)
        assertEquals(3000000000L, root.opt("bigSerial") as Long)
        assertTrue(root.opt("ratio") is Double)
        assertTrue(root.optBoolean("active", true).not())
        assertTrue(root.optBoolean("transferLocked", false))
        assertTrue(root.optBoolean("errorCode", true))
        assertFalse(root.optBoolean("missing", false))
        val nameservers = root.optJSONArray("nameservers")
        assertNotNull(nameservers)
        assertEquals(1, nameservers!!.length())
        assertNull(nameservers.optJSONObject(1))
        assertNull(nameservers.optJSONArray(0))
        val nameserver = nameservers.optJSONObject(0)
        assertNotNull(nameserver)
        assertEquals("A.IANA-SERVERS.NET", nameserver!!.optString("ldhName"))
        val addresses = nameserver.optJSONObject("ipAddresses")
        assertNotNull(addresses)
        assertEquals("192.0.2.1", addresses!!.optJSONArray("v4")!!.optString(0))
        assertTrue(addresses.has("v6"))
        assertTrue(addresses.opt("v6") === JSONObject.NULL)
        assertNull(addresses.optJSONArray("v6"))
        assertNull(root.optJSONObject("nameservers"))
        assertNull(root.optJSONArray("ldhName"))
        assertNull(root.optJSONObject("remarks"))
        val status = root.optJSONArray("status")
        assertNotNull(status)
        assertEquals(2, status!!.length())
        assertEquals("client delete prohibited", status.optString(0))
        assertEquals("client transfer prohibited", status.optString(1))
        assertEquals("", status.optString(2))
        assertEquals("", status.optString(-1))
        assertNull(status.opt(-1))
        assertNull(status.optJSONObject(0))
        assertNull(status.optJSONArray(0))
        val secure = root.optJSONObject("secureDNS")
        assertNotNull(secure)
        assertTrue(secure!!.optBoolean("delegationSigned", false))
        assertFalse(secure.optBoolean("signedText", true))
        assertEquals(0, secure.optJSONArray("dsData")!!.length())
        val entities = root.optJSONArray("entities")
        assertNotNull(entities)
        val vcard = entities!!.optJSONObject(0)!!.optJSONArray("vcardArray")
        assertNotNull(vcard)
        assertEquals("vcard", vcard!!.optString(0))
        assertEquals("", vcard.optString(3))
        val fields = vcard.optJSONArray(1)
        assertNotNull(fields)
        val fn = fields!!.optJSONArray(0)
        assertNotNull(fn)
        assertEquals("fn", fn!!.optString(0))
        assertTrue(fn.opt(1) is JSONObject)
        assertFalse((fn.opt(1) as JSONObject).keys().hasNext())
        assertEquals("Hello \"World\"\nLine\tA", fn.optString(3))
        val url = fields.optJSONArray(1)
        assertEquals("https://rdap.example/domain/A", url!!.optString(3))
        val notice = "quote:\" slash:\\ slash2:/ back:\b form:\u000C line:\n cr:\r tab:\t uni:A"
        assertEquals(notice, root.optString("notice"))
        assertEquals("registration", root.optJSONArray("events")!!.optJSONObject(0)!!.optString("eventAction"))
        val pretty = root.toString(2)
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("\n  \"ldhName\": \"EXAMPLE.COM\""))
        assertTrue(pretty.contains("\n      \"ldhName\": \"A.IANA-SERVERS.NET\""))
        assertTrue(pretty.startsWith("{"))
        assertTrue(pretty.endsWith("}"))
        val compact = root.toString()
        assertFalse(compact.contains("\n"))
        assertTrue(compact.contains("\"errorCode\":404"))
        assertTrue(compact.contains("\"remarks\":null"))
        assertTrue(compact.contains("\"active\":false"))
        val again = JSONObject(pretty)
        assertEquals(root.optString("notice"), again.optString("notice"))
        assertEquals(root.optString("unicodeName"), again.optString("unicodeName"))
        assertEquals(root.optString("emoji"), again.optString("emoji"))
        assertEquals(404, (again.opt("errorCode") as Number).toInt())
        assertTrue(again.opt("remarks") === JSONObject.NULL)
        assertEquals("A.IANA-SERVERS.NET", again.optJSONArray("nameservers")!!.optJSONObject(0)!!.optString("ldhName"))
    }

    @Test
    fun classifiesNumbers() {
        assertTrue(JSONObject("""{"n":0}""").opt("n") is Int)
        assertTrue(JSONObject("""{"n":-0}""").opt("n") == 0)
        assertTrue(JSONObject("""{"n":-0}""").opt("n") is Int)
        assertEquals(Int.MAX_VALUE, JSONObject("""{"n":2147483647}""").opt("n"))
        assertEquals(Int.MIN_VALUE, JSONObject("""{"n":-2147483648}""").opt("n"))
        assertTrue(JSONObject("""{"n":2147483648}""").opt("n") is Long)
        assertEquals(2147483648L, JSONObject("""{"n":2147483648}""").opt("n"))
        assertTrue(JSONObject("""{"n":-2147483649}""").opt("n") is Long)
        assertEquals(Long.MAX_VALUE, JSONObject("""{"n":9223372036854775807}""").opt("n"))
        assertEquals(Long.MIN_VALUE, JSONObject("""{"n":-9223372036854775808}""").opt("n"))
        val huge = JSONObject("""{"n":9223372036854775808}""")
        assertTrue(huge.opt("n") is Number)
        assertTrue(huge.opt("n") !is Int)
        assertTrue(huge.opt("n") !is Long)
        assertEquals("""{"n":9223372036854775808}""", huge.toString())
        val negativeHuge = JSONObject("""{"n":-9223372036854775809}""")
        assertTrue(negativeHuge.opt("n") is Number)
        assertTrue(negativeHuge.opt("n") !is Long)
        assertEquals("""{"n":-9223372036854775809}""", negativeHuge.toString())
        val real = JSONObject("""{"n":1e+2}""")
        assertTrue(real.opt("n") is Double)
        assertEquals(100.0, real.opt("n") as Double, 0.0)
        val overflow = JSONObject("""{"n":1e309}""")
        assertTrue(overflow.opt("n") is Number)
        assertTrue(overflow.opt("n") !is Double)
        assertEquals("""{"n":1e309}""", overflow.toString())
        val built = JSONObject(
            mapOf(
                "secureDNS" to mapOf("delegationSigned" to true),
                "status" to listOf("ok", null),
                "empty" to emptyMap<String, Any?>(),
                "gone" to null
            )
        )
        assertTrue(built.optJSONObject("secureDNS")!!.optBoolean("delegationSigned", false))
        assertEquals("ok", built.optJSONArray("status")!!.optString(0))
        assertEquals("", built.optJSONArray("status")!!.optString(1))
        assertTrue(built.optJSONArray("status")!!.opt(1) === JSONObject.NULL)
        assertNotNull(built.optJSONObject("empty"))
        assertFalse(built.optJSONObject("empty")!!.keys().hasNext())
        assertTrue(built.opt("gone") === JSONObject.NULL)
        assertEquals("{}", JSONObject(" \n{\r\n}\t ").toString(4))
    }

    @Test
    fun rejectsInvalidJson() {
        assertInvalid("")
        assertInvalid("   ")
        assertInvalid("null")
        assertInvalid("[]")
        assertInvalid("""{"a":1}{"b":2}""")
        assertInvalid("""{"a":1} true""")
        assertInvalid("""{"a":1,}""")
        assertInvalid("""{"a":[1,]}""")
        assertInvalid("""{"a":01}""")
        assertInvalid("""{"a":+1}""")
        assertInvalid("""{"a":1.}""")
        assertInvalid("""{"a":.5}""")
        assertInvalid("""{"a":-}""")
        assertInvalid("""{"a":truee}""")
        assertInvalid("""{"a":"\u12"}""")
        assertInvalid("""{"a":"\q"}""")
        assertInvalid("{\"a\":\"line\nbreak\"}")
        assertInvalid("\uFEFF\uFEFF{}")
        assertInvalid("""{"a":1} // tail""")
    }

    private fun assertInvalid(json: String) {
        try {
            JSONObject(json)
            throw AssertionError(json)
        } catch (e: JSONException) {
            assertNotNull(e.message)
        }
    }
}
