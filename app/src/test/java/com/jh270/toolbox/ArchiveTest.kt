package com.jh270.toolbox

import com.jh270.toolbox.archive.Lzma
import com.jh270.toolbox.archive.Lzma2
import com.jh270.toolbox.archive.TarInput
import com.jh270.toolbox.archive.TarOutput
import com.jh270.toolbox.archive.gzipInput
import com.jh270.toolbox.archive.gzipOutput
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class ArchiveTest {
    @Test
    fun tarRoundTripWithLongName() {
        val longName = "目录/" + "很长的名字".repeat(20) + ".txt"
        val payload = "hello archive".toByteArray()
        val bytes = ByteArrayOutputStream()
        gzipOutput(bytes).use { gzip ->
            TarOutput(gzip).use { tar ->
                tar.putEntry("dir/", 0, true)
                tar.putEntry("$longName", payload.size.toLong(), false)
                tar.write(payload)
                tar.closeEntry()
                tar.finish()
            }
        }
        val names = linkedMapOf<String, String>()
        TarInput(gzipInput(ByteArrayInputStream(bytes.toByteArray()))).use { tar ->
            var entry = tar.nextEntry()
            while (entry != null) {
                val text = tar.readBytes().toString(StandardCharsets.UTF_8)
                names[entry.name] = text
                entry = tar.nextEntry()
            }
        }
        assertEquals("", names["dir/"])
        assertEquals("hello archive", names[longName])
    }

    @Test
    fun readsSystemTar() {
        val root = File.createTempFile("toolbox_tar_", "")
        root.delete()
        root.mkdirs()
        val archive = File.createTempFile("toolbox_tar_", ".tar")
        try {
            File(root, "hello.txt").writeText("from-tar")
            File(root, "sub").mkdirs()
            File(root, "sub/note.txt").writeText("nested")
            val process = ProcessBuilder("tar", "-cf", archive.absolutePath, "-C", root.absolutePath, ".")
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            assertEquals(log, 0, process.waitFor())
            val found = mutableMapOf<String, String>()
            TarInput(archive.inputStream()).use { tar ->
                var entry = tar.nextEntry()
                while (entry != null) {
                    found[entry.name.trimStart('.', '/')] = tar.readBytes().toString(StandardCharsets.UTF_8)
                    entry = tar.nextEntry()
                }
            }
            assertEquals("from-tar", found["hello.txt"])
            assertEquals("nested", found["sub/note.txt"])
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun sevenZipStoredRoundTrip() {
        val file = File.createTempFile("toolbox_7z_", ".7z")
        try {
            SevenZOutputFile(file).use { out ->
                val dir = SevenZArchiveEntry()
                dir.name = "dir/"
                dir.isDirectory = true
                out.putArchiveEntry(dir)
                out.closeArchiveEntry()
                val hello = SevenZArchiveEntry()
                hello.name = "dir/hello.txt"
                out.putArchiveEntry(hello)
                out.write("hello world".toByteArray(StandardCharsets.UTF_8))
                out.closeArchiveEntry()
                val empty = SevenZArchiveEntry()
                empty.name = "empty.txt"
                out.putArchiveEntry(empty)
                out.closeArchiveEntry()
            }
            val found = linkedMapOf<String, String>()
            SevenZFile.Builder().setFile(file).get().use { reader ->
                var entry = reader.nextEntry
                while (entry != null) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                    found[entry.name] = out.toString(StandardCharsets.UTF_8)
                    assertEquals(entry.isDirectory, entry.name.endsWith("/"))
                    entry = reader.nextEntry
                }
            }
            assertEquals("", found["dir/"])
            assertEquals("hello world", found["dir/hello.txt"])
            assertEquals("", found["empty.txt"])
        } finally {
            file.delete()
        }
    }

    @Test
    fun lzmaAloneAndLzma2() {
        val plain = resource("hamlet.txt")
        val alone = Lzma.decodeAlone(resource("hamlet.lzma"))
        assertTrue(alone.contentEquals(plain))
        val raw = Lzma2.decodeWithDict(1 shl 22, resource("hamlet.lzma2"), plain.size.toLong())
        assertTrue(raw.contentEquals(plain))
    }

    private fun resource(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: throw IllegalStateException("missing $name")
        return stream.use { it.readBytes() }
    }
}
