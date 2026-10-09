package com.jh270.toolbox

import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.ProfileStore
import com.jh270.toolbox.data.SshConfig
import com.jh270.toolbox.data.SshProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStoreTest {
    @Test
    fun roundTripKeepsConnectionFields() {
        val profile = SshProfile(
            id = "abc",
            name = "home",
            config = SshConfig(
                name = "home",
                host = "10.0.0.8",
                port = 2222,
                username = "admin",
                password = "secret",
                authType = AuthType.PRIVATE_KEY,
                privateKey = "KEY",
                passphrase = "phrase"
            )
        )
        val restored = ProfileStore.decode(ProfileStore.encode(listOf(profile)))
        assertEquals(1, restored.size)
        assertEquals("abc", restored[0].id)
        assertEquals("10.0.0.8", restored[0].config.host)
        assertEquals(2222, restored[0].config.port)
        assertEquals("admin", restored[0].config.username)
        assertEquals("secret", restored[0].config.password)
        assertEquals(AuthType.PRIVATE_KEY, restored[0].config.authType)
        assertEquals("KEY", restored[0].config.privateKey)
        assertEquals("phrase", restored[0].config.passphrase)
    }

    @Test
    fun blankPayloadIsEmpty() {
        assertTrue(ProfileStore.decode("").isEmpty())
        assertTrue(ProfileStore.decode("not-json").isEmpty())
    }
}
