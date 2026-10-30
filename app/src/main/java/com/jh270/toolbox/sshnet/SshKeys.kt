package com.jh270.toolbox.sshnet

import com.jh270.toolbox.crypto.BcryptPbkdf
import com.jh270.toolbox.crypto.Ed25519
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class SshSigner(
    val publicBlob: ByteArray,
    val algorithms: List<String>,
    private val signer: (String, ByteArray) -> ByteArray
) {
    fun sign(algorithm: String, data: ByteArray): ByteArray = signer(algorithm, data)
}

internal object SshPrivateKeys {
    fun load(text: String, passphrase: String?): SshSigner {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.contains("BEGIN OPENSSH PRIVATE KEY")) return loadOpenSsh(normalized, passphrase)
        if (normalized.contains("PuTTY-User-Key-File-")) return loadPutty(normalized, passphrase)
        if (normalized.contains("PRIVATE KEY")) return loadPem(normalized, passphrase)
        throw IllegalArgumentException("invalid privatekey")
    }

    private fun loadOpenSsh(text: String, passphrase: String?): SshSigner {
        val body = decodePemBody(text)
        val magic = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
        if (body.size < magic.size || !body.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw IllegalArgumentException("invalid privatekey")
        }
        val reader = SshReader(body.copyOfRange(magic.size, body.size))
        val cipher = reader.readString().toString(Charsets.US_ASCII)
        val kdf = reader.readString().toString(Charsets.US_ASCII)
        val kdfOptions = reader.readString()
        val count = reader.readInt()
        if (count < 1) throw IllegalArgumentException("invalid privatekey")
        repeat(count) { reader.readString() }
        val secret = reader.readString()
        val plain = decryptOpenSsh(cipher, kdf, kdfOptions, secret, passphrase)
        val inner = SshReader(plain)
        val checkA = inner.readInt()
        val checkB = inner.readInt()
        if (checkA != checkB) {
            if (passphrase.isNullOrEmpty()) throw IllegalArgumentException("private key is encrypted")
            throw IllegalArgumentException("private key passphrase is wrong")
        }
        val type = inner.readString().toString(Charsets.US_ASCII)
        return when (type) {
            "ssh-ed25519" -> {
                val publicKey = inner.readString()
                val privateKey = inner.readString()
                ed25519Signer(privateKey.copyOf(32), publicKey)
            }
            "ssh-rsa" -> {
                val n = inner.readMpint()
                val e = inner.readMpint()
                val d = inner.readMpint()
                rsaSigner(n, e, d)
            }
            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> {
                val curve = inner.readString().toString(Charsets.US_ASCII)
                val point = inner.readString()
                val scalar = inner.readMpint()
                ecdsaSigner(type, curve, point, scalar)
            }
            else -> throw IllegalArgumentException("invalid privatekey")
        }
    }

    private fun decryptOpenSsh(cipher: String, kdf: String, options: ByteArray, secret: ByteArray, passphrase: String?): ByteArray {
        if (cipher == "none") {
            if (kdf != "none") throw IllegalArgumentException("invalid privatekey")
            return secret
        }
        if (passphrase.isNullOrEmpty()) throw IllegalArgumentException("private key is encrypted")
        if (kdf != "bcrypt") throw IllegalArgumentException("invalid privatekey")
        val optionReader = SshReader(options)
        val salt = optionReader.readString()
        val rounds = optionReader.readInt()
        val (keyLength, ivLength, transformation) = when (cipher) {
            "aes256-ctr" -> Triple(32, 16, "AES/CTR/NoPadding")
            "aes128-ctr" -> Triple(16, 16, "AES/CTR/NoPadding")
            "aes256-cbc" -> Triple(32, 16, "AES/CBC/NoPadding")
            else -> throw IllegalArgumentException("invalid privatekey")
        }
        val material = BcryptPbkdf.derive(passphrase.toByteArray(Charsets.UTF_8), salt, rounds, keyLength + ivLength)
        val key = material.copyOf(keyLength)
        val iv = material.copyOfRange(keyLength, keyLength + ivLength)
        return cipher(transformation, Cipher.DECRYPT_MODE, key, iv, secret)
    }

    private fun loadPem(text: String, passphrase: String?): SshSigner {
        val encrypted = text.contains("ENCRYPTED")
        val dek = Regex("""DEK-Info:\s*([A-Z0-9-]+),([0-9A-Fa-f]+)""").find(text)
        val der = if (dek != null || text.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            if (passphrase.isNullOrEmpty()) throw IllegalArgumentException("private key is encrypted")
            if (dek != null) {
                val iv = hex(dek.groupValues[2])
                val transformation = when (dek.groupValues[1]) {
                    "AES-128-CBC" -> "AES/CBC/PKCS5Padding" to 16
                    "AES-256-CBC" -> "AES/CBC/PKCS5Padding" to 32
                    "DES-EDE3-CBC" -> "DESede/CBC/PKCS5Padding" to 24
                    else -> throw IllegalArgumentException("invalid privatekey")
                }
                val (key, derivedIv) = evpBytesToKey(passphrase.toByteArray(Charsets.UTF_8), iv, transformation.second, iv.size)
                cipher(transformation.first, Cipher.DECRYPT_MODE, key, derivedIv, decodePemBody(text))
            } else {
                decryptPkcs8(decodePemBody(text), passphrase)
            }
        } else {
            decodePemBody(text)
        }
        if (text.contains("BEGIN RSA PRIVATE KEY")) return loadPkcs1Rsa(der)
        if (text.contains("BEGIN EC PRIVATE KEY")) return loadSec1(der)
        return loadPkcs8(der)
    }

    private fun decryptPkcs8(der: ByteArray, passphrase: String): ByteArray {
        val value = Asn1.parse(der)
        val sequence = value.children ?: throw IllegalArgumentException("invalid privatekey")
        val alg = sequence[0].children ?: throw IllegalArgumentException("invalid privatekey")
        val oid = oid(alg[0].bytes)
        val params = alg.getOrNull(1)?.bytes ?: ByteArray(0)
        val encrypted = sequence[1].bytes
        if (oid != "1.2.840.113549.1.5.13") throw IllegalArgumentException("invalid privatekey")
        val pbes = Asn1.parseContent(0x30, params).children ?: throw IllegalArgumentException("invalid privatekey")
        val kdf = pbes[0].children ?: throw IllegalArgumentException("invalid privatekey")
        val enc = pbes[1].children ?: throw IllegalArgumentException("invalid privatekey")
        val kdfOid = oid(kdf[0].bytes)
        val encOid = oid(enc[0].bytes)
        if (kdfOid != "1.2.840.113549.1.5.12") throw IllegalArgumentException("invalid privatekey")
        val kdfParams = kdf[1].children ?: throw IllegalArgumentException("invalid privatekey")
        val salt = kdfParams[0].bytes
        val iterations = BigInteger(kdfParams[1].bytes).toInt()
        val iv = enc[1].bytes
        val (transformation, length) = when (encOid) {
            "2.16.840.1.101.3.4.1.42" -> "AES/CBC/PKCS5Padding" to 32
            "2.16.840.1.101.3.4.1.2" -> "AES/CBC/PKCS5Padding" to 16
            else -> throw IllegalArgumentException("invalid privatekey")
        }
        val key = pbkdf2(passphrase.toByteArray(Charsets.UTF_8), salt, iterations, length)
        return cipher(transformation, Cipher.DECRYPT_MODE, key, iv, encrypted)
    }

    private fun loadPkcs8(der: ByteArray): SshSigner {
        val rsa = runCatching {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)) as java.security.interfaces.RSAPrivateCrtKey
            rsaSigner(key.modulus, key.publicExponent, key.privateExponent)
        }
        if (rsa.isSuccess) return rsa.getOrThrow()
        val ec = runCatching {
            val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der)) as java.security.interfaces.ECPrivateKey
            val curve = curveName(key.params)
            val point = encodeFixedPoint(ecMultiply(key.params, key.s), (key.params.order.bitLength() + 7) / 8)
            ecdsaSigner(curve.algorithm, curve.name, point, key.s)
        }
        if (ec.isSuccess) return ec.getOrThrow()
        throw IllegalArgumentException("invalid privatekey")
    }

    private fun loadPkcs1Rsa(der: ByteArray): SshSigner {
        val values = Asn1.parse(der).children ?: throw IllegalArgumentException("invalid privatekey")
        val n = integer(values[1])
        val e = integer(values[2])
        val d = integer(values[3])
        return rsaSigner(n, e, d)
    }

    private fun loadSec1(der: ByteArray): SshSigner {
        val values = Asn1.parse(der).children ?: throw IllegalArgumentException("invalid privatekey")
        val scalar = BigInteger(1, values[1].bytes)
        val named = values.firstOrNull { it.tag == 0xa0 }?.children?.firstOrNull()
        val oid = named?.let { oid(it.bytes) } ?: "1.2.840.10045.3.1.7"
        val curve = curveByOid(oid)
        val embedded = values.firstOrNull { it.tag == 0xa1 }?.children?.firstOrNull()?.let { bitString(it) }
        val point = embedded ?: encodeFixedPoint(ecMultiply(curve.parameters, scalar), curve.size)
        return ecdsaSigner(curve.algorithm, curve.name, point, scalar)
    }

    private fun loadPutty(text: String, passphrase: String?): SshSigner {
        val lines = text.lines()
        val header = lines.firstOrNull { it.startsWith("PuTTY-User-Key-File-") } ?: throw IllegalArgumentException("invalid privatekey")
        val version = header.substringAfter("PuTTY-User-Key-File-").substringBefore(":").toIntOrNull() ?: 2
        val algorithm = header.substringAfter(":").trim()
        val encryption = field(lines, "Encryption")
        val comment = field(lines, "Comment")
        val publicBlob = Base64.getDecoder().decode(section(lines, "Public-Lines"))
        var privateBlob = Base64.getDecoder().decode(section(lines, "Private-Lines"))
        val mac = field(lines, "Private-MAC")
        if (!encryption.equals("none", ignoreCase = true)) {
            if (passphrase.isNullOrEmpty()) throw IllegalArgumentException("private key is encrypted")
            if (version >= 3) throw IllegalArgumentException("invalid privatekey")
            privateBlob = decryptPutty(passphrase, privateBlob)
        }
        if (!verifyPuttyMac(version, algorithm, encryption, comment, publicBlob, privateBlob, mac, passphrase.orEmpty())) {
            if (encryption.equals("none", ignoreCase = true)) throw IllegalArgumentException("invalid privatekey")
            throw IllegalArgumentException("private key passphrase is wrong")
        }
        val reader = SshReader(privateBlob)
        return when {
            algorithm == "ssh-ed25519" -> {
                val seed = reader.readMpint().toUnsignedBytes(32)
                val publicKey = SshReader(publicBlob).let { it.readString(); it.readString() }
                ed25519Signer(seed, publicKey)
            }
            algorithm == "ssh-rsa" -> {
                val public = SshReader(publicBlob)
                public.readString()
                val e = public.readMpint()
                val n = public.readMpint()
                val d = reader.readMpint()
                rsaSigner(n, e, d)
            }
            algorithm.startsWith("ecdsa-") -> {
                val public = SshReader(publicBlob)
                val type = public.readString().toString(Charsets.US_ASCII)
                val curve = public.readString().toString(Charsets.US_ASCII)
                val point = public.readString()
                val scalar = reader.readMpint()
                ecdsaSigner(type, curve, point, scalar)
            }
            else -> throw IllegalArgumentException("invalid privatekey")
        }
    }

    private fun decryptPutty(passphrase: String, blob: ByteArray): ByteArray {
        return cipher("AES/CBC/NoPadding", Cipher.DECRYPT_MODE, puttyV2Key(passphrase), ByteArray(16), blob)
    }

    private fun puttyV2Key(passphrase: String): ByteArray {
        val password = passphrase.toByteArray(Charsets.UTF_8)
        val first = sha1(byteArrayOf(0) + password)
        val second = sha1(byteArrayOf(1) + password)
        return (first + second).copyOf(32)
    }

    private fun verifyPuttyMac(version: Int, algorithm: String, encryption: String, comment: String, publicBlob: ByteArray, privateBlob: ByteArray, mac: String, passphrase: String): Boolean {
        val writer = SshWriter()
        writer.writeString(algorithm)
        writer.writeString(encryption)
        writer.writeString(comment)
        writer.writeString(publicBlob)
        writer.writeString(privateBlob)
        val data = writer.toByteArray()
        val key = if (version >= 3) {
            sha256("putty-private-key-file-mac-key".toByteArray(Charsets.US_ASCII) + passphrase.toByteArray(Charsets.UTF_8))
        } else {
            sha1("putty-private-key-file-mac-key".toByteArray(Charsets.US_ASCII) + passphrase.toByteArray(Charsets.UTF_8))
        }
        val hmac = javax.crypto.Mac.getInstance(if (version >= 3) "HmacSHA256" else "HmacSHA1")
        hmac.init(SecretKeySpec(key, hmac.algorithm))
        val expected = hex(mac.trim())
        return hmac.doFinal(data).contentEquals(expected)
    }

    private fun ed25519Signer(seed: ByteArray, publicKey: ByteArray): SshSigner {
        val derived = Ed25519.publicKey(seed)
        if (!derived.contentEquals(publicKey)) throw IllegalArgumentException("invalid privatekey")
        val blob = SshWriter().apply {
            writeString("ssh-ed25519")
            writeString(publicKey)
        }.toByteArray()
        return SshSigner(blob, listOf("ssh-ed25519")) { algorithm, data ->
            if (algorithm != "ssh-ed25519") throw IllegalArgumentException(algorithm)
            val signature = Ed25519.sign(data, seed)
            SshWriter().apply {
                writeString(algorithm)
                writeString(signature)
            }.toByteArray()
        }
    }

    private fun rsaSigner(modulus: BigInteger, exponent: BigInteger, privateExponent: BigInteger): SshSigner {
        val key = KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateKeySpec(modulus, privateExponent))
        val blob = SshWriter().apply {
            writeString("ssh-rsa")
            writeMpint(exponent)
            writeMpint(modulus)
        }.toByteArray()
        return SshSigner(blob, listOf("rsa-sha2-512", "rsa-sha2-256", "ssh-rsa")) { algorithm, data ->
            val javaName = when (algorithm) {
                "rsa-sha2-512" -> "SHA512withRSA"
                "rsa-sha2-256" -> "SHA256withRSA"
                "ssh-rsa" -> "SHA1withRSA"
                else -> throw IllegalArgumentException(algorithm)
            }
            rsaSignature(algorithm, javaName, key, data)
        }
    }

    private fun rsaSignature(algorithm: String, javaName: String, key: PrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance(javaName)
        signature.initSign(key)
        signature.update(data)
        return SshWriter().apply {
            writeString(algorithm)
            writeString(signature.sign())
        }.toByteArray()
    }

    private fun ecdsaSigner(type: String, curve: String, point: ByteArray, scalar: BigInteger): SshSigner {
        val spec = curveSpec(curve)
        val key = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar, spec.parameters))
        val blob = SshWriter().apply {
            writeString(type)
            writeString(curve)
            writeString(point)
        }.toByteArray()
        return SshSigner(blob, listOf(type)) { algorithm, data ->
            if (algorithm != type) throw IllegalArgumentException(algorithm)
            val signature = Signature.getInstance(spec.javaSignature)
            signature.initSign(key)
            signature.update(data)
            val parsed = derToRs(signature.sign(), spec.size)
            SshWriter().apply {
                writeString(algorithm)
                writeString(parsed)
            }.toByteArray()
        }
    }

}

