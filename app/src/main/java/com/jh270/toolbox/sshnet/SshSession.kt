package com.jh270.toolbox.sshnet

import com.jh270.toolbox.crypto.X25519
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SshSession {
    @Volatile var isConnected: Boolean = false
        private set

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var reader: Thread? = null
    private var keepalive: Thread? = null
    private val kexLock = Object()
    private val channels = HashMap<Int, ChannelRuntime>()
    private val nextChannel = AtomicInteger()
    private var seqIn = 0L
    private var seqOut = 0L
    private var inboundCipher: Cipher? = null
    private var outboundCipher: Cipher? = null
    private var inboundMac: Mac? = null
    private var outboundMac: Mac? = null
    private var inboundMacLength = 0
    private var outboundBlock = 8
    private var inboundBlock = 8
    private var clientVersion = "SSH-2.0-ToolBox_1.0"
    var serverVersion: String = ""
        private set
    private var sessionId: ByteArray? = null
    private var strictKex = false
    private var aliveInterval = 15_000
    private var aliveCount = 4
    private val globalReplies = ArrayBlockingQueue<Boolean>(8)
    @Volatile private var running = false

    fun setServerAliveInterval(intervalMs: Int) {
        aliveInterval = intervalMs
    }

    fun setServerAliveCountMax(count: Int) {
        aliveCount = count
    }

    fun connect(
        username: String,
        host: String,
        port: Int,
        password: String?,
        privateKey: String?,
        passphrase: String?,
        timeoutMs: Int,
        preferPublicKey: Boolean
    ) {
        disconnect()
        val opened = Socket()
        socket = opened
        opened.tcpNoDelay = true
        opened.keepAlive = true
        opened.connect(InetSocketAddress(host, port), timeoutMs)
        opened.soTimeout = timeoutMs
        input = opened.getInputStream()
        output = opened.getOutputStream()
        exchangeVersions()
        val signer = if (!privateKey.isNullOrBlank()) SshPrivateKeys.load(privateKey, passphrase) else null
        kex(null)
        authenticate(username, password, signer, preferPublicKey)
        running = true
        isConnected = true
        opened.soTimeout = 20_000
        reader = Thread({ readLoop() }, "ssh-reader").apply { isDaemon = true; start() }
        keepalive = Thread({ keepaliveLoop() }, "ssh-keepalive").apply { isDaemon = true; start() }
    }

    fun disconnect() {
        running = false
        isConnected = false
        try {
            if (output != null && inboundCipher != null) {
                val writer = SshWriter()
                writer.writeByte(1)
                writer.writeInt(11)
                writer.writeString("disconnect")
                writer.writeString("")
                synchronized(kexLock) { writePacket(writer.toByteArray()) }
            }
        } catch (_: Exception) {
        }
        synchronized(channels) {
            channels.values.forEach { it.finish() }
            channels.clear()
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        reader?.interrupt()
        keepalive?.interrupt()
        socket = null
        input = null
        output = null
        inboundCipher = null
        outboundCipher = null
        inboundMac = null
        outboundMac = null
        sessionId = null
        seqIn = 0
        seqOut = 0
        strictKex = false
    }

    fun openChannel(type: String): Any {
        if (!isConnected) throw IOException("session is down")
        return when (type) {
            "shell" -> ShellChannel(this)
            "exec" -> ExecChannel(this)
            "sftp" -> SftpChannel(this)
            else -> throw IllegalArgumentException(type)
        }
    }

    internal fun openRuntime(timeoutMs: Int): ChannelRuntime {
        if (!isConnected) throw IOException("session is down")
        val runtime = ChannelRuntime(nextChannel.getAndIncrement())
        synchronized(channels) { channels[runtime.localId] = runtime }
        val writer = SshWriter()
        writer.writeByte(90)
        writer.writeString("session")
        writer.writeInt(runtime.localId)
        writer.writeInt(runtime.localWindow.toInt())
        writer.writeInt(32 * 1024)
        synchronized(kexLock) { writePacket(writer.toByteArray()) }
        val opened = runtime.opened.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        if (opened != true) {
            synchronized(channels) { channels.remove(runtime.localId) }
            throw java.net.SocketTimeoutException("timeout")
        }
        return runtime
    }

    internal fun request(runtime: ChannelRuntime, type: String, wantReply: Boolean, body: ByteArray, timeoutMs: Int) {
        val writer = SshWriter()
        writer.writeByte(98)
        writer.writeInt(runtime.remoteId)
        writer.writeString(type)
        writer.writeBoolean(wantReply)
        writer.writeBytes(body)
        synchronized(kexLock) { writePacket(writer.toByteArray()) }
        if (!wantReply) return
        val ok = runtime.requests.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        if (ok != true) throw IOException("channel is not opened")
    }

    internal fun sendData(runtime: ChannelRuntime, data: ByteArray, offset: Int, length: Int) {
        var sent = 0
        while (sent < length) {
            if (!isConnected || runtime.closed) throw IOException("channel is not opened")
            val take = runtime.takeWindow(minOf(32 * 1024, length - sent))
            if (take <= 0) continue
            val writer = SshWriter()
            writer.writeByte(94)
            writer.writeInt(runtime.remoteId)
            writer.writeString(data.copyOfRange(offset + sent, offset + sent + take))
            synchronized(kexLock) { writePacket(writer.toByteArray()) }
            sent += take
        }
    }

    internal fun closeRuntime(runtime: ChannelRuntime) {
        if (runtime.closed) return
        runtime.closed = true
        try {
            val writer = SshWriter()
            writer.writeByte(97)
            writer.writeInt(runtime.remoteId)
            synchronized(kexLock) { writePacket(writer.toByteArray()) }
        } catch (_: Exception) {
        }
        runtime.finish()
        synchronized(channels) { channels.remove(runtime.localId) }
    }

    private fun exchangeVersions() {
        val version = "$clientVersion\r\n".toByteArray(Charsets.US_ASCII)
        output!!.write(version)
        output!!.flush()
        val line = readLine()
        if (!line.startsWith("SSH-2.0-") && !line.startsWith("SSH-1.99-")) throw IOException("algorithm negotiation fail")
        serverVersion = line
    }

    private fun readLine(): String {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val value = input!!.read()
            if (value < 0) throw IOException("session is down")
            if (value == '\n'.code) break
            if (value != '\r'.code) buffer.write(value)
            if (buffer.size() > 1024) throw IOException("algorithm negotiation fail")
        }
        val text = buffer.toString(Charsets.US_ASCII)
        if (text.startsWith("SSH-")) return text
        return readLine()
    }

    private fun kex(peerInit: ByteArray?) {
        val clientInit = kexInit()
        writePacket(clientInit)
        val serverInit = peerInit ?: nextOf(20)
        val clientAlgorithms = parseNameLists(clientInit)
        val serverAlgorithms = parseNameLists(serverInit)
        strictKex = strictKex || (clientAlgorithms[0].any { it == "kex-strict-c-v00@openssh.com" } && serverAlgorithms[0].any { it == "kex-strict-s-v00@openssh.com" })
        if (serverAlgorithms.getOrNull(8)?.firstOrNull() == "1") {
            val guessed = serverAlgorithms[0].firstOrNull { !it.startsWith("kex-strict-") }
            val chosen = choose(clientAlgorithms[0], serverAlgorithms[0])
            if (guessed != chosen) readPacket()
        }
        val kexAlgorithm = choose(clientAlgorithms[0], serverAlgorithms[0])
        val hostAlgorithm = choose(clientAlgorithms[1], serverAlgorithms[1])
        val cipherC2s = choose(clientAlgorithms[2], serverAlgorithms[2])
        val cipherS2c = choose(clientAlgorithms[3], serverAlgorithms[3])
        val macC2s = choose(clientAlgorithms[4], serverAlgorithms[4])
        val macS2c = choose(clientAlgorithms[5], serverAlgorithms[5])
        val result = when (kexAlgorithm) {
            "curve25519-sha256", "curve25519-sha256@libssh.org" -> curve25519(clientInit, serverInit, "SHA-256")
            "ecdh-sha2-nistp256" -> ecdh(clientInit, serverInit)
            "diffie-hellman-group14-sha256" -> diffieHellman(clientInit, serverInit, "SHA-256")
            "diffie-hellman-group14-sha1" -> diffieHellman(clientInit, serverInit, "SHA-1")
            else -> throw IOException("algorithm negotiation fail")
        }
        if (!verifyHostKey(result.hostKey, result.signature, result.hash)) throw IOException("algorithm negotiation fail")
        val hostType = SshReader(result.hostKey).readString().toString(Charsets.US_ASCII)
        if (hostType != hostAlgorithm && !(hostAlgorithm.startsWith("rsa") && hostType == "ssh-rsa")) {
            throw IOException("algorithm negotiation fail")
        }
        if (sessionId == null) sessionId = result.hash
        writePacket(byteArrayOf(21))
        val id = sessionId!!
        installOutbound(result.secret, result.hash, id, cipherC2s, macC2s)
        if (strictKex) seqOut = 0
        val newKeys = nextOf(21)
        if (newKeys.isEmpty()) throw IOException("session is down")
        installInbound(result.secret, result.hash, id, cipherS2c, macS2c)
        if (strictKex) seqIn = 0
    }

    private fun curve25519(clientInit: ByteArray, serverInit: ByteArray, hashName: String): KexResult {
        val scalar = X25519.generatePrivate()
        val publicKey = X25519.publicKey(scalar)
        val init = SshWriter()
        init.writeByte(30)
        init.writeString(publicKey)
        writePacket(init.toByteArray())
        val payload = nextOf(31)
        val reply = SshReader(payload.copyOfRange(1, payload.size))
        val host = reply.readString()
        val serverPublic = reply.readString()
        val signature = reply.readString()
        val shared = BigInteger(1, X25519.shared(scalar, serverPublic).reversedArray())
        if (shared.signum() == 0) throw IOException("algorithm negotiation fail")
        val hash = hashExchange(hashName, clientInit, serverInit, host, publicKey, serverPublic, shared, false)
        return KexResult(shared, hash, host, signature)
    }

    private fun ecdh(clientInit: ByteArray, serverInit: ByteArray): KexResult {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), sshRandom)
        val pair = generator.generateKeyPair()
        val point = (pair.public as java.security.interfaces.ECPublicKey).w
        val publicKey = byteArrayOf(4) + fixed(point.affineX, 32) + fixed(point.affineY, 32)
        val init = SshWriter()
        init.writeByte(30)
        init.writeString(publicKey)
        writePacket(init.toByteArray())
        val payload = nextOf(31)
        val reply = SshReader(payload.copyOfRange(1, payload.size))
        val host = reply.readString()
        val serverPublic = reply.readString()
        val signature = reply.readString()
        val agreement = javax.crypto.KeyAgreement.getInstance("ECDH")
        agreement.init(pair.private)
        val peer = parsePoint(serverPublic)
        val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(peer, (pair.public as java.security.interfaces.ECPublicKey).params))
        agreement.doPhase(key, true)
        val shared = BigInteger(1, agreement.generateSecret())
        val hash = hashExchange("SHA-256", clientInit, serverInit, host, publicKey, serverPublic, shared, false)
        return KexResult(shared, hash, host, signature)
    }

    private fun diffieHellman(clientInit: ByteArray, serverInit: ByteArray, hashName: String): KexResult {
        val prime = GROUP14
        val secret = BigInteger(256, sshRandom).mod(prime.subtract(BigInteger.ONE)).add(BigInteger.ONE)
        val clientPublic = BigInteger.valueOf(2).modPow(secret, prime)
        val init = SshWriter()
        init.writeByte(30)
        init.writeMpint(clientPublic)
        writePacket(init.toByteArray())
        val payload = nextOf(31)
        val reply = SshReader(payload.copyOfRange(1, payload.size))
        val host = reply.readString()
        val serverPublic = reply.readMpint()
        val signature = reply.readString()
        val shared = serverPublic.modPow(secret, prime)
        val writer = SshWriter()
        writer.writeString(clientVersion)
        writer.writeString(serverVersion)
        writer.writeString(clientInit)
        writer.writeString(serverInit)
        writer.writeString(host)
        writer.writeMpint(clientPublic)
        writer.writeMpint(serverPublic)
        writer.writeMpint(shared)
        val hash = MessageDigest.getInstance(hashName).digest(writer.toByteArray())
        return KexResult(shared, hash, host, signature)
    }

    private fun hashExchange(hashName: String, clientInit: ByteArray, serverInit: ByteArray, host: ByteArray, clientPublic: ByteArray, serverPublic: ByteArray, shared: BigInteger, dh: Boolean): ByteArray {
        if (dh) return ByteArray(0)
        val writer = SshWriter()
        writer.writeString(clientVersion)
        writer.writeString(serverVersion)
        writer.writeString(clientInit)
        writer.writeString(serverInit)
        writer.writeString(host)
        writer.writeString(clientPublic)
        writer.writeString(serverPublic)
        writer.writeMpint(shared)
        return MessageDigest.getInstance(hashName).digest(writer.toByteArray())
    }

    private fun installOutbound(secret: BigInteger, hash: ByteArray, session: ByteArray, cipherName: String, macName: String) {
        val keyLength = if (cipherName.startsWith("aes256")) 32 else 16
        val iv = derive(secret, hash, session, 'A', 16)
        val key = derive(secret, hash, session, 'C', keyLength)
        outboundCipher = cipher(Cipher.ENCRYPT_MODE, key, iv)
        outboundMac = mac(macName, derive(secret, hash, session, 'E', macLength(macName)))
        outboundBlock = 16
    }

    private fun installInbound(secret: BigInteger, hash: ByteArray, session: ByteArray, cipherName: String, macName: String) {
        val keyLength = if (cipherName.startsWith("aes256")) 32 else 16
        val iv = derive(secret, hash, session, 'B', 16)
        val key = derive(secret, hash, session, 'D', keyLength)
        inboundCipher = cipher(Cipher.DECRYPT_MODE, key, iv)
        inboundMac = mac(macName, derive(secret, hash, session, 'F', macLength(macName)))
        inboundMacLength = macLength(macName)
        inboundBlock = 16
    }

    private fun derive(secret: BigInteger, hash: ByteArray, session: ByteArray, letter: Char, length: Int): ByteArray {
        val digest = MessageDigest.getInstance(if (hash.size == 20) "SHA-1" else "SHA-256")
        val encodedSecret = positiveMpint(secret)
        fun round(seed: ByteArray): ByteArray {
            digest.reset()
            digest.update(encodedSecret)
            digest.update(hash)
            digest.update(seed)
            return digest.digest()
        }
        var block = round(byteArrayOf(letter.code.toByte()) + session)
        val out = ByteArrayOutputStream()
        out.write(block)
        while (out.size() < length) {
            block = round(block)
            out.write(block)
        }
        return out.toByteArray().copyOf(length)
    }

    private fun authenticate(username: String, password: String?, signer: SshSigner?, preferPublicKey: Boolean) {
        val request = SshWriter()
        request.writeByte(5)
        request.writeString("ssh-userauth")
        writePacket(request.toByteArray())
        nextOf(6)
        var success = false
        if (signer != null && (preferPublicKey || password.isNullOrEmpty())) success = publicKeyAuth(username, signer)
        if (!success && !password.isNullOrEmpty()) success = passwordAuth(username, password) || keyboardAuth(username, password)
        if (!success && signer != null && !preferPublicKey) success = publicKeyAuth(username, signer)
        if (!success) throw IOException("authentication failed")
    }

    private fun publicKeyAuth(username: String, signer: SshSigner): Boolean {
        for (algorithm in signer.algorithms) {
            val packet = SshWriter()
            packet.writeByte(50)
            packet.writeString(username)
            packet.writeString("ssh-connection")
            packet.writeString("publickey")
            packet.writeBoolean(true)
            packet.writeString(algorithm)
            packet.writeString(signer.publicBlob)
            val signed = SshWriter()
            signed.writeString(sessionId!!)
            signed.writeBytes(packet.toByteArray())
            packet.writeString(signer.sign(algorithm, signed.toByteArray()))
            writePacket(packet.toByteArray())
            val response = nextAuth()
            val type = response[0].toInt() and 0xff
            if (type == 52) return true
            if (type != 51) throw IOException("authentication failed")
        }
        return false
    }

    private fun passwordAuth(username: String, password: String): Boolean {
        val packet = SshWriter()
        packet.writeByte(50)
        packet.writeString(username)
        packet.writeString("ssh-connection")
        packet.writeString("password")
        packet.writeBoolean(false)
        packet.writeString(password)
        writePacket(packet.toByteArray())
        val response = nextAuth()
        return (response[0].toInt() and 0xff) == 52
    }

    private fun keyboardAuth(username: String, password: String): Boolean {
        val packet = SshWriter()
        packet.writeByte(50)
        packet.writeString(username)
        packet.writeString("ssh-connection")
        packet.writeString("keyboard-interactive")
        packet.writeString("")
        packet.writeString("")
        writePacket(packet.toByteArray())
        while (true) {
            val response = nextAuth()
            when (response[0].toInt() and 0xff) {
                52 -> return true
                51 -> return false
                60 -> {
                    val reader = SshReader(response.copyOfRange(1, response.size))
                    reader.readString()
                    reader.readString()
                    reader.readString()
                    val prompts = reader.readInt()
                    val answer = SshWriter()
                    answer.writeByte(61)
                    answer.writeInt(prompts)
                    repeat(prompts) { answer.writeString(password) }
                    writePacket(answer.toByteArray())
                }
                else -> return false
            }
        }
    }

    private fun nextAuth(): ByteArray {
        while (true) {
            val payload = readPacket()
            when (payload[0].toInt() and 0xff) {
                2, 3, 4, 7, 53 -> continue
                1 -> throw IOException("authentication failed")
                else -> return payload
            }
        }
    }

    private fun nextOf(type: Int): ByteArray {
        while (true) {
            val payload = readPacket()
            when (payload[0].toInt() and 0xff) {
                type -> return payload
                2, 3, 4, 7 -> continue
                1 -> throw IOException("session is down")
                else -> throw IOException("algorithm negotiation fail")
            }
        }
    }

    private fun readLoop() {
        try {
            while (running) {
                val payload = try {
                    readPacket()
                } catch (e: java.net.SocketTimeoutException) {
                    continue
                }
                dispatch(payload)
            }
        } catch (_: Exception) {
            running = false
            isConnected = false
            synchronized(channels) { channels.values.forEach { it.finish() } }
        }
    }

    private fun dispatch(payload: ByteArray) {
        when (payload[0].toInt() and 0xff) {
            20 -> {
                synchronized(kexLock) { kex(payload) }
            }
            80 -> {
                val reader = SshReader(payload.copyOfRange(1, payload.size))
                reader.readString()
                if (reader.readBoolean()) {
                    synchronized(kexLock) { writePacket(byteArrayOf(82)) }
                }
            }
            81 -> globalReplies.offer(true)
            82 -> globalReplies.offer(false)
            91 -> confirm(payload)
            92 -> fail(payload)
            93 -> adjust(payload)
            94 -> data(payload, false)
            95 -> data(payload, true)
            96 -> eof(payload)
            97 -> close(payload)
            98 -> channelRequest(payload)
            99 -> reply(payload, true)
            100 -> reply(payload, false)
            2, 3, 4, 7 -> Unit
            1 -> throw IOException("session is down")
        }
    }

    private fun confirm(payload: ByteArray) {
        val reader = SshReader(payload.copyOfRange(1, payload.size))
        val local = reader.readInt()
        val remote = reader.readInt()
        val window = reader.readInt().toLong() and 0xffffffffL
        val max = reader.readInt()
        val runtime = synchronized(channels) { channels[local] } ?: return
        runtime.remoteId = remote
        runtime.remoteWindow = window
        runtime.remoteMax = max
        runtime.opened.offer(true)
    }

    private fun fail(payload: ByteArray) {
        val local = SshReader(payload.copyOfRange(1, payload.size)).readInt()
        synchronized(channels) { channels[local] }?.opened?.offer(false)
    }

    private fun adjust(payload: ByteArray) {
        val reader = SshReader(payload.copyOfRange(1, payload.size))
        val local = reader.readInt()
        val extra = reader.readInt().toLong() and 0xffffffffL
        val runtime = synchronized(channels) { channels[local] } ?: return
        synchronized(runtime.lock) {
            runtime.remoteWindow += extra
            runtime.lock.notifyAll()
        }
    }

    private fun data(payload: ByteArray, extended: Boolean) {
        val reader = SshReader(payload.copyOfRange(1, payload.size))
        val local = reader.readInt()
        if (extended) reader.readInt()
        val bytes = reader.readString()
        val runtime = synchronized(channels) { channels[local] } ?: return
        if (extended) runtime.stderr.emit(bytes) else runtime.incoming.add(bytes)
    }

    private fun eof(payload: ByteArray) {
        val local = SshReader(payload.copyOfRange(1, payload.size)).readInt()
        val runtime = synchronized(channels) { channels[local] } ?: return
        runtime.eof = true
        runtime.incoming.finish()
    }

    private fun close(payload: ByteArray) {
        val local = SshReader(payload.copyOfRange(1, payload.size)).readInt()
        val runtime = synchronized(channels) { channels.remove(local) } ?: return
        runtime.closed = true
        runtime.finish()
    }

    private fun channelRequest(payload: ByteArray) {
        val reader = SshReader(payload.copyOfRange(1, payload.size))
        val local = reader.readInt()
        val name = reader.readString().toString(Charsets.UTF_8)
        val wantReply = reader.readBoolean()
        val runtime = synchronized(channels) { channels[local] }
        if (runtime != null && name == "exit-status") runtime.exitStatus = reader.readInt()
        if (wantReply && runtime != null) {
            val writer = SshWriter()
            writer.writeByte(if (name == "exit-status") 99 else 100)
            writer.writeInt(runtime.remoteId)
            synchronized(kexLock) { writePacket(writer.toByteArray()) }
        }
    }

    private fun reply(payload: ByteArray, success: Boolean) {
        val local = SshReader(payload.copyOfRange(1, payload.size)).readInt()
        synchronized(channels) { channels[local] }?.requests?.offer(success)
    }

    private fun keepaliveLoop() {
        var failures = 0
        while (running) {
            try {
                Thread.sleep(aliveInterval.toLong())
                if (!running) return
                val writer = SshWriter()
                writer.writeByte(80)
                writer.writeString("keepalive@openssh.com")
                writer.writeBoolean(true)
                synchronized(kexLock) { writePacket(writer.toByteArray()) }
                val ok = globalReplies.poll((aliveInterval.coerceAtLeast(5_000)).toLong(), TimeUnit.MILLISECONDS)
                if (ok == true) failures = 0 else failures++
                if (failures >= aliveCount) disconnect()
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                failures++
                if (failures >= aliveCount) disconnect()
            }
        }
    }

    private fun kexInit(): ByteArray {
        val writer = SshWriter()
        writer.writeByte(20)
        val cookie = ByteArray(16)
        sshRandom.nextBytes(cookie)
        writer.writeBytes(cookie)
        listOf(KEX, HOST_KEY, CIPHER, CIPHER, MAC, MAC, "none", "none", "", "").forEach { writer.writeString(it) }
        writer.writeBoolean(false)
        writer.writeInt(0)
        return writer.toByteArray()
    }

    private fun parseNameLists(payload: ByteArray): List<List<String>> {
        val reader = SshReader(payload.copyOfRange(17, payload.size))
        val lists = mutableListOf<List<String>>()
        repeat(8) {
            lists += reader.readString().toString(Charsets.US_ASCII).split(',').filter { it.isNotEmpty() }
        }
        reader.readString()
        reader.readString()
        lists += listOf(if (reader.readBoolean()) "1" else "0")
        return lists
    }

    private fun choose(client: List<String>, server: List<String>): String {
        return client.firstOrNull { it in server && !it.startsWith("kex-strict-") } ?: throw IOException("algorithm negotiation fail")
    }

    private fun readPacket(): ByteArray {
        val block = inboundBlock
        val first = decrypt(readRaw(block))
        val length = ((first[0].toInt() and 0xff) shl 24) or ((first[1].toInt() and 0xff) shl 16) or ((first[2].toInt() and 0xff) shl 8) or (first[3].toInt() and 0xff)
        if (length < 1 || length > 512 * 1024) throw IOException("session is down")
        val total = 4 + length
        if (total % block != 0) throw IOException("session is down")
        val rest = if (total > block) decrypt(readRaw(total - block)) else ByteArray(0)
        val packet = first + rest
        if (inboundMac != null) {
            val actual = readRaw(inboundMacLength)
            inboundMac!!.update(sequence(seqIn))
            inboundMac!!.update(packet)
            val expected = inboundMac!!.doFinal()
            if (!expected.contentEquals(actual)) throw IOException("session is down")
        }
        seqIn = (seqIn + 1) and 0xffffffffL
        val padding = packet[4].toInt() and 0xff
        val payloadLength = length - 1 - padding
        if (payloadLength < 1 || 5 + payloadLength > packet.size) throw IOException("session is down")
        return packet.copyOfRange(5, 5 + payloadLength)
    }

    private fun writePacket(payload: ByteArray) {
        val block = outboundBlock
        var padding = block - ((5 + payload.size) % block)
        if (padding < 4) padding += block
        val writer = SshWriter()
        writer.writeInt(1 + payload.size + padding)
        writer.writeByte(padding)
        writer.writeBytes(payload)
        val pad = ByteArray(padding)
        sshRandom.nextBytes(pad)
        writer.writeBytes(pad)
        val packet = writer.toByteArray()
        val encrypted = encrypt(packet)
        output!!.write(encrypted)
        if (outboundMac != null) {
            outboundMac!!.update(sequence(seqOut))
            outboundMac!!.update(packet)
            output!!.write(outboundMac!!.doFinal())
        }
        seqOut = (seqOut + 1) and 0xffffffffL
        output!!.flush()
    }

    internal fun writeLocked(payload: ByteArray) {
        synchronized(kexLock) { writePacket(payload) }
    }

    private fun readRaw(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input!!.read(buffer, offset, length - offset)
            if (read < 0) throw IOException("session is down")
            offset += read
        }
        return buffer
    }

    private fun encrypt(packet: ByteArray): ByteArray {
        val cipher = outboundCipher ?: return packet
        return cipher.update(packet) ?: ByteArray(0)
    }

    private fun decrypt(packet: ByteArray): ByteArray {
        val cipher = inboundCipher ?: return packet
        return cipher.update(packet) ?: ByteArray(0)
    }

    private fun sequence(value: Long): ByteArray {
        return byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    }
}

