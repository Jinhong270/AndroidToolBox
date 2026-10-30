package com.jh270.toolbox.archive

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class ArchiveEntry(val name: String, val isDirectory: Boolean, val size: Long)

fun gzipInput(input: InputStream): InputStream = GZIPInputStream(input)

fun gzipOutput(output: OutputStream): OutputStream = GZIPOutputStream(output)

class TarInput(private val source: InputStream) : InputStream() {
    private var remaining = 0L
    private var padding = 0
    private var open = false
    private var eof = false
    private val pending = HashMap<String, String>()

    fun nextEntry(): ArchiveEntry? {
        if (eof) return null
        skipCurrent()
        while (true) {
            val header = readBlock() ?: return finish()
            if (header.all { it.toInt() == 0 }) return finish()
            val type = header[156].toInt().toChar()
            val size = readSize(header)
            val rawName = readName(header)
            if (type == 'L' || type == 'K' || type == 'x' || type == 'g') {
                val body = readExactly(size)
                skipBlockPadding(size)
                when (type) {
                    'L' -> pending["path"] = body.toString(Charsets.UTF_8).trimEnd('\u0000')
                    'x', 'g' -> parsePax(body)
                }
                continue
            }
            val raw = pending.remove("path") ?: rawName
            val override = pending.remove("size")?.toLongOrNull()
            val entrySize = override ?: size
            val directory = type == '5' || raw.endsWith("/")
            val name = if (directory && raw.isNotEmpty() && !raw.endsWith("/")) "$raw/" else raw
            remaining = if (directory) 0L else entrySize
            padding = if (remaining == 0L) 0 else ((512 - (remaining % 512)) % 512).toInt()
            open = true
            return ArchiveEntry(name, directory, if (directory) 0L else entrySize)
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val count = read(one, 0, 1)
        return if (count < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!open || remaining == 0L) return -1
        val want = minOf(len.toLong(), remaining).toInt()
        val count = readFully(b, off, want)
        remaining -= count
        return count
    }

    override fun close() {
        source.close()
    }

    private fun finish(): ArchiveEntry? {
        eof = true
        open = false
        return null
    }

    private fun skipCurrent() {
        if (!open) return
        var left = remaining
        val buf = ByteArray(8192)
        while (left > 0) {
            val count = readFully(buf, 0, minOf(buf.size.toLong(), left).toInt())
            left -= count
        }
        skipBytes(padding)
        remaining = 0
        padding = 0
        open = false
    }

    private fun readBlock(): ByteArray? {
        val buf = ByteArray(512)
        var offset = 0
        while (offset < 512) {
            val count = source.read(buf, offset, 512 - offset)
            if (count < 0) {
                if (offset == 0) return null
                throw IOException("truncated tar")
            }
            offset += count
        }
        return buf
    }

    private fun readExactly(size: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var left = size
        val buf = ByteArray(8192)
        while (left > 0) {
            val count = readFully(buf, 0, minOf(buf.size.toLong(), left).toInt())
            out.write(buf, 0, count)
            left -= count
        }
        return out.toByteArray()
    }

    private fun readFully(buf: ByteArray, off: Int, len: Int): Int {
        var offset = 0
        while (offset < len) {
            val count = source.read(buf, off + offset, len - offset)
            if (count < 0) throw IOException("truncated tar")
            offset += count
        }
        return len
    }

    private fun skipBlockPadding(size: Long) {
        skipBytes(((512 - (size % 512)) % 512).toInt())
    }

    private fun skipBytes(count: Int) {
        if (count <= 0) return
        val buf = ByteArray(count)
        readFully(buf, 0, count)
    }

    private fun readName(header: ByteArray): String {
        val name = text(header, 0, 100)
        val prefix = text(header, 345, 155)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun text(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val stop = offset + length
        while (end < stop && header[end].toInt() != 0) end++
        return header.copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    private fun readSize(header: ByteArray): Long {
        val first = header[124].toInt() and 0xFF
        if (first == 0x80 || first == 0xFF) {
            var value = 0L
            for (i in 1 until 12) value = (value shl 8) or (header[124 + i].toLong() and 0xFF)
            return if (first == 0xFF) -value else value
        }
        return octal(header, 124, 12)
    }

    private fun octal(header: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            val c = header[offset + i].toInt() and 0xFF
            if (c == 0 || c == 32) continue
            if (c !in 48..55) break
            value = (value shl 3) or (c - 48).toLong()
        }
        return value
    }

    private fun parsePax(body: ByteArray) {
        var index = 0
        while (index < body.size) {
            var mark = index
            while (mark < body.size && body[mark].toInt() != 32) mark++
            if (mark >= body.size) return
            val length = body.copyOfRange(index, mark).toString(Charsets.US_ASCII).toIntOrNull() ?: return
            if (length <= 0 || index + length > body.size) return
            val record = body.copyOfRange(index, index + length).toString(Charsets.UTF_8)
            val bodyText = record.substring(mark - index + 1).trimEnd('\n')
            val eq = bodyText.indexOf('=')
            if (eq > 0) pending[bodyText.substring(0, eq)] = bodyText.substring(eq + 1)
            index += length
        }
    }
}

class TarOutput(out: OutputStream) : FilterOutputStream(out) {
    private var remaining = 0L
    private var entrySize = 0L
    private var entryOpen = false
    private var finished = false

    fun putEntry(name: String, size: Long, directory: Boolean) {
        closeEntry()
        val path = if (directory && !name.endsWith("/")) "$name/" else if (!directory) name.trimEnd('/') else name
        val storedSize = if (directory) 0L else size
        val bytes = path.toByteArray(Charsets.UTF_8)
        if (bytes.size > 100 || storedSize > MAX_OCTAL) {
            val records = ByteArrayOutputStream()
            if (bytes.size > 100) records.write(pax("path", path))
            if (storedSize > MAX_OCTAL) records.write(pax("size", storedSize.toString()))
            val payload = records.toByteArray()
            writeHeader("./PaxHeaders.0/${path.substringAfterLast('/').take(80)}", payload.size.toLong(), 'x', false)
            out.write(payload)
            pad(payload.size.toLong())
        }
        writeHeader(path, storedSize, if (directory) '5' else '0', bytes.size <= 100)
        remaining = storedSize
        entrySize = storedSize
        entryOpen = true
    }

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (!entryOpen || len > remaining) throw IOException("tar entry overflow")
        if (len == 0) return
        out.write(b, off, len)
        remaining -= len
    }

    fun closeEntry() {
        if (!entryOpen) return
        if (remaining != 0L) throw IOException("tar entry short")
        pad(entrySize)
        entryOpen = false
    }

    fun finish() {
        if (finished) return
        closeEntry()
        out.write(ByteArray(1024))
        out.flush()
        finished = true
    }

    override fun close() {
        finish()
        out.close()
    }

    private fun writeHeader(path: String, size: Long, type: Char, split: Boolean) {
        val header = ByteArray(512)
        val (prefix, name) = if (split) splitName(path) else "" to path.toByteArray(Charsets.UTF_8).take(100).toByteArray()
        put(header, 0, if (split) name else path.toByteArray(Charsets.UTF_8).copyOf(100))
        putOctal(header, 100, if (type == '5') 493L else 420L, 8)
        putOctal(header, 108, 0, 8)
        putOctal(header, 116, 0, 8)
        if (size <= MAX_OCTAL) putOctal(header, 124, size, 12) else putOctal(header, 124, 0, 12)
        putOctal(header, 136, System.currentTimeMillis() / 1000, 12)
        for (i in 148 until 156) header[i] = 32
        header[156] = type.code.toByte()
        put(header, 257, "ustar".toByteArray(Charsets.US_ASCII))
        header[262] = 0
        put(header, 263, "00".toByteArray(Charsets.US_ASCII))
        if (prefix.isNotEmpty()) put(header, 345, prefix.toByteArray(Charsets.UTF_8))
        var sum = 0
        for (value in header) sum += value.toInt() and 0xFF
        val checksum = String.format("%06o", sum).toByteArray(Charsets.US_ASCII)
        checksum.copyInto(header, 148)
        header[154] = 0
        header[155] = 32
        out.write(header)
    }

    private fun splitName(path: String): Pair<String, ByteArray> {
        val trimmed = path.trimEnd('/')
        if (trimmed.toByteArray(Charsets.UTF_8).size <= 100) return "" to trimmed.toByteArray(Charsets.UTF_8)
        var slash = trimmed.lastIndexOf('/')
        while (slash > 0) {
            val prefix = trimmed.substring(0, slash)
            val name = trimmed.substring(slash + 1)
            if (prefix.toByteArray(Charsets.UTF_8).size <= 155 && name.toByteArray(Charsets.UTF_8).size <= 100) {
                return prefix to name.toByteArray(Charsets.UTF_8)
            }
            slash = trimmed.lastIndexOf('/', slash - 1)
        }
        return "" to trimmed.toByteArray(Charsets.UTF_8).copyOf(100)
    }

    private fun put(header: ByteArray, offset: Int, bytes: ByteArray) {
        val count = minOf(bytes.size, header.size - offset)
        bytes.copyInto(header, offset, 0, count)
    }

    private fun putOctal(header: ByteArray, offset: Int, value: Long, length: Int) {
        val text = String.format("%0${length - 1}o", value.coerceAtLeast(0)).toByteArray(Charsets.US_ASCII)
        val start = offset + length - 1 - text.size
        text.copyInto(header, start.coerceAtLeast(offset))
        header[offset + length - 1] = 0
    }

    private fun pad(size: Long) {
        val extra = ((512 - (size % 512)) % 512).toInt()
        if (extra > 0) out.write(ByteArray(extra))
    }

    private fun pax(key: String, value: String): ByteArray {
        val suffix = " $key=$value\n".toByteArray(Charsets.UTF_8)
        var digits = 1
        while (true) {
            val size = digits + suffix.size
            val shown = size.toString()
            if (shown.length == digits) return shown.toByteArray(Charsets.US_ASCII) + suffix
            digits = shown.length
        }
    }

    companion object {
        private const val MAX_OCTAL = 8589934591L
    }
}