private fun ecMultiply(params: ECParameterSpec, scalar: BigInteger): ECPoint {
    val field = params.curve.field as ECFieldFp
    val prime = field.p
    val coefficient = params.curve.a
    var result: ECPoint? = null
    var addend = params.generator
    var value = scalar
    while (value.signum() > 0) {
        if (value.testBit(0)) result = if (result == null) addend else ecAdd(prime, coefficient, result, addend)
        addend = ecAdd(prime, coefficient, addend, addend)
        value = value.shiftRight(1)
    }
    return result ?: throw IllegalArgumentException("invalid privatekey")
}

private fun ecAdd(prime: BigInteger, coefficient: BigInteger, first: ECPoint, second: ECPoint): ECPoint {
    val x1 = first.affineX
    val y1 = first.affineY
    val x2 = second.affineX
    val y2 = second.affineY
    val slope = if (x1 == x2 && y1 == y2) {
        x1.pow(2).multiply(BigInteger.valueOf(3)).add(coefficient).mod(prime).multiply(y1.shiftLeft(1).modInverse(prime)).mod(prime)
    } else {
        y2.subtract(y1).mod(prime).multiply(x2.subtract(x1).mod(prime).modInverse(prime)).mod(prime)
    }
    val x3 = slope.pow(2).subtract(x1).subtract(x2).mod(prime)
    val y3 = slope.multiply(x1.subtract(x3)).subtract(y1).mod(prime)
    return ECPoint(x3, y3)
}

