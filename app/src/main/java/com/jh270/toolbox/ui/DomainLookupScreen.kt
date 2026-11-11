package com.jh270.toolbox.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jh270.toolbox.R
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
    BackHandler(onBack = onBack)
    ToolBoxScaffold(
        title = stringResource(R.string.domain_title),
        subtitle = stringResource(R.string.domain_subtitle),
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenPadding(padding),
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
                        title = if (state.error) {
                            stringResource(R.string.domain_query_failed)
                        } else {
                            stringResource(R.string.domain_not_registered_title)
                        },
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
    val dismissKeyboard = rememberKeyboardDismisser()
    val submit: (String) -> Unit = { value ->
        dismissKeyboard()
        if (!loading) onLookup(value)
    }
    OutlinedPanel {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.domain_label)) },
            placeholder = { Text(stringResource(R.string.domain_placeholder)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Search,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onSearch = { submit(input) })
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { submit(input) },
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
                Text(stringResource(R.string.domain_querying))
            } else {
                Text(stringResource(R.string.domain_query))
            }
        }
        if (recent.isNotEmpty() && !loading) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.domain_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            RecentRow(recent = recent, onPick = submit)
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
        title = stringResource(R.string.domain_intro_title),
        text = stringResource(R.string.domain_intro_text)
    )
}

@Composable
private fun LoadingBlock() {
    OutlinedPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.domain_loading),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DomainResult(record: DomainRecord) {
    val context = LocalContext.current
    val copiedSummary = stringResource(R.string.copied_summary)
    val copiedRaw = stringResource(R.string.copied_raw)
    var showRaw by remember(record.raw) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DomainTitle(record)
        if (record.statuses.isNotEmpty()) StatusRow(record)
        SectionHeader(stringResource(R.string.section_registration))
        OutlinedPanel {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                record.registrar?.name?.let { DetailLine(stringResource(R.string.field_registrar), it) }
                record.registrar?.ianaId?.let { DetailLine(stringResource(R.string.field_registrar_id), it) }
                record.registrar?.url?.let { value ->
                    DetailLine(stringResource(R.string.field_url), value, link = true) { openExternalLink(context, value) }
                }
                record.registrar?.abuseEmail?.let { value ->
                    DetailLine(stringResource(R.string.field_abuse_email), value, link = true) {
                        openExternalLink(context, "mailto:$value")
                    }
                }
                record.registrar?.abusePhone?.let { DetailLine(stringResource(R.string.field_abuse_phone), it) }
                record.handle?.let { DetailLine(stringResource(R.string.field_handle), it) }
                record.registeredAt?.let { DetailLine(stringResource(R.string.field_registered), DomainDates.format(it)) }
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
                        "${DomainDates.format(rawDate)} · ${context.expiryText(hint)}"
                    }
                    DetailLine(stringResource(R.string.field_expires), text, valueColor = color)
                }
                record.changedAt?.let { DetailLine(stringResource(R.string.field_changed), DomainDates.format(it)) }
                record.databaseUpdatedAt?.let { DetailLine(stringResource(R.string.field_updated), DomainDates.format(it)) }
                record.dnssec?.let { DetailLine("DNSSEC", context.dnssecText(it)) }
                if (record.contacts.isEmpty()) {
                    DetailLine(stringResource(R.string.field_contacts), stringResource(R.string.contacts_private))
                }
            }
        }
        if (record.nameservers.isNotEmpty()) {
            SectionHeader(stringResource(R.string.section_nameservers))
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
            SectionHeader(stringResource(R.string.section_contacts))
            OutlinedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    record.contacts.forEach { contact -> ContactBlock(contact) }
                }
            }
        }
        if (record.sparse) {
            InfoBanner(
                icon = Icons.Default.Public,
                title = stringResource(R.string.domain_sparse_title),
                text = stringResource(R.string.domain_sparse_text)
            )
        }
        SectionHeader(stringResource(R.string.section_source))
        OutlinedPanel {
            Text(
                text = record.sourceLabel(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (record.source == RecordSource.RDAP) {
                    stringResource(R.string.source_rdap)
                } else {
                    stringResource(R.string.source_whois)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    copyPlainText(
                        context,
                        context.domainSummary(record),
                        copiedSummary
                    )
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_summary))
                }
                TextButton(onClick = { showRaw = true }) {
                    Text(stringResource(R.string.raw_record))
                }
            }
        }
    }
    if (showRaw) {
        AlertDialog(
            onDismissRequest = { showRaw = false },
            title = { Text(stringResource(R.string.raw_record)) },
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
                TextButton(onClick = {
                    copyPlainText(context, record.raw, copiedRaw)
                }) {
                    Text(stringResource(R.string.action_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRaw = false }) { Text(stringResource(R.string.action_close)) }
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
                    text = LocalContext.current.statusText(status),
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
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = context.roleText(contact.role),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = context.contactSummary(contact),
            style = MaterialTheme.typography.bodyMedium
        )
        if (contact.redacted && contact.visible) {
            Text(
                text = stringResource(R.string.contact_redacted_rest),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
