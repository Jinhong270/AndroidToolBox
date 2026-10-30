package com.jh270.toolbox.crypto

import java.math.BigInteger
import java.security.SecureRandom

internal object X25519 {
    private val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val A24 = BigInteger.valueOf(121665)
    private val random = SecureRandom()

    fun generatePrivate(): ByteArray {
        val scalar = ByteArray(32)
        random.nextBytes(scalar)
        return clamp(scalar)
    }

    fun publicKey(scalar: ByteArray): ByteArray {
        return shared(scalar, byteArrayOf(9) + ByteArray(31))
    }

    fun shared(scalar: ByteArray, peer: ByteArray): ByteArray {
        val clamped = clamp(scalar)
        val coordinate = decode(peer)
        val result = ladder(clamped, coordinate)
        return result.toUnsignedLittle(32)
    }

    private fun clamp(scalar: ByteArray): ByteArray {
        val key = scalar.copyOf(32)
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
        return key
    }

    private fun decode(encoded: ByteArray): BigInteger {
        val bytes = encoded.copyOf(32)
        bytes[31] = (bytes[31].toInt() and 127).toByte()
        return bytes.toPositiveLittle().mod(P)
    }

    private fun ladder(scalar: ByteArray, u: BigInteger): BigInteger {
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0
        for (bit in 254 downTo 0) {
            val current = ((scalar[bit ushr 3].toInt() ushr (bit and 7)) and 1)
            swap = swap xor current
            if (swap == 1) {
                val oldX = x2
                x2 = x3
                x3 = oldX
                val oldZ = z2
                z2 = z3
                z3 = oldZ
            }
            swap = current
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            x3 = da.add(cb).modPow(BigInteger.valueOf(2), P)
            z3 = u.multiply(da.subtract(cb).modPow(BigInteger.valueOf(2), P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }
        if (swap == 1) {
            val oldX = x2
            x2 = x3
            x3 = oldX
            val oldZ = z2
            z2 = z3
            z3 = oldZ
        }
        return x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P)
    }
}
