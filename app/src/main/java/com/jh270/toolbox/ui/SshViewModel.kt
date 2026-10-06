package com.jh270.toolbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jh270.toolbox.data.ArchiveEntryItem
import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.ChecksumResult
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.data.RemoteFile
import com.jh270.toolbox.data.SshConfig
import com.jh270.toolbox.data.SshProfile
import com.jh270.toolbox.ssh.SshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    SSH_MANAGER
}

data class SshUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val config: SshConfig = SshConfig(name = "默认服务器", host = "192.168.1.100", port = 22, username = "root"),
    val savedProfiles: List<SshProfile> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val currentPath: String = "/",
    val fileList: List<RemoteFile> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val fileFetchError: String? = null,
    val selectedFilePreview: FilePreview? = null,
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null,
    val isSavingFile: Boolean = false,
    val saveSuccessMessage: String? = null,
    val saveErrorMessage: String? = null,
    val actionTargetFile: RemoteFile? = null,
    val showActionMenu: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val showCompressDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val showCreateFileDialog: Boolean = false,
    val showFileDetailsDialog: Boolean = false,
    val isInArchiveMode: Boolean = false,
    val archiveFile: RemoteFile? = null,
    val archiveSubPath: String = "",
    val archiveEntries: List<ArchiveEntryItem> = emptyList(),
    val isLoadingArchiveEntries: Boolean = false,
    val archiveError: String? = null,
    val showTerminalScreen: Boolean = false,
    val terminalPath: String = "/",
    val terminalOutputBuffer: String = "",
    val terminalCommandInput: String = "",
    val isCtrlActive: Boolean = false,
    val commandHistoryList: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val isOperatingFile: Boolean = false,
    val isCalculatingChecksum: Boolean = false,
    val checksumResult: ChecksumResult? = null,
    val actionSuccessMessage: String? = null,
    val actionErrorMessage: String? = null,
    val searchQuery: String = ""
) {
    val displayPath: String
        get() = if (isInArchiveMode) {
            val archiveName = archiveFile?.name ?: ""
            if (archiveSubPath.isEmpty()) archiveName else "$archiveName/$archiveSubPath"
        } else {
            currentPath
        }
}

