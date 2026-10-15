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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.data.AppInfo

private data class AgreementSection(
    val title: String,
    val paragraphs: List<String>
)

private val AgreementSections = listOf(
    AgreementSection(
        title = "一、开源免费，认准唯一官方来源",
        paragraphs = listOf(
            "ToolBox 是完全开源免费的软件。源码公开在唯一官方仓库 ${AppInfo.REPO_URL}，以 ${AppInfo.LICENSE_NAME} 许可发布，任何人都可以免费下载、使用、修改和分发。",
            "作者从未在任何应用商店或第三方渠道上架收费版本，也没有会员、内购、解锁码、去广告付费等任何收费项目。",
            "如果你在某个渠道为 ToolBox 付了钱，或被人以“高级版”“专业版”“永久授权”为名收费，请立即申请退款，并向该平台举报该卖家：你买到的是别人倒卖的免费开源软件。遇到付费，就说明来源是假的。"
        )
    ),
    AgreementSection(
        title = "二、软件功能与用途",
        paragraphs = listOf(
            "本应用是一款 SSH / SFTP 客户端，用于连接你自有的远程主机（Linux、macOS、Windows OpenSSH 等），提供文件浏览与管理、文本查看与编辑、压缩与解压、MD5 / SHA256 校验值计算，以及交互式 Shell 终端。",
            "本应用不是云服务，没有账号体系，也不需要注册或登录。"
        )
    ),
    AgreementSection(
        title = "三、数据与隐私",
        paragraphs = listOf(
            "所有连接都由你的设备与目标服务器直接建立，不经过任何中间服务器，开发者无法看到你的连接内容。",
            "应用不收集、不上传主机地址、端口、用户名、密码、私钥或远程文件内容；安装包内不含统计 SDK、广告 SDK 或崩溃上报 SDK，也不需要位置、通讯录、短信等权限。",
            "为免去重复输入，主机配置与密码会保存在本机应用私有存储中（Android 私有目录里的 SharedPreferences，字段经 Base64 编码，并非加密）。设备被 root 或被入侵时这些内容可能被读取，高敏感环境请改用私钥认证并妥善保管设备。",
            "卸载应用会一并删除上述本地数据；也可以随时在应用内删除已保存的主机配置。",
            "向远程主机发起连接、传输文件、执行命令属于网络通信行为，相关网络流量由你的运营商与目标服务器记录，这部分不在本应用控制范围内。"
        )
    ),
    AgreementSection(
        title = "四、使用者的责任",
        paragraphs = listOf(
            "你只能连接自己拥有，或已获得所有者明确授权的服务器。",
            "禁止将本应用用于未授权访问、扫描、入侵、干扰他人系统或任何违法用途。",
            "你需要遵守所在地法律法规、目标服务器所在地法律法规，以及服务器所有者的使用规定。",
            "你在终端里执行的命令、上传下载的文件，其后果由你本人承担。"
        )
    ),
    AgreementSection(
        title = "五、风险提示与免责声明",
        paragraphs = listOf(
            "远程文件操作存在误删、覆盖、移动、改权限等不可逆风险。执行删除、覆盖、解压、重命名、修改权限等操作前，请自行确认并提前备份重要数据。",
            "终端命令由远程服务器执行，本应用只负责传递与显示，不保证命令结果符合你的预期。",
            "本软件按“现状”提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性与不侵权担保。",
            "在法律允许的最大范围内，作者不对因使用或无法使用本软件造成的任何直接或间接损失承担责任，包括数据丢失、服务中断、业务中断或利润损失。"
        )
    ),
    AgreementSection(
        title = "六、第三方开源组件",
        paragraphs = listOf(
            "本应用使用了以下开源组件，版权归各自作者所有，并遵循各自的许可协议：",
            "· JSch（BSD 许可）—— SSH / SFTP 协议实现",
            "· Bouncy Castle（MIT 许可）与 EdDSA-Java（CC0）—— 密钥与算法支持",
            "· Apache Commons Compress（Apache-2.0）与 XZ for Java（公有领域）—— 压缩包处理",
            "· AndroidX / Jetpack Compose / Material Design（Apache-2.0）—— 界面框架",
            "本应用本身遵循 ${AppInfo.LICENSE_NAME}，完整许可文本见仓库根目录的 LICENSE 文件。"
        )
    ),
    AgreementSection(
        title = "七、协议的更新与接受",
        paragraphs = listOf(
            "协议内容可能随版本更新而调整，最新版本随安装包与官方仓库一同发布。",
            "点击“同意并继续”表示你已完整阅读并接受以上全部条款；若不同意，请点击“拒绝并退出”，应用会立即关闭且不保存任何数据。",
            "同意后你仍可随时在“设置 → 关于本项目”中重新查看本协议全文。"
        )
    )
)

@Composable
private fun AgreementBody(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AgreementSections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                section.paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun UserAgreementGate(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val reachedEnd = scrollState.value >= scrollState.maxValue - 12

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolBoxMark(size = 46.dp, fontSize = 18)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "使用条款与隐私声明",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "首次使用前请完整阅读",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                InfoBanner(
                    icon = Icons.Default.Gavel,
                    title = "开源免费，付费即为被骗",
                    text = "ToolBox 不收费、无内购。唯一官方来源是 ${AppInfo.REPO_LABEL}。若你在任何平台付费购买，请立即退款并向平台举报卖家。",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(20.dp))

                AgreementBody()

                Spacer(modifier = Modifier.height(18.dp))

                TextButton(onClick = { openExternalLink(context, AppInfo.REPO_URL) }) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("打开官方仓库 ${AppInfo.REPO_LABEL}")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (reachedEnd) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (reachedEnd) "已阅读到末尾，可以继续使用" else "请向下滚动，阅读完整条款",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(0.4f)
                        ) {
                            Text("拒绝并退出")
                        }
                        Button(
                            onClick = onAccept,
                            enabled = reachedEnd,
                            modifier = Modifier.weight(0.6f)
                        ) {
                            Text("同意并继续")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgreementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    ToolBoxScaffold(
        title = "使用条款与隐私声明",
        subtitle = "${AppInfo.NAME} · ${AppInfo.LICENSE_NAME}",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            InfoBanner(
                icon = Icons.Default.Gavel,
                title = "开源免费，付费即为被骗",
                text = "ToolBox 不收费、无内购。唯一官方来源是 ${AppInfo.REPO_LABEL}。若你在任何平台付费购买，请立即退款并向平台举报卖家。",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(20.dp))

            AgreementBody()

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { copyPlainText(context, AppInfo.REPO_URL, "已复制仓库地址") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制仓库地址")
                }
                Button(
                    onClick = { openExternalLink(context, AppInfo.REPO_URL) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("打开仓库")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
