package com.jh270.toolbox.ssh

import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal const val TEXT_EDIT_LIMIT = 204800

internal data class DecodedText(val text: String, val charsetName: String)

internal fun encodeEditableText(text: String, charsetName: String): ByteArray {
    val encoded = text.toByteArray(charset(charsetName))
    return when (charsetName) {
        "UTF-16LE" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + encoded
        "UTF-16BE" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + encoded
        else -> encoded
    }
}

internal fun decodeEditableText(bytes: ByteArray, windows: Boolean): DecodedText? {
    if (bytes.isEmpty()) return DecodedText("", "UTF-8")
    val utf16 = utf16Charset(bytes)
    if (utf16 != null) {
        val text = String(bytes, charset(utf16)).removePrefix("\uFEFF")
        return DecodedText(text, utf16)
    }
    if (bytes.any { it == 0.toByte() }) return null
    val utf8 = try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
        null
    }
    if (utf8 != null && !windows) return DecodedText(utf8, "UTF-8")
    if (utf8 != null && windows && '\uFFFD' !in utf8) return DecodedText(utf8, "UTF-8")
    return try {
        DecodedText(String(bytes, charset("GBK")), "GBK")
    } catch (_: Exception) {
        if (utf8 != null) DecodedText(utf8, "UTF-8") else DecodedText(String(bytes, Charsets.ISO_8859_1), "ISO-8859-1")
    }
}

internal fun editableFilePreview(
    name: String,
    path: String,
    size: Long,
    permissions: String,
    modifiedTime: Long,
    bytes: ByteArray,
    fileType: FileType,
    windows: Boolean,
    truncated: Boolean,
): FilePreview {
    val decoded = decodeEditableText(bytes, windows)
    if (decoded != null) {
        val over = decoded.text.length > TEXT_EDIT_LIMIT
        val text = if (over) decoded.text.take(TEXT_EDIT_LIMIT) else decoded.text
        return FilePreview(
            name = name,
            path = path,
            size = size,
            permissions = permissions,
            modifiedTime = modifiedTime,
            content = text,
            charsetName = decoded.charsetName,
            fileType = fileType,
            editable = true,
            truncated = truncated || over,
        )
    }
    if (fileType == FileType.IMAGE) {
        return FilePreview(
            name = name,
            path = path,
            size = size,
            permissions = permissions,
            modifiedTime = modifiedTime,
            imageData = bytes,
            fileType = FileType.IMAGE,
            editable = false,
            truncated = truncated,
        )
    }
    return FilePreview(
        name = name,
        path = path,
        size = size,
        permissions = permissions,
        modifiedTime = modifiedTime,
        fileType = fileType,
        editable = false,
        truncated = truncated,
    )
}

private fun utf16Charset(bytes: ByteArray): String? {
    if (bytes.size < 2 || bytes.size % 2 != 0) return null
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    if (b0 == 0xFF && b1 == 0xFE) return "UTF-16LE"
    if (b0 == 0xFE && b1 == 0xFF) return "UTF-16BE"
    if (bytes.size < 8) return null
    var le = true
    var be = true
    val sample = minOf(bytes.size, 256)
    var index = 0
    while (index + 1 < sample) {
        val even = bytes[index].toInt() and 0xFF
        val odd = bytes[index + 1].toInt() and 0xFF
        if (odd != 0 || even == 0 || even > 126) le = false
        if (even != 0 || odd == 0 || odd > 126) be = false
        index += 2
    }
    return when {
        le && !be -> "UTF-16LE"
        be && !le -> "UTF-16BE"
        else -> null
    }
}
