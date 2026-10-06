package com.jh270.toolbox

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jh270.toolbox.ssh.SshRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

class SshRepositoryProviderTest {

    private fun readResource(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: throw IllegalStateException("missing test resource $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun installsBouncyCastleAndEdDsaProviders() {
        SshRepository()

        val bc = Security.getProvider("BC")
        assertNotNull("BouncyCastle provider must be installed", bc)
        assertTrue(bc!!.javaClass.name.startsWith("org.bouncycastle"))

        assertNotNull("EdDSA provider must be installed", Security.getProvider("EdDSA"))
    }

    @Test
    fun loadsWindowsStyleOpenSshEd25519Key() {
        SshRepository()

        val keyContent = readResource("ed25519_openssh.key")
        val jsch = JSch()
        val keyPair = KeyPair.load(jsch, keyContent.toByteArray(Charsets.UTF_8), null)
        try {
            assertFalse("test key must be unencrypted", keyPair.isEncrypted)
        } finally {
            keyPair.dispose()
        }
    }
}