internal class ChannelRuntime(val localId: Int) {
    val lock = Object()
    var remoteId: Int = -1
    var remoteWindow: Long = 0
    var remoteMax: Int = 32 * 1024
    val localWindow: Long = 2L * 1024 * 1024
    val incoming = ByteQueue { count ->
        localConsumed += count
        if (localConsumed >= 32 * 1024) {
            val adjust = localConsumed
            localConsumed = 0
            adjustWindow(adjust)
        }
    }
    val stderr = ByteQueue {}
    val opened = ArrayBlockingQueue<Boolean>(1)
    val requests = ArrayBlockingQueue<Boolean>(1)
    @Volatile var closed = false
    @Volatile var eof = false
    @Volatile var exitStatus = -1
    private var localConsumed = 0
    var adjustWindow: (Int) -> Unit = {}

    fun takeWindow(wanted: Int): Int {
        synchronized(lock) {
            while (remoteWindow <= 0 && !closed) lock.wait(1000)
            if (remoteWindow <= 0) return 0
            val take = minOf(remoteWindow, wanted.toLong(), remoteMax.toLong()).toInt()
            remoteWindow -= take
            return take
        }
    }

    fun finish() {
        closed = true
        eof = true
        incoming.finish()
        stderr.finish()
        opened.offer(false)
        requests.offer(false)
        synchronized(lock) { lock.notifyAll() }
    }
}

