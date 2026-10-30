package com.jh270.toolbox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.data.AppInfo

@Composable
fun ToolBoxHomeScreen(
    onSelectSshFileManager: () -> Unit,
    onSelectDomainLookup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    ToolBoxScaffold(
        title = AppInfo.NAME,
        subtitle = "SSH · 域名查询",
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置"
                )
            }
        }
    ) { padding ->
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { HeroPanel() }

                item { SectionHeader("工具") }

                item {
                    NavCard(
                        title = "SSH 文件管理器",
                        subtitle = "连接 · 文件 · 终端",
                        description = "密码或私钥登录，浏览与编辑远程文件，压缩解压，校验哈希。",
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        onClick = onSelectSshFileManager
                    )
                }

                item {
                    NavCard(
                        title = "域名查询",
                        subtitle = "RDAP · WHOIS",
                        description = "查询注册局公开信息：注册商、到期时间、域名服务器与状态。支持国家域名和 .com、.org、.dev 等全部后缀。",
                        icon = Icons.Default.Public,
                        onClick = onSelectDomainLookup,
                        accentContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        accentContent = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                item {
                    NavCard(
                        title = "设置",
                        subtitle = "外观 · 关于 · 使用协议",
                        description = "调整终端配色，查看版本信息与开源许可。",
                        icon = Icons.Default.Settings,
                        onClick = onOpenSettings,
                        accentContainer = MaterialTheme.colorScheme.secondaryContainer,
                        accentContent = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                item { SectionHeader("安全与开源") }

                item {
                    InfoBanner(
                        icon = Icons.Default.Lock,
                        title = "凭据只发给你连接的那台服务器",
                        text = "密码与私钥仅保存在本机，应用不会把它们上传到任何第三方服务器。",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                item {
                    OpenSourceCard(onClick = onOpenSettings)
                }

                item {
                    Text(
                        text = "ToolBox · 开源免费 · 无广告 · 无内购",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPanel() {
    val heroContent = heroContentColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(heroBrush())
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolBoxMark(
                    size = 46.dp,
                    fontSize = 18,
                    container = heroContent.copy(alpha = 0.18f),
                    content = heroContent
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppInfo.NAME,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = heroContent
                    )
                    Text(
                        text = AppInfo.TAGLINE,
                        style = MaterialTheme.typography.labelMedium,
                        color = heroContent.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "两个各自独立的工具：连接远程主机管理文件和终端，或向注册局查询域名的公开注册信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = heroContent.copy(alpha = 0.92f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip(icon = Icons.Default.VerifiedUser, text = "开源免费", content = heroContent)
                HeroChip(icon = Icons.Default.Cloud, text = "无广告", content = heroContent)
                HeroChip(icon = Icons.Default.Terminal, text = "本地运行", content = heroContent)
            }
        }
    }
}

@Composable
private fun HeroChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    content: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = content.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = content
            )
        }
    }
}

@Composable
private fun OpenSourceCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "完全开源，永久免费",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = AppInfo.REPO_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
