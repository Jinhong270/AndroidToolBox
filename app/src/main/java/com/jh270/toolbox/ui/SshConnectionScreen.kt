package com.jh270.toolbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.data.AuthType

@Composable
fun SshConnectionScreen(
    viewModel: SshViewModel,
    uiState: SshUiState
) {
    var showPassword by remember { mutableStateOf(false) }

    val isHostValid = uiState.config.host.isNotBlank()
    val isPortValid = uiState.config.port in 1..65535
    val isUsernameValid = uiState.config.username.isNotBlank()
    val isAuthValid = if (uiState.config.authType == AuthType.PASSWORD) {
        uiState.config.password.isNotBlank()
    } else {
        uiState.config.privateKey.isNotBlank()
    }

    val isFormValid = isHostValid && isPortValid && isUsernameValid && isAuthValid

    ToolBoxScaffold(
        title = "SSH 连接",
        subtitle = "连接你的远程主机",
        onBack = { viewModel.returnToHome() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionHero()

            if (uiState.savedProfiles.isNotEmpty()) {
                SectionHeader("已保存的主机")
                SettingsGroup {
                    uiState.savedProfiles.forEachIndexed { index, profile ->
                        if (index > 0) {
                            GroupDivider()
                        }
                        Surface(
                            onClick = { viewModel.applyProfile(profile) },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${profile.config.username}@${profile.config.host}:${profile.config.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteProfile(profile) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除预设",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionHeader("连接信息")

            OutlinedPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "服务器地址与账号",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.newConfig() }) {
                        Text("清空")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.config.name,
                    onValueChange = { viewModel.updateConfigName(it) },
                    label = { Text("预设备注名称 (选填)") },
                    placeholder = { Text("如: 生产服务器 / 树莓派") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.config.host,
                        onValueChange = { viewModel.updateHost(it) },
                        label = { Text("IP 地址 / 主机名") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.weight(0.68f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        isError = !isHostValid && uiState.config.host.isNotEmpty()
                    )

                    OutlinedTextField(
                        value = uiState.config.port.toString(),
                        onValueChange = { viewModel.updatePort(it) },
                        label = { Text("端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.32f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        isError = !isPortValid
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.config.username,
                    onValueChange = { viewModel.updateUsername(it) },
                    label = { Text("用户名") },
                    placeholder = { Text("例如: root") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    isError = !isUsernameValid && uiState.config.username.isNotEmpty()
                )
            }

            SectionHeader("身份认证")

            OutlinedPanel {
                Text(
                    text = "选择登录方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthChoice(
                        label = "密码认证",
                        icon = Icons.Default.Password,
                        selected = uiState.config.authType == AuthType.PASSWORD,
                        onSelect = { viewModel.updateAuthType(AuthType.PASSWORD) },
                        modifier = Modifier.weight(1f)
                    )
                    AuthChoice(
                        label = "私钥认证",
                        icon = Icons.Default.Key,
                        selected = uiState.config.authType == AuthType.PRIVATE_KEY,
                        onSelect = { viewModel.updateAuthType(AuthType.PRIVATE_KEY) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (uiState.config.authType == AuthType.PASSWORD) {
                    OutlinedTextField(
                        value = uiState.config.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("密码") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                } else {
                    OutlinedTextField(
                        value = uiState.config.privateKey,
                        onValueChange = { viewModel.updatePrivateKey(it) },
                        label = { Text("SSH 私钥内容 (OpenSSH / PEM)") },
                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY----- ...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        minLines = 3,
                        maxLines = 6
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.config.passphrase,
                        onValueChange = { viewModel.updatePassphrase(it) },
                        label = { Text("私钥密码 (选填，若私钥已加密)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                InfoBanner(
                    icon = Icons.Default.Lock,
                    text = "凭据只保存在本机，并且只发送给你填写的这台服务器。",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveCurrentProfile() },
                    enabled = isFormValid,
                    modifier = Modifier.weight(0.42f)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存配置")
                }

                Button(
                    onClick = { viewModel.connect() },
                    enabled = isFormValid && !uiState.isConnecting,
                    modifier = Modifier.weight(0.58f)
                ) {
                    if (uiState.isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Text("正在连接...")
                    } else {
                        Text("连接 SSH 设备")
                    }
                }
            }

            if (uiState.connectionError != null) {
                InfoBanner(
                    icon = Icons.Default.Lock,
                    title = "连接失败",
                    text = uiState.connectionError,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConnectionHero() {
    val heroContent = heroContentColor()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(heroBrush())
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = heroContent,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "连接到远程主机",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = heroContent
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "支持密码或私钥登录，连接后可浏览文件、编辑文本并打开 Shell 终端。",
                style = MaterialTheme.typography.bodyMedium,
                color = heroContent.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun AuthChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelect,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