internal class ByteQueue(private val consumed: (Int) -> Unit) : InputStream() {
    private val lock = Object()
    private val chunks = ArrayDeque<ByteArray>()
    private var index = 0
    private var stored = 0
    private var eof = false

    fun add(data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(lock) {
            chunks.addLast(data)
            stored += data.size
            lock.notifyAll()
        }
    }

    var listener: OutputStream? = null

    fun emit(data: ByteArray) {
        listener?.write(data) ?: add(data)
    }

    fun finish() {
        synchronized(lock) {
            eof = true
            lock.notifyAll()
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val count = read(one, 0, 1)
        return if (count < 0) -1 else one[0].toInt() and 0xff
    }

    override fun available(): Int = synchronized(lock) { stored }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val written = synchronized(lock) {
            while (stored == 0 && !eof) lock.wait()
            if (stored == 0) return -1
            var count = 0
            while (count < length && chunks.isNotEmpty()) {
                val chunk = chunks.first()
                val copy = minOf(length - count, chunk.size - index)
                System.arraycopy(chunk, index, buffer, offset + count, copy)
                index += copy
                count += copy
                stored -= copy
                if (index == chunk.size) {
                    chunks.removeFirst()
                    index = 0
                }
            }
            count
        }
        if (written > 0) consumed(written)
        return written
    }
}