private fun encodeFixedPoint(point: ECPoint, size: Int): ByteArray {
    return byteArrayOf(4) + point.affineX.toUnsignedFixed(size) + point.affineY.toUnsignedFixed(size)
}

internal fun verifyHostKey(hostBlob: ByteArray, signatureBlob: ByteArray, exchange: ByteArray): Boolean {
    val host = SshReader(hostBlob)
    val type = host.readString().toString(Charsets.US_ASCII)
    val signature = SshReader(signatureBlob)
    val algorithm = signature.readString().toString(Charsets.US_ASCII)
    val raw = signature.readString()
    return when (type) {
        "ssh-ed25519" -> {
            val publicKey = host.readString()
            algorithm == "ssh-ed25519" && Ed25519.verify(exchange, raw, publicKey)
        }
        "ssh-rsa" -> {
            val exponent = host.readMpint()
            val modulus = host.readMpint()
            val javaName = when (algorithm) {
                "rsa-sha2-512" -> "SHA512withRSA"
                "rsa-sha2-256" -> "SHA256withRSA"
                "ssh-rsa" -> "SHA1withRSA"
                else -> return false
            }
            val key = KeyFactory.getInstance("RSA").generatePublic(java.security.spec.RSAPublicKeySpec(modulus, exponent))
            val verifier = Signature.getInstance(javaName)
            verifier.initVerify(key)
            verifier.update(exchange)
            verifier.verify(raw)
        }
        "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> {
            val curve = host.readString().toString(Charsets.US_ASCII)
            val point = host.readString()
            if (algorithm != type) return false
            val spec = curveSpec(curve)
            val parsed = parsePoint(point)
            val key = KeyFactory.getInstance("EC").generatePublic(java.security.spec.ECPublicKeySpec(parsed, spec.parameters))
            val verifier = Signature.getInstance(spec.javaSignature)
            verifier.initVerify(key)
            verifier.update(exchange)
            verifier.verify(rsToDer(raw))
        }
        else -> false
    }
}

