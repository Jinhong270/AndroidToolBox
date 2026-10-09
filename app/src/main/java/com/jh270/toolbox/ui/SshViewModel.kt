package com.jh270.toolbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jh270.toolbox.data.ArchiveEntryItem
import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.ChecksumResult
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.data.RemoteFile
import com.jh270.toolbox.data.RemotePath
import com.jh270.toolbox.data.RemotePlatform
import com.jh270.toolbox.data.SshConfig
import com.jh270.toolbox.data.SshProfile
import com.jh270.toolbox.ssh.SshRepository
import com.jh270.toolbox.ssh.TerminalEmulator
import kotlinx.coroutines.Job
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
    val remotePlatform: RemotePlatform = RemotePlatform.UNKNOWN,
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
    val terminalLines: List<TerminalEmulator.TerminalLine> = emptyList(),
    val terminalRevision: Int = 0,
    val terminalSessionStarted: Boolean = false,
    val pendingInitialCommand: String? = null,
    val terminalClosed: Boolean = false,
    val isCtrlActive: Boolean = false,
    val isOperatingFile: Boolean = false,
    val isCalculatingChecksum: Boolean = false,
    val checksumResult: ChecksumResult? = null,
    val actionSuccessMessage: String? = null,
    val actionErrorMessage: String? = null,
    val searchQuery: String = "",
) {
    val displayPath: String
        get() = if (isInArchiveMode) {
            val archiveName = archiveFile?.name ?: ""
            if (archiveSubPath.isEmpty()) archiveName else "$archiveName/$archiveSubPath"
        } else {
            RemotePath.toDisplayPath(currentPath, remotePlatform)
        }
}

