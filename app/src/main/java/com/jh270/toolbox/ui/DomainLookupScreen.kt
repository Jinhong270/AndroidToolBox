package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jh270.toolbox.domain.ContactInfo
import com.jh270.toolbox.domain.DomainDates
import com.jh270.toolbox.domain.DomainRecord
import com.jh270.toolbox.domain.RecordSource
import com.jh270.toolbox.domain.sourceLabel

@Composable
fun DomainLookupScreen(
    onBack: () -> Unit,
    viewModel: DomainLookupViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ToolBoxScaffold(
        title = "域名查询",
        subtitle = "注册局公开数据",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SearchPanel(
                    input = state.input,
                    loading = state.loading,
                    recent = state.recent,
                    onInput = viewModel::updateInput,
                    onLookup = viewModel::lookup
                )
            }
            when {
                state.loading -> item { LoadingBlock() }
                state.record != null -> item { DomainResult(state.record!!) }
                state.message != null -> item {
                    InfoBanner(
                        icon = Icons.Default.Public,
                        title = if (state.error) "无法查询" else "无注册记录",
                        text = state.message!!,
                        container = if (state.error) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        content = if (state.error) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
                else -> item { IntroBlock() }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    input: String,
    loading: Boolean,
    recent: List<String>,
    onInput: (String) -> Unit,
    onLookup: (String) -> Unit
) {
    OutlinedPanel {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("域名") },
            placeholder = { Text("example.com、example.co.uk、例子.中国") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Search,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onSearch = { if (!loading) onLookup(input) })
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onLookup(input) },
            enabled = !loading && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("查询中")
            } else {
                Text("查询")
            }
        }
        if (recent.isNotEmpty() && !loading) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "最近查询",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            RecentRow(recent = recent, onPick = onLookup)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentRow(recent: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recent.forEach { domain ->
            Surface(
                onClick = { onPick(domain) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun IntroBlock() {
    InfoBanner(
        icon = Icons.Default.Public,
        title = "直接查询官方注册数据",
        text = "支持国家域名和 .com、.org、.dev 等全部后缀。应用先读取 IANA 公布的 RDAP 服务表，再向负责该后缀的注册局查询；没有 RDAP 的后缀改用 IANA 公布的 WHOIS 服务器。查询从你的设备直接发出，不经过作者的服务器，也不使用第三方查询接口。"
    )
}

@Composable
private fun LoadingBlock() {
    OutlinedPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "正在向注册局查询",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DomainResult(record: DomainRecord) {
    val context = LocalContext.current
    var showRaw by remember(record.raw) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DomainTitle(record)
        if (record.statuses.isNotEmpty()) StatusRow(record)
        SectionHeader("注册信息")
        OutlinedPanel {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                record.registrar?.name?.let { DetailLine("注册商", it) }
                record.registrar?.ianaId?.let { DetailLine("注册商编号", it) }
                record.registrar?.url?.let { value ->
                    DetailLine("网址", value, link = true) { openExternalLink(context, value) }
                }
                record.registrar?.abuseEmail?.let { value ->
                    DetailLine("滥用联系邮箱", value, link = true) {
                        openExternalLink(context, "mailto:$value")
                    }
                }
                record.registrar?.abusePhone?.let { DetailLine("滥用联系电话", it) }
                record.handle?.let { DetailLine("注册编号", it) }
                record.registeredAt?.let { DetailLine("注册时间", DomainDates.format(it)) }
                record.expiresAt?.let { rawDate ->
                    val hint = DomainDates.expiryHint(rawDate)
                    val color = if (hint?.urgent == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val text = if (hint == null) {
                        DomainDates.format(rawDate)
                    } else {
                        "${DomainDates.format(rawDate)} · ${hint.text}"
                    }
                    DetailLine("到期时间", text, valueColor = color)
                }
                record.changedAt?.let { DetailLine("最近变更", DomainDates.format(it)) }
                record.databaseUpdatedAt?.let { DetailLine("数据更新", DomainDates.format(it)) }
                record.dnssec?.let { DetailLine("DNSSEC", it) }
                if (record.contacts.isEmpty()) DetailLine("联系人", "注册局未公开")
            }
        }
        if (record.nameservers.isNotEmpty()) {
            SectionHeader("域名服务器")
            OutlinedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    record.nameservers.forEach { server ->
                        Column {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            if (server.addresses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = server.addresses.joinToString("  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
        if (record.contacts.isNotEmpty()) {
            SectionHeader("联系人")
            OutlinedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    record.contacts.forEach { contact -> ContactBlock(contact) }
                }
            }
        }
        if (record.sparse) {
            InfoBanner(
                icon = Icons.Default.Public,
                title = "结构化字段很少",
                text = "注册局返回了原文，但里面几乎没有标准字段。可以打开原始记录查看全文。"
            )
        }
        SectionHeader("数据来源")
        OutlinedPanel {
            Text(
                text = record.sourceLabel(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (record.source == RecordSource.RDAP) {
                    "来自 IANA RDAP 服务表指向的注册局，以及注册商自己的 RDAP。隐私政策要求隐藏的字段不会出现。"
                } else {
                    "这个后缀没有 RDAP，结果来自 IANA 公布的 WHOIS 服务器。不同注册局的文本格式不一样，原始记录里保留了全文。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { copyPlainText(context, record.summary(), "已复制摘要") }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制摘要")
                }
                TextButton(onClick = { showRaw = true }) {
                    Text("原始记录")
                }
            }
        }
    }
    if (showRaw) {
        AlertDialog(
            onDismissRequest = { showRaw = false },
            title = { Text("原始记录") },
            text = {
                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = record.raw,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyPlainText(context, record.raw, "已复制原始记录") }) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRaw = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun DomainTitle(record: DomainRecord) {
    val heroContent = heroContentColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(heroBrush())
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = record.unicodeName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = heroContent
            )
            if (!record.asciiName.equals(record.unicodeName, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.asciiName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = heroContent.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(record: DomainRecord) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        record.statuses.forEach { status ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    link: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    val color = if (link) MaterialTheme.colorScheme.primary else valueColor
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
        )
    }
}

@Composable
private fun ContactBlock(contact: ContactInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = contact.roleLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = contact.summary(),
            style = MaterialTheme.typography.bodyMedium
        )
        if (contact.redacted && contact.visible) {
            Text(
                text = "其余字段已按隐私政策隐藏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