private data class CurveSpec(val name: String, val algorithm: String, val javaSignature: String, val size: Int, val parameters: ECParameterSpec)

private fun curveSpec(name: String): CurveSpec {
    val (algorithm, javaName, signature, generator) = when (name) {
        "nistp256" -> CurveTuple("ecdsa-sha2-nistp256", "secp256r1", "SHA256withECDSA", "secp256r1")
        "nistp384" -> CurveTuple("ecdsa-sha2-nistp384", "secp384r1", "SHA384withECDSA", "secp384r1")
        "nistp521" -> CurveTuple("ecdsa-sha2-nistp521", "secp521r1", "SHA512withECDSA", "secp521r1")
        else -> throw IllegalArgumentException("invalid privatekey")
    }
    val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec(generator)) }.getParameterSpec(ECParameterSpec::class.java)
    val size = (parameters.order.bitLength() + 7) / 8
    return CurveSpec(name, algorithm, signature, size, parameters)
}

private data class CurveTuple(val algorithm: String, val javaName: String, val signature: String, val generator: String)

private fun curveName(params: ECParameterSpec): CurveSpec {
    val size = (params.order.bitLength() + 7) / 8
    return when (size) {
        32 -> curveSpec("nistp256")
        48 -> curveSpec("nistp384")
        else -> curveSpec("nistp521")
    }
}

