package com.jh270.toolbox

import com.jh270.toolbox.crypto.BcryptPbkdf
import com.jh270.toolbox.crypto.Ed25519
import com.jh270.toolbox.crypto.X25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    @Test
    fun ed25519RfcVectors() {
        val firstSeed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val firstPublic = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val firstSignature = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        assertArrayEquals(firstPublic, Ed25519.publicKey(firstSeed))
        assertArrayEquals(firstSignature, Ed25519.sign(ByteArray(0), firstSeed))
        assertTrue(Ed25519.verify(ByteArray(0), firstSignature, firstPublic))

        val secondSeed = hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
        val secondPublic = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val message = byteArrayOf(0x72)
        val secondSignature = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")
        assertArrayEquals(secondPublic, Ed25519.publicKey(secondSeed))
        assertArrayEquals(secondSignature, Ed25519.sign(message, secondSeed))
        assertTrue(Ed25519.verify(message, secondSignature, secondPublic))
    }

    @Test
    fun x25519RfcVectors() {
        val scalar = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val peer = hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        val shared = hex("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552")
        assertArrayEquals(shared, X25519.shared(scalar, peer))
        val alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bobPublic = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        val aliceShared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        assertArrayEquals(aliceShared, X25519.shared(alice, bobPublic))
    }

    @Test
    fun bcryptPbkdfVector() {
        val key = BcryptPbkdf.derive("password".toByteArray(), "salt".toByteArray(), 4, 32)
        assertArrayEquals(hex("5bbf0cc293587f1c3635555c27796598d47e579071bf427e9d8fbe842aba34d9"), key)
    }

    private fun hex(value: String): ByteArray {
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
