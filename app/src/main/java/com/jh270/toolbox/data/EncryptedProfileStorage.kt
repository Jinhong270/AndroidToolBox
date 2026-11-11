package com.jh270.toolbox.data

class ProfileStorageException internal constructor() : Exception("Secure profile storage is unavailable")

internal interface ProfileStorageBackend {
    fun readEncrypted(): ByteArray?
    fun writeEncrypted(value: ByteArray)
    fun readLegacy(): String?
    fun deleteLegacy()
}

internal class EncryptedProfileStorage(
    private val backend: ProfileStorageBackend,
    private val encryption: ProfileEncryption,
) {
    fun load(): List<SshProfile> = protected {
        val encrypted = backend.readEncrypted()
        if (encrypted != null) {
            val profiles = decryptProfiles(encrypted)
            backend.deleteLegacy()
            profiles
        } else {
            val legacy = backend.readLegacy() ?: return@protected emptyList()
            val profiles = ProfileStore.decode(legacy)
            writeVerified(profiles)
            backend.deleteLegacy()
            profiles
        }
    }

    fun save(profiles: List<SshProfile>) = protected {
        load()
        writeVerified(profiles)
    }

    private fun decryptProfiles(encrypted: ByteArray): List<SshProfile> {
        val plaintext = encryption.decrypt(encrypted)
        return try {
            ProfileStore.decode(plaintext.decodeToString(throwOnInvalidSequence = true))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun writeVerified(profiles: List<SshProfile>) {
        val plaintext = ProfileStore.encode(profiles).toByteArray(Charsets.UTF_8)
        try {
            val encrypted = encryption.encrypt(plaintext)
            check(decryptProfiles(encrypted) == profiles)
            backend.writeEncrypted(encrypted)
            val stored = backend.readEncrypted() ?: error("Encrypted profiles were not persisted")
            check(stored.contentEquals(encrypted))
            check(decryptProfiles(stored) == profiles)
        } finally {
            plaintext.fill(0)
        }
    }

    private inline fun <T> protected(action: () -> T): T {
        return try {
            action()
        } catch (error: ProfileStorageException) {
            throw error
        } catch (_: Exception) {
            throw ProfileStorageException()
        }
    }
}