class ShellChannel(private val session: SshSession) {
    private var runtime: ChannelRuntime? = null
    private var pty = false
    private var term = "xterm-256color"
    private var cols = 80
    private var rows = 24
    private val environment = linkedMapOf<String, String>()
    val inputStream: InputStream = object : InputStream() {
        private fun source() = runtime?.incoming ?: closedStream()
        override fun read(): Int = source().read()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source().read(buffer, offset, length)
        override fun available(): Int = source().available()
    }
    val outputStream: OutputStream = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()))
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            val current = runtime ?: throw IOException("channel is not opened")
            session.sendData(current, buffer, offset, length)
        }
    }
    val isConnected: Boolean get() = runtime?.let { !it.closed } == true
    val isClosed: Boolean get() = runtime?.eof == true || runtime?.closed == true

    fun setPty(enabled: Boolean) {
        pty = enabled
    }

    fun setPtyType(termName: String, columns: Int, lines: Int, width: Int, height: Int) {
        pty = true
        term = termName
        cols = columns
        rows = lines
        if (width < 0 || height < 0) return
    }

    fun setEnv(name: String, value: String) {
        environment[name] = value
    }

    fun setPtySize(columns: Int, lines: Int, width: Int, height: Int) {
        cols = columns
        rows = lines
        val current = runtime ?: return
        val body = SshWriter()
        body.writeInt(columns)
        body.writeInt(lines)
        body.writeInt(width)
        body.writeInt(height)
        session.request(current, "window-change", false, body.toByteArray(), 1000)
    }

    fun connect(timeoutMs: Int) {
        val current = session.openRuntime(timeoutMs)
        current.adjustWindow = { count ->
            val writer = SshWriter()
            writer.writeByte(93)
            writer.writeInt(current.remoteId)
            writer.writeInt(count)
            session.writeLocked(writer.toByteArray())
        }
        runtime = current
        if (pty) {
            val body = SshWriter()
            body.writeString(term)
            body.writeInt(cols)
            body.writeInt(rows)
            body.writeInt(0)
            body.writeInt(0)
            body.writeString(ByteArray(0))
            session.request(current, "pty-req", false, body.toByteArray(), timeoutMs)
        }
        environment.forEach { (name, value) ->
            val body = SshWriter()
            body.writeString(name)
            body.writeString(value)
            session.request(current, "env", false, body.toByteArray(), timeoutMs)
        }
        session.request(current, "shell", false, ByteArray(0), timeoutMs)
    }

    fun disconnect() {
        runtime?.let { session.closeRuntime(it) }
        runtime = null
    }
}

