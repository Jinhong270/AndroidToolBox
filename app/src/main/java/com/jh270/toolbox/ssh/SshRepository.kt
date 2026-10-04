package com.jh270.toolbox.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.ChecksumResult
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.data.RemoteFile
import com.jh270.toolbox.data.SshConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Vector

class SshRepository {
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null

    suspend fun connect(config: SshConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            disconnectInternal()
            val jsch = JSch()

            if (config.authType == AuthType.PRIVATE_KEY && config.privateKey.isNotBlank()) {
                val prvKeyBytes = config.privateKey.toByteArray(Charsets.UTF_8)
                jsch.addIdentity("customKey", prvKeyBytes, null, null)
            }

            val newSession = jsch.getSession(config.username, config.host, config.port)
            if (config.authType == AuthType.PASSWORD) {
                newSession.setPassword(config.password)
            }

            val properties = java.util.Properties()
            properties["StrictHostKeyChecking"] = "no"
            newSession.setConfig(properties)
            newSession.timeout = 15000
            newSession.connect(15000)

            val channel = newSession.openChannel("sftp")
            channel.connect(15000)

            session = newSession
            sftpChannel = channel as ChannelSftp

            val pwd = sftpChannel?.pwd() ?: "/"
            Result.success(pwd)
        } catch (e: Exception) {
            Result.failure(Exception(formatSshException(e)))
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        try {
            sftpChannel?.disconnect()
        } catch (_: Exception) {}
        sftpChannel = null

        try {
            session?.disconnect()
        } catch (_: Exception) {}
        session = null
    }

    fun isConnected(): Boolean {
        return session?.isConnected == true && sftpChannel?.isConnected == true
    }

    suspend fun listFiles(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val targetPath = if (path.isBlank()) "/" else path

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

                val fullPath = when {
                    targetPath == "/" -> "/$name"
                    targetPath.endsWith("/") -> "$targetPath$name"
                    else -> "$targetPath/$name"
                }

                val type = determineFileType(name, isDir)

                fileItems.add(
                    RemoteFile(
                        name = name,
                        path = fullPath,
                        isDirectory = isDir,
                        size = size,
                        permissions = permissions,
                        modifiedTime = mTime,
                        fileType = type
                    )
                )
            }

            fileItems.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            val finalResult = mutableListOf<RemoteFile>()
            val normalizedPath = targetPath.trimEnd('/')
            if (normalizedPath.isNotEmpty() && normalizedPath != "/") {
                val parent = normalizedPath.substringBeforeLast('/', "")
                val parentPath = if (parent.isEmpty()) "/" else parent
                finalResult.add(
                    RemoteFile(
                        name = "..",
                        path = parentPath,
                        isDirectory = true,
                        size = 0,
                        permissions = "drwxr-xr-x",
                        modifiedTime = 0,
                        fileType = FileType.DIRECTORY
                    )
                )
            }

            finalResult.addAll(fileItems)
            finalResult
        }
    }

    suspend fun previewFile(file: RemoteFile, maxBytes: Int = 5242880): Result<FilePreview> = withContext(Dispatchers.IO) {
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

            val readLimit = if (fileType == FileType.TEXT) 204800 else maxBytes
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
                FileType.TEXT -> {
                    val text = String(bytes, Charsets.UTF_8)
                    FilePreview(
                        name = file.name,
                        path = file.path,
                        size = file.size,
                        permissions = file.permissions,
                        modifiedTime = file.modifiedTime,
                        content = text,
                        fileType = FileType.TEXT
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
                            fileType = FileType.BINARY
                        )
                    } else {
                        val text = String(bytes, Charsets.UTF_8)
                        FilePreview(
                            name = file.name,
                            path = file.path,
                            size = file.size,
                            permissions = file.permissions,
                            modifiedTime = file.modifiedTime,
                            content = text,
                            fileType = FileType.TEXT
                        )
                    }
                }
            }
        }
    }

    suspend fun saveFileContent(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val bytes = content.toByteArray(Charsets.UTF_8)
            val inputStream = ByteArrayInputStream(bytes)
            channel.put(inputStream, path, ChannelSftp.OVERWRITE)
        }
    }

    suspend fun deleteFileOrFolder(file: RemoteFile): Result<Unit> = withContext(Dispatchers.IO) {
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
                val childPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                deleteRecursive(channel, childPath)
            }
            channel.rmdir(path)
        } else {
            channel.rm(path)
        }
    }

    suspend fun renameFileOrFolder(file: RemoteFile, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val parentDir = file.path.substringBeforeLast('/', "")
            val newPath = if (parentDir.isEmpty()) "/$newName" else "$parentDir/$newName"
            channel.rename(file.path, newPath)
        }
    }

    suspend fun calculateChecksums(file: RemoteFile): Result<ChecksumResult> = withContext(Dispatchers.IO) {
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

    private fun determineFileType(filename: String, isDirectory: Boolean): FileType {
        if (isDirectory) return FileType.DIRECTORY
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt", "log", "json", "xml", "yaml", "yml", "conf", "cfg", "ini", "sh", "bash",
            "py", "kt", "java", "c", "cpp", "h", "hpp", "html", "css", "js", "ts", "md",
            "env", "properties", "gradle", "kts", "sql", "csv", "rc", "pro" -> FileType.TEXT
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> FileType.IMAGE
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

    private fun formatSshException(e: Throwable): String {
        val msg = e.message ?: ""
        val causeMsg = e.cause?.message ?: ""
        val fullText = "$msg $causeMsg ${e.javaClass.name}".lowercase()

        return when {
            fullText.contains("auth fail") || fullText.contains("authentication failed") || fullText.contains("userauth") -> {
                "身份认证失败：用户名、密码或 SSH 私钥错误"
            }
            fullText.contains("unknownhost") || fullText.contains("name or service not known") || fullText.contains("no address associated") -> {
                "主机无法解析：请检查 IP 地址或域名是否正确"
            }
            fullText.contains("connection refused") || fullText.contains("connectexception") -> {
                "连接被拒绝：请检查端口号是否正确，以及服务器 SSH 服务（sshd）是否开启"
            }
            fullText.contains("timeout") || fullText.contains("timed out") || fullText.contains("sockettimeoutexception") -> {
                "连接超时：请检查网络连接、防火墙开放端口或目标 IP 是否可达"
            }
            fullText.contains("algorithm negotiation fail") -> {
                "加密算法协商失败：服务器禁用了兼容算法，请检查 SSH 服务配置"
            }
            fullText.contains("invalid privatekey") || fullText.contains("illegal key") || fullText.contains("keyinvalid") -> {
                "私钥格式错误：输入的 SSH 私钥内容无效"
            }
            fullText.contains("network is unreachable") || fullText.contains("no route to host") -> {
                "网络不可达：请检查手机网络连接或局域网设置"
            }
            msg.isNotBlank() -> "连接失败: $msg"
            else -> "连接失败: 未知网络或系统错误"
        }
    }
}