class SshViewModel(
    private val repository: SshRepository = SshRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SshUiState())
    val uiState: StateFlow<SshUiState> = _uiState.asStateFlow()

    init {
        loadDefaultProfiles()
    }

    private fun loadDefaultProfiles() {
        val defaultProfile = SshProfile(
            name = "本地测试服务器",
            config = SshConfig(host = "192.168.1.100", port = 22, username = "root")
        )
        _uiState.update { it.copy(savedProfiles = listOf(defaultProfile)) }
    }

    fun selectScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun returnToHome() {
        viewModelScope.launch {
            repository.disconnect()
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.HOME,
                    isConnected = false,
                    isConnecting = false,
                    fileList = emptyList(),
                    selectedFilePreview = null,
                    actionTargetFile = null,
                    showTerminalScreen = false,
                    terminalOutputBuffer = "",
                    isInArchiveMode = false,
                    archiveFile = null,
                    archiveSubPath = "",
                    archiveEntries = emptyList()
                )
            }
        }
    }

    fun applyProfile(profile: SshProfile) {
        _uiState.update {
            it.copy(
                config = profile.config,
                connectionError = null
            )
        }
    }

    fun saveCurrentProfile() {
        val config = uiState.value.config
        if (config.host.isBlank()) return
        val profileName = if (config.name.isNotBlank()) config.name else "${config.username}@${config.host}"
        val newProfile = SshProfile(name = profileName, config = config)
        _uiState.update {
            val updated = it.savedProfiles.filter { p -> p.config.host != config.host || p.config.port != config.port } + newProfile
            it.copy(savedProfiles = updated, actionSuccessMessage = "配置已保存")
        }
    }

    fun deleteProfile(profile: SshProfile) {
        _uiState.update {
            val updated = it.savedProfiles.filter { p -> p.id != profile.id }
            it.copy(savedProfiles = updated)
        }
    }

    fun updateConfigName(name: String) {
        _uiState.update { it.copy(config = it.config.copy(name = name), connectionError = null) }
    }

    fun updateHost(host: String) {
        _uiState.update { it.copy(config = it.config.copy(host = host), connectionError = null) }
    }

    fun updatePort(portStr: String) {
        val port = portStr.toIntOrNull() ?: 22
        _uiState.update { it.copy(config = it.config.copy(port = port), connectionError = null) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(config = it.config.copy(username = username), connectionError = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(config = it.config.copy(password = password), connectionError = null) }
    }

    fun updateAuthType(authType: AuthType) {
        _uiState.update { it.copy(config = it.config.copy(authType = authType), connectionError = null) }
    }

    fun updatePrivateKey(privateKey: String) {
        _uiState.update { it.copy(config = it.config.copy(privateKey = privateKey), connectionError = null) }
    }

    fun updatePassphrase(passphrase: String) {
        _uiState.update { it.copy(config = it.config.copy(passphrase = passphrase), connectionError = null) }
    }

    fun connect() {
        val config = uiState.value.config
        if (config.host.isBlank()) {
            _uiState.update { it.copy(connectionError = "请输入服务器 IP 地址或主机名") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            val result = repository.connect(config)
            result.onSuccess { pwd ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        currentPath = pwd,
                        terminalPath = pwd,
                        connectionError = null,
                        isInArchiveMode = false,
                        archiveFile = null,
                        archiveSubPath = "",
                        archiveEntries = emptyList()
                    )
                }
                loadDirectory(pwd)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        connectionError = error.message ?: "连接 SSH 服务器失败"
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _uiState.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    fileList = emptyList(),
                    selectedFilePreview = null,
                    actionTargetFile = null,
                    showTerminalScreen = false,
                    terminalOutputBuffer = "",
                    isInArchiveMode = false,
                    archiveFile = null,
                    archiveSubPath = "",
                    archiveEntries = emptyList()
                )
            }
        }
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingFiles = true,
                    fileFetchError = null,
                    currentPath = path,
                    isInArchiveMode = false,
                    archiveFile = null,
                    archiveSubPath = "",
                    archiveEntries = emptyList()
                )
            }
            val result = repository.listFiles(path)
            result.onSuccess { files ->
                _uiState.update {
                    it.copy(
                        isLoadingFiles = false,
                        fileList = files,
                        fileFetchError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingFiles = false,
                        fileFetchError = error.message ?: "获取目录文件列表失败"
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val state = uiState.value
        if (state.isInArchiveMode) {
            if (state.archiveSubPath.isNotEmpty()) {
                val parentSub = state.archiveSubPath.trimEnd('/').substringBeforeLast('/', "")
                _uiState.update { it.copy(archiveSubPath = parentSub) }
            } else {
                exitArchiveMode()
            }
            return
        }

        val current = state.currentPath
        if (current == "/" || current.isBlank()) return

        val parent = current.trimEnd('/').substringBeforeLast('/', "")
        val targetPath = if (parent.isEmpty()) "/" else parent
        loadDirectory(targetPath)
    }

    fun refreshDirectory() {
        val state = uiState.value
        if (state.isInArchiveMode && state.archiveFile != null) {
            enterArchiveMode(state.archiveFile)
        } else {
            loadDirectory(state.currentPath)
        }
    }

    fun openCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = true, actionErrorMessage = null) }
    }

    fun closeCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = false) }
    }

    fun openCreateFileDialog() {
        _uiState.update { it.copy(showCreateFileDialog = true, actionErrorMessage = null) }
    }

    fun closeCreateFileDialog() {
        _uiState.update { it.copy(showCreateFileDialog = false) }
    }

    fun openFileDetailsDialog() {
        _uiState.update {
            it.copy(
                showActionMenu = false,
                showFileDetailsDialog = true,
                checksumResult = null,
                actionErrorMessage = null
            )
        }
    }

    fun closeFileDetailsDialog() {
        _uiState.update {
            it.copy(
                showFileDetailsDialog = false,
                checksumResult = null
            )
        }
    }

    fun executeCreateFolder(folderName: String) {
        if (folderName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingFile = true, actionErrorMessage = null) }
            val parent = uiState.value.currentPath
            val result = repository.createFolder(parent, folderName.trim())
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        showCreateFolderDialog = false,
                        actionSuccessMessage = "文件夹创建成功"
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "文件夹创建失败"
                    )
                }
            }
        }
    }

    fun executeCreateFile(fileName: String) {
        if (fileName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingFile = true, actionErrorMessage = null) }
            val parent = uiState.value.currentPath
            val result = repository.createFile(parent, fileName.trim())
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        showCreateFileDialog = false,
                        actionSuccessMessage = "文件创建成功"
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "文件创建失败"
                    )
                }
            }
        }
    }

    fun previewFile(file: RemoteFile) {
        if (uiState.value.isInArchiveMode) {
            if (file.isDirectory) {
                if (file.name == "..") {
                    navigateUp()
                } else {
                    val currentSub = uiState.value.archiveSubPath
                    val newSub = if (currentSub.isEmpty()) file.name else "$currentSub/${file.name}"
                    _uiState.update { it.copy(archiveSubPath = newSub) }
                }
            } else {
                val fullEntryPath = file.path
                previewArchiveEntry(fullEntryPath)
            }
            return
        }

        if (file.isDirectory) {
            loadDirectory(file.path)
            return
        }

        if (file.fileType == FileType.ARCHIVE) {
            enterArchiveMode(file)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPreviewLoading = true, previewError = null, selectedFilePreview = null) }
            val result = repository.previewFile(file)
            result.onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        isPreviewLoading = false,
                        selectedFilePreview = preview,
                        previewError = preview.errorMessage
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPreviewLoading = false,
                        previewError = error.message ?: "预览文件失败"
                    )
                }
            }
        }
    }

    fun enterArchiveMode(file: RemoteFile) {
        _uiState.update {
            it.copy(
                isInArchiveMode = true,
                archiveFile = file,
                archiveSubPath = "",
                isLoadingArchiveEntries = true,
                archiveError = null,
                archiveEntries = emptyList()
            )
        }

        viewModelScope.launch {
            val result = repository.listArchiveEntries(file)
            result.onSuccess { entries ->
                _uiState.update {
                    it.copy(
                        isLoadingArchiveEntries = false,
                        archiveEntries = entries
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingArchiveEntries = false,
                        archiveError = error.message ?: "无法解析压缩包结构"
                    )
                }
            }
        }
    }

    fun exitArchiveMode() {
        _uiState.update {
            it.copy(
                isInArchiveMode = false,
                archiveFile = null,
                archiveSubPath = "",
                archiveEntries = emptyList(),
                archiveError = null,
                isLoadingArchiveEntries = false
            )
        }
    }

    fun previewArchiveEntry(entryPath: String) {
        val targetArchive = uiState.value.archiveFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPreviewLoading = true, previewError = null, selectedFilePreview = null) }
            val result = repository.previewArchiveEntry(targetArchive, entryPath)
            result.onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        isPreviewLoading = false,
                        selectedFilePreview = preview,
                        previewError = preview.errorMessage
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPreviewLoading = false,
                        previewError = error.message ?: "预览压缩包内文件失败"
                    )
                }
            }
        }
    }

    fun getActiveFileList(): List<RemoteFile> {
        val state = uiState.value
        if (!state.isInArchiveMode) {
            return state.fileList
        }

        val entries = state.archiveEntries
        val currentSub = state.archiveSubPath.trim('/')

        val subDirPrefix = if (currentSub.isEmpty()) "" else "$currentSub/"

        val result = mutableListOf<RemoteFile>()

        result.add(
            RemoteFile(
                name = "..",
                path = if (currentSub.isEmpty()) state.archiveFile?.path ?: "/" else currentSub.substringBeforeLast('/', ""),
                isDirectory = true,
                size = 0,
                permissions = "drwxr-xr-x",
                owner = "root",
                group = "root",
                modifiedTime = 0,
                fileType = FileType.DIRECTORY
            )
        )

        val directChildrenMap = mutableMapOf<String, RemoteFile>()

        for (e in entries) {
            val ePath = e.path.trim('/')
            if (currentSub.isNotEmpty() && !ePath.startsWith(subDirPrefix)) {
                continue
            }

            val relativePath = if (currentSub.isEmpty()) ePath else ePath.removePrefix(subDirPrefix)
            if (relativePath.isEmpty()) continue

            val parts = relativePath.split('/')
            val childName = parts[0]
            val isDir = e.isDirectory || parts.size > 1

            if (!directChildrenMap.containsKey(childName)) {
                val fullEntryPath = if (currentSub.isEmpty()) childName else "$currentSub/$childName"
                val fType = if (isDir) FileType.DIRECTORY else determineFileTypeByName(childName)

                directChildrenMap[childName] = RemoteFile(
                    name = childName,
                    path = fullEntryPath,
                    isDirectory = isDir,
                    size = if (isDir) 0L else e.size,
                    permissions = if (isDir) "drwxr-xr-x" else "-rw-r--r--",
                    owner = "root",
                    group = "root",
                    modifiedTime = System.currentTimeMillis(),
                    fileType = fType
                )
            }
        }

        val sortedChildren = directChildrenMap.values.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        result.addAll(sortedChildren)
        return result
    }

    private fun determineFileTypeByName(filename: String): FileType {
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

    fun saveFileContent(path: String, newContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingFile = true, saveSuccessMessage = null, saveErrorMessage = null) }
            val result = repository.saveFileContent(path, newContent)
            result.onSuccess {
                _uiState.update {
                    val updatedPreview = it.selectedFilePreview?.copy(content = newContent)
                    it.copy(
                        isSavingFile = false,
                        selectedFilePreview = updatedPreview,
                        saveSuccessMessage = "文件保存成功"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSavingFile = false,
                        saveErrorMessage = error.message ?: "文件保存失败"
                    )
                }
            }
        }
    }

    fun selectFileForAction(file: RemoteFile) {
        if (file.name == "..") return
        _uiState.update {
            it.copy(
                actionTargetFile = file,
                showActionMenu = true,
                actionErrorMessage = null,
                actionSuccessMessage = null
            )
        }
    }

    fun closeActionMenu() {
        _uiState.update { it.copy(showActionMenu = false) }
    }

    fun openRenameDialog() {
        _uiState.update { it.copy(showActionMenu = false, showRenameDialog = true) }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false) }
    }

    fun openDeleteConfirmDialog() {
        _uiState.update { it.copy(showActionMenu = false, showDeleteConfirmDialog = true) }
    }

    fun closeDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun openCompressDialog() {
        _uiState.update { it.copy(showActionMenu = false, showCompressDialog = true) }
    }

    fun closeCompressDialog() {
        _uiState.update { it.copy(showCompressDialog = false) }
    }

    fun executeRemoteFileInTerminal(file: RemoteFile) {
        closeActionMenu()
        openTerminal(initialCommand = "chmod +x \"${file.path}\" && \"${file.path}\"")
    }

    fun openTerminal(initialCommand: String? = null) {
        _uiState.update {
            it.copy(
                showTerminalScreen = true,
                terminalOutputBuffer = ""
            )
        }

        viewModelScope.launch {
            repository.startShellSession { chunk ->
                _uiState.update { st ->
                    var newBuffer = st.terminalOutputBuffer + chunk
                    if (newBuffer.length > 50000) {
                        newBuffer = newBuffer.takeLast(40000)
                    }
                    st.copy(terminalOutputBuffer = newBuffer)
                }
            }

            if (!initialCommand.isNullOrBlank()) {
                repository.sendShellInput("$initialCommand\n")
            }
        }
    }

    fun closeTerminal() {
        viewModelScope.launch {
            repository.closeShellSession()
            _uiState.update {
                it.copy(
                    showTerminalScreen = false,
                    isCtrlActive = false,
                    terminalCommandInput = ""
                )
            }
        }
    }

    fun toggleCtrlState() {
        _uiState.update { it.copy(isCtrlActive = !it.isCtrlActive) }
    }

    fun updateTerminalCommand(cmd: String) {
        _uiState.update { it.copy(terminalCommandInput = cmd) }
    }

    fun clearTerminalHistory() {
        _uiState.update {
            it.copy(
                terminalCommandInput = "",
                terminalOutputBuffer = "",
                historyIndex = -1
            )
        }
    }

    fun navigateCommandHistory(direction: Int) {
        val history = uiState.value.commandHistoryList
        if (history.isEmpty()) return

        val currentIndex = uiState.value.historyIndex
        val newIndex = when {
            direction < 0 -> (currentIndex + 1).coerceAtMost(history.size - 1)
            direction > 0 -> (currentIndex - 1).coerceAtLeast(-1)
            else -> currentIndex
        }

        val targetCmd = if (newIndex in history.indices) history[history.size - 1 - newIndex] else ""
        _uiState.update {
            it.copy(
                historyIndex = newIndex,
                terminalCommandInput = targetCmd
            )
        }
    }

    fun sendTerminalKey(key: String) {
        viewModelScope.launch {
            repository.sendShellInput(key)
        }
    }

    fun runTerminalCommand() {
        val input = uiState.value.terminalCommandInput
        val isCtrl = uiState.value.isCtrlActive

        val updatedCmdHistory = if (input.isNotBlank() && !uiState.value.commandHistoryList.contains(input)) {
            uiState.value.commandHistoryList + input
        } else {
            uiState.value.commandHistoryList
        }

        _uiState.update {
            it.copy(
                terminalCommandInput = "",
                commandHistoryList = updatedCmdHistory,
                historyIndex = -1,
                isCtrlActive = false
            )
        }

        viewModelScope.launch {
            if (isCtrl && input.isNotEmpty()) {
                val firstChar = input[0]
                repository.sendShellControlChar(firstChar)
                if (input.length > 1) {
                    repository.sendShellInput(input.substring(1) + "\n")
                }
            } else {
                repository.sendShellInput("$input\n")
            }
        }
    }

    fun sendControlKey(char: Char) {
        viewModelScope.launch {
            repository.sendShellControlChar(char)
            _uiState.update { it.copy(isCtrlActive = false) }
        }
    }

    fun executeDeleteFile() {
        val target = uiState.value.actionTargetFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingFile = true, actionErrorMessage = null) }
            val result = repository.deleteFileOrFolder(target)
            result.onSuccess {
                _uiState.update {
                    val updatedList = it.fileList.filter { file -> file.path != target.path }
                    it.copy(
                        isOperatingFile = false,
                        showDeleteConfirmDialog = false,
                        actionTargetFile = null,
                        fileList = updatedList,
                        actionSuccessMessage = "删除成功"
                    )
                }
                silentRefreshDirectory()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "删除失败"
                    )
                }
            }
        }
    }

    private fun silentRefreshDirectory() {
        val path = uiState.value.currentPath
        viewModelScope.launch {
            val result = repository.listFiles(path)
            result.onSuccess { files ->
                _uiState.update { it.copy(fileList = files) }
            }
        }
    }

    fun executeRenameFile(newName: String) {
        val target = uiState.value.actionTargetFile ?: return
        if (newName.isBlank() || newName == target.name) {
            closeRenameDialog()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingFile = true, actionErrorMessage = null) }
            val result = repository.renameFileOrFolder(target, newName)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        showRenameDialog = false,
                        actionTargetFile = null,
                        actionSuccessMessage = "重命名成功"
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "重命名失败"
                    )
                }
            }
        }
    }

    fun executeCalculateChecksum() {
        val target = uiState.value.actionTargetFile ?: return
        _uiState.update {
            it.copy(
                isCalculatingChecksum = true,
                checksumResult = null,
                actionErrorMessage = null
            )
        }

        viewModelScope.launch {
            val result = repository.calculateChecksums(target)
            result.onSuccess { checksums ->
                _uiState.update {
                    it.copy(
                        isCalculatingChecksum = false,
                        checksumResult = checksums
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCalculatingChecksum = false,
                        actionErrorMessage = error.message ?: "校验计算失败"
                    )
                }
            }
        }
    }

    fun executeCompress(format: String) {
        val target = uiState.value.actionTargetFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingFile = true, actionErrorMessage = null) }
            val result = repository.compressFile(target, format)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        showCompressDialog = false,
                        actionTargetFile = null,
                        actionSuccessMessage = "压缩成功"
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "压缩失败"
                    )
                }
            }
        }
    }

    fun executeDecompress() {
        val target = uiState.value.actionTargetFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingFile = true, actionErrorMessage = null) }
            val result = repository.decompressFile(target)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        showActionMenu = false,
                        actionTargetFile = null,
                        actionSuccessMessage = "解压成功"
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "解压失败"
                    )
                }
            }
        }
    }

    fun closeChecksumDialog() {
        _uiState.update { it.copy(checksumResult = null, isCalculatingChecksum = false) }
    }

    fun closePreview() {
        _uiState.update {
            it.copy(
                selectedFilePreview = null,
                previewError = null,
                isPreviewLoading = false,
                saveSuccessMessage = null,
                saveErrorMessage = null
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearActionMessages() {
        _uiState.update { it.copy(actionSuccessMessage = null, actionErrorMessage = null) }
    }
}
