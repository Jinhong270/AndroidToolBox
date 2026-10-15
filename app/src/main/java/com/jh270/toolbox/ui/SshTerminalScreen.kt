package com.jh270.toolbox.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SshTerminalScreen(
    viewModel: SshViewModel,
    uiState: SshUiState
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    var fontSize by remember { mutableStateOf(13f) }
    var autoScroll by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var anchor by remember { mutableStateOf<TextPoint?>(null) }
    var focus by remember { mutableStateOf<TextPoint?>(null) }
    val termBg = Color(uiState.terminalBackground)
    val selectionColor = Color(uiState.terminalSelection)
    val barBg = derivedBar(termBg)
    val chromeFg = readableForeground(barBg)
    val chromeDim = chromeFg.copy(alpha = 0.6f)
    val keyBg = derivedBar(barBg)
    val keyFg = readableForeground(keyBg)
    val accent = if (luminance(termBg) > 0.5f) Color(0xFF0369A1) else Color(0xFF38BDF8)
    fun clearSelection() {
        anchor = null
        focus = null
    }

    fun linePlain(index: Int): String {
        val line = uiState.terminalLines.getOrNull(index) ?: return ""
        return line.runs.joinToString("") { it.text }.trimEnd()
    }

    fun orderedSelection(): Pair<TextPoint, TextPoint>? {
        val a = anchor ?: return null
        val b = focus ?: return null
        return if (a.line < b.line || (a.line == b.line && a.col <= b.col)) a to b else b to a
    }

    fun selectedText(): String {
        val pair = orderedSelection() ?: return ""
        val (start, end) = pair
        return (start.line..end.line).joinToString("\n") { index ->
            val text = linePlain(index)
            val from = if (index == start.line) start.col.coerceIn(0, text.length) else 0
            val toExclusive = if (index == end.line) (end.col + 1).coerceIn(0, text.length) else text.length
            if (from < toExclusive) text.substring(from, toExclusive) else ""
        }
    }

    fun copyText(text: String) {
        if (text.isEmpty()) return
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("terminal", text)))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
    }

    fun pasteClipboard() {
        scope.launch {
            val clip = clipboard.getClipEntry()?.clipData
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString()
            } else {
                null
            }
            if (text.isNullOrEmpty()) {
                Toast.makeText(context, "剪贴板是空的", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.pasteTerminalText(text)
            }
        }
    }

    fun sendBackspace() {
        viewModel.sendTerminalText("\u007F")
        val text = input.text
        if (text.isNotEmpty()) {
            input = TextFieldValue(text.dropLast(1))
        }
    }

    LaunchedEffect(uiState.terminalRevision) {
        if (autoScroll && anchor == null && uiState.terminalLines.isNotEmpty()) {
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
        containerColor = termBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.hideTerminal() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = chromeFg
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
                                color = chromeFg
                            )
                            Text(
                                text = "SSH Shell Terminal",
                                fontSize = 10.sp,
                                color = chromeDim
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
                            color = chromeFg
                        )
                    }
                    IconButton(onClick = { fontSize = (fontSize + 2f).coerceAtMost(24f) }) {
                        Text(
                            text = "A+",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = chromeFg
                        )
                    }
                    IconButton(onClick = { viewModel.clearTerminal() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清屏",
                            tint = chromeDim
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barBg
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(termBg)
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

                var lastSelectX by remember { mutableStateOf(0f) }
                var autoScrollJob by remember { mutableStateOf<Job?>(null) }
                var autoScrollDir by remember { mutableStateOf(0) }

                fun stopAutoScroll() {
                    autoScrollJob?.cancel()
                    autoScrollJob = null
                    autoScrollDir = 0
                }

                fun runAutoScroll(direction: Int) {
                    if (autoScrollJob?.isActive == true && autoScrollDir == direction) return
                    autoScrollJob?.cancel()
                    autoScrollDir = direction
                    autoScrollJob = scope.launch {
                        while (isActive) {
                            if (direction < 0 && !listState.canScrollBackward) break
                            if (direction > 0 && !listState.canScrollForward) break
                            listState.scroll { scrollBy(direction * lineHeightPx) }
                            val info = listState.layoutInfo
                            val target = if (direction < 0) info.visibleItemsInfo.firstOrNull() else info.visibleItemsInfo.lastOrNull()
                            if (target != null) {
                                focus = TextPoint(target.index, columnAt(lastSelectX, charWidthPx, linePlain(target.index).length))
                            }
                            delay(60)
                        }
                    }
                }

                LaunchedEffect(cols) {
                    viewModel.onTerminalSizeChanged(cols, rows)
                }

                val pair = orderedSelection()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(termBg)
                        .pointerInput(Unit) {
                            detectTapGestures { clearSelection() }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.terminalLines.size) { index ->
                            val line = uiState.terminalLines[index]
                            val text = linePlain(index)
                            val range = pair?.let { (start, end) -> selectionRange(index, text.length, start, end) }
                            TerminalLineText(
                                line = line,
                                fontSize = fontSize.sp,
                                selection = range,
                                selectionColor = selectionColor,
                                terminalBackground = termBg,
                                onPress = {
                                    if (anchor != null) {
                                        clearSelection()
                                    } else {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                },
                                onLongPress = { x ->
                                    lastSelectX = x
                                    val col = columnAt(x, charWidthPx, text.length)
                                    val word = wordBounds(text, col)
                                    anchor = TextPoint(index, word.first)
                                    focus = TextPoint(index, word.second)
                                },
                                onDragSelect = { x, yLocal ->
                                    lastSelectX = x
                                    val info = listState.layoutInfo
                                    val itemInfo = info.visibleItemsInfo.find { it.index == index }
                                    val pointerY = (itemInfo?.offset?.toFloat() ?: 0f) + yLocal
                                    val target = info.visibleItemsInfo.firstOrNull { pointerY >= it.offset && pointerY < it.offset + it.size }
                                    val targetIndex = target?.index ?: index
                                    focus = TextPoint(targetIndex, columnAt(x, charWidthPx, linePlain(targetIndex).length))
                                    val edge = lineHeightPx
                                    when {
                                        pointerY < edge -> runAutoScroll(-1)
                                        pointerY > heightPx - edge -> runAutoScroll(1)
                                        else -> stopAutoScroll()
                                    }
                                },
                                onDragEnd = { stopAutoScroll() }
                            )
                        }
                    }

                    if (pair != null) {
                        val startSpot = caretOffset(listState, pair.first.line, pair.first.col, charWidthPx)
                        val endSpot = caretOffset(listState, pair.second.line, pair.second.col + 1, charWidthPx)
                        SelectionOverlay(
                            start = startSpot,
                            end = endSpot,
                            color = selectionColor,
                            onDragStart = { point ->
                                val other = orderedSelection()?.second ?: point
                                if (pointAfter(point, other)) {
                                    anchor = other
                                    focus = point
                                } else {
                                    anchor = point
                                    focus = other
                                }
                            },
                            onDragEnd = { point ->
                                val other = orderedSelection()?.first ?: point
                                if (pointAfter(other, point)) {
                                    anchor = point
                                    focus = other
                                } else {
                                    anchor = other
                                    focus = point
                                }
                            },
                            pointAt = { offset ->
                                pointFromOffset(offset, listState, charWidthPx, uiState.terminalLines.size, ::linePlain)
                            },
                            onCopy = {
                                copyText(selectedText())
                                clearSelection()
                            },
                            onPaste = ::pasteClipboard
                        )
                    }

                    if (!autoScroll && anchor == null) {
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
                            color = barBg,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "回到底部",
                                tint = accent,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.terminalClosed) {
                TerminalClosedBar(
                    onExit = { viewModel.closeTerminal() },
                    barColor = barBg,
                    accentColor = accent
                )
            } else {
                TerminalKeyBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBackspace = ::sendBackspace,
                    barColor = barBg,
                    keyColor = keyBg,
                    keyFgColor = keyFg,
                    accentColor = accent
                )
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
                                Key.V -> {
                                    if (event.isCtrlPressed) {
                                        pasteClipboard()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.C -> {
                                    if (event.isCtrlPressed && event.isShiftPressed) {
                                        if (anchor != null) {
                                            copyText(selectedText())
                                            clearSelection()
                                        } else {
                                            copyText(viewModel.terminalPlainText())
                                        }
                                        true
                                    } else {
                                        false
                                    }
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

private fun committedText(value: TextFieldValue): String {
    val comp = value.composition
    return if (comp != null && comp.start < comp.end && comp.end <= value.text.length) {
        value.text.removeRange(comp.start, comp.end)
    } else {
        value.text
    }
}