private fun curveByOid(oid: String): CurveSpec {
    return when (oid) {
        "1.2.840.10045.3.1.7" -> curveSpec("nistp256")
        "1.3.132.0.34" -> curveSpec("nistp384")
        "1.3.132.0.35" -> curveSpec("nistp521")
        else -> throw IllegalArgumentException("invalid privatekey")
    }
}



internal fun parsePoint(point: ByteArray): java.security.spec.ECPoint {
    if (point.isEmpty() || point[0] != 4.toByte() || (point.size - 1) % 2 != 0) throw IllegalArgumentException("invalid privatekey")
    val size = (point.size - 1) / 2
    return java.security.spec.ECPoint(BigInteger(1, point.copyOfRange(1, 1 + size)), BigInteger(1, point.copyOfRange(1 + size, point.size)))
}

private fun derToRs(der: ByteArray, size: Int): ByteArray {
    val values = Asn1.parse(der).children ?: throw IllegalArgumentException("invalid privatekey")
    val r = integer(values[0]).toUnsignedFixed(size)
    val s = integer(values[1]).toUnsignedFixed(size)
    val writer = SshWriter()
    writer.writeMpint(BigInteger(1, r))
    writer.writeMpint(BigInteger(1, s))
    return writer.toByteArray()
}

private fun rsToDer(raw: ByteArray): ByteArray {
    val reader = SshReader(raw)
    val r = reader.readMpint()
    val s = reader.readMpint()
    return Asn1.sequence(Asn1.integer(r), Asn1.integer(s))
}

