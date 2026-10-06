package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jh270.toolbox.ssh.TerminalEmulator
import kotlinx.coroutines.launch

private val TerminalBg = Color(0xFF0A0E14)
private val TerminalBar = Color(0xFF11161F)
private val TerminalFg = Color(0xFFE6EDF3)
private val TerminalDim = Color(0xFF6E7681)
private val TerminalAccent = Color(0xFF38BDF8)
private val TerminalKeyBg = Color(0xFF1B2330)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SshTerminalScreen(
    viewModel: SshViewModel,
    uiState: SshUiState
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var fontSize by remember { mutableStateOf(13f) }
    var autoScroll by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf(TextFieldValue("")) }

    fun sendBackspace() {
        viewModel.sendTerminalText("\u007F")
        val text = input.text
        if (text.isNotEmpty()) {
            input = TextFieldValue(text.dropLast(1))
        }
    }

    LaunchedEffect(uiState.terminalRevision) {
        if (autoScroll && uiState.terminalLines.isNotEmpty()) {
            listState.scrollToItem(uiState.terminalLines.size - 1)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            autoScroll = lastVisible >= info.totalItemsCount - 1
        }
    }

    Scaffold(
        containerColor = TerminalBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeTerminal() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TerminalFg
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${uiState.config.username}@${uiState.config.host}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalFg
                            )
                            Text(
                                text = "SSH Shell Terminal",
                                fontSize = 10.sp,
                                color = TerminalDim
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { fontSize = (fontSize - 2f).coerceAtLeast(9f) }) {
                        Text(
                            text = "A-",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalFg
                        )
                    }
                    IconButton(onClick = { fontSize = (fontSize + 2f).coerceAtMost(24f) }) {
                        Text(
                            text = "A+",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalFg
                        )
                    }
                    IconButton(onClick = { viewModel.clearTerminal() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清屏",
                            tint = TerminalDim
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalBar
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalBg)
                .imePadding()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val fs = fontSize.sp
                val charWidthPx = with(density) { fs.toPx() * 0.60f }
                val lineHeightPx = with(density) { fs.toPx() * 1.22f }
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val cols = (widthPx / charWidthPx).toInt().coerceIn(24, 240)
                val rows = (heightPx / lineHeightPx).toInt().coerceIn(6, 100)

                LaunchedEffect(cols) {
                    viewModel.onTerminalSizeChanged(cols, rows)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.terminalLines.size) { index ->
                            TerminalLineText(uiState.terminalLines[index], fontSize.sp)
                        }
                    }

                    if (!autoScroll) {
                        Surface(
                            onClick = {
                                autoScroll = true
                                if (uiState.terminalLines.isNotEmpty()) {
                                    scope.launch {
                                        listState.scrollToItem(uiState.terminalLines.size - 1)
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = TerminalBar,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "回到底部",
                                tint = TerminalAccent,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.terminalClosed) {
                TerminalClosedBar(onExit = { viewModel.closeTerminal() })
            } else {
                TerminalKeyBar(viewModel, uiState, ::sendBackspace)
            }

            BasicTextField(
                value = input,
                onValueChange = { new ->
                    val oldComm = committedText(input)
                    val newComm = committedText(new)
                    if (uiState.isCtrlActive && newComm.length > oldComm.length && newComm.startsWith(oldComm)) {
                        val added = newComm.removePrefix(oldComm)
                        viewModel.sendTerminalControlChar(added[0])
                        if (added.length > 1) {
                            viewModel.sendTerminalText(added.substring(1))
                        }
                        input = TextFieldValue("")
                    } else {
                        when {
                            newComm.startsWith(oldComm) -> {
                                val added = newComm.removePrefix(oldComm)
                                if (added.isNotEmpty()) {
                                    viewModel.sendTerminalText(added)
                                }
                            }
                            oldComm.startsWith(newComm) -> {
                                repeat(oldComm.length - newComm.length) {
                                    viewModel.sendTerminalText("\u007F")
                                }
                            }
                            else -> {
                                repeat(oldComm.length) {
                                    viewModel.sendTerminalText("\u007F")
                                }
                                if (newComm.isNotEmpty()) {
                                    viewModel.sendTerminalText(newComm)
                                }
                            }
                        }
                        input = new
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.Tab -> {
                                    viewModel.sendTerminalText("\t")
                                    true
                                }
                                Key.Backspace -> {
                                    if (input.text.isEmpty()) {
                                        viewModel.sendTerminalText("\u007F")
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionLeft -> {
                                    viewModel.sendTerminalText("\u001B[D")
                                    true
                                }
                                Key.DirectionRight -> {
                                    viewModel.sendTerminalText("\u001B[C")
                                    true
                                }
                                Key.DirectionUp -> {
                                    viewModel.sendTerminalText("\u001B[A")
                                    true
                                }
                                Key.DirectionDown -> {
                                    viewModel.sendTerminalText("\u001B[B")
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (uiState.terminalClosed) {
                            viewModel.closeTerminal()
                        } else {
                            viewModel.sendTerminalText("\r")
                            input = TextFieldValue("")
                        }
                    }
                )
            )
        }
    }
}

@Composable
private fun TerminalLineText(line: TerminalEmulator.TerminalLine, fontSize: TextUnit) {
    val annotated = remember(line) {
        buildAnnotatedString {
            for (run in line.runs) {
                val style = run.style
                var fg = Color(style.fg)
                var bg = Color(style.bg)
                if (style.inverse) {
                    val tmp = fg
                    fg = bg
                    bg = tmp
                }
                pushStyle(
                    SpanStyle(
                        color = if (style.dim) fg.copy(alpha = 0.55f) else fg,
                        background = bg,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = when {
                            style.underline && style.strike -> TextDecoration.combine(
                                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                            )
                            style.underline -> TextDecoration.Underline
                            style.strike -> TextDecoration.LineThrough
                            else -> TextDecoration.None
                        }
                    )
                )
                append(run.text)
                pop()
            }
        }
    }
    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        lineHeight = fontSize * 1.22f,
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TerminalClosedBar(onExit: () -> Unit) {
    Surface(
        onClick = onExit,
        color = TerminalBar,
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
                color = TerminalAccent
            )
        }
    }
}

@Composable
private fun TerminalKeyBar(
    viewModel: SshViewModel,
    uiState: SshUiState,
    onBackspace: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(TerminalBar)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TerminalKey("ESC") { viewModel.sendTerminalText("\u001B") }
        TerminalKey("TAB") { viewModel.sendTerminalText("\t") }
        TerminalKey(if (uiState.isCtrlActive) "CTRL ON" else "CTRL", active = uiState.isCtrlActive) {
            viewModel.toggleCtrlState()
        }
        TerminalKey("DEL", onClick = onBackspace)
        TerminalKey("←") { viewModel.sendTerminalText("\u001B[D") }
        TerminalKey("↑") { viewModel.sendTerminalText("\u001B[A") }
        TerminalKey("↓") { viewModel.sendTerminalText("\u001B[B") }
        TerminalKey("→") { viewModel.sendTerminalText("\u001B[C") }
        TerminalKey("HOME") { viewModel.sendTerminalText("\u001B[H") }
        TerminalKey("END") { viewModel.sendTerminalText("\u001B[F") }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (active) TerminalAccent else TerminalKeyBg
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.Black else TerminalFg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun committedText(value: TextFieldValue): String {
    val comp = value.composition
    return if (comp != null && comp.start < comp.end && comp.end <= value.text.length) {
        value.text.removeRange(comp.start, comp.end)
    } else {
        value.text
    }
}
