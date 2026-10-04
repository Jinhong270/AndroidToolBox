package com.jh270.toolbox.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalScreen(
    viewModel: SshViewModel,
    uiState: SshUiState
) {
    val outputScrollState = rememberScrollState()

    LaunchedEffect(uiState.terminalOutput) {
        outputScrollState.animateScrollTo(outputScrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SSH 远程终端",
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
                        onClick = { viewModel.closeTerminal() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("关闭终端")
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = { viewModel.updateTerminalCommand("ls -la"); viewModel.runTerminalCommand() }) {
                    Text("ls")
                }
                OutlinedButton(onClick = { viewModel.updateTerminalCommand("pwd"); viewModel.runTerminalCommand() }) {
                    Text("pwd")
                }
                OutlinedButton(onClick = { viewModel.updateTerminalCommand("df -h"); viewModel.runTerminalCommand() }) {
                    Text("df")
                }
                OutlinedButton(onClick = { viewModel.updateTerminalCommand("free -h"); viewModel.runTerminalCommand() }) {
                    Text("free")
                }
                OutlinedButton(onClick = { viewModel.updateTerminalCommand("uname -a"); viewModel.runTerminalCommand() }) {
                    Text("uname")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
                    .verticalScroll(outputScrollState)
            ) {
                SelectionContainer {
                    Text(
                        text = uiState.terminalOutput.ifEmpty { "连接成功。在此输入并执行远程 Shell 命令...\n" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFD4D4D4)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.terminalCommandInput,
                    onValueChange = { viewModel.updateTerminalCommand(it) },
                    placeholder = { Text("输入 shell 命令 (例如: htop, ps aux)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { viewModel.runTerminalCommand() }
                    )
                )

                Button(
                    onClick = { viewModel.runTerminalCommand() },
                    enabled = !uiState.isExecutingCommand
                ) {
                    if (uiState.isExecutingCommand) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .width(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("执行")
                    }
                }
            }
        }
    }
}
