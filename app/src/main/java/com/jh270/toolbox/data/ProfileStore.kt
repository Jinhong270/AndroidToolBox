package com.jh270.toolbox.data

import android.content.Context
import kotlin.io.encoding.Base64

object ProfileStore {
    @Synchronized
    fun load(context: Context): List<SshProfile> {
        return storage(context).load()
    }

    @Synchronized
    fun save(context: Context, profiles: List<SshProfile>) {
        storage(context).save(profiles)
    }

    private fun storage(context: Context) = EncryptedProfileStorage(
        AndroidProfileStorage(context),
        ProfileEncryption(AndroidProfileKey::get)
    )

    internal fun encode(profiles: List<SshProfile>): String {
        return profiles.joinToString("\n") { profile ->
            val config = profile.config
            listOf(
                profile.id,
                profile.name,
                config.name,
                config.host,
                config.port.toString(),
                config.username,
                config.password,
                config.authType.name,
                config.privateKey,
                config.passphrase
            ).joinToString("\t") { field ->
                Base64.encode(field.toByteArray(Charsets.UTF_8))
            }
        }
    }

    internal fun decode(raw: String): List<SshProfile> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .map(::decodeLine)
            .toList()
    }

    private fun decodeLine(line: String): SshProfile {
        val parts = line.split('\t')
        require(parts.size == 10)
        val values = parts.map { Base64.decode(it).decodeToString(throwOnInvalidSequence = true) }
        val auth = AuthType.valueOf(values[7])
        val port = values[4].toInt().also { require(it in 1..65535) }
        return SshProfile(
            id = values[0],
            name = values[1],
            config = SshConfig(
                name = values[2],
                host = values[3],
                port = port,
                username = values[5],
                password = values[6],
                authType = auth,
                privateKey = values[8],
                passphrase = values[9]
            )
        )
    }
}
