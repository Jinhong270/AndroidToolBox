package com.jh270.toolbox.ui

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.data.FileType
import com.jh270.toolbox.data.RemoteFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteFileManagerScreen(
    viewModel: SshViewModel,
    uiState: SshUiState,
) {
    val context = LocalContext.current
    var editingPath by remember(uiState.displayPath) { mutableStateOf(uiState.displayPath) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.displayPath) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(uiState.actionSuccessMessage) {
        if (uiState.actionSuccessMessage != null) {
            Toast.makeText(context, uiState.actionSuccessMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "文件管理器",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.openTerminal() }) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "终端"
                        )
                    }
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(
                            imageVector = Icons.Default.PowerOff,
                            contentDescription = "断开",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { viewModel.navigateUp() },
                    enabled = uiState.isInArchiveMode || (uiState.currentPath != "/" && uiState.currentPath.isNotBlank())
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "上级"
                    )
                }

                OutlinedTextField(
                    value = editingPath,
                    onValueChange = { editingPath = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("路径") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!uiState.isInArchiveMode) {
                                viewModel.loadDirectory(editingPath)
                            }
                        }
                    )
                )

                IconButton(
                    onClick = { viewModel.refreshDirectory() },
                    enabled = !uiState.isLoadingFiles && !uiState.isLoadingArchiveEntries
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("搜索...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                if (!uiState.isInArchiveMode) {
                    OutlinedButton(
                        onClick = { viewModel.openCreateFolderDialog() },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "新文件夹")
                    }

                    OutlinedButton(
                        onClick = { viewModel.openCreateFileDialog() },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "新文件")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.fileFetchError != null || uiState.archiveError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "错误:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = uiState.fileFetchError ?: uiState.archiveError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refreshDirectory() }) {
                            Text("重试")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val activeFiles = viewModel.getActiveFileList()
            val isLoading = uiState.isLoadingFiles || uiState.isLoadingArchiveEntries

            if (isLoading && activeFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("读取中...")
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val filteredFiles = activeFiles.filter {
                        it.name == ".." || uiState.searchQuery.isBlank() || it.name.contains(uiState.searchQuery, ignoreCase = true)
                    }

                    if (filteredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "无匹配" else "空目录",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredFiles, key = { it.path }) { file ->
                                RemoteFileRow(
                                    file = file,
                                    onClick = { viewModel.previewFile(file) },
                                    onLongClick = {
                                        if (!uiState.isInArchiveMode) {
                                            viewModel.selectFileForAction(file)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showCreateFolderDialog) {
        CreateFileDialog(
            title = "新建文件夹",
            label = "名称",
            isOperating = uiState.isOperatingFile,
            errorMessage = uiState.actionErrorMessage,
            onConfirm = { name -> viewModel.executeCreateFolder(name) },
            onDismiss = { viewModel.closeCreateFolderDialog() }
        )
    }

    if (uiState.showCreateFileDialog) {
        CreateFileDialog(
            title = "新建文件",
            label = "名称",
            isOperating = uiState.isOperatingFile,
            errorMessage = uiState.actionErrorMessage,
            onConfirm = { name -> viewModel.executeCreateFile(name) },
            onDismiss = { viewModel.closeCreateFileDialog() }
        )
    }

    if (uiState.selectedFilePreview != null || uiState.isPreviewLoading || uiState.previewError != null) {
        FilePreviewDialog(
            filePreview = uiState.selectedFilePreview,
            isLoading = uiState.isPreviewLoading,
            errorMessage = uiState.previewError,
            isSaving = uiState.isSavingFile,
            saveSuccessMessage = uiState.saveSuccessMessage,
            saveErrorMessage = uiState.saveErrorMessage,
            onSaveContent = { path, content ->
                viewModel.saveFileContent(path, content)
            },
            onDismiss = { viewModel.closePreview() }
        )
    }

    if (uiState.showActionMenu && (uiState.actionTargetFile != null)) {
        FileActionMenuDialog(
            targetFile = uiState.actionTargetFile,
            onPreview = {
                val file = uiState.actionTargetFile
                viewModel.closeActionMenu()
                viewModel.previewFile(file)
            },
            onExecuteInTerminal = {
                val file = uiState.actionTargetFile
                viewModel.executeRemoteFileInTerminal(file)
            },
            onDetails = { viewModel.openFileDetailsDialog() },
            onRename = { viewModel.openRenameDialog() },
            onCompress = { viewModel.openCompressDialog() },
            onDecompress = { viewModel.executeDecompress() },
            onDelete = { viewModel.openDeleteConfirmDialog() },
            onDismiss = { viewModel.closeActionMenu() }
        )
    }

    if (uiState.showFileDetailsDialog && uiState.actionTargetFile != null) {
        FileDetailDialog(
            targetFile = uiState.actionTargetFile,
            checksumResult = uiState.checksumResult,
            isCalculating = uiState.isCalculatingChecksum,
            errorMessage = uiState.actionErrorMessage,
            onCalculateChecksum = { viewModel.executeCalculateChecksum() },
            onDismiss = { viewModel.closeFileDetailsDialog() }
        )
    }

    if (uiState.showCompressDialog && uiState.actionTargetFile != null) {
        CompressDialog(
            targetFile = uiState.actionTargetFile,
            isOperating = uiState.isOperatingFile,
            errorMessage = uiState.actionErrorMessage,
            onConfirmCompress = { format -> viewModel.executeCompress(format) },
            onDismiss = { viewModel.closeCompressDialog() }
        )
    }

    if (uiState.showRenameDialog && uiState.actionTargetFile != null) {
        RenameFileDialog(
            targetFile = uiState.actionTargetFile,
            isOperating = uiState.isOperatingFile,
            errorMessage = uiState.actionErrorMessage,
            onConfirmRename = { newName -> viewModel.executeRenameFile(newName) },
            onDismiss = { viewModel.closeRenameDialog() }
        )
    }

    if (uiState.showDeleteConfirmDialog && uiState.actionTargetFile != null) {
        DeleteConfirmDialog(
            targetFile = uiState.actionTargetFile,
            isOperating = uiState.isOperatingFile,
            errorMessage = uiState.actionErrorMessage,
            onConfirmDelete = { viewModel.executeDeleteFile() },
            onDismiss = { viewModel.closeDeleteConfirmDialog() }
        )
    }
}

@Composable
fun RemoteFileRow(
    file: RemoteFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (file.name == "..") null else onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (file.isDirectory) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (file.fileType) {
                FileType.DIRECTORY -> Icons.Default.Folder
                FileType.TEXT -> Icons.Default.Code
                FileType.IMAGE -> Icons.Default.Image
                FileType.AUDIO -> Icons.Default.Audiotrack
                FileType.VIDEO -> Icons.Default.Videocam
                FileType.ARCHIVE -> Icons.Default.Archive
                FileType.EXECUTABLE -> Icons.Default.Terminal
                FileType.BINARY -> Icons.Default.Terminal
                FileType.UNKNOWN -> Icons.AutoMirrored.Filled.InsertDriveFile
            }

            val tint = when (file.fileType) {
                FileType.DIRECTORY -> Color(0xFFF59E0B)
                FileType.TEXT -> Color(0xFF3B82F6)
                FileType.IMAGE -> Color(0xFF8B5CF6)
                FileType.AUDIO -> Color(0xFF06B6D4)
                FileType.VIDEO -> Color(0xFFEF4444)
                FileType.ARCHIVE -> Color(0xFF10B981)
                FileType.EXECUTABLE -> Color(0xFF10B981)
                FileType.BINARY -> Color(0xFFEF4444)
                FileType.UNKNOWN -> Color(0xFF64748B)
            }

            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (file.name == "..") {
                    Text(
                        text = "返回",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (file.isDirectory) "目录" else formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = file.permissions,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
