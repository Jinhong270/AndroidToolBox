package com.jh270.toolbox.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jh270.toolbox.data.ArchiveEntryItem
import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.ChecksumResult
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.data.RemoteFile
import com.jh270.toolbox.data.RemotePath
import com.jh270.toolbox.data.RemotePlatform
import com.jh270.toolbox.data.RemotePlatformDetector
import com.jh270.toolbox.data.SshConfig
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Vector
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SshRepository {
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null

    private var shellChannel: ChannelShell? = null
    private var shellInputStream: InputStream? = null
    private var shellOutputStream: OutputStream? = null
    private var shellReadingThread: Thread? = null
    private var shellPending: ByteArray = ByteArray(0)

    @Volatile
    private var shellClosingLocally = false

    private val gate = Mutex()
    private var lastConfig: SshConfig? = null

    @Volatile
    private var reconnectNotified = false

    @Volatile
    var remotePlatform: RemotePlatform = RemotePlatform.UNKNOWN
        private set

    companion object {
        init {
            installSecurityProviders()
        }

        private fun installSecurityProviders() {
            try {
                val bc = org.bouncycastle.jce.provider.BouncyCastleProvider()
                java.security.Security.removeProvider(bc.name)
                java.security.Security.insertProviderAt(bc, 1)
            } catch (_: Exception) {}
            try {
                val eddsa = net.i2p.crypto.eddsa.EdDSASecurityProvider()
                java.security.Security.removeProvider(eddsa.name)
                java.security.Security.insertProviderAt(eddsa, 2)
            } catch (_: Exception) {}
        }
    }

    suspend fun connect(config: SshConfig): Result<String> = withContext(Dispatchers.IO) {
        gate.withLock { connectLocked(config, recovering = false) }
    }

    suspend fun reconnect(): Result<String> = withContext(Dispatchers.IO) {
        gate.withLock {
            val config = lastConfig ?: return@withLock Result.failure(IllegalStateException("没有可恢复的连接"))
            connectLocked(config, recovering = true)
        }
    }

    suspend fun probeAlive(): Boolean = withContext(Dispatchers.IO) {
        if (session == null || sftpChannel == null) return@withContext false
        gate.withLock {
            try {
                val sess = session ?: return@withLock false
                val channel = sftpChannel ?: return@withLock false
                if (!sess.isConnected || !channel.isConnected) return@withLock false
                channel.pwd()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun consumeReconnectNotice(): Boolean {
        if (!reconnectNotified) return false
        reconnectNotified = false
        return true
    }

    private fun connectLocked(config: SshConfig, recovering: Boolean): Result<String> {
        var openedSession: Session? = null
        var openedChannel: ChannelSftp? = null
        return try {
            disconnectInternal()
            remotePlatform = RemotePlatform.UNKNOWN
            val jsch = JSch()

            if (config.authType == AuthType.PRIVATE_KEY && config.privateKey.isNotBlank()) {
                val keyContent = normalizePrivateKey(config.privateKey)
                val keyBytes = keyContent.toByteArray(Charsets.UTF_8)

                val probe = KeyPair.load(jsch, keyBytes, null)
                try {
                    if (probe.isEncrypted) {
                        if (config.passphrase.isBlank()) {
                            throw IllegalArgumentException("私钥已加密：请填写私钥密码")
                        }
                        if (!probe.decrypt(config.passphrase)) {
                            throw IllegalArgumentException("私钥密码错误，无法解密私钥")
                        }
                    }
                } finally {
                    probe.dispose()
                }

                val passphraseBytes = if (config.passphrase.isNotBlank()) config.passphrase.toByteArray(Charsets.UTF_8) else null
                jsch.addIdentity("toolbox-key", keyBytes, null, passphraseBytes)
            }

            val newSession = jsch.getSession(config.username, config.host, config.port)
            openedSession = newSession
            if (config.authType == AuthType.PASSWORD) {
                newSession.setPassword(config.password)
            }

            val userInfo = MyUserInfo(
                password = if (config.authType == AuthType.PASSWORD) config.password else null,
                passphrase = if (config.authType == AuthType.PRIVATE_KEY && config.passphrase.isNotBlank()) config.passphrase else null
            )
            newSession.setUserInfo(userInfo)

            val properties = java.util.Properties()
            properties["StrictHostKeyChecking"] = "no"
            if (config.authType == AuthType.PASSWORD) {
                properties["PreferredAuthentications"] = "password,keyboard-interactive,publickey"
            } else {
                properties["PreferredAuthentications"] = "publickey,password,keyboard-interactive"
            }
            newSession.setConfig(properties)
            newSession.timeout = 20000
            newSession.setServerAliveInterval(15_000)
            newSession.setServerAliveCountMax(4)
            newSession.connect(20000)

            val channel = newSession.openChannel("sftp") as ChannelSftp
            openedChannel = channel
            channel.connect(20000)
            val pwd = RemotePath.normalize(channel.pwd() ?: "/")
            remotePlatform = detectRemotePlatform(newSession, pwd)

            session = newSession
            sftpChannel = channel
            lastConfig = config
            if (recovering) reconnectNotified = true
            Result.success(pwd)
        } catch (e: Exception) {
            try {
                openedChannel?.disconnect()
            } catch (_: Exception) {}
            try {
                openedSession?.disconnect()
            } catch (_: Exception) {}
            disconnectInternal()
            Result.failure(Exception(formatSshException(e)))
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        gate.withLock {
            lastConfig = null
            reconnectNotified = false
            disconnectInternal()
        }
    }

    private suspend fun <T> remoteCall(block: () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        gate.withLock {
            val first = invokeRemote(block)
            if (first.isSuccess) return@withLock first
            val error = first.exceptionOrNull() ?: return@withLock first
            if (error is CancellationException) throw error
            ensureActive()
            if (!shouldReconnect(error)) return@withLock first
            val config = lastConfig ?: return@withLock first
            val restored = connectLocked(config, recovering = true)
            if (restored.isFailure) {
                val failure = restored.exceptionOrNull() ?: error
                if (failure is CancellationException) throw failure
                return@withLock Result.failure(failure)
            }
            ensureActive()
            invokeRemote(block)
        }
    }

    private fun <T> invokeRemote(block: () -> Result<T>): Result<T> {
        return try {
            val result = block()
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shouldReconnect(error: Throwable): Boolean {
        if (isTransportFailure(error)) return true
        return session?.isConnected != true || sftpChannel?.isConnected != true
    }

    private fun isTransportFailure(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }
            .joinToString(" ") { "${it.javaClass.name} ${it.message}" }
            .lowercase()
        val markers = listOf(
            "session is down",
            "channel is not opened",
            "channel is closed",
            "pipe closed",
            "broken pipe",
            "connection reset",
            "connection abort",
            "socket closed",
            "socket is closed",
            "inputstream is closed",
            "outputstream is closed",
            "end of io stream",
            "not connected",
            "session is not connected",
            "software caused connection abort",
            "connection timed out",
            "sockettimeoutexception",
            "未连接至 ssh"
        )
        return markers.any { text.contains(it) }
    }

    private fun disconnectInternal() {
        closeShellSessionInternal()

        try {
            sftpChannel?.disconnect()
        } catch (_: Exception) {}
        sftpChannel = null

        try {
            session?.disconnect()
        } catch (_: Exception) {}
        session = null

        remotePlatform = RemotePlatform.UNKNOWN
    }

    @Suppress("unused")
    fun isConnected(): Boolean {
        return session?.isConnected == true && sftpChannel?.isConnected == true
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun startShellSession(cols: Int, rows: Int, onOutput: (String) -> Unit, onExit: () -> Unit): Result<Unit> = remoteCall {
        runCatching {
            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            closeShellSessionInternal()
            shellClosingLocally = false

            val channel = sess.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("xterm-256color", cols, rows, 0, 0)
            try {
                channel.setEnv("TERM", "xterm-256color")
            } catch (_: Exception) {}
            try {
                channel.setEnv("LANG", "en_US.UTF-8")
            } catch (_: Exception) {}

            val inStream = channel.inputStream
            val outStream = channel.outputStream

            channel.connect(15000)

            shellChannel = channel
            shellInputStream = inStream
            shellOutputStream = outStream
            shellPending = ByteArray(0)
            if (remotePlatform == RemotePlatform.WINDOWS) {
                outStream.write("chcp 65001\r".toByteArray(Charsets.US_ASCII))
                outStream.flush()
            }

            shellReadingThread = Thread {
                val buffer = ByteArray(4096)
                var eof = false
                try {
                    while (shellChannel?.isConnected == true) {
                        val read = inStream.read(buffer)
                        if (read < 0) {
                            eof = true
                            break
                        }
                        if (read > 0) {
                            val text = decodeShell(buffer, read)
                            if (text.isNotEmpty()) onOutput(text)
                        }
                    }
                } catch (_: Exception) {}
                if (eof && !shellClosingLocally) {
                    onExit()
                }
            }.apply { start() }
        }
    }

    suspend fun sendShellRaw(bytes: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            shellOutputStream?.let { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }

    suspend fun sendShellInput(input: String) = sendShellRaw(input.toByteArray(Charsets.UTF_8))

    suspend fun sendShellControlChar(char: Char) = withContext(Dispatchers.IO) {
        runCatching {
            val controlCode = (char.uppercaseChar() - 'A' + 1).toByte()
            shellOutputStream?.let { out ->
                out.write(byteArrayOf(controlCode))
                out.flush()
            }
        }
    }

    suspend fun resizeTerminal(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        runCatching {
            shellChannel?.setPtySize(cols, rows, 0, 0)
        }
    }

    suspend fun closeShellSession() = withContext(Dispatchers.IO) {
        gate.withLock { closeShellSessionInternal() }
    }

    private fun closeShellSessionInternal() {
        shellClosingLocally = true

        try {
            shellReadingThread?.interrupt()
        } catch (_: Exception) {}
        shellReadingThread = null

        try {
            shellOutputStream?.close()
        } catch (_: Exception) {}
        shellOutputStream = null

        try {
            shellInputStream?.close()
        } catch (_: Exception) {}
        shellInputStream = null

        try {
            shellChannel?.disconnect()
        } catch (_: Exception) {}
        shellChannel = null
        shellPending = ByteArray(0)
    }

    suspend fun listFiles(path: String): Result<List<RemoteFile>> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val targetPath = RemotePath.normalize(if (path.isBlank()) "/" else path)

            @Suppress("UNCHECKED_CAST")
            val entries = channel.ls(targetPath) as Vector<ChannelSftp.LsEntry>

            val fileItems = mutableListOf<RemoteFile>()
            for (entry in entries) {
                val name = entry.filename
                if (name == "." || name == "..") continue

                val attrs = entry.attrs
                val isDir = attrs.isDir
                val size = attrs.size
                val permissions = attrs.permissionsString
                val mTime = attrs.mTime.toLong() * 1000

                val owner = attrs.uId.toString()
                val group = attrs.gId.toString()

                val fullPath = RemotePath.join(targetPath, name)

                val type = determineFileType(name, isDir)

                fileItems.add(
                    RemoteFile(
                        name = name,
                        path = fullPath,
                        isDirectory = isDir,
                        size = size,
                        permissions = permissions,
                        owner = owner,
                        group = group,
                        modifiedTime = mTime,
                        fileType = type
                    )
                )
            }

            fileItems.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            val finalResult = mutableListOf<RemoteFile>()
            if (!RemotePath.isRoot(targetPath)) {
                val parentPath = RemotePath.parent(targetPath)
                finalResult.add(
                    RemoteFile(
                        name = "..",
                        path = parentPath,
                        isDirectory = true,
                        size = 0,
                        permissions = "drwxr-xr-x",
                        owner = "root",
                        group = "root",
                        modifiedTime = 0,
                        fileType = FileType.DIRECTORY
                    )
                )
            }

            finalResult.addAll(fileItems)
            finalResult
        }
    }

    suspend fun createFolder(parentPath: String, folderName: String): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val newFolderPath = RemotePath.join(parentPath, folderName)
            channel.mkdir(newFolderPath)
        }
    }

    suspend fun createFile(parentPath: String, fileName: String): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val newFilePath = RemotePath.join(parentPath, fileName)
            val inputStream = ByteArrayInputStream(ByteArray(0))
            channel.put(inputStream, newFilePath)
        }
    }

    suspend fun previewFile(file: RemoteFile, maxBytes: Int = 5242880): Result<FilePreview> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")

            if (file.isDirectory) {
                return@runCatching FilePreview(
                    name = file.name,
                    path = file.path,
                    size = file.size,
                    permissions = file.permissions,
                    modifiedTime = file.modifiedTime,
                    fileType = FileType.DIRECTORY,
                    errorMessage = "文件夹无法作为文件预览"
                )
            }

            val fileType = if (file.fileType == FileType.UNKNOWN) {
                determineFileType(file.name, false)
            } else {
                file.fileType
            }

            val readLimit = if (fileType == FileType.TEXT || fileType == FileType.EXECUTABLE) 204800 else maxBytes
            val outputStream = ByteArrayOutputStream()
            val inputStream = channel.get(file.path)

            val buffer = ByteArray(4096)
            var bytesReadTotal = 0
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                if (bytesReadTotal + read > readLimit) {
                    val allowed = readLimit - bytesReadTotal
                    if (allowed > 0) {
                        outputStream.write(buffer, 0, allowed)
                    }
                    break
                } else {
                    outputStream.write(buffer, 0, read)
                    bytesReadTotal += read
                }
            }
            inputStream.close()

            val bytes = outputStream.toByteArray()

            when (fileType) {
                FileType.IMAGE -> {
                    FilePreview(
                        name = file.name,
                        path = file.path,
                        size = file.size,
                        permissions = file.permissions,
                        modifiedTime = file.modifiedTime,
                        imageData = bytes,
                        fileType = FileType.IMAGE
                    )
                }
                FileType.TEXT, FileType.EXECUTABLE -> {
                    val text = decodeBytes(bytes, remotePlatform == RemotePlatform.WINDOWS)
                    FilePreview(
                        name = file.name,
                        path = file.path,
                        size = file.size,
                        permissions = file.permissions,
                        modifiedTime = file.modifiedTime,
                        content = text,
                        fileType = fileType
                    )
                }
                else -> {
                    val isBinary = isBinaryContent(bytes)
                    if (isBinary) {
                        FilePreview(
                            name = file.name,
                            path = file.path,
                            size = file.size,
                            permissions = file.permissions,
                            modifiedTime = file.modifiedTime,
                            fileType = fileType
                        )
                    } else {
                        val text = decodeBytes(bytes, remotePlatform == RemotePlatform.WINDOWS)
                        FilePreview(
                            name = file.name,
                            path = file.path,
                            size = file.size,
                            permissions = file.permissions,
                            modifiedTime = file.modifiedTime,
                            content = text,
                            fileType = fileType
                        )
                    }
                }
            }
        }
    }

    suspend fun listArchiveEntries(file: RemoteFile): Result<List<ArchiveEntryItem>> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val lowerName = file.name.lowercase()
            when {
                lowerName.endsWith(".zip") -> readZipEntries(channel, file.path)
                lowerName.endsWith(".7z") -> readSevenZEntries(channel, file.path)
                else -> listTarEntries(channel, file)
            }
        }
    }

    suspend fun previewArchiveEntry(archiveFile: RemoteFile, entryPath: String): Result<FilePreview> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val lowerName = archiveFile.name.lowercase()
            val outputStream = ByteArrayOutputStream()

            if (lowerName.endsWith(".zip")) {
                val inputStream = channel.get(archiveFile.path)
                val zipIn = openZip(inputStream)
                var entry: ZipEntry?
                var found = false
                val buffer = ByteArray(4096)
                while (zipIn.nextEntry.also { entry = it } != null) {
                    val e = entry!!
                    if (e.name == entryPath || e.name.trimEnd('/') == entryPath.trimEnd('/')) {
                        found = true
                        var read: Int
                        while (zipIn.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        zipIn.closeEntry()
                        break
                    }
                    zipIn.closeEntry()
                }
                zipIn.close()
                inputStream.close()

                if (!found) throw IllegalStateException("压缩包中未找到文件: $entryPath")
            } else if (lowerName.endsWith(".7z")) {
                outputStream.write(readSevenZEntryContent(channel, archiveFile.path, entryPath))
            } else {
                try {
                    if (remotePlatform == RemotePlatform.WINDOWS) {
                        throw IllegalStateException("use local tar")
                    }
                    val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
                    val cmd = tarReadCommand(archiveFile, entryPath)
                    val execChan = sess.openChannel("exec") as ChannelExec
                    execChan.setCommand(cmd)
                    val inStream = execChan.inputStream
                    execChan.connect(15000)
                    val buffer = ByteArray(4096)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    execChan.disconnect()
                    if (outputStream.size() == 0) throw IllegalStateException("empty tar entry")
                } catch (e: Exception) {
                    if (isTransportFailure(e)) throw e
                    outputStream.reset()
                    outputStream.write(readTarEntryLocal(channel, archiveFile, entryPath))
                }
            }

            val bytes = outputStream.toByteArray()
            val innerName = entryPath.trimEnd('/').substringAfterLast('/')
            val innerType = determineFileType(innerName, false)

            if (innerType == FileType.IMAGE) {
                FilePreview(
                    name = innerName,
                    path = "${archiveFile.name}/$entryPath",
                    size = bytes.size.toLong(),
                    permissions = "-rw-r--r--",
                    modifiedTime = System.currentTimeMillis(),
                    imageData = bytes,
                    fileType = FileType.IMAGE
                )
            } else {
                val isBin = isBinaryContent(bytes)
                if (isBin) {
                    FilePreview(
                        name = innerName,
                        path = "${archiveFile.name}/$entryPath",
                        size = bytes.size.toLong(),
                        permissions = "-rw-r--r--",
                        modifiedTime = System.currentTimeMillis(),
                        fileType = FileType.BINARY
                    )
                } else {
                    val text = decodeBytes(bytes, remotePlatform == RemotePlatform.WINDOWS)
                    FilePreview(
                        name = innerName,
                        path = "${archiveFile.name}/$entryPath",
                        size = bytes.size.toLong(),
                        permissions = "-rw-r--r--",
                        modifiedTime = System.currentTimeMillis(),
                        content = text,
                        fileType = FileType.TEXT
                    )
                }
            }
        }
    }

    suspend fun saveFileContent(path: String, content: String): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val bytes = content.toByteArray(Charsets.UTF_8)
            val inputStream = ByteArrayInputStream(bytes)
            channel.put(inputStream, path, ChannelSftp.OVERWRITE)
        }
    }

    suspend fun deleteFileOrFolder(file: RemoteFile): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            if (file.isDirectory) {
                deleteRecursive(channel, file.path)
            } else {
                channel.rm(file.path)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deleteRecursive(channel: ChannelSftp, path: String) {
        val attrs = channel.stat(path)
        if (attrs.isDir) {
            val entries = channel.ls(path) as Vector<ChannelSftp.LsEntry>
            for (entry in entries) {
                val name = entry.filename
                if (name == "." || name == "..") continue
                val childPath = RemotePath.join(path, name)
                deleteRecursive(channel, childPath)
            }
            channel.rmdir(path)
        } else {
            channel.rm(path)
        }
    }

    suspend fun renameFileOrFolder(file: RemoteFile, newName: String): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val newPath = RemotePath.join(RemotePath.parent(file.path), newName)
            channel.rename(file.path, newPath)
        }
    }

    suspend fun calculateChecksums(file: RemoteFile): Result<ChecksumResult> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val md5Digest = MessageDigest.getInstance("MD5")
            val sha256Digest = MessageDigest.getInstance("SHA-256")

            val inputStream = channel.get(file.path)
            val buffer = ByteArray(16384)
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                md5Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
            }
            inputStream.close()

            val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
            val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }

            ChecksumResult(
                fileName = file.name,
                filePath = file.path,
                md5 = md5Hex,
                sha256 = sha256Hex
            )
        }
    }

    private fun normalizePrivateKey(key: String): String {
        var normalized = key.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isNotEmpty() && !normalized.endsWith("\n")) {
            normalized += "\n"
        }
        return normalized
    }

    private fun shellQuote(arg: String): String {
        return if (remotePlatform == RemotePlatform.WINDOWS) {
            "\"" + arg.replace("\"", "\"\"") + "\""
        } else {
            "'" + arg.replace("'", "'\\''") + "'"
        }
    }

    private fun remoteShellPath(path: String): String {
        return if (remotePlatform == RemotePlatform.WINDOWS) RemotePath.toWindowsPath(path) else path
    }

    private fun shellArg(path: String): String = shellQuote(remoteShellPath(path))

    private fun runExecCapture(sess: Session, command: String, timeoutMs: Int = 120000): String {
        val channel = sess.openChannel("exec") as ChannelExec
        try {
            channel.setCommand(command)
            val err = ByteArrayOutputStream()
            channel.setErrStream(err)
            val input = channel.inputStream
            channel.connect(15000)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                while (input.available() > 0) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                if (channel.isClosed) {
                    while (input.available() > 0) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                    break
                }
                if (System.currentTimeMillis() > deadline) break
                Thread.sleep(25)
            }
            val stdout = decodeBytes(out.toByteArray(), remotePlatform == RemotePlatform.WINDOWS)
            if (stdout.isNotBlank()) return stdout
            return decodeBytes(err.toByteArray(), remotePlatform == RemotePlatform.WINDOWS)
        } finally {
            try {
                channel.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun detectRemotePlatform(sess: Session, homePath: String): RemotePlatform {
        val banner = sess.serverVersion ?: ""
        val quick = RemotePlatformDetector.fromSignals(banner, homePath, "")
        if (quick == RemotePlatform.WINDOWS) return RemotePlatform.WINDOWS
        val uname = runExecCapture(sess, "uname -s", 8000)
        val fromUname = RemotePlatformDetector.fromSignals(banner, homePath, uname)
        if (fromUname != RemotePlatform.UNKNOWN) return fromUname
        val ver = runExecCapture(sess, "cmd.exe /d /c ver", 8000)
        val fromVer = RemotePlatformDetector.fromSignals(banner, homePath, ver)
        return if (fromVer == RemotePlatform.WINDOWS) RemotePlatform.WINDOWS else RemotePlatform.UNIX
    }

    private fun decodeBytes(bytes: ByteArray, windows: Boolean): String {
        if (bytes.isEmpty()) return ""
        val utf8 = try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
        if (utf8 != null && !windows) return utf8
        if (utf8 != null && windows && !utf8.contains('\uFFFD')) return utf8
        return try {
            String(bytes, charset("GBK"))
        } catch (_: Exception) {
            utf8 ?: String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun decodeShell(buffer: ByteArray, length: Int): String {
        val merged = ByteArray(shellPending.size + length)
        System.arraycopy(shellPending, 0, merged, 0, shellPending.size)
        System.arraycopy(buffer, 0, merged, shellPending.size, length)
        val tail = incompleteUtf8Tail(merged)
        val usable = merged.size - tail
        shellPending = if (tail == 0) ByteArray(0) else merged.copyOfRange(usable, merged.size)
        if (usable <= 0) return ""
        val slice = if (usable == merged.size) merged else merged.copyOf(usable)
        return decodeBytes(slice, remotePlatform == RemotePlatform.WINDOWS)
    }

    private fun incompleteUtf8Tail(bytes: ByteArray): Int {
        var index = bytes.size - 1
        var continuation = 0
        while (index >= 0 && continuation < 3 && (bytes[index].toInt() and 0xC0) == 0x80) {
            index--
            continuation++
        }
        if (index < 0) return 0
        val lead = bytes[index].toInt() and 0xFF
        val need = when {
            lead and 0x80 == 0 -> 0
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            else -> 0
        }
        if (need == 0) return 0
        val have = bytes.size - index
        return if (have < need) have else 0
    }

    private fun psQuote(value: String): String = "'" + value.replace("'", "''") + "'"

    private fun ps(script: String): String {
        val body = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding \$false; $script"
        return "powershell.exe -NoProfile -NonInteractive -Command ${psQuote(body)}"
    }

    private fun windowsPath(path: String): String = RemotePath.toWindowsPath(path)

    private fun openZip(input: InputStream): ZipInputStream {
        val charset = if (remotePlatform == RemotePlatform.WINDOWS) charset("GBK") else Charsets.UTF_8
        return ZipInputStream(input, charset)
    }

    private fun parseUnzipListing(output: String): List<ArchiveEntryItem> {
        val entries = mutableListOf<ArchiveEntryItem>()
        var parsingFiles = false
        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("----")) {
                parsingFiles = !parsingFiles
                return@forEach
            }
            if (parsingFiles && trimmed.isNotEmpty()) {
                val tokens = trimmed.split(Regex("\\s+"))
                if (tokens.size >= 4) {
                    val size = tokens[0].toLongOrNull() ?: 0L
                    val entryPath = tokens.drop(3).joinToString(" ")
                    val isDir = entryPath.endsWith("/")
                    val rawName = entryPath.trimEnd('/')
                    if (rawName.isNotBlank() && !rawName.startsWith("----")) {
                        entries.add(
                            ArchiveEntryItem(
                                path = entryPath,
                                name = rawName.substringAfterLast('/'),
                                isDirectory = isDir,
                                size = if (isDir) 0L else size
                            )
                        )
                    }
                }
            }
        }
        return entries
    }

    private fun parseTarVerboseListing(output: String): List<ArchiveEntryItem> {
        val entries = mutableListOf<ArchiveEntryItem>()
        output.lines().forEach { line ->
            val cleanLine = line.trim()
            if (cleanLine.isBlank()) return@forEach
            val tokens = cleanLine.split(Regex("\\s+"))
            if (tokens.size < 6) return@forEach

            val perms = tokens[0]
            val size: Long
            val entryPath: String
            if (tokens[1].contains('/')) {
                size = tokens[2].toLongOrNull() ?: 0L
                entryPath = tokens.drop(5).joinToString(" ")
            } else if (tokens.size >= 9) {
                size = tokens[4].toLongOrNull() ?: 0L
                entryPath = tokens.drop(8).joinToString(" ")
            } else {
                return@forEach
            }

            val isDir = perms.startsWith("d") || entryPath.endsWith("/")
            val rawName = entryPath.trimEnd('/')
            if (rawName.isNotBlank()) {
                entries.add(
                    ArchiveEntryItem(
                        path = entryPath,
                        name = rawName.substringAfterLast('/'),
                        isDirectory = isDir,
                        size = if (isDir) 0L else size
                    )
                )
            }
        }
        return entries
    }

    private fun parsePathOnlyListing(output: String): List<ArchiveEntryItem> {
        val entries = mutableListOf<ArchiveEntryItem>()
        output.lines().forEach { line ->
            val cleanLine = line.trim()
            if (cleanLine.isBlank()) return@forEach
            val isDir = cleanLine.endsWith("/")
            val entryName = cleanLine.trimEnd('/')
            if (entryName.isNotBlank()) {
                entries.add(
                    ArchiveEntryItem(
                        path = cleanLine,
                        name = entryName.substringAfterLast('/'),
                        isDirectory = isDir,
                        size = 0L
                    )
                )
            }
        }
        return entries
    }

    private fun readZipEntries(channel: ChannelSftp, path: String): List<ArchiveEntryItem> {
        val entries = mutableListOf<ArchiveEntryItem>()
        val inputStream = channel.get(path)
        val zipIn = openZip(inputStream)
        var entry: ZipEntry?
        while (zipIn.nextEntry.also { entry = it } != null) {
            val e = entry!!
            val rawName = e.name.trimEnd('/')
            if (rawName.isNotBlank()) {
                entries.add(
                    ArchiveEntryItem(
                        path = e.name,
                        name = rawName.substringAfterLast('/'),
                        isDirectory = e.isDirectory,
                        size = if (e.isDirectory) 0L else if (e.size >= 0) e.size else 0L
                    )
                )
            }
            zipIn.closeEntry()
        }
        zipIn.close()
        inputStream.close()
        return entries
    }

    private fun downloadToTemp(channel: ChannelSftp, path: String): File {
        val tempFile = File.createTempFile("toolbox_archive_", ".tmp")
        try {
            val fos = FileOutputStream(tempFile)
            val input = channel.get(path)
            val buffer = ByteArray(16384)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                fos.write(buffer, 0, read)
            }
            input.close()
            fos.close()
            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun readSevenZEntries(channel: ChannelSftp, path: String): List<ArchiveEntryItem> {
        val tempFile = downloadToTemp(channel, path)
        try {
            val entries = mutableListOf<ArchiveEntryItem>()
            SevenZFile.Builder().setFile(tempFile).get().use { sevenZ ->
                var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                while (entry != null) {
                    val e = entry
                    val rawName = e.name.trimEnd('/')
                    if (rawName.isNotBlank()) {
                        entries.add(
                            ArchiveEntryItem(
                                path = e.name,
                                name = rawName.substringAfterLast('/'),
                                isDirectory = e.isDirectory,
                                size = if (e.isDirectory) 0L else if (e.size >= 0) e.size else 0L
                            )
                        )
                    }
                    entry = sevenZ.nextEntry
                }
            }
            return entries
        } finally {
            tempFile.delete()
        }
    }

    private fun readSevenZEntryContent(channel: ChannelSftp, archivePath: String, entryPath: String): ByteArray {
        val tempFile = downloadToTemp(channel, archivePath)
        try {
            SevenZFile.Builder().setFile(tempFile).get().use { sevenZ ->
                var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == entryPath || name.trimEnd('/') == entryPath.trimEnd('/')) {
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(16384)
                        var read: Int
                        while (sevenZ.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                        return out.toByteArray()
                    }
                    entry = sevenZ.nextEntry
                }
            }
            throw IllegalStateException("压缩包中未找到文件: $entryPath")
        } finally {
            tempFile.delete()
        }
    }

    private fun buildCompressCommand(parentDir: String, fileName: String, format: String): String {
        if (remotePlatform == RemotePlatform.WINDOWS) {
            val winDir = windowsPath(parentDir).trimEnd('\\')
            return when (format) {
                "tar.gz" -> ps("tar -czf ${psQuote("$winDir\\$fileName.tar.gz")} -C ${psQuote(winDir)} ${psQuote(fileName)}")
                "tar" -> ps("tar -cf ${psQuote("$winDir\\$fileName.tar")} -C ${psQuote(winDir)} ${psQuote(fileName)}")
                "zip" -> throw IllegalArgumentException("压缩 zip 在 Windows 上使用本地打包")
                else -> throw IllegalArgumentException("不支持的压缩格式: $format")
            }
        }

        val escapedDir = shellQuote(parentDir)
        val escapedFile = shellQuote(fileName)
        return when (format) {
            "zip" -> "cd $escapedDir && zip -r ${shellQuote("$fileName.zip")} $escapedFile"
            "tar.gz" -> "cd $escapedDir && tar -czf ${shellQuote("$fileName.tar.gz")} $escapedFile"
            "tar" -> "cd $escapedDir && tar -cf ${shellQuote("$fileName.tar")} $escapedFile"
            else -> throw IllegalArgumentException("不支持的压缩格式: $format")
        }
    }

    private fun buildDecompressCommand(parentDir: String, fileName: String, lowerName: String): String {
        if (remotePlatform == RemotePlatform.WINDOWS) {
            val winDir = windowsPath(parentDir).trimEnd('\\')
            val archive = psQuote("$winDir\\$fileName")
            val folder = psQuote(winDir)
            return when {
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> ps("tar -xzf $archive -C $folder")
                lowerName.endsWith(".tar") -> ps("tar -xf $archive -C $folder")
                else -> throw IllegalArgumentException("该格式在 Windows 上改为本地解压")
            }
        }

        val escapedDir = shellQuote(parentDir)
        val escapedFile = shellQuote(fileName)
        return when {
            lowerName.endsWith(".zip") -> "cd $escapedDir && unzip -o $escapedFile"
            lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "cd $escapedDir && tar -xzf $escapedFile"
            lowerName.endsWith(".tar") -> "cd $escapedDir && tar -xf $escapedFile"
            lowerName.endsWith(".gz") -> "cd $escapedDir && gunzip -k $escapedFile"
            lowerName.endsWith(".rar") -> "cd $escapedDir && unrar x -o+ $escapedFile"
            lowerName.endsWith(".7z") -> "cd $escapedDir && 7z x -y $escapedFile"
            else -> throw IllegalArgumentException("不支持的解压格式: $fileName")
        }
    }

    private fun tarListCommand(file: RemoteFile): String {
        val lower = file.name.lowercase()
        val flags = if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) "-tzvf" else "-tvf"
        return if (remotePlatform == RemotePlatform.WINDOWS) {
            ps("tar $flags ${psQuote(windowsPath(file.path))}")
        } else {
            "tar $flags ${shellQuote(file.path)}"
        }
    }

    private fun tarReadCommand(file: RemoteFile, entryPath: String): String {
        val lower = file.name.lowercase()
        val flags = if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) "-xzOf" else "-xOf"
        return if (remotePlatform == RemotePlatform.WINDOWS) {
            ps("tar $flags ${psQuote(windowsPath(file.path))} ${psQuote(entryPath)}")
        } else {
            "tar $flags ${shellQuote(file.path)} ${shellQuote(entryPath)}"
        }
    }

    fun buildExecuteInTerminalCommand(file: RemoteFile): String {
        return if (remotePlatform == RemotePlatform.WINDOWS) {
            val winPath = windowsPath(file.path).replace("\"", "")
            if (file.name.lowercase().endsWith(".ps1")) {
                "powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"$winPath\""
            } else {
                "cmd.exe /c \"$winPath\""
            }
        } else {
            "chmod +x ${shellQuote(file.path)} && ${shellQuote(file.path)}"
        }
    }

    suspend fun compressFile(file: RemoteFile, format: String): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val parentDir = RemotePath.parent(file.path)
            val fileName = file.name

            if (format == "zip") {
                compressZipSFTPFallback(channel, file, parentDir)
                return@runCatching
            }

            val cmd = buildCompressCommand(parentDir, fileName, format)
            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            try {
                executeSshCommand(sess, cmd)
            } catch (e: Exception) {
                if (isTransportFailure(e)) throw e
                if (format == "tar" || format == "tar.gz") {
                    compressTarLocal(channel, file, parentDir, format == "tar.gz")
                } else {
                    throw e
                }
            }
        }
    }

    private fun compressZipSFTPFallback(channel: ChannelSftp, file: RemoteFile, parentDir: String) {
        val tempZipFile = File.createTempFile("toolbox_compress_", ".zip")
        try {
            val fos = FileOutputStream(tempZipFile)
            val zipOut = ZipOutputStream(fos, Charsets.UTF_8)

            if (file.isDirectory) {
                compressFolderRecursiveSFTP(channel, file.path, file.name, zipOut)
            } else {
                val fileIn = channel.get(file.path)
                zipOut.putNextEntry(ZipEntry(file.name))
                val buffer = ByteArray(4096)
                var read: Int
                while (fileIn.read(buffer).also { read = it } != -1) {
                    zipOut.write(buffer, 0, read)
                }
                fileIn.close()
                zipOut.closeEntry()
            }

            zipOut.close()
            fos.close()

            val targetZipPath = RemotePath.join(parentDir, "${file.name}.zip")
            val fis = FileInputStream(tempZipFile)
            channel.put(fis, targetZipPath, ChannelSftp.OVERWRITE)
            fis.close()
        } finally {
            tempZipFile.delete()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compressFolderRecursiveSFTP(channel: ChannelSftp, currentPath: String, zipPathPrefix: String, zipOut: ZipOutputStream) {
        val entries = channel.ls(currentPath) as Vector<ChannelSftp.LsEntry>
        for (entry in entries) {
            val name = entry.filename
            if (name == "." || name == "..") continue
            val childFullPath = RemotePath.join(currentPath, name)
            val childZipPath = "$zipPathPrefix/$name"

            if (entry.attrs.isDir) {
                zipOut.putNextEntry(ZipEntry("$childZipPath/"))
                zipOut.closeEntry()
                compressFolderRecursiveSFTP(channel, childFullPath, childZipPath, zipOut)
            } else {
                val fileIn = channel.get(childFullPath)
                zipOut.putNextEntry(ZipEntry(childZipPath))
                val buffer = ByteArray(4096)
                var read: Int
                while (fileIn.read(buffer).also { read = it } != -1) {
                    zipOut.write(buffer, 0, read)
                }
                fileIn.close()
                zipOut.closeEntry()
            }
        }
    }

    suspend fun decompressFile(file: RemoteFile): Result<Unit> = remoteCall {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val parentDir = RemotePath.parent(file.path)
            val fileName = file.name
            val lowerName = fileName.lowercase()

            when {
                lowerName.endsWith(".zip") -> {
                    decompressZipSFTPFallback(channel, file, parentDir)
                    return@runCatching
                }
                lowerName.endsWith(".7z") -> {
                    decompressSevenZLocal(channel, file, parentDir)
                    return@runCatching
                }
                lowerName.endsWith(".gz") && !lowerName.endsWith(".tar.gz") -> {
                    decompressGzipLocal(channel, file, parentDir)
                    return@runCatching
                }
            }

            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            try {
                val cmd = buildDecompressCommand(parentDir, fileName, lowerName)
                executeSshCommand(sess, cmd)
            } catch (e: Exception) {
                if (isTransportFailure(e)) throw e
                when {
                    lowerName.endsWith(".tar") || lowerName.endsWith(".tgz") || lowerName.endsWith(".tar.gz") -> {
                        decompressTarLocal(channel, file, parentDir)
                    }
                    lowerName.endsWith(".rar") -> throw IllegalArgumentException("主机上没有可用的 unrar，无法解压 rar")
                    else -> throw e
                }
            }
        }
    }

    private fun decompressZipSFTPFallback(channel: ChannelSftp, file: RemoteFile, parentDir: String) {
        val tempZipFile = File.createTempFile("toolbox_decompress_", ".zip")
        try {
            val fos = FileOutputStream(tempZipFile)
            val inputStream = channel.get(file.path)
            val buffer = ByteArray(4096)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                fos.write(buffer, 0, read)
            }
            inputStream.close()
            fos.close()

            val fis = FileInputStream(tempZipFile)
            val zipIn = openZip(fis)
            var entry: ZipEntry?
            while (zipIn.nextEntry.also { entry = it } != null) {
                val e = entry!!
                val relative = RemotePath.safeRelative(e.name)
                if (relative == null) {
                    zipIn.closeEntry()
                    continue
                }
                val targetPath = RemotePath.resolveChild(parentDir, relative)
                if (targetPath.isEmpty()) {
                    zipIn.closeEntry()
                    continue
                }

                if (e.isDirectory) {
                    ensureRemoteDir(channel, targetPath)
                } else {
                    ensureRemoteDir(channel, RemotePath.parent(targetPath))
                    val outStream = channel.put(targetPath, ChannelSftp.OVERWRITE)
                    while (zipIn.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                    }
                    outStream.close()
                }
                zipIn.closeEntry()
            }
            zipIn.close()
            fis.close()
        } finally {
            tempZipFile.delete()
        }
    }

    private fun readTarEntryLocal(channel: ChannelSftp, file: RemoteFile, entryPath: String): ByteArray {
        val tempFile = downloadToTemp(channel, file.path)
        try {
            openTar(tempFile, file.name).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == entryPath || name.trimEnd('/') == entryPath.trimEnd('/')) {
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(16384)
                        var read = tar.read(buffer)
                        while (read != -1) {
                            out.write(buffer, 0, read)
                            read = tar.read(buffer)
                        }
                        return out.toByteArray()
                    }
                    entry = tar.nextEntry
                }
            }
            throw IllegalStateException("压缩包中未找到文件: $entryPath")
        } finally {
            tempFile.delete()
        }
    }

    private fun listTarEntries(channel: ChannelSftp, file: RemoteFile): List<ArchiveEntryItem> {
        val sess = session
        if (sess != null) {
            try {
                val output = runExecCapture(sess, tarListCommand(file))
                val parsed = parseTarVerboseListing(output).ifEmpty { parsePathOnlyListing(output) }
                if (parsed.isNotEmpty()) return parsed
            } catch (e: Exception) {
                if (isTransportFailure(e)) throw e
            }
        }
        return listTarLocal(channel, file)
    }

    private fun listTarLocal(channel: ChannelSftp, file: RemoteFile): List<ArchiveEntryItem> {
        val tempFile = downloadToTemp(channel, file.path)
        try {
            val entries = mutableListOf<ArchiveEntryItem>()
            openTar(tempFile, file.name).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val rawName = entry.name.trimEnd('/')
                    if (rawName.isNotBlank() && RemotePath.safeRelative(entry.name) != null) {
                        entries.add(
                            ArchiveEntryItem(
                                path = entry.name,
                                name = rawName.substringAfterLast('/'),
                                isDirectory = entry.isDirectory,
                                size = if (entry.isDirectory) 0L else entry.size
                            )
                        )
                    }
                    entry = tar.nextEntry
                }
            }
            return entries
        } finally {
            tempFile.delete()
        }
    }

    private fun openTar(file: File, archiveName: String): TarArchiveInputStream {
        val raw = FileInputStream(file)
        val lower = archiveName.lowercase()
        val input = if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) GzipCompressorInputStream(raw) else raw
        return TarArchiveInputStream(input, "UTF-8")
    }

    private fun compressTarLocal(channel: ChannelSftp, file: RemoteFile, parentDir: String, gzip: Boolean) {
        val ext = if (gzip) ".tar.gz" else ".tar"
        val tempRoot = File.createTempFile("toolbox_tar_src_", "")
        tempRoot.delete()
        tempRoot.mkdirs()
        val tempArchive = File.createTempFile("toolbox_tar_", if (gzip) ".tgz" else ".tar")
        try {
            downloadRecursive(channel, file.path, File(tempRoot, file.name), file.isDirectory)
            writeTar(tempRoot, tempArchive, gzip)
            FileInputStream(tempArchive).use { input ->
                channel.put(input, RemotePath.join(parentDir, file.name + ext), ChannelSftp.OVERWRITE)
            }
        } finally {
            tempArchive.delete()
            tempRoot.deleteRecursively()
        }
    }

    private fun writeTar(root: File, dest: File, gzip: Boolean) {
        val raw = FileOutputStream(dest)
        val compressed = if (gzip) GzipCompressorOutputStream(raw) else raw
        TarArchiveOutputStream(compressed).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            val base = root.absoluteFile
            root.walkTopDown().forEach { child ->
                if (child == root) return@forEach
                val rel = base.toPath().relativize(child.toPath()).toString().replace('\\', '/')
                val entryName = if (child.isDirectory) rel.trimEnd('/') + "/" else rel
                val entry = TarArchiveEntry(child, entryName)
                tar.putArchiveEntry(entry)
                if (child.isFile) child.inputStream().use { it.copyTo(tar) }
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun downloadRecursive(channel: ChannelSftp, remote: String, local: File, isDir: Boolean) {
        if (isDir) {
            local.mkdirs()
            val entries = channel.ls(remote) as Vector<ChannelSftp.LsEntry>
            for (entry in entries) {
                val name = entry.filename
                if (name == "." || name == "..") continue
                downloadRecursive(channel, RemotePath.join(remote, name), File(local, name), entry.attrs.isDir)
            }
        } else {
            local.parentFile?.mkdirs()
            FileOutputStream(local).use { out ->
                channel.get(remote).use { input -> input.copyTo(out) }
            }
        }
    }

    private fun decompressTarLocal(channel: ChannelSftp, file: RemoteFile, parentDir: String) {
        val tempFile = downloadToTemp(channel, file.path)
        try {
            openTar(tempFile, file.name).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val relative = RemotePath.safeRelative(entry.name)
                    if (relative != null) {
                        val target = RemotePath.resolveChild(parentDir, relative)
                        if (target.isNotEmpty()) {
                            if (entry.isDirectory) {
                                ensureRemoteDir(channel, target)
                            } else {
                                ensureRemoteDir(channel, RemotePath.parent(target))
                                uploadLimited(channel, tar, target)
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun decompressGzipLocal(channel: ChannelSftp, file: RemoteFile, parentDir: String) {
        val tempFile = downloadToTemp(channel, file.path)
        val tempOut = File.createTempFile("toolbox_gunzip_", ".bin")
        try {
            GzipCompressorInputStream(FileInputStream(tempFile)).use { input ->
                FileOutputStream(tempOut).use { output -> input.copyTo(output) }
            }
            val outName = file.name.substringBeforeLast('.').ifBlank { "${file.name}.out" }
            FileInputStream(tempOut).use { input ->
                channel.put(input, RemotePath.join(parentDir, outName), ChannelSftp.OVERWRITE)
            }
        } finally {
            tempFile.delete()
            tempOut.delete()
        }
    }

    private fun decompressSevenZLocal(channel: ChannelSftp, file: RemoteFile, parentDir: String) {
        val tempFile = downloadToTemp(channel, file.path)
        try {
            SevenZFile.Builder().setFile(tempFile).get().use { sevenZ ->
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    val relative = RemotePath.safeRelative(entry.name)
                    if (relative != null) {
                        val target = RemotePath.resolveChild(parentDir, relative)
                        if (target.isNotEmpty()) {
                            if (entry.isDirectory) {
                                ensureRemoteDir(channel, target)
                            } else {
                                ensureRemoteDir(channel, RemotePath.parent(target))
                                val part = File.createTempFile("toolbox_7z_", ".bin")
                                try {
                                    FileOutputStream(part).use { out ->
                                        val buffer = ByteArray(16384)
                                        var read = sevenZ.read(buffer)
                                        while (read != -1) {
                                            out.write(buffer, 0, read)
                                            read = sevenZ.read(buffer)
                                        }
                                    }
                                    FileInputStream(part).use { input ->
                                        channel.put(input, target, ChannelSftp.OVERWRITE)
                                    }
                                } finally {
                                    part.delete()
                                }
                            }
                        }
                    }
                    entry = sevenZ.nextEntry
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun uploadLimited(channel: ChannelSftp, input: InputStream, target: String) {
        val part = File.createTempFile("toolbox_entry_", ".bin")
        try {
            FileOutputStream(part).use { out ->
                val buffer = ByteArray(16384)
                var read = input.read(buffer)
                while (read != -1) {
                    out.write(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            FileInputStream(part).use { channel.put(it, target, ChannelSftp.OVERWRITE) }
        } finally {
            part.delete()
        }
    }

    private fun ensureRemoteDir(channel: ChannelSftp, dir: String) {
        val normalized = RemotePath.normalize(dir)
        if (normalized.isBlank() || RemotePath.isRoot(normalized)) return
        val parent = RemotePath.parent(normalized)
        if (parent != normalized) ensureRemoteDir(channel, parent)
        try {
            channel.mkdir(normalized)
        } catch (_: Exception) {}
    }

    private fun executeSshCommand(sess: Session, command: String) {
        val channel = sess.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val errStream = ByteArrayOutputStream()
        channel.setErrStream(errStream)
        val inStream = channel.inputStream
        channel.connect(15000)

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var read: Int
        while (inStream.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        channel.disconnect()

        val exitStatus = channel.exitStatus
        if (exitStatus == -1) {
            val errStr = stripAnsiCodes(decodeBytes(errStream.toByteArray(), remotePlatform == RemotePlatform.WINDOWS))
            if (errStr.isNotBlank()) throw RuntimeException(errStr)
            return
        }
        if (exitStatus != 0) {
            val errStr = stripAnsiCodes(decodeBytes(errStream.toByteArray(), remotePlatform == RemotePlatform.WINDOWS))
            val outStr = stripAnsiCodes(decodeBytes(output.toByteArray(), remotePlatform == RemotePlatform.WINDOWS))
            val message = listOf(errStr, outStr).firstOrNull { it.isNotBlank() } ?: "Command exited with status $exitStatus"
            throw RuntimeException(message)
        }
    }

    private fun determineFileType(filename: String, isDirectory: Boolean): FileType {
        if (isDirectory) return FileType.DIRECTORY
        val lowerName = filename.lowercase()
        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) return FileType.ARCHIVE

        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt", "log", "json", "xml", "yaml", "yml", "conf", "cfg", "ini",
            "py", "kt", "java", "c", "cpp", "h", "hpp", "html", "css", "js", "ts", "md",
            "env", "properties", "gradle", "kts", "sql", "csv", "pro" -> FileType.TEXT
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "svg" -> FileType.IMAGE
            "mp3", "wav", "aac", "ogg", "flac", "m4a" -> FileType.AUDIO
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> FileType.VIDEO
            "zip", "tar", "gz", "rar", "7z", "bz2", "xz" -> FileType.ARCHIVE
            "sh", "rc", "bash", "bin", "exe", "so", "dll", "deb", "apk", "pl" -> FileType.EXECUTABLE
            else -> FileType.UNKNOWN
        }
    }

    private fun isBinaryContent(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var nullCount = 0
        for (b in bytes) {
            if (b == 0.toByte()) {
                nullCount++
            }
        }
        return nullCount > 0
    }

    private fun stripAnsiCodes(text: String): String {
        return text.replace("\u001B\\[[;?0-9]*[a-zA-Z]".toRegex(), "")
    }

    private fun formatSshException(e: Throwable): String {
        val msg = e.message ?: ""
        val causeMsg = e.cause?.message ?: ""
        val fullText = "$msg $causeMsg ${e.javaClass.name}".lowercase()

        return when {
            fullText.contains("私钥密码错误") || fullText.contains("私钥已加密") || fullText.contains("无法解密") -> msg
            fullText.contains("invalid privatekey") || fullText.contains("illegal key") || fullText.contains("failed to load key") || fullText.contains("not a valid") || fullText.contains("keyinvalid") -> {
                "私钥解析失败：请检查私钥格式（支持 OpenSSH / PEM / PPK），并确保复制完整"
            }
            fullText.contains("no route to host") || fullText.contains("noroute") || fullText.contains("unknownhost") || fullText.contains("name or service not known") || fullText.contains("no address associated") -> {
                "地址错误：无法找到目标主机，请检查 IP 地址或域名"
            }
            fullText.contains("timeout") || fullText.contains("timed out") || fullText.contains("sockettimeoutexception") -> {
                "连接超时：服务器未响应，请检查防火墙或网络状态"
            }
            fullText.contains("auth fail") || fullText.contains("authentication failed") || fullText.contains("userauth") -> {
                "身份认证失败：用户名、密码或 SSH 私钥/密码错误"
            }
            fullText.contains("connection refused") || fullText.contains("connectexception") -> {
                "连接被拒绝：请检查端口号是否正确，以及服务器 SSH 服务（sshd）是否开启"
            }
            fullText.contains("algorithm negotiation fail") -> {
                "加密算法协商失败：服务器禁用了兼容算法，请检查 SSH 服务配置"
            }
            fullText.contains("network is unreachable") -> {
                "网络不可达：请检查手机网络连接或局域网设置"
            }
            msg.isNotBlank() -> "连接失败: $msg"
            else -> "连接失败: Unknown error"
        }
    }

    private class MyUserInfo(
        private val password: String?,
        private val passphrase: String?
    ) : com.jcraft.jsch.UserInfo, com.jcraft.jsch.UIKeyboardInteractive {
        override fun getPassword(): String? = password
        override fun promptPassword(message: String?): Boolean = true
        override fun getPassphrase(): String? = passphrase
        override fun promptPassphrase(message: String?): Boolean = true
        override fun promptYesNo(message: String?): Boolean = true
        override fun showMessage(message: String?) {}

        override fun promptKeyboardInteractive(
            destination: String?,
            name: String?,
            instruction: String?,
            prompt: Array<out String>?,
            echo: BooleanArray?
        ): Array<String>? {
            if (prompt != null && password != null) {
                val response = arrayOfNulls<String>(prompt.size)
                for (i in prompt.indices) {
                    response[i] = password
                }
                @Suppress("UNCHECKED_CAST")
                return response as Array<String>
            }
            return null
        }
    }
}
