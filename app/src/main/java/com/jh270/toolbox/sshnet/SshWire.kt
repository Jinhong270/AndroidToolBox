package com.jh270.toolbox.sshnet

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

internal class SshWriter {
    private val out = java.io.ByteArrayOutputStream()

    fun writeByte(value: Int) {
        out.write(value and 0xff)
    }

    fun writeInt(value: Int) {
        out.write((value ushr 24) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write(value and 0xff)
    }

    fun writeLong(value: Long) {
        writeInt((value ushr 32).toInt())
        writeInt(value.toInt())
    }

    fun writeBytes(value: ByteArray) {
        out.write(value)
    }

    fun writeString(value: ByteArray) {
        writeInt(value.size)
        writeBytes(value)
    }

    fun writeString(value: String) {
        writeString(value.toByteArray(StandardCharsets.UTF_8))
    }

    fun writeMpint(value: BigInteger) {
        if (value.signum() == 0) {
            writeInt(0)
            return
        }
        writeString(value.toByteArray())
    }

    fun writeBoolean(value: Boolean) {
        writeByte(if (value) 1 else 0)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

internal class SshReader(private val data: ByteArray) {
    private var offset = 0

    fun remaining(): Int = data.size - offset

    fun readByte(): Int {
        if (offset >= data.size) throw SshProtocolException("truncated packet")
        return data[offset++].toInt() and 0xff
    }

    fun readInt(): Int {
        val value = readByte() shl 24 or (readByte() shl 16) or (readByte() shl 8) or readByte()
        return value
    }

    fun readLong(): Long {
        return (readInt().toLong() and 0xffffffffL) shl 32 or (readInt().toLong() and 0xffffffffL)
    }

    fun readBytes(length: Int): ByteArray {
        if (length < 0 || offset + length > data.size) throw SshProtocolException("truncated packet")
        val value = data.copyOfRange(offset, offset + length)
        offset += length
        return value
    }

    fun readString(): ByteArray {
        return readBytes(readInt())
    }

    fun readMpint(): BigInteger {
        val bytes = readString()
        if (bytes.isEmpty()) return BigInteger.ZERO
        return BigInteger(bytes)
    }

    fun readBoolean(): Boolean = readByte() != 0

    fun rest(): ByteArray = readBytes(remaining())
}

internal class SshProtocolException(message: String) : java.io.IOException(message)

internal fun sshString(value: ByteArray): ByteArray {
    val writer = SshWriter()
    writer.writeString(value)
    return writer.toByteArray()
}

internal fun sshString(value: String): ByteArray = sshString(value.toByteArray(StandardCharsets.UTF_8))

internal fun positiveMpint(value: BigInteger): ByteArray {
    val writer = SshWriter()
    writer.writeMpint(value)
    return writer.toByteArray()
}

internal val sshRandom = SecureRandom()