class SshViewModel(
    private val repository: SshRepository = SshRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SshUiState())
    val uiState: StateFlow<SshUiState> = _uiState.asStateFlow()

    @Volatile
    private var terminalEmulator: TerminalEmulator? = null

    private var requestedDirectory: String = "/"
    private var directoryJob: Job? = null
    private var resumeJob: Job? = null
    private var sessionEpoch: Int = 0
    private var terminalCols: Int = 80
    private var terminalRows: Int = 24

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
        sessionEpoch++
        directoryJob?.cancel()
        requestedDirectory = "/"
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
                    terminalLines = emptyList(),
                    terminalSessionStarted = false,
                    pendingInitialCommand = null,
                    terminalClosed = false,
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
        val profileName = config.name.ifBlank { "${config.username}@${config.host}" }
        val newProfile = SshProfile(name = profileName, config = config)
        _uiState.update {
            val updated = it.savedProfiles.filter { (_, _, profileConfig) -> (profileConfig.host != config.host) || (profileConfig.port != config.port) } + newProfile
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

        val epoch = ++sessionEpoch
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            val result = repository.connect(config)
            if (epoch != sessionEpoch) return@launch
            result.onSuccess { pwd ->
                requestedDirectory = pwd
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        remotePlatform = repository.remotePlatform,
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
        sessionEpoch++
        directoryJob?.cancel()
        requestedDirectory = "/"
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
                    terminalLines = emptyList(),
                    terminalSessionStarted = false,
                    pendingInitialCommand = null,
                    terminalClosed = false,
                    isInArchiveMode = false,
                    archiveFile = null,
                    archiveSubPath = "",
                    archiveEntries = emptyList()
                )
            }
        }
    }

    fun loadDirectory(path: String) {
        val normalized = RemotePath.normalize(path)
        val epoch = sessionEpoch
        requestedDirectory = normalized
        _uiState.update {
            it.copy(
                isLoadingFiles = true,
                fileFetchError = null,
                currentPath = normalized,
                isInArchiveMode = false,
                archiveFile = null,
                archiveSubPath = "",
                archiveEntries = emptyList()
            )
        }
        if (directoryJob?.isActive == true) return
        directoryJob = viewModelScope.launch {
            while (epoch == sessionEpoch) {
                val target = requestedDirectory
                val result = repository.listFiles(target)
                if (epoch != sessionEpoch || requestedDirectory != target) continue
                result.onSuccess { files ->
                    if (epoch != sessionEpoch || requestedDirectory != target) return@onSuccess
                    val restored = repository.consumeReconnectNotice()
                    _uiState.update {
                        it.copy(
                            isLoadingFiles = false,
                            isConnected = true,
                            remotePlatform = repository.remotePlatform,
                            fileList = files,
                            fileFetchError = null,
                            actionSuccessMessage = if (restored) "连接已恢复" else it.actionSuccessMessage
                        )
                    }
                }.onFailure { error ->
                    if (epoch != sessionEpoch || requestedDirectory != target) return@onFailure
                    if (!repository.isConnected()) {
                        dropToConnection(error.message ?: "连接已断开，请重新连接")
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoadingFiles = false,
                                fileFetchError = error.message ?: "获取目录文件列表失败"
                            )
                        }
                    }
                }
                if (epoch != sessionEpoch || requestedDirectory == target) break
            }
        }
    }

    fun onHostResume() {
        val state = _uiState.value
        if (!state.isConnected || state.isConnecting) return
        if (resumeJob?.isActive == true) return
        val epoch = sessionEpoch
        resumeJob = viewModelScope.launch {
            val alive = repository.probeAlive()
            if (epoch != sessionEpoch || !_uiState.value.isConnected || alive) return@launch
            _uiState.update { it.copy(isLoadingFiles = true, fileFetchError = null) }
            val result = repository.reconnect()
            if (epoch != sessionEpoch) {
                repository.disconnect()
                return@launch
            }
            result.onSuccess { pwd ->
                repository.consumeReconnectNotice()
                _uiState.update {
                    it.copy(
                        isConnected = true,
                        isLoadingFiles = false,
                        remotePlatform = repository.remotePlatform,
                        actionSuccessMessage = "连接已恢复"
                    )
                }
                if (_uiState.value.showTerminalScreen) {
                    restartTerminal()
                } else if (_uiState.value.isInArchiveMode && _uiState.value.archiveFile != null) {
                    enterArchiveMode(_uiState.value.archiveFile!!)
                } else {
                    loadDirectory(_uiState.value.currentPath.ifBlank { pwd })
                }
            }.onFailure { error ->
                dropToConnection(error.message ?: "连接已断开，请重新连接")
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

        val current = requestedDirectory.ifBlank { state.currentPath }
        if (RemotePath.isRoot(current) || current.isBlank()) return

        loadDirectory(RemotePath.parent(current))
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
                        actionSuccessMessage = noted("文件夹创建成功")
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                failRemote(error, "文件夹创建失败") {
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
                        actionSuccessMessage = noted("文件创建成功")
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                failRemote(error, "文件创建失败") {
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
            if (file.name == "..") navigateUp() else loadDirectory(file.path)
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
                failRemote(error, "预览文件失败") {
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
                failRemote(error, "无法解析压缩包结构") {
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
                failRemote(error, "预览压缩包内文件失败") {
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
        return when (filename.substringAfterLast('.', "").lowercase()) {
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
                        saveSuccessMessage = noted("文件保存成功")
                    )
                }
            }.onFailure { error ->
                failRemote(error, "文件保存失败") {
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
        openTerminal(initialCommand = repository.buildExecuteInTerminalCommand(file))
    }

    fun openTerminal(initialCommand: String? = null) {
        terminalEmulator = null
        _uiState.update {
            it.copy(
                showTerminalScreen = true,
                terminalLines = emptyList(),
                terminalRevision = 0,
                terminalSessionStarted = false,
                pendingInitialCommand = initialCommand,
                terminalClosed = false,
                isCtrlActive = false
            )
        }
    }

    fun startTerminalSession(cols: Int, rows: Int) {
        if (uiState.value.terminalSessionStarted) return
        val emulator = TerminalEmulator(cols, rows)
        terminalEmulator = emulator
        _uiState.update {
            it.copy(
                terminalSessionStarted = true,
                terminalClosed = false,
                terminalLines = emulator.getLines(),
                terminalRevision = it.terminalRevision + 1
            )
        }
        viewModelScope.launch {
            repository.startShellSession(cols, rows, { chunk ->
                feedTerminalOutput(chunk)
            }) {
                _uiState.update { it.copy(terminalClosed = true, isCtrlActive = false) }
            }
            val initial = uiState.value.pendingInitialCommand
            if (!initial.isNullOrBlank()) {
                repository.sendShellInput(initial + "\r")
                _uiState.update { it.copy(pendingInitialCommand = null) }
            }
        }
    }

    fun onTerminalSizeChanged(cols: Int, rows: Int) {
        terminalCols = cols
        terminalRows = rows
        if (uiState.value.terminalSessionStarted) {
            resizeTerminal(cols, rows)
        } else {
            startTerminalSession(cols, rows)
        }
    }

    private fun resizeTerminal(cols: Int, rows: Int) {
        val emulator = terminalEmulator ?: return
        emulator.resize(cols, rows)
        _uiState.update { st ->
            st.copy(
                terminalLines = emulator.getLines(),
                terminalRevision = st.terminalRevision + 1
            )
        }
        viewModelScope.launch {
            repository.resizeTerminal(cols, rows)
        }
    }

    fun closeTerminal() {
        viewModelScope.launch {
            repository.closeShellSession()
            terminalEmulator = null
            _uiState.update {
                it.copy(
                    showTerminalScreen = false,
                    isCtrlActive = false,
                    terminalSessionStarted = false,
                    pendingInitialCommand = null,
                    terminalClosed = false,
                    terminalLines = emptyList()
                )
            }
        }
    }

    private fun feedTerminalOutput(chunk: String) {
        val emulator = terminalEmulator ?: return
        emulator.feed(chunk)
        _uiState.update { st ->
            st.copy(
                terminalLines = emulator.getLines(),
                terminalRevision = st.terminalRevision + 1
            )
        }
    }

    fun toggleCtrlState() {
        _uiState.update { it.copy(isCtrlActive = !it.isCtrlActive) }
    }

    fun clearTerminal() {
        val emulator = terminalEmulator
        if (emulator != null) {
            emulator.clearScreen()
            _uiState.update { st ->
                st.copy(
                    terminalLines = emulator.getLines(),
                    terminalRevision = st.terminalRevision + 1
                )
            }
        }
    }

    fun sendTerminalText(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch {
            repository.sendShellInput(text)
        }
    }

    fun terminalPlainText(): String = terminalEmulator?.plainText().orEmpty()

    fun pasteTerminalText(text: String) {
        if (text.isEmpty() || uiState.value.terminalClosed) return
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val payload = if (terminalEmulator?.isBracketedPaste == true) {
            "\u001B[200~$normalized\u001B[201~"
        } else {
            normalized.replace('\n', '\r')
        }
        viewModelScope.launch {
            repository.sendShellInput(payload)
        }
    }

    fun sendTerminalRaw(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        viewModelScope.launch {
            repository.sendShellRaw(bytes)
        }
    }

    fun sendTerminalControlChar(char: Char) {
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
                        actionSuccessMessage = noted("删除成功")
                    )
                }
                silentRefreshDirectory()
            }.onFailure { error ->
                failRemote(error, "删除失败") {
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
        val epoch = sessionEpoch
        viewModelScope.launch {
            val result = repository.listFiles(path)
            if (epoch != sessionEpoch || requestedDirectory != path) return@launch
            result.onSuccess { files ->
                _uiState.update { it.copy(fileList = files, fileFetchError = null) }
            }.onFailure { error ->
                failRemote(error, "连接已断开，请重新连接") {
                    it.copy(fileFetchError = error.message ?: "获取目录文件列表失败")
                }
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
                        actionSuccessMessage = noted("重命名成功")
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                failRemote(error, "重命名失败") {
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
                failRemote(error, "校验计算失败") {
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
                        actionSuccessMessage = noted("压缩成功")
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                failRemote(error, "压缩失败") {
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
                        actionSuccessMessage = noted("解压成功")
                    )
                }
                refreshDirectory()
            }.onFailure { error ->
                failRemote(error, "解压失败") {
                    it.copy(
                        isOperatingFile = false,
                        actionErrorMessage = error.message ?: "解压失败"
                    )
                }
            }
        }
    }

    @Suppress("unused")
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

    private fun failRemote(error: Throwable, fallback: String, update: (SshUiState) -> SshUiState) {
        if (!repository.isConnected()) {
            dropToConnection(error.message ?: fallback)
        } else {
            _uiState.update { update(it) }
        }
    }

    private fun dropToConnection(message: String) {
        sessionEpoch++
        directoryJob?.cancel()
        requestedDirectory = "/"
        terminalEmulator = null
        _uiState.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                isLoadingFiles = false,
                isOperatingFile = false,
                isPreviewLoading = false,
                isSavingFile = false,
                isCalculatingChecksum = false,
                fileList = emptyList(),
                fileFetchError = null,
                previewError = null,
                selectedFilePreview = null,
                showTerminalScreen = false,
                terminalSessionStarted = false,
                terminalClosed = false,
                terminalLines = emptyList(),
                pendingInitialCommand = null,
                isCtrlActive = false,
                remotePlatform = RemotePlatform.UNKNOWN,
                connectionError = message,
                isInArchiveMode = false,
                archiveFile = null,
                archiveSubPath = "",
                archiveEntries = emptyList(),
                archiveError = null,
                showActionMenu = false,
                showRenameDialog = false,
                showDeleteConfirmDialog = false,
                showCompressDialog = false,
                showCreateFolderDialog = false,
                showCreateFileDialog = false,
                showFileDetailsDialog = false
            )
        }
    }

    private fun restartTerminal() {
        val cols = terminalCols
        val rows = terminalRows
        terminalEmulator = null
        _uiState.update {
            it.copy(
                terminalSessionStarted = false,
                terminalClosed = false,
                terminalLines = emptyList(),
                isCtrlActive = false
            )
        }
        startTerminalSession(cols, rows)
    }

    private fun noted(action: String): String {
        return if (repository.consumeReconnectNotice()) "连接已恢复，$action" else action
    }
}