private fun BigInteger.toUnsignedBytes(length: Int): ByteArray = toUnsignedFixed(length)

private fun BigInteger.toUnsignedFixed(length: Int): ByteArray {
    val raw = toByteArray()
    val significant = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    if (significant.size > length) throw IllegalArgumentException("invalid privatekey")
    val out = ByteArray(length)
    System.arraycopy(significant, 0, out, length - significant.size, significant.size)
    return out
}

private fun evpBytesToKey(password: ByteArray, salt: ByteArray, keyLength: Int, ivLength: Int): Pair<ByteArray, ByteArray> {
    val digest = java.security.MessageDigest.getInstance("MD5")
    val need = keyLength + ivLength
    val out = java.io.ByteArrayOutputStream()
    var previous = ByteArray(0)
    while (out.size() < need) {
        digest.update(previous)
        digest.update(password)
        digest.update(salt)
        previous = digest.digest()
        out.write(previous)
    }
    val all = out.toByteArray()
    return all.copyOf(keyLength) to all.copyOfRange(keyLength, keyLength + ivLength)
}

private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    val spec = javax.crypto.spec.PBEKeySpec(password.toString(Charsets.UTF_8).toCharArray(), salt, iterations, length * 8)
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return factory.generateSecret(spec).encoded
}

private fun cipher(transformation: String, mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    val algorithm = transformation.substringBefore('/')
    val cipher = Cipher.getInstance(transformation)
    cipher.init(mode, SecretKeySpec(key, algorithm), IvParameterSpec(iv))
    return cipher.doFinal(data)
}

private fun decodePemBody(text: String): ByteArray {
    val body = text.lines().filter { it.isNotBlank() && !it.startsWith("-----") && !it.contains(":") }.joinToString("")
    return try {
        Base64.getDecoder().decode(body)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("invalid privatekey")
    }
}

