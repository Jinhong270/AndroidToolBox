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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jh270.toolbox.BuildConfig
import com.jh270.toolbox.R
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
                clearClipboardAfterKeyLogin = uiState.clearClipboardAfterKeyLogin,
                onClearClipboardAfterKeyLogin = viewModel::setClearClipboardAfterKeyLogin,
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
    clearClipboardAfterKeyLogin: Boolean,
    onClearClipboardAfterKeyLogin: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    var confirmDisableClipboardClear by remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val clipboardWipeOn = stringResource(R.string.clipboard_wipe_on)
    val clipboardWipeOff = stringResource(R.string.clipboard_wipe_off)
    ToolBoxScaffold(
        title = stringResource(R.string.settings_title),
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
            item { SectionHeader(stringResource(R.string.section_appearance)) }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_colors_title),
                        subtitle = stringResource(R.string.settings_colors_sub),
                        icon = Icons.Default.Palette,
                        onClick = onOpenAppearance
                    )
                    GroupDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_terms_title),
                        subtitle = stringResource(R.string.settings_terms_sub),
                        icon = Icons.Default.Description,
                        onClick = onOpenAgreement
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.section_security_settings)) }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.clipboard_wipe_title),
                        subtitle = if (clearClipboardAfterKeyLogin) {
                            clipboardWipeOn
                        } else {
                            clipboardWipeOff
                        },
                        icon = Icons.Default.Key,
                        tint = if (clearClipboardAfterKeyLogin) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        onClick = {
                            if (clearClipboardAfterKeyLogin) {
                                confirmDisableClipboardClear = true
                            } else {
                                onClearClipboardAfterKeyLogin(true)
                            }
                        },
                        trailing = {
                            Switch(
                                checked = clearClipboardAfterKeyLogin,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        onClearClipboardAfterKeyLogin(true)
                                    } else {
                                        confirmDisableClipboardClear = true
                                    }
                                }
                            )
                        }
                    )
                }
            }

            if (!clearClipboardAfterKeyLogin) {
                item {
                    InfoBanner(
                        icon = Icons.Default.Warning,
                        title = stringResource(R.string.clipboard_risk_title),
                        text = stringResource(R.string.clipboard_risk_text),
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.section_about)) }

            item {
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.about_project),
                        subtitle = stringResource(
                            R.string.settings_version_sub,
                            BuildConfig.VERSION_NAME,
                            AppInfo.LICENSE_NAME
                        ),
                        icon = Icons.Default.Info,
                        onClick = onOpenAbout,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Default.VerifiedUser,
                    title = stringResource(R.string.settings_paid_title),
                    text = stringResource(R.string.settings_paid_text, AppInfo.REPO_LABEL),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            item {
                Text(
                    text = stringResource(
                        R.string.version_line,
                        appName,
                        BuildConfig.VERSION_NAME,
                        AppInfo.LICENSE_NAME,
                        AppInfo.AUTHOR
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }

    if (confirmDisableClipboardClear) {
        AlertDialog(
            onDismissRequest = { confirmDisableClipboardClear = false },
            title = { Text(stringResource(R.string.clipboard_dialog_title)) },
            text = {
                Text(stringResource(R.string.clipboard_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisableClipboardClear = false
                        onClearClipboardAfterKeyLogin(false)
                    }
                ) {
                    Text(stringResource(R.string.still_turn_off))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisableClipboardClear = false }) {
                    Text(stringResource(R.string.keep_on))
                }
            }
        )
    }
}
