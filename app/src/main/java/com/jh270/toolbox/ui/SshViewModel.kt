package com.jh270.toolbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jh270.toolbox.data.AuthType
import com.jh270.toolbox.data.ChecksumResult
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.RemoteFile
import com.jh270.toolbox.data.SshConfig
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

data class TerminalRecord(
    val prompt: String,
    val command: String,
    val output: String
)

data class SshUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val config: SshConfig = SshConfig(host = "192.168.1.100", port = 22, username = "root"),
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
    val showTerminalScreen: Boolean = false,
    val terminalPath: String = "/",
    val terminalHistory: List<TerminalRecord> = emptyList(),
    val terminalCommandInput: String = "",
    val isExecutingCommand: Boolean = false,
    val isOperatingFile: Boolean = false,
    val isCalculatingChecksum: Boolean = false,
    val checksumResult: ChecksumResult? = null,
    val actionSuccessMessage: String? = null,
    val actionErrorMessage: String? = null,
    val searchQuery: String = ""
)

class SshViewModel(
    private val repository: SshRepository = SshRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SshUiState())
    val uiState: StateFlow<SshUiState> = _uiState.asStateFlow()

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
                    terminalHistory = emptyList()
                )
            }
        }
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
            _uiState.update { it.copy(connectionError = "Host IP / Domain cannot be empty") }
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
                        connectionError = null
                    )
                }
                loadDirectory(pwd)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        connectionError = error.message ?: "Failed to connect to SSH server"
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
                    terminalHistory = emptyList()
                )
            }
        }
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true, fileFetchError = null, currentPath = path) }
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
                        fileFetchError = error.message ?: "Failed to list directory contents"
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val current = uiState.value.currentPath
        if (current == "/" || current.isBlank()) return

        val parent = current.trimEnd('/').substringBeforeLast('/', "")
        val targetPath = if (parent.isEmpty()) "/" else parent
        loadDirectory(targetPath)
    }

    fun refreshDirectory() {
        loadDirectory(uiState.value.currentPath)
    }

    fun previewFile(file: RemoteFile) {
        if (file.isDirectory) {
            loadDirectory(file.path)
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
                        previewError = error.message ?: "Failed to preview file"
                    )
                }
            }
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

    fun openTerminal() {
        _uiState.update { it.copy(showTerminalScreen = true) }
    }

    fun closeTerminal() {
        _uiState.update { it.copy(showTerminalScreen = false) }
    }

    fun updateTerminalCommand(cmd: String) {
        _uiState.update { it.copy(terminalCommandInput = cmd) }
    }

    fun runTerminalCommand() {
        val cmd = uiState.value.terminalCommandInput.trim()
        if (cmd.isBlank()) return

        val state = uiState.value
        val promptStr = "${state.config.username}@${state.config.host}:${state.terminalPath}$ "

        _uiState.update {
            it.copy(
                terminalCommandInput = "",
                isExecutingCommand = true
            )
        }

        viewModelScope.launch {
            val fullCmd = if (cmd.startsWith("cd ")) {
                "cd \"${state.terminalPath}\" && $cmd && pwd"
            } else {
                "cd \"${state.terminalPath}\" && $cmd"
            }

            val result = repository.executeShellCommand(fullCmd)
            result.onSuccess { output ->
                var newPath = state.terminalPath
                var cleanOutput = output
                if (cmd.startsWith("cd ")) {
                    val lines = output.lines().filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        newPath = lines.last().trim()
                        cleanOutput = lines.dropLast(1).joinToString("\n")
                    }
                }

                val newRecord = TerminalRecord(
                    prompt = promptStr,
                    command = cmd,
                    output = cleanOutput
                )

                _uiState.update {
                    it.copy(
                        terminalPath = newPath,
                        terminalHistory = it.terminalHistory + newRecord,
                        isExecutingCommand = false
                    )
                }
            }.onFailure { error ->
                val errRecord = TerminalRecord(
                    prompt = promptStr,
                    command = cmd,
                    output = "错误: ${error.message}"
                )
                _uiState.update {
                    it.copy(
                        terminalHistory = it.terminalHistory + errRecord,
                        isExecutingCommand = false
                    )
                }
            }
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
                showActionMenu = false,
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
}
