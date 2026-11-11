package com.jh270.toolbox.data

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class ProfileEncryption(private val key: (create: Boolean) -> SecretKey) {
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(HEADER)
        val iv = cipher.iv
        check(iv.size == IV_SIZE)
        return HEADER + iv + cipher.doFinal(plaintext)
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        if (encrypted.size < HEADER.size + IV_SIZE + TAG_SIZE ||
            !encrypted.copyOfRange(0, HEADER.size).contentEquals(HEADER)
        ) {
            throw GeneralSecurityException("Invalid encrypted profile format")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = encrypted.copyOfRange(HEADER.size, HEADER.size + IV_SIZE)
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(TAG_SIZE * 8, iv))
        cipher.updateAAD(HEADER)
        return cipher.doFinal(encrypted, HEADER.size + IV_SIZE, encrypted.size - HEADER.size - IV_SIZE)
    }

    private companion object {
        val HEADER = "ToolBox.SshProfiles.v1\u0000".toByteArray(Charsets.US_ASCII)
        const val IV_SIZE = 12
        const val TAG_SIZE = 16
    }
}