class ExecChannel(private val session: SshSession) {
    private var runtime: ChannelRuntime? = null
    private var command: String = ""
    val inputStream: InputStream = object : InputStream() {
        private fun source() = runtime?.incoming ?: closedStream()
        override fun read(): Int = source().read()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source().read(buffer, offset, length)
        override fun available(): Int = source().available()
    }
    val isConnected: Boolean get() = runtime?.let { !it.closed } == true
    val isClosed: Boolean get() = runtime?.eof == true || runtime?.closed == true
    val exitStatus: Int get() = runtime?.exitStatus ?: -1

    fun setCommand(value: String) {
        command = value
    }

    fun setErrStream(stream: OutputStream) {
        runtime?.stderr?.listener = stream
        errTarget = stream
    }

    private var errTarget: OutputStream? = null

    fun connect(timeoutMs: Int) {
        val current = session.openRuntime(timeoutMs)
        current.adjustWindow = { count ->
            val writer = SshWriter()
            writer.writeByte(93)
            writer.writeInt(current.remoteId)
            writer.writeInt(count)
            session.writeLocked(writer.toByteArray())
        }
        errTarget?.let { current.stderr.listener = it }
        runtime = current
        val body = SshWriter()
        body.writeString(command)
        session.request(current, "exec", false, body.toByteArray(), timeoutMs)
    }

