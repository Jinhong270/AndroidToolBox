package com.jh270.toolbox.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal object AndroidProfileKey {
    private const val KEY_ALIAS = "toolbox.ssh.profiles.aes256.v1"
    private const val PROVIDER = "AndroidKeyStore"

    fun get(create: Boolean): SecretKey = get(create, KEY_ALIAS)

    @Synchronized
    fun get(create: Boolean, alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            return keyStore.getKey(alias, null) as? SecretKey
                ?: throw GeneralSecurityException("Profile encryption key is unavailable")
        }
        if (!create) throw GeneralSecurityException("Profile encryption key is missing")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}

internal class AndroidProfileStorage(
    context: Context,
    storageName: String = "ssh_profiles.v1",
    private val legacyName: String = "toolbox_profiles",
) : ProfileStorageBackend {
    private val appContext = context.applicationContext
    private val file = File(appContext.noBackupFilesDir, storageName)
    private val pending = File(appContext.noBackupFilesDir, "$storageName.pending")
    private val legacy = appContext.getSharedPreferences(legacyName, Context.MODE_PRIVATE)

    override fun readEncrypted(): ByteArray? {
        if (!file.exists()) return null
        return file.readBytes()
    }

    override fun writeEncrypted(value: ByteArray) {
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(value)
                stream.fd.sync()
            }
            check(pending.readBytes().contentEquals(value))
            Os.rename(pending.path, file.path)
            val directory = Os.open(appContext.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(directory)
            } finally {
                Os.close(directory)
            }
        } finally {
            pending.delete()
        }
    }

    override fun readLegacy(): String? = legacy.getString("profiles", null)

    override fun deleteLegacy() {
        check(appContext.deleteSharedPreferences(legacyName))
    }
}
