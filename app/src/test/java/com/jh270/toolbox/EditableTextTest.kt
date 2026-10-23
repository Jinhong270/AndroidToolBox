package com.jh270.toolbox

import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.ssh.decodeEditableText
import com.jh270.toolbox.ssh.encodeEditableText
import com.jh270.toolbox.ssh.editableFilePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableTextTest {
    @Test
    fun extensionlessTextIsEditable() {
        val bytes = "#!/bin/sh\n# comment\necho hi\n".toByteArray()
        val preview = editableFilePreview(
            name = "startup",
            path = "/etc/startup",
            size = bytes.size.toLong(),
            permissions = "-rw-r--r--",
            modifiedTime = 0,
            bytes = bytes,
            fileType = FileType.UNKNOWN,
            windows = false,
            truncated = false,
        )
        assertTrue(preview.editable)
        assertEquals("UTF-8", preview.charsetName)
        assertNotNull(preview.content)
    }

    @Test
    fun wrongExtensionTextIsEditable() {
        val bytes = "key=value\n".toByteArray()
        val preview = editableFilePreview(
            name = "notes.png",
            path = "/tmp/notes.png",
            size = bytes.size.toLong(),
            permissions = "-rw-r--r--",
            modifiedTime = 0,
            bytes = bytes,
            fileType = FileType.IMAGE,
            windows = false,
            truncated = false,
        )
        assertTrue(preview.editable)
        assertEquals("key=value\n", preview.content)
    }

    @Test
    fun binaryBytesOpenAsText() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x01)
        val preview = editableFilePreview(
            name = "a.png",
            path = "/a.png",
            size = bytes.size.toLong(),
            permissions = "-rw-r--r--",
            modifiedTime = 0,
            bytes = bytes,
            fileType = FileType.IMAGE,
            windows = false,
            truncated = false,
        )
        assertTrue(preview.editable)
        assertEquals("ISO-8859-1", preview.charsetName)
        assertNotNull(preview.content)
        val encoded = encodeEditableText(preview.content!!, preview.charsetName)
        assertTrue(bytes.contentEquals(encoded))
    }

    @Test
    fun nulBytesOpenAsUtf8() {
        val decoded = decodeEditableText(byteArrayOf(0x41, 0x00, 0x42), false)
        assertEquals("UTF-8", decoded.charsetName)
        assertEquals("A\u0000B", decoded.text)
    }

    @Test
    fun utf16LeBomIsText() {
        val raw = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'A'.code.toByte(), 0, 'B'.code.toByte(), 0)
        val decoded = decodeEditableText(raw, false)
        assertEquals("UTF-16LE", decoded!!.charsetName)
        assertEquals("AB", decoded.text)
    }

    @Test
    fun utf16RoundTripKeepsTheBom() {
        val encoded = encodeEditableText("AB", "UTF-16LE")
        val decoded = decodeEditableText(encoded, false)
        assertEquals("UTF-16LE", decoded!!.charsetName)
        assertEquals("AB", decoded.text)
        assertEquals(0xFF.toByte(), encoded[0])
        assertEquals(0xFE.toByte(), encoded[1])
    }
}
