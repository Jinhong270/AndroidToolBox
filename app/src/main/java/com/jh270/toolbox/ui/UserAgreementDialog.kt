package com.jh270.toolbox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun UserAgreementDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "使用条款与隐私声明",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "工具箱（ToolBox）是一款开源的 SSH 文件管理与远程诊断工具。所有的网络连接、身份凭证与文件传输均直接在您的设备与远程服务器之间建立与执行，绝不收集或传输任何敏感数据至第三方服务器。使用本应用即表示您同意合法、合规地使用相关功能。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("同意并继续")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("拒绝")
            }
        }
    )
}
