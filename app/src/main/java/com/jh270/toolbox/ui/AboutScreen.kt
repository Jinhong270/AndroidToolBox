package com.jh270.toolbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.BuildConfig
import com.jh270.toolbox.data.AppInfo

private val ThirdPartyComponents = listOf(
    "JSch（BSD）— SSH / SFTP 协议实现",
    "Bouncy Castle（MIT）与 EdDSA-Java（CC0）— 密钥与算法支持",
    "Apache Commons Compress（Apache-2.0）与 XZ for Java（公有领域）— 压缩包处理",
    "AndroidX、Jetpack Compose、Material Design（Apache-2.0）— 界面框架"
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    ToolBoxScaffold(
        title = "关于本项目",
        subtitle = "${AppInfo.NAME} · 开源免费",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AboutHeader() }

            item { SectionHeader("官方仓库") }

            item {
                OutlinedPanel {
                    Text(
                        text = "源码、问题反馈与版本更新都只在这个仓库发布。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = AppInfo.REPO_URL,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { copyPlainText(context, AppInfo.REPO_URL, "已复制仓库地址") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("复制地址")
                        }
                        Button(
                            onClick = { openExternalLink(context, AppInfo.REPO_URL) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("打开仓库")
                        }
                    }
                }
            }

            item { SectionHeader("许可与费用") }

            item {
                OutlinedPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = AppInfo.LICENSE_NAME,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "可自由使用、修改与分发",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "本软件开源免费，没有付费版本、没有内购、没有会员。作者不会以任何名义向你收费，也不需要你提供任何支付信息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Default.Gavel,
                    title = "在别处付过钱？请退款并举报",
                    text = "如果你在应用商店、网盘、群聊或任何渠道付费获得了本软件，请立即申请退款，并向该平台举报卖家：ToolBox 本身完全免费，收费渠道都是倒卖。",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            item { SectionHeader("隐私") }

            item {
                InfoBanner(
                    icon = Icons.Default.Lock,
                    title = "连接不经过第三方",
                    text = "主机配置与密码只保存在本机应用私有目录。域名查询直接发往 IANA 和对应注册局。应用不含统计、广告或崩溃上报 SDK，不会把主机信息、凭据、文件内容或查询记录发送给作者。",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            item { SectionHeader("相关链接") }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = "作者主页",
                        subtitle = AppInfo.AUTHOR,
                        icon = Icons.Default.Person,
                        onClick = { openExternalLink(context, AppInfo.AUTHOR_URL) },
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "版本与发布",
                        subtitle = "查看最新版本与更新日志",
                        icon = Icons.Default.SystemUpdate,
                        onClick = { openExternalLink(context, AppInfo.RELEASE_PAGE) },
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            item { SectionHeader("第三方开源组件") }

            item {
                OutlinedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThirdPartyComponents.forEach { component ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = component,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "© 2026 ${AppInfo.AUTHOR} · ${AppInfo.LICENSE_NAME} · 版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AboutHeader() {
    val heroContent = heroContentColor()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(heroBrush())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBoxMark(
                size = 54.dp,
                fontSize = 20,
                container = heroContent.copy(alpha = 0.18f),
                content = heroContent
            )
            Spacer(modifier = Modifier.width(16.dp))
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
                    color = heroContent.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME} · 开源免费",
                    style = MaterialTheme.typography.labelSmall,
                    color = heroContent.copy(alpha = 0.95f)
                )
            }
        }
    }
}