    fun disconnect() {
        runtime?.let { session.closeRuntime(it) }
    }
}

class SftpChannel(private val session: SshSession) {
    private var runtime: ChannelRuntime? = null
    private val lock = Object()
    private var requestId = 0
    val isConnected: Boolean get() = runtime?.let { !it.closed } == true

    fun connect(timeoutMs: Int) {
        val current = session.openRuntime(timeoutMs)
        current.adjustWindow = { count ->
            val writer = SshWriter()
            writer.writeByte(93)
            writer.writeInt(current.remoteId)
            writer.writeInt(count)
            session.writeLocked(writer.toByteArray())
        }
        runtime = current
        val body = SshWriter()
        body.writeString("sftp")
        session.request(current, "subsystem", true, body.toByteArray(), timeoutMs)
        val init = SshWriter()
        init.writeByte(1)
        init.writeInt(3)
        send(init.toByteArray())
        val version = readPacket()
        if (version.readByte() != 2) throw IOException("channel is not opened")
    }

    fun disconnect() {
        runtime?.let { session.closeRuntime(it) }
    }

    fun pwd(): String {
        val packet = SshWriter()
        packet.writeByte(16)
        packet.writeInt(nextId())
        packet.writeString(".")
        send(packet.toByteArray())
        val response = readPacket()
        if (response.readByte() != 104) throw IOException("sftp")
        response.readInt()
        if (response.readInt() < 1) throw IOException("sftp")
        return response.readString().toString(Charsets.UTF_8)
    }

