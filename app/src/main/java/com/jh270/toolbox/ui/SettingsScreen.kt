package com.jh270.toolbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.BuildConfig
import com.jh270.toolbox.data.AppInfo

private enum class SettingsPage {
    MAIN,
    APPEARANCE,
    ABOUT,
    AGREEMENT
}

@Composable
fun SettingsScreen(
    viewModel: SshViewModel,
    uiState: SshUiState,
    onBack: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }

    BackHandler {
        if (page == SettingsPage.MAIN) {
            onBack()
        } else {
            page = SettingsPage.MAIN
        }
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "settings_page"
    ) { current ->
        when (current) {
            SettingsPage.MAIN -> SettingsMainPage(
                onBack = onBack,
                onOpenAppearance = { page = SettingsPage.APPEARANCE },
                onOpenAbout = { page = SettingsPage.ABOUT },
                onOpenAgreement = { page = SettingsPage.AGREEMENT }
            )

            SettingsPage.APPEARANCE -> TerminalAppearanceScreen(
                uiState = uiState,
                onBackground = viewModel::setTerminalBackground,
                onSelection = viewModel::setTerminalSelection,
                onBack = { page = SettingsPage.MAIN }
            )

            SettingsPage.ABOUT -> AboutScreen(onBack = { page = SettingsPage.MAIN })

            SettingsPage.AGREEMENT -> AgreementScreen(onBack = { page = SettingsPage.MAIN })
        }
    }
}

@Composable
private fun SettingsMainPage(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    ToolBoxScaffold(
        title = "设置",
        subtitle = "v${BuildConfig.VERSION_NAME}",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader("外观") }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = "终端配色",
                        subtitle = "背景与选中高亮，带实时预览",
                        icon = Icons.Default.Palette,
                        onClick = onOpenAppearance
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "使用协议与隐私",
                        subtitle = "完整条款、隐私说明与开源免费声明",
                        icon = Icons.Default.Description,
                        onClick = onOpenAgreement
                    )
                }
            }

            item { SectionHeader("关于") }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = "关于本项目",
                        subtitle = "版本 ${BuildConfig.VERSION_NAME} · ${AppInfo.LICENSE_NAME}",
                        icon = Icons.Default.Info,
                        onClick = onOpenAbout,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Default.VerifiedUser,
                    title = "开源免费，遇到收费请退款并举报",
                    text = "唯一官方来源：${AppInfo.REPO_LABEL}。作者没有收费版本，也不会索要任何费用。",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            item {
                Text(
                    text = "${AppInfo.NAME} v${BuildConfig.VERSION_NAME} · ${AppInfo.LICENSE_NAME} · © 2026 ${AppInfo.AUTHOR}",
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