private fun field(lines: List<String>, name: String): String {
    return lines.firstOrNull { it.startsWith("$name:") }?.substringAfter(":")?.trim() ?: ""
}

private fun section(lines: List<String>, name: String): String {
    val header = "$name:"
    val start = lines.indexOfFirst { it.startsWith(header) }
    if (start < 0) throw IllegalArgumentException("invalid privatekey")
    val count = lines[start].substringAfter(":").trim().toInt()
    return lines.subList(start + 1, start + 1 + count).joinToString("")
}

private fun sha1(data: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-1").digest(data)

private fun sha256(data: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(data)

private fun hex(value: String): ByteArray {
    if (value.length % 2 != 0) throw IllegalArgumentException("invalid privatekey")
    return ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

private fun integer(value: Asn1.Value): BigInteger = BigInteger(value.bytes)

private fun oid(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val parts = mutableListOf((bytes[0].toInt() and 0xff) / 40, (bytes[0].toInt() and 0xff) % 40)
    var value = 0L
    for (index in 1 until bytes.size) {
        val current = bytes[index].toInt() and 0xff
        value = (value shl 7) or (current and 0x7f).toLong()
        if (current and 0x80 == 0) {
            parts += value.toInt()
            value = 0
        }
    }
    return parts.joinToString(".")
}

private fun bitString(value: Asn1.Value): ByteArray {
    val bytes = value.bytes
    if (bytes.isEmpty()) return ByteArray(0)
    return bytes.copyOfRange(1, bytes.size)
}

private class Asn1 private constructor() {
    data class Value(val tag: Int, val bytes: ByteArray, val children: List<Value>?)

    companion object {
        fun parse(data: ByteArray): Value {
            val reader = Parser(data)
            val value = reader.read()
            if (reader.remaining() != 0) throw IllegalArgumentException("invalid privatekey")
            return value
        }

        fun parseContent(tag: Int, data: ByteArray): Value = Parser(data).readContent(tag)

        fun sequence(vararg values: ByteArray): ByteArray {
            val body = values.fold(ByteArray(0)) { left, right -> left + right }
            return header(0x30, body) + body
        }

        fun integer(value: BigInteger): ByteArray {
            val body = value.toByteArray()
            return header(0x02, body) + body
        }

        private fun header(tag: Int, body: ByteArray): ByteArray {
            val length = body.size
            return if (length < 128) byteArrayOf(tag.toByte(), length.toByte()) else {
                val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
                byteArrayOf(tag.toByte(), (0x80 or bytes.size).toByte()) + bytes
            }
        }
    }

    private class Parser(private val data: ByteArray) {
        private var offset = 0

        fun remaining(): Int = data.size - offset

        fun read(): Value {
            val tag = readByte()
            val length = readLength()
            val bytes = readBytes(length)
            val constructed = tag and 0x20 != 0 || tag == 0x30
            val children = if (constructed) Parser(bytes).readAll(tag) else null
            return Value(tag, if (constructed && children != null) bytes else bytes, children)
        }

        fun readContent(tag: Int): Value {
            val children = readAll(tag)
            return Value(tag, data, children)
        }

        private fun readAll(tag: Int): List<Value> {
            val values = mutableListOf<Value>()
            while (offset < data.size) values += read()
            if (tag == 0) return values
            return values
        }

        private fun readByte(): Int {
            if (offset >= data.size) throw IllegalArgumentException("invalid privatekey")
            return data[offset++].toInt() and 0xff
        }

        private fun readLength(): Int {
            val first = readByte()
            if (first and 0x80 == 0) return first
            val count = first and 0x7f
            var length = 0
            repeat(count) { length = (length shl 8) or readByte() }
            return length
        }

        private fun readBytes(length: Int): ByteArray {
            if (length < 0 || offset + length > data.size) throw IllegalArgumentException("invalid privatekey")
            val bytes = data.copyOfRange(offset, offset + length)
            offset += length
            return bytes
        }
    }
}

