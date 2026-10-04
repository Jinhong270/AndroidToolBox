package com.jh270.toolbox.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
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
    uiState: SshUiState
) {
    val context = LocalContext.current
    var editingPath by remember(uiState.currentPath) { mutableStateOf(uiState.currentPath) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.currentPath) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(uiState.actionSuccessMessage) {
        if (uiState.actionSuccessMessage != null) {
            Toast.makeText(context, uiState.actionSuccessMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "远程 SSH 文件管理器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.config.username}@${uiState.config.host}:${uiState.config.port}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("断开连接")
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editingPath,
                    onValueChange = { editingPath = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("当前路径 (输入后回车跳转)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { viewModel.loadDirectory(editingPath) }
                    )
                )

                Button(
                    onClick = { viewModel.refreshDirectory() },
                    enabled = !uiState.isLoadingFiles
                ) {
                    Text("刷新")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("搜索当前目录下的文件...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.fileFetchError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "目录读取错误:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = uiState.fileFetchError,
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

            if (uiState.isLoadingFiles && uiState.fileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在获取远程文件列表...")
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.isLoadingFiles) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val filteredFiles = uiState.fileList.filter {
                        if (it.name == "..") true
                        else if (uiState.searchQuery.isBlank()) true
                        else it.name.contains(uiState.searchQuery, ignoreCase = true)
                    }

                    if (filteredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "未搜索到匹配的文件" else "此目录为空",
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
                                    onLongClick = { viewModel.selectFileForAction(file) }
                                )
                            }
                        }
                    }
                }
            }
        }
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

    if (uiState.showActionMenu && uiState.actionTargetFile != null) {
        FileActionMenuDialog(
            targetFile = uiState.actionTargetFile,
            onPreview = {
                val file = uiState.actionTargetFile
                viewModel.closeActionMenu()
                viewModel.previewFile(file)
            },
            onRename = { viewModel.openRenameDialog() },
            onCalculateChecksum = { viewModel.executeCalculateChecksum() },
            onCompress = { viewModel.openCompressDialog() },
            onDecompress = { viewModel.executeDecompress() },
            onDelete = { viewModel.openDeleteConfirmDialog() },
            onDismiss = { viewModel.closeActionMenu() }
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

    if (uiState.checksumResult != null || uiState.isCalculatingChecksum) {
        ChecksumResultDialog(
            checksumResult = uiState.checksumResult,
            isCalculating = uiState.isCalculatingChecksum,
            errorMessage = uiState.actionErrorMessage,
            onDismiss = { viewModel.closeChecksumDialog() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteFileRow(
    file: RemoteFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (file.name == "..") null else onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (file.isDirectory) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeTag = when (file.fileType) {
                FileType.DIRECTORY -> "[文件夹]"
                FileType.TEXT -> "[文本]"
                FileType.IMAGE -> "[图片]"
                FileType.BINARY -> "[二进制]"
                FileType.UNKNOWN -> "[文件]"
            }

            Text(
                text = typeTag,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp)
            )

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
                        text = "返回上级目录 (${file.path})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (file.isDirectory) "文件夹" else formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = file.permissions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = formatDate(file.modifiedTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
