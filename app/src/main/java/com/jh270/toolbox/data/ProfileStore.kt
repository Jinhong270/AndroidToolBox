package com.jh270.toolbox.data

import android.content.Context
import kotlin.io.encoding.Base64

object ProfileStore {
    private const val PREFS = "toolbox_profiles"
    private const val KEY = "profiles"

    fun load(context: Context): List<SshProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return decode(raw)
    }

    fun save(context: Context, profiles: List<SshProfile>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(profiles))
            .apply()
    }

    fun encode(profiles: List<SshProfile>): String {
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

    fun decode(raw: String): List<SshProfile> {
        if (raw.isBlank()) return emptyList()
        return try {
            raw.lineSequence().filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                if (parts.size < 10) return@map null
                val values = parts.map { Base64.decode(it).decodeToString() }
                val auth = runCatching { AuthType.valueOf(values[7]) }.getOrDefault(AuthType.PASSWORD)
                val port = values[4].toIntOrNull()?.takeIf { it in 1..65535 } ?: 22
                val username = values[5].ifBlank { "root" }
                SshProfile(
                    id = values[0].ifBlank { java.util.UUID.randomUUID().toString() },
                    name = values[1].ifBlank { "$username@${values[3]}" },
                    config = SshConfig(
                        name = values[2],
                        host = values[3],
                        port = port,
                        username = username,
                        password = values[6],
                        authType = auth,
                        privateKey = values[8],
                        passphrase = values[9]
                    )
                )
            }.filterNotNull().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
