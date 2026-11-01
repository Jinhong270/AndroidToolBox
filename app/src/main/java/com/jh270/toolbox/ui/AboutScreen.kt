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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.BuildConfig
import com.jh270.toolbox.R
import com.jh270.toolbox.data.AppInfo

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val copiedRepo = stringResource(R.string.copied_repo)
    val appName = stringResource(R.string.app_name)

    ToolBoxScaffold(
        title = stringResource(R.string.about_title),
        subtitle = stringResource(R.string.about_subtitle, appName),
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

            item { SectionHeader(stringResource(R.string.section_repo)) }

            item {
                OutlinedPanel {
                    Text(
                        text = stringResource(R.string.about_repo_text),
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
                            onClick = { copyPlainText(context, AppInfo.REPO_URL, copiedRepo) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.copy_address))
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
                            Text(stringResource(R.string.open_repo))
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.section_license)) }

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
                                text = stringResource(R.string.license_freedom),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_free_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Default.Gavel,
                    title = stringResource(R.string.about_refund_title),
                    text = stringResource(R.string.about_refund_text),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            item { SectionHeader(stringResource(R.string.section_privacy)) }

            item {
                InfoBanner(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.privacy_title),
                    text = stringResource(R.string.privacy_text),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            item { SectionHeader(stringResource(R.string.section_links)) }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.author_page),
                        subtitle = AppInfo.AUTHOR,
                        icon = Icons.Default.Person,
                        onClick = { openExternalLink(context, AppInfo.AUTHOR_URL) },
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    GroupDivider()
                    SettingsRow(
                        title = stringResource(R.string.releases_title),
                        subtitle = stringResource(R.string.releases_sub),
                        icon = Icons.Default.SystemUpdate,
                        onClick = { openExternalLink(context, AppInfo.RELEASE_PAGE) },
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.section_oss)) }

            item {
                val thirdParty = listOf(
                    stringResource(R.string.oss_7z),
                    stringResource(R.string.oss_androidx)
                )
                OutlinedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        thirdParty.forEach { component ->
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
                    text = stringResource(
                        R.string.about_footer,
                        AppInfo.AUTHOR,
                        AppInfo.LICENSE_NAME,
                        BuildConfig.VERSION_NAME
                    ),
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
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = heroContent
                )
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.labelMedium,
                    color = heroContent.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.about_version_free, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = heroContent.copy(alpha = 0.95f)
                )
            }
        }
    }
}
