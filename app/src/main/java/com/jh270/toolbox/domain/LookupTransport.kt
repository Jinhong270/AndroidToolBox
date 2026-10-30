package com.jh270.toolbox.domain

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class HttpFetch(val code: Int, val body: String, val finalUrl: String)

interface LookupTransport {
    fun httpsGet(url: String, headers: Map<String, String>): HttpFetch
    fun whois(host: String, query: String): String
}

class PlatformLookupTransport : LookupTransport {
    override fun httpsGet(url: String, headers: Map<String, String>): HttpFetch {
        var current = url
        val seen = HashSet<String>()
        for (hop in 0 until 6) {
            if (!RegistryHosts.isAllowedRegistryUrl(current)) throw IOException("拒绝访问非 HTTPS 注册局地址")
            if (!seen.add(current)) throw IOException("重定向循环")
            val connection = open(current, headers)
            try {
                val code = try {
                    connection.responseCode
                } catch (e: FileNotFoundException) {
                    404
                }
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("重定向缺少地址")
                    current = URL(URL(current), location).toString()
                    continue
                }
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = if (stream == null) "" else stream.use { decode(readBytes(it, MAX_HTTP_BYTES)) }
                return HttpFetch(code, body, current)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("重定向次数过多")
    }

    override fun whois(host: String, query: String): String {
        if (!RegistryHosts.isPublicHost(host)) throw IOException("拒绝连接这个 WHOIS 服务器")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, 43), 15_000)
            socket.soTimeout = 15_000
            socket.getOutputStream().apply {
                write("$query\r\n".toByteArray(Charsets.UTF_8))
                flush()
            }
            val raw = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            val input = socket.getInputStream()
            try {
                while (raw.size() < MAX_WHOIS_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    raw.write(buffer, 0, count)
                }
            } catch (e: SocketTimeoutException) {
                if (raw.size() == 0) throw IOException("WHOIS 服务器没有返回数据")
            }
            if (raw.size() == 0) throw IOException("WHOIS 服务器没有返回数据")
            return decode(raw.toByteArray())
        } finally {
            socket.close()
        }
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/rdap+json, application/json;q=0.9")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
    }

    private fun readBytes(input: InputStream, max: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < max) {
            val count = input.read(buffer, 0, minOf(buffer.size, max - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            bytes.toString(Charsets.ISO_8859_1)
        }
    }

    private companion object {
        const val USER_AGENT = "ToolBox/1.0 (+https://github.com/Jinhong270/AndroidToolBox)"
        const val MAX_HTTP_BYTES = 1_500_000
        const val MAX_WHOIS_BYTES = 512_000
    }
}
