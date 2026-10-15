package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TerminalClosedBar(
    onExit: () -> Unit,
    barColor: Color,
    accentColor: Color
) {
    Surface(
        onClick = onExit,
        color = barColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[终端已关闭，按Enter退出]",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
internal fun TerminalKeyBar(
    viewModel: SshViewModel,
    uiState: SshUiState,
    onBackspace: () -> Unit,
    barColor: Color,
    keyColor: Color,
    keyFgColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(barColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TerminalKey("Esc", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B") }
        TerminalKey("Tab", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\t") }
        TerminalKey(
            if (uiState.isCtrlActive) "Ctrl ON" else "Ctrl",
            active = uiState.isCtrlActive,
            keyColor = keyColor,
            keyFgColor = keyFgColor,
            accentColor = accentColor
        ) {
            viewModel.toggleCtrlState()
        }
        TerminalKey("Del", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor, onClick = onBackspace)
        TerminalKey("←", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B[D") }
        TerminalKey("↑", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B[A") }
        TerminalKey("↓", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B[B") }
        TerminalKey("→", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B[C") }
        TerminalKey("Home", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B[H") }
        TerminalKey("End", keyColor = keyColor, keyFgColor = keyFgColor, accentColor = accentColor) { viewModel.sendTerminalText("\u001B[F") }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    active: Boolean = false,
    keyColor: Color,
    keyFgColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (active) accentColor else keyColor
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) readableForeground(accentColor) else keyFgColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
