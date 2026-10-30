package com.jh270.toolbox

import com.jh270.toolbox.crypto.Ed25519
import com.jh270.toolbox.sshnet.SshPrivateKeys
import com.jh270.toolbox.sshnet.SshReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshRepositoryProviderTest {
    @Test
    fun loadsOpenSshEd25519Key() {
        val stream = javaClass.classLoader!!.getResourceAsStream("ed25519_openssh.key")
            ?: throw IllegalStateException("missing test resource")
        val signer = SshPrivateKeys.load(stream.bufferedReader().use { it.readText() }, null)
        assertEquals(listOf("ssh-ed25519"), signer.algorithms)
        val reader = SshReader(signer.publicBlob)
        assertEquals("ssh-ed25519", reader.readString().toString(Charsets.US_ASCII))
        val publicKey = reader.readString()
        val signed = signer.sign("ssh-ed25519", "toolbox".toByteArray())
        val signature = SshReader(signed)
        assertEquals("ssh-ed25519", signature.readString().toString(Charsets.US_ASCII))
        assertTrue(Ed25519.verify("toolbox".toByteArray(), signature.readString(), publicKey))
    }
}
