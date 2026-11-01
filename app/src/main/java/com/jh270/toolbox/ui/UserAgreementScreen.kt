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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.R
import com.jh270.toolbox.data.AppInfo

private data class AgreementSection(
    val title: String,
    val paragraphs: List<String>
)

@Composable
private fun AgreementBody(modifier: Modifier = Modifier) {
    val sections = listOf(
        AgreementSection(
            title = stringResource(R.string.agreement_1_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_1_p1, AppInfo.REPO_URL, AppInfo.LICENSE_NAME),
                stringResource(R.string.agreement_1_p2),
                stringResource(R.string.agreement_1_p3)
            )
        ),
        AgreementSection(
            title = stringResource(R.string.agreement_2_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_2_p1),
                stringResource(R.string.agreement_2_p2),
                stringResource(R.string.agreement_2_p3)
            )
        ),
        AgreementSection(
            title = stringResource(R.string.agreement_3_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_3_p1),
                stringResource(R.string.agreement_3_p2),
                stringResource(R.string.agreement_3_p3),
                stringResource(R.string.agreement_3_p4),
                stringResource(R.string.agreement_3_p5),
                stringResource(R.string.agreement_3_p6)
            )
        ),
        AgreementSection(
            title = stringResource(R.string.agreement_4_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_4_p1),
                stringResource(R.string.agreement_4_p2),
                stringResource(R.string.agreement_4_p3),
                stringResource(R.string.agreement_4_p4)
            )
        ),
        AgreementSection(
            title = stringResource(R.string.agreement_5_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_5_p1),
                stringResource(R.string.agreement_5_p2),
                stringResource(R.string.agreement_5_p3),
                stringResource(R.string.agreement_5_p4),
                stringResource(R.string.agreement_5_p5)
            )
        ),
        AgreementSection(
            title = stringResource(R.string.agreement_6_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_6_p1),
                stringResource(R.string.agreement_6_p2),
                stringResource(R.string.agreement_6_p3),
                stringResource(R.string.agreement_6_p4, AppInfo.LICENSE_NAME)
            )
        ),
        AgreementSection(
            title = stringResource(R.string.agreement_7_title),
            paragraphs = listOf(
                stringResource(R.string.agreement_7_p1),
                stringResource(R.string.agreement_7_p2),
                stringResource(R.string.agreement_7_p3)
            )
        )
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        sections.forEach { section ->
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
    val readDone = stringResource(R.string.agreement_read_done)
    val readMore = stringResource(R.string.agreement_read_more)

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
                        text = stringResource(R.string.agreement_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.agreement_gate_sub),
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
                    title = stringResource(R.string.agreement_scam_title),
                    text = stringResource(R.string.agreement_scam_text, AppInfo.REPO_LABEL),
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
                    Text(stringResource(R.string.agreement_open_repo, AppInfo.REPO_LABEL))
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
                            text = if (reachedEnd) readDone else readMore,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(0.4f)
                        ) {
                            Text(stringResource(R.string.agreement_reject))
                        }
                        Button(
                            onClick = onAccept,
                            enabled = reachedEnd,
                            modifier = Modifier.weight(0.6f)
                        ) {
                            Text(stringResource(R.string.agreement_accept))
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
    val copiedRepo = stringResource(R.string.copied_repo)
    val appName = stringResource(R.string.app_name)

    ToolBoxScaffold(
        title = stringResource(R.string.agreement_title),
        subtitle = stringResource(R.string.agreement_subtitle, appName, AppInfo.LICENSE_NAME),
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
                title = stringResource(R.string.agreement_scam_title),
                text = stringResource(R.string.agreement_scam_text, AppInfo.REPO_LABEL),
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
                    onClick = { copyPlainText(context, AppInfo.REPO_URL, copiedRepo) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.agreement_copy_repo))
                }
                Button(
                    onClick = { openExternalLink(context, AppInfo.REPO_URL) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.open_repo))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
