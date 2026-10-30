package com.jh270.toolbox.crypto

import java.math.BigInteger
import java.security.MessageDigest

internal object Ed25519 {
    private val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val L = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
    private val D = BigInteger.valueOf(-121665).mod(P).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val TWO = BigInteger.valueOf(2)
    private val SQRT_M1 = TWO.modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)
    private val BASE: Point

    init {
        val y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
        val x = recoverX(y, 0)
        BASE = point(x, y)
    }

    fun publicKey(seed: ByteArray): ByteArray {
        require(seed.size == 32)
        return encode(multiply(secretScalar(seed), BASE))
    }

    fun sign(message: ByteArray, seed: ByteArray): ByteArray {
        require(seed.size == 32)
        val digest = sha512(seed)
        val scalar = secretScalar(seed)
        val prefix = digest.copyOfRange(32, 64)
        val publicKey = encode(multiply(scalar, BASE))
        val nonce = sha512(prefix + message).toPositiveLittle().mod(L)
        val commitment = encode(multiply(nonce, BASE))
        val challenge = sha512(commitment + publicKey + message).toPositiveLittle().mod(L)
        val signatureScalar = nonce.add(challenge.multiply(scalar)).mod(L)
        return commitment + signatureScalar.toUnsignedLittle(32)
    }

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        val pointA = decode(publicKey) ?: return false
        val pointR = decode(signature.copyOfRange(0, 32)) ?: return false
        val scalarS = signature.copyOfRange(32, 64).toPositiveLittle()
        if (scalarS >= L) return false
        val challenge = sha512(signature.copyOfRange(0, 32) + publicKey + message).toPositiveLittle().mod(L)
        val left = multiply(scalarS, BASE)
        val right = add(pointR, multiply(challenge, pointA))
        return encode(left).contentEquals(encode(right))
    }

    private fun secretScalar(seed: ByteArray): BigInteger {
        val hash = sha512(seed)
        hash[0] = (hash[0].toInt() and 248).toByte()
        hash[31] = (hash[31].toInt() and 63).toByte()
        hash[31] = (hash[31].toInt() or 64).toByte()
        return hash.copyOfRange(0, 32).toPositiveLittle()
    }

    private fun recoverX(y: BigInteger, sign: Int): BigInteger {
        if (y < BigInteger.ZERO || y >= P) throw IllegalArgumentException("ed25519 point")
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
        val x = u.multiply(v.modInverse(P)).modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        val vx2 = v.multiply(x).multiply(x).mod(P)
        val chosen = when {
            vx2 == u -> x
            vx2 == u.negate().mod(P) -> x.multiply(SQRT_M1).mod(P)
            else -> throw IllegalArgumentException("ed25519 point")
        }
        if (chosen == BigInteger.ZERO && sign == 1) throw IllegalArgumentException("ed25519 point")
        val odd = chosen.testBit(0)
        return if (odd != (sign == 1)) P.subtract(chosen).mod(P) else chosen
    }

    private fun decode(encoded: ByteArray): Point? {
        return try {
            val bytes = encoded.copyOf(32)
            val sign = (bytes[31].toInt() ushr 7) and 1
            bytes[31] = (bytes[31].toInt() and 127).toByte()
            val y = bytes.toPositiveLittle()
            if (y >= P) return null
            point(recoverX(y, sign), y)
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(point: Point): ByteArray {
        val inverse = point.z.modInverse(P)
        val x = point.x.multiply(inverse).mod(P)
        val y = point.y.multiply(inverse).mod(P)
        val bytes = y.toUnsignedLittle(32)
        if (x.testBit(0)) bytes[31] = (bytes[31].toInt() or 128).toByte()
        return bytes
    }

    private fun multiply(scalar: BigInteger, point: Point): Point {
        var result = identity()
        var addend = point
        var value = scalar
        if (value.signum() < 0) value = value.mod(L)
        while (value.signum() > 0) {
            if (value.testBit(0)) result = add(result, addend)
            addend = double(addend)
            value = value.shiftRight(1)
        }
        return result
    }

    private fun add(first: Point, second: Point): Point {
        val a = first.y.subtract(first.x).multiply(second.y.subtract(second.x)).mod(P)
        val b = first.y.add(first.x).multiply(second.y.add(second.x)).mod(P)
        val c = first.t.multiply(TWO).multiply(D).multiply(second.t).mod(P)
        val d = first.z.multiply(TWO).multiply(second.z).mod(P)
        val e = b.subtract(a).mod(P)
        val f = d.subtract(c).mod(P)
        val g = d.add(c).mod(P)
        val h = b.add(a).mod(P)
        return Point(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun double(point: Point): Point {
        val a = point.x.multiply(point.x).mod(P)
        val b = point.y.multiply(point.y).mod(P)
        val c = point.z.multiply(point.z).mod(P).multiply(TWO).mod(P)
        val h = a.add(b).mod(P)
        val e = h.subtract(point.x.add(point.y).modPow(TWO, P)).mod(P)
        val g = a.subtract(b).mod(P)
        val f = c.add(g).mod(P)
        return Point(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun point(x: BigInteger, y: BigInteger): Point {
        return Point(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun identity(): Point {
        return Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    }

    private fun sha512(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-512").digest(data)
    }

    private data class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)
}

internal fun ByteArray.toPositiveLittle(): BigInteger {
    var result = BigInteger.ZERO
    for (index in indices.reversed()) {
        result = result.shiftLeft(8).or(BigInteger.valueOf((this[index].toInt() and 0xff).toLong()))
    }
    return result
}

internal fun BigInteger.toUnsignedLittle(length: Int): ByteArray {
    val bytes = ByteArray(length)
    var value = this
    for (index in 0 until length) {
        bytes[index] = value.and(BigInteger.valueOf(0xff)).toInt().toByte()
        value = value.shiftRight(8)
    }
    return bytes
}
