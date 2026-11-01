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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.R
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
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.home_subtitle),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings)
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

                item { SectionHeader(stringResource(R.string.section_tools)) }

                item {
                    NavCard(
                        title = stringResource(R.string.home_ssh_title),
                        subtitle = stringResource(R.string.home_ssh_subtitle),
                        description = stringResource(R.string.home_ssh_desc),
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        onClick = onSelectSshFileManager
                    )
                }

                item {
                    NavCard(
                        title = stringResource(R.string.home_domain_title),
                        subtitle = stringResource(R.string.home_domain_subtitle),
                        description = stringResource(R.string.home_domain_desc),
                        icon = Icons.Default.Public,
                        onClick = onSelectDomainLookup,
                        accentContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        accentContent = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                item {
                    NavCard(
                        title = stringResource(R.string.home_settings_title),
                        subtitle = stringResource(R.string.home_settings_subtitle),
                        description = stringResource(R.string.home_settings_desc),
                        icon = Icons.Default.Settings,
                        onClick = onOpenSettings,
                        accentContainer = MaterialTheme.colorScheme.secondaryContainer,
                        accentContent = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                item { SectionHeader(stringResource(R.string.section_security)) }

                item {
                    InfoBanner(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.home_cred_title),
                        text = stringResource(R.string.home_cred_text),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                item {
                    OpenSourceCard(onClick = onOpenSettings)
                }

                item {
                    Text(
                        text = stringResource(R.string.home_footer),
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
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = heroContent
                    )
                    Text(
                        text = stringResource(R.string.tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = heroContent.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.home_hero_body),
                style = MaterialTheme.typography.bodyMedium,
                color = heroContent.copy(alpha = 0.92f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip(icon = Icons.Default.VerifiedUser, text = stringResource(R.string.chip_open_source), content = heroContent)
                HeroChip(icon = Icons.Default.Cloud, text = stringResource(R.string.chip_no_ads), content = heroContent)
                HeroChip(icon = Icons.Default.Terminal, text = stringResource(R.string.chip_on_device), content = heroContent)
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
                    text = stringResource(R.string.home_free_title),
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