    fun ls(path: String): List<SftpEntry> {
        val handle = openHandle(11, path, 0)
        val entries = mutableListOf<SftpEntry>()
        try {
            while (true) {
                val packet = SshWriter()
                packet.writeByte(12)
                packet.writeInt(nextId())
                packet.writeString(handle)
                send(packet.toByteArray())
                val response = readPacket()
                val type = response.readByte()
                response.readInt()
                if (type == 101) {
                    if (response.readInt() == 1) break
                    throw IOException(response.readString().toString(Charsets.UTF_8))
                }
                val count = response.readInt()
                repeat(count) {
                    val name = response.readString().toString(Charsets.UTF_8)
                    val longName = response.readString().toString(Charsets.UTF_8)
                    entries += SftpEntry(name, readAttrs(response, longName))
                }
            }
        } finally {
            closeHandle(handle)
        }
        return entries
    }

    fun mkdir(path: String) = simple(14, path)

    fun rm(path: String) = simple(13, path)

    fun rmdir(path: String) = simple(15, path)

    fun rename(from: String, to: String) {
        val packet = SshWriter()
        packet.writeByte(18)
        packet.writeInt(nextId())
        packet.writeString(from)
        packet.writeString(to)
        send(packet.toByteArray())
        expectOk(readPacket())
    }

    fun stat(path: String): SftpAttrs {
        val packet = SshWriter()
        packet.writeByte(17)
        packet.writeInt(nextId())
        packet.writeString(path)
        send(packet.toByteArray())
        val response = readPacket()
        if (response.readByte() != 105) throw IOException("sftp")
        response.readInt()
        return readAttrs(response, "")
    }

    fun get(path: String): InputStream {
        val handle = openHandle(3, path, 1)
        return object : InputStream() {
            private var offset = 0L
            private var buffer = ByteArray(0)
            private var position = 0
            private var eof = false
            override fun read(): Int {
                val one = ByteArray(1)
                val count = read(one, 0, 1)
                return if (count < 0) -1 else one[0].toInt() and 0xff
            }
            override fun read(destination: ByteArray, off: Int, len: Int): Int {
                synchronized(lock) {
                    if (position >= buffer.size && !eof) fill()
                    if (position >= buffer.size) return -1
                    val count = minOf(len, buffer.size - position)
                    System.arraycopy(buffer, position, destination, off, count)
                    position += count
                    return count
                }
            }
            override fun close() {
                synchronized(lock) { closeHandle(handle) }
            }
            private fun fill() {
                val packet = SshWriter()
                packet.writeByte(5)
                packet.writeInt(nextId())
                packet.writeString(handle)
                packet.writeLong(offset)
                packet.writeInt(32 * 1024)
                send(packet.toByteArray())
                val response = readPacket()
                val type = response.readByte()
                response.readInt()
                if (type == 101) {
                    eof = true
                    return
                }
                buffer = response.readString()
                position = 0
                offset += buffer.size
            }
        }
    }

    fun put(path: String, mode: Int): OutputStream {
        if (mode < 0) throw IOException("sftp")
        val handle = openHandle(3, path, 0x2 or 0x8 or 0x10)
        return object : OutputStream() {
            private var offset = 0L
            private val pending = ByteArrayOutputStream()
            override fun write(value: Int) {
                pending.write(value)
                flushPending(false)
            }
            override fun write(buffer: ByteArray, off: Int, len: Int) {
                pending.write(buffer, off, len)
                flushPending(false)
            }
            override fun close() {
                flushPending(true)
                closeHandle(handle)
            }
            private fun flushPending(force: Boolean) {
                while (pending.size() >= 32 * 1024 || (force && pending.size() > 0)) {
                    val data = pending.toByteArray()
                    val count = minOf(32 * 1024, data.size)
                    val packet = SshWriter()
                    packet.writeByte(6)
                    packet.writeInt(nextId())
                    packet.writeString(handle)
                    packet.writeLong(offset)
                    packet.writeString(data.copyOf(count))
                    send(packet.toByteArray())
                    expectOk(readPacket())
                    offset += count
                    pending.reset()
                    if (count < data.size) pending.write(data, count, data.size - count)
                }
            }
        }
    }

