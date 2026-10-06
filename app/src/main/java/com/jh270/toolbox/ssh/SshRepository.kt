package com.jh270.toolbox.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jh270.toolbox.data.ArchiveEntryItem
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

    suspend fun connect(config: SshConfig): Result<String> = withContext(Dispatchers.IO) {
        var tempKeyFile: File? = null
        try {
            disconnectInternal()
            val jsch = JSch()

            try {
                java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
                java.security.Security.addProvider(net.i2p.crypto.eddsa.EdDSASecurityProvider())
            } catch (_: Exception) {}

            if (config.authType == AuthType.PRIVATE_KEY && config.privateKey.isNotBlank()) {
                tempKeyFile = File.createTempFile("ssh_private_key", ".pem")
                tempKeyFile.writeText(config.privateKey.trim(), Charsets.UTF_8)
                tempKeyFile.setReadable(false, false)
                tempKeyFile.setReadable(true, true)

                if (config.passphrase.isNotBlank()) {
                    jsch.addIdentity(tempKeyFile.absolutePath, config.passphrase)
                } else {
                    jsch.addIdentity(tempKeyFile.absolutePath)
                }
            }

            val newSession = jsch.getSession(config.username, config.host, config.port)
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
        } finally {
            try {
                tempKeyFile?.delete()
            } catch (_: Exception) {}
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
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
    }

    @Suppress("unused")
    fun isConnected(): Boolean {
        return session?.isConnected == true && sftpChannel?.isConnected == true
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun startShellSession(cols: Int, rows: Int, onOutput: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            closeShellSessionInternal()

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

            shellReadingThread = Thread {
                val buffer = ByteArray(4096)
                try {
                    while (shellChannel?.isConnected == true) {
                        val read = inStream.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            onOutput(String(buffer, 0, read, Charsets.UTF_8))
                        }
                    }
                } catch (_: Exception) {}
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

    suspend fun closeShellSession() = withContext(Dispatchers.IO) {
        closeShellSessionInternal()
    }

    private fun closeShellSessionInternal() {
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

                val owner = attrs.uId.toString()
                val group = attrs.gId.toString()

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
                        owner = owner,
                        group = group,
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

    suspend fun createFolder(parentPath: String, folderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val cleanParent = parentPath.trimEnd('/')
            val newFolderPath = if (cleanParent.isEmpty()) "/$folderName" else "$cleanParent/$folderName"
            channel.mkdir(newFolderPath)
        }
    }

    suspend fun createFile(parentPath: String, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val cleanParent = parentPath.trimEnd('/')
            val newFilePath = if (cleanParent.isEmpty()) "/$fileName" else "$cleanParent/$fileName"
            val inputStream = ByteArrayInputStream(ByteArray(0))
            channel.put(inputStream, newFilePath)
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
                    val text = String(bytes, Charsets.UTF_8)
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
                        val text = String(bytes, Charsets.UTF_8)
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

    suspend fun listArchiveEntries(file: RemoteFile): Result<List<ArchiveEntryItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            val escapedPath = escapeSh(file.path)
            val lowerName = file.name.lowercase()
            val entriesList = mutableListOf<ArchiveEntryItem>()

            val cmd = when {
                lowerName.endsWith(".zip") -> "unzip -l $escapedPath"
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "tar -ztvf $escapedPath"
                lowerName.endsWith(".tar") -> "tar -tvf $escapedPath"
                else -> "tar -tvf $escapedPath"
            }

            try {
                val execChan = sess.openChannel("exec") as ChannelExec
                execChan.setCommand(cmd)
                val inStream = execChan.inputStream
                execChan.connect(15000)

                val outputStr = inStream.bufferedReader(Charsets.UTF_8).readText()
                execChan.disconnect()

                if (lowerName.endsWith(".zip")) {
                    var parsingFiles = false
                    outputStr.lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("----")) {
                            parsingFiles = !parsingFiles
                            return@forEach
                        }
                        if (parsingFiles && trimmed.isNotEmpty()) {
                            val tokens = trimmed.split(Regex("\\s+"))
                            if (tokens.size >= 4) {
                                val sizeStr = tokens[0]
                                val size = sizeStr.toLongOrNull() ?: 0L
                                val entryPath = tokens.drop(3).joinToString(" ")
                                val isDir = entryPath.endsWith("/")
                                val rawName = entryPath.trimEnd('/')
                                if (rawName.isNotBlank() && !rawName.startsWith("----")) {
                                    entriesList.add(
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
                } else {
                    outputStr.lines().forEach { line ->
                        val cleanLine = line.trim()
                        if (cleanLine.isNotBlank()) {
                            val tokens = cleanLine.split(Regex("\\s+"))
                            if (tokens.size >= 6) {
                                val perms = tokens[0]
                                val sizeStr = tokens[2]
                                val size = sizeStr.toLongOrNull() ?: 0L
                                val entryPath = tokens.drop(5).joinToString(" ")
                                val isDir = perms.startsWith("d") || entryPath.endsWith("/")
                                val rawName = entryPath.trimEnd('/')
                                if (rawName.isNotBlank()) {
                                    entriesList.add(
                                        ArchiveEntryItem(
                                            path = entryPath,
                                            name = rawName.substringAfterLast('/'),
                                            isDirectory = isDir,
                                            size = if (isDir) 0L else size
                                        )
                                    )
                                }
                            } else {
                                val isDir = cleanLine.endsWith("/")
                                val entryName = cleanLine.trimEnd('/')
                                if (entryName.isNotBlank()) {
                                    entriesList.add(
                                        ArchiveEntryItem(
                                            path = cleanLine,
                                            name = entryName.substringAfterLast('/'),
                                            isDirectory = isDir,
                                            size = 0L
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (entriesList.isEmpty()) {
                val channel = sftpChannel
                if (lowerName.endsWith(".zip") && channel != null) {
                    val inputStream = channel.get(file.path)
                    val zipIn = ZipInputStream(inputStream)
                    var entry: ZipEntry?
                    while (zipIn.nextEntry.also { entry = it } != null) {
                        val e = entry!!
                        val rawName = e.name.trimEnd('/')
                        if (rawName.isNotBlank()) {
                            entriesList.add(
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
                } else if (channel != null) {
                    val fallbackCmd = when {
                        lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "tar -ztf $escapedPath"
                        else -> "tar -tf $escapedPath"
                    }
                    val execChan2 = sess.openChannel("exec") as ChannelExec
                    execChan2.setCommand(fallbackCmd)
                    val inStream2 = execChan2.inputStream
                    execChan2.connect(15000)
                    val output2 = inStream2.bufferedReader(Charsets.UTF_8).readText()
                    execChan2.disconnect()

                    output2.lines().forEach { line ->
                        val cleanLine = line.trim()
                        if (cleanLine.isNotBlank()) {
                            val isDir = cleanLine.endsWith("/")
                            val entryName = cleanLine.trimEnd('/')
                            if (entryName.isNotBlank()) {
                                entriesList.add(
                                    ArchiveEntryItem(
                                        path = cleanLine,
                                        name = entryName.substringAfterLast('/'),
                                        isDirectory = isDir,
                                        size = 0L
                                    )
                                )
                            }
                        }
                    }
                }
            }

            entriesList
        }
    }

    suspend fun previewArchiveEntry(archiveFile: RemoteFile, entryPath: String): Result<FilePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val lowerName = archiveFile.name.lowercase()
            val outputStream = ByteArrayOutputStream()

            if (lowerName.endsWith(".zip")) {
                val inputStream = channel.get(archiveFile.path)
                val zipIn = ZipInputStream(inputStream)
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
            } else {
                val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
                val escapedArchive = escapeSh(archiveFile.path)
                val escapedEntry = escapeSh(entryPath)
                val cmd = when {
                    lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "tar -xzOf $escapedArchive $escapedEntry"
                    lowerName.endsWith(".tar") -> "tar -xOf $escapedArchive $escapedEntry"
                    else -> "tar -xOf $escapedArchive $escapedEntry"
                }

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
                    val text = String(bytes, Charsets.UTF_8)
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

    private fun escapeSh(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    suspend fun compressFile(file: RemoteFile, format: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val rawParent = file.path.substringBeforeLast('/', "")
            val parentDir = if (rawParent.isEmpty()) "/" else rawParent
            val fileName = file.name

            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            val escapedDir = escapeSh(parentDir)
            val escapedFile = escapeSh(fileName)

            val cmd = when (format) {
                "zip" -> "cd $escapedDir && (zip -r ${escapeSh("$fileName.zip")} $escapedFile || python3 -c \"import shutil; shutil.make_archive('$fileName', 'zip', '.', '$fileName')\")"
                "tar.gz" -> "cd $escapedDir && tar -czf ${escapeSh("$fileName.tar.gz")} $escapedFile"
                "tar" -> "cd $escapedDir && tar -cf ${escapeSh("$fileName.tar")} $escapedFile"
                else -> throw IllegalArgumentException("不支持的压缩格式: $format")
            }

            try {
                executeSshCommand(sess, cmd)
            } catch (e: Exception) {
                if (format == "zip") {
                    compressZipSFTPFallback(channel, file, parentDir)
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
            val zipOut = ZipOutputStream(fos)

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

            val targetZipPath = if (parentDir.endsWith("/")) "$parentDir${file.name}.zip" else "$parentDir/${file.name}.zip"
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
            val childFullPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
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

    suspend fun decompressFile(file: RemoteFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = sftpChannel ?: throw IllegalStateException("未连接至 SSH 服务器")
            val rawParent = file.path.substringBeforeLast('/', "")
            val parentDir = if (rawParent.isEmpty()) "/" else rawParent
            val fileName = file.name
            val lowerName = fileName.lowercase()

            val sess = session ?: throw IllegalStateException("未连接至 SSH 服务器")
            val escapedDir = escapeSh(parentDir)
            val escapedFile = escapeSh(fileName)

            val cmd = when {
                lowerName.endsWith(".zip") -> "cd $escapedDir && (unzip -o $escapedFile || python3 -c \"import zipfile; zipfile.ZipFile('$fileName').extractall('.')\")"
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "cd $escapedDir && tar -xzF $escapedFile"
                lowerName.endsWith(".tar") -> "cd $escapedDir && tar -xf $escapedFile"
                lowerName.endsWith(".gz") -> "cd $escapedDir && gunzip -k $escapedFile"
                lowerName.endsWith(".rar") -> "cd $escapedDir && unrar x -o+ $escapedFile"
                lowerName.endsWith(".7z") -> "cd $escapedDir && 7z x -y $escapedFile"
                else -> throw IllegalArgumentException("不支持的解压格式: $fileName")
            }

            try {
                executeSshCommand(sess, cmd)
            } catch (e: Exception) {
                if (lowerName.endsWith(".zip")) {
                    decompressZipSFTPFallback(channel, file, parentDir)
                } else {
                    throw e
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
            val zipIn = ZipInputStream(fis)
            var entry: ZipEntry?
            while (zipIn.nextEntry.also { entry = it } != null) {
                val e = entry!!
                val entryName = e.name
                val targetPath = if (parentDir.endsWith("/")) "$parentDir$entryName" else "$parentDir/$entryName"

                if (e.isDirectory) {
                    try {
                        channel.mkdir(targetPath)
                    } catch (_: Exception) {}
                } else {
                    val parentFolder = targetPath.substringBeforeLast('/', "")
                    if (parentFolder.isNotEmpty()) {
                        try {
                            channel.mkdir(parentFolder)
                        } catch (_: Exception) {}
                    }

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
        if (exitStatus != 0) {
            val errStr = stripAnsiCodes(errStream.toString(Charsets.UTF_8.name()))
            throw RuntimeException(if (errStr.isNotBlank()) errStr else "Command exited with status $exitStatus")
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
            fullText.contains("no route to host") || fullText.contains("noroute") || fullText.contains("unknownhost") || fullText.contains("name or service not known") || fullText.contains("no address associated") -> {
                "地址错误：无法找到目标主机，请检查 IP 地址或域名"
            }
            fullText.contains("timeout") || fullText.contains("timed out") || fullText.contains("sockettimeoutexception") -> {
                "连接超时：服务器未响应，请检查防火墙或网络状态"
            }
            fullText.contains("auth fail") || fullText.contains("authentication failed") || fullText.contains("userauth") || fullText.contains("invalid privatekey") || fullText.contains("illegal key") || fullText.contains("keyinvalid") -> {
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
