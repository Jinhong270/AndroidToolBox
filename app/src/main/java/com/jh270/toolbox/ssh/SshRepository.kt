package com.jh270.toolbox.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.data.RemoteFile
import com.jh270.toolbox.data.SshConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Vector

class SshRepository {
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null

    suspend fun connect(config: SshConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
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
            pwd
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
            val channel = sftpChannel ?: throw IllegalStateException("Not connected")
            val targetPath = if (path.isBlank()) "/" else path

            @Suppress("UNCHECKED_CAST")
            val entries = channel.ls(targetPath) as Vector<ChannelSftp.LsEntry>

            val result = mutableListOf<RemoteFile>()
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

                result.add(
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

            result.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            result
        }
    }

    suspend fun previewFile(file: RemoteFile, maxBytes: Int = 5242880): Result<FilePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("Not connected")

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
            val channel = sftpChannel ?: throw IllegalStateException("Not connected")
            val bytes = content.toByteArray(Charsets.UTF_8)
            val inputStream = ByteArrayInputStream(bytes)
            channel.put(inputStream, path, ChannelSftp.OVERWRITE)
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
}