    fun put(source: InputStream, path: String, mode: Int) {
        if (mode < 0) throw IOException("sftp")
        val handle = openHandle(3, path, 0x2 or 0x8 or 0x10)
        try {
            val buffer = ByteArray(32 * 1024)
            var offset = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                val packet = SshWriter()
                packet.writeByte(6)
                packet.writeInt(nextId())
                packet.writeString(handle)
                packet.writeLong(offset)
                packet.writeString(buffer.copyOf(count))
                send(packet.toByteArray())
                expectOk(readPacket())
                offset += count
            }
        } finally {
            closeHandle(handle)
        }
    }

    private fun simple(type: Int, path: String) {
        val packet = SshWriter()
        packet.writeByte(type)
        packet.writeInt(nextId())
        packet.writeString(path)
        if (type == 14) packet.writeInt(0)
        send(packet.toByteArray())
        expectOk(readPacket())
    }

    private fun openHandle(type: Int, path: String, flags: Int): ByteArray {
        val packet = SshWriter()
        packet.writeByte(type)
        packet.writeInt(nextId())
        packet.writeString(path)
        if (type == 3) {
            packet.writeInt(flags)
            packet.writeInt(0)
        }
        send(packet.toByteArray())
        val response = readPacket()
        if (response.readByte() != 102) {
            response.readInt()
            throw IOException(runCatching { response.readString().toString(Charsets.UTF_8) }.getOrDefault("sftp"))
        }
        response.readInt()
        return response.readString()
    }

    private fun closeHandle(handle: ByteArray) {
        val packet = SshWriter()
        packet.writeByte(4)
        packet.writeInt(nextId())
        packet.writeString(handle)
        send(packet.toByteArray())
        expectOk(readPacket())
    }

    private fun expectOk(response: SshReader) {
        val type = response.readByte()
        response.readInt()
        if (type == 101 && response.readInt() == 0) return
        throw IOException(runCatching { response.readString().toString(Charsets.UTF_8) }.getOrDefault("sftp"))
    }

    private fun readAttrs(reader: SshReader, longName: String): SftpAttrs {
        val flags = reader.readInt()
        val size = if (flags and 1 != 0) reader.readLong() else 0L
        val uid = if (flags and 2 != 0) reader.readInt() else 0
        val gid = if (flags and 2 != 0) reader.readInt() else 0
        val permissions = if (flags and 4 != 0) reader.readInt() else 0
        if (flags and 8 != 0) {
            reader.readInt()
        }
        val mtime = if (flags and 8 != 0) reader.readInt() else 0
        if (flags and 0x80000000.toInt() != 0) {
            val count = reader.readInt()
            repeat(count) {
                reader.readString()
                reader.readString()
            }
        }
        val directory = if (flags and 4 != 0) permissions and 0xf000 == 0x4000 else longName.startsWith("d")
        val text = if (flags and 4 != 0) modeText(permissions) else if (longName.length >= 10) longName.substring(0, 10) else "----------"
        return SftpAttrs(directory, size, text, mtime, uid, gid)
    }

    private fun modeText(mode: Int): String {
        val chars = CharArray(10)
        chars[0] = when (mode and 0xf000) {
            0x4000 -> 'd'
            0xa000 -> 'l'
            else -> '-'
        }
        val symbols = charArrayOf('r', 'w', 'x')
        for (index in 0 until 9) chars[index + 1] = if (mode and (1 shl (8 - index)) != 0) symbols[index % 3] else '-'
        return String(chars)
    }

    private fun send(payload: ByteArray) {
        val current = runtime ?: throw IOException("channel is not opened")
        val framed = SshWriter()
        framed.writeInt(payload.size)
        framed.writeBytes(payload)
        val bytes = framed.toByteArray()
        session.sendData(current, bytes, 0, bytes.size)
    }

    private fun readPacket(): SshReader {
        val current = runtime ?: throw IOException("channel is not opened")
        val header = ByteArray(4)
        readFully(current.incoming, header)
        val length = ((header[0].toInt() and 0xff) shl 24) or ((header[1].toInt() and 0xff) shl 16) or ((header[2].toInt() and 0xff) shl 8) or (header[3].toInt() and 0xff)
        if (length < 1 || length > 1024 * 1024) throw IOException("session is down")
        val body = ByteArray(length)
        readFully(current.incoming, body)
        return SshReader(body)
    }

    private fun readFully(source: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = source.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("pipe closed")
            offset += count
        }
    }

    private fun nextId(): Int = requestId++

    companion object {
        const val OVERWRITE = 0
    }
}

class SftpEntry(val filename: String, val attrs: SftpAttrs)

class SftpAttrs(
    val isDir: Boolean,
    val size: Long,
    val permissionsString: String,
    val mTime: Int,
    val uId: Int,
    val gId: Int
)

private data class KexResult(val secret: BigInteger, val hash: ByteArray, val hostKey: ByteArray, val signature: ByteArray)

private fun closedStream(): InputStream = object : InputStream() {
    override fun read(): Int = -1
}

private fun cipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
    val value = Cipher.getInstance("AES/CTR/NoPadding")
    value.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return value
}

private fun mac(name: String, key: ByteArray): Mac {
    val algorithm = if (name.contains("512")) "HmacSHA512" else "HmacSHA256"
    val value = Mac.getInstance(algorithm)
    value.init(SecretKeySpec(key, algorithm))
    return value
}

private fun macLength(name: String): Int = if (name.contains("512")) 64 else 32

private fun fixed(value: BigInteger, size: Int): ByteArray {
    val raw = value.toByteArray()
    val significant = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    val out = ByteArray(size)
    System.arraycopy(significant, 0, out, size - significant.size, significant.size)
    return out
}

private const val KEX = "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1,kex-strict-c-v00@openssh.com"
private const val HOST_KEY = "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ecdsa-sha2-nistp256,ssh-rsa"
private const val CIPHER = "aes128-ctr,aes256-ctr"
private const val MAC = "hmac-sha2-256,hmac-sha2-512"
private val GROUP14 = BigInteger(
    "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF",
    16
)
