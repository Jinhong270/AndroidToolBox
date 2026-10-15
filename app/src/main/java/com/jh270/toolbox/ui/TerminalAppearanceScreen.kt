package com.jh270.toolbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jh270.toolbox.data.AppSettings

private data class ColorChoice(val name: String, val value: Int)

private val BackgroundChoices = listOf(
    ColorChoice("深空", 0xFF0A0E14.toInt()),
    ColorChoice("纯黑", 0xFF000000.toInt()),
    ColorChoice("午夜蓝", 0xFF111827.toInt()),
    ColorChoice("石墨", 0xFF1C1917.toInt()),
    ColorChoice("藏青", 0xFF0F172A.toInt()),
    ColorChoice("亮白", 0xFFF8FAFC.toInt())
)

private val SelectionChoices = listOf(
    ColorChoice("蓝", 0xCC2563EB.toInt()),
    ColorChoice("琥珀", 0xCCF59E0B.toInt()),
    ColorChoice("翠绿", 0xCC16A34A.toInt()),
    ColorChoice("朱红", 0xCCDC2626.toInt()),
    ColorChoice("紫罗兰", 0xCC9333EA.toInt()),
    ColorChoice("青", 0xCC06B6D4.toInt())
)

@Composable
fun TerminalAppearanceScreen(
    uiState: SshUiState,
    onBackground: (Int) -> Unit,
    onSelection: (Int) -> Unit,
    onBack: () -> Unit
) {
    ToolBoxScaffold(
        title = "终端配色",
        subtitle = "背景与选中高亮",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeader("终端背景")
                SwatchGrid(
                    choices = BackgroundChoices,
                    selected = uiState.terminalBackground,
                    onSelect = onBackground
                )

                SectionHeader("选中高亮")
                Text(
                    text = "长按终端文字拖动选择时出现的颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwatchGrid(
                    choices = SelectionChoices,
                    selected = uiState.terminalSelection,
                    onSelect = onSelection
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            onBackground(AppSettings.DEFAULT_TERMINAL_BG)
                            onSelection(AppSettings.DEFAULT_TERMINAL_SELECTION)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("恢复默认配色")
                    }
                }

                InfoBanner(
                    icon = Icons.Default.Palette,
                    text = "配色只影响终端界面，保存在本机，重装应用后恢复默认。",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            TerminalColorPreview(
                background = uiState.terminalBackground,
                selection = uiState.terminalSelection
            )
        }
    }
}

@Composable
private fun SwatchGrid(
    choices: List<ColorChoice>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { choice ->
                    SwatchCell(
                        choice = choice,
                        isSelected = choice.value == selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SwatchCell(
    choice: ColorChoice,
    isSelected: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier
) {
    Surface(
        onClick = { onSelect(choice.value) },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(choice.value))
                    .border(
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = readableForeground(Color(choice.value)),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = choice.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TerminalColorPreview(
    background: Int,
    selection: Int
) {
    val termBg = Color(background)
    val selectionColor = Color(selection)
    val foreground = readableForeground(termBg)
    val dim = foreground.copy(alpha = 0.55f)
    val accent = if (luminance(termBg) > 0.5f) Color(0xFF0369A1) else Color(0xFF38BDF8)
    val keyBg = derivedBar(termBg)
    val keyFg = readableForeground(keyBg)

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "实时预览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "不可输入 · 按钮无效",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = termBg,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PreviewLine("ToolBox Shell Terminal Connected", accent)
                        PreviewLine("root@server:~# ls -la", foreground)

                        Row {
                            PreviewLine("-rw-r--r--  1 root root  842 ", foreground)
                            Box(
                                modifier = Modifier
                                    .background(selectionColor, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 2.dp)
                            ) {
                                PreviewLine("notes.md", readableForeground(selectionColor))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PreviewLine("root@server:~# ", foreground)
                            Box(
                                modifier = Modifier
                                    .size(width = 7.dp, height = 13.dp)
                                    .background(dim)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Esc", "Tab", "Ctrl", "Del", "←", "↑", "↓", "→").forEach { label ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = keyBg
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = keyFg,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(text: String, color: Color) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = color
    )
}
