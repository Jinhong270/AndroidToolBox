package com.jh270.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.data.AuthType

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ToolBox - SSH文件管理器", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "SSH 远程设备连接配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.config.host,
                            onValueChange = { viewModel.updateHost(it) },
                            label = { Text("IP 地址 / 主机名") },
                            placeholder = { Text("例如: 192.168.1.100") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true,
                            isError = !isHostValid && uiState.config.host.isNotEmpty()
                        )

                        OutlinedTextField(
                            value = uiState.config.port.toString(),
                            onValueChange = { viewModel.updatePort(it) },
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.3f),
                            singleLine = true,
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
                        isError = !isUsernameValid && uiState.config.username.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "认证方式",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            RadioButton(
                                selected = uiState.config.authType == AuthType.PASSWORD,
                                onClick = { viewModel.updateAuthType(AuthType.PASSWORD) }
                            )
                            Text("密码认证", modifier = Modifier.padding(start = 4.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = uiState.config.authType == AuthType.PRIVATE_KEY,
                                onClick = { viewModel.updateAuthType(AuthType.PRIVATE_KEY) }
                            )
                            Text("私钥认证", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.config.authType == AuthType.PASSWORD) {
                        OutlinedTextField(
                            value = uiState.config.password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = { Text("密码") },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = uiState.config.privateKey,
                            onValueChange = { viewModel.updatePrivateKey(it) },
                            label = { Text("SSH 私钥内容") },
                            placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY----- ...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isFormValid) {
                        Text(
                            text = "请完整填写 IP 地址、端口(1-65535)、用户名以及密码或私钥",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.connect() },
                        enabled = isFormValid && !uiState.isConnecting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .height(18.dp)
                                    .width(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text("正在连接 SSH 服务器...")
                        } else {
                            Text("连接 SSH 设备")
                        }
                    }
                }
            }

            if (uiState.connectionError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "连接失败:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.connectionError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
