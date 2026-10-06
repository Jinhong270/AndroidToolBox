package com.jh270.toolbox

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class SevenZReadingTest {

    @Test
    fun listAndReadEntries() {
        val file = File.createTempFile("toolbox_test_", ".7z")
        try {
            SevenZOutputFile(file).use { out ->
                val entry = SevenZArchiveEntry()
                entry.name = "dir/hello.txt"
                out.putArchiveEntry(entry)
                out.write("hello world".toByteArray(Charsets.UTF_8))
                out.closeArchiveEntry()
            }

            val names = mutableListOf<String>()
            SevenZFile.Builder().setFile(file).get().use { sevenZ ->
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    names.add(entry.name)
                    entry = sevenZ.nextEntry
                }
            }

            assertEquals(1, names.size)
            assertEquals("dir/hello.txt", names[0])

            SevenZFile.Builder().setFile(file).get().use { sevenZ ->
                var entry = sevenZ.nextEntry
                assertNotNull(entry)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var read: Int
                while (sevenZ.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                assertEquals("hello world", out.toString(Charsets.UTF_8.name()))
            }
        } finally {
            file.delete()
        }
    }
}
