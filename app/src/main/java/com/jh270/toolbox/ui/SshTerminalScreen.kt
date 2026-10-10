package com.jh270.toolbox.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.jh270.toolbox.ssh.TerminalEmulator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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

                LaunchedEffect(cols) {
                    viewModel.onTerminalSizeChanged(cols, rows)
                }

                val pair = orderedSelection()
                Box(modifier = Modifier.fillMaxSize().background(termBg)) {
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
                                onPress = { x ->
                                    val col = columnAt(x, charWidthPx, text.length)
                                    if (anchor == null) {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    } else {
                                        focus = TextPoint(index, col)
                                    }
                                },
                                onLongPress = { x ->
                                    val col = columnAt(x, charWidthPx, text.length)
                                    val word = wordBounds(text, col)
                                    anchor = TextPoint(index, word.first)
                                    focus = TextPoint(index, word.second)
                                },
                                onDragSelect = { x ->
                                    focus = TextPoint(index, columnAt(x, charWidthPx, text.length))
                                }
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
                            }
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
                TerminalKeyBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBackspace = ::sendBackspace,
                    onCopy = {
                        if (anchor != null) {
                            copyText(selectedText())
                            clearSelection()
                        } else {
                            Toast.makeText(context, "长按文字后再复制", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPaste = ::pasteClipboard
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

@Composable
private fun TerminalLineText(
    line: TerminalEmulator.TerminalLine,
    fontSize: TextUnit,
    selection: IntRange?,
    selectionColor: Color,
    terminalBackground: Color,
    onPress: (Float) -> Unit,
    onLongPress: (Float) -> Unit,
    onDragSelect: (Float) -> Unit
) {
    val plainFg = readableForeground(terminalBackground)
    val annotated = remember(line, selection, selectionColor, terminalBackground) {
        buildAnnotatedString {
            var index = 0
            for (run in line.runs) {
                val style = run.style
                val rawFg = if (style.fg == TerminalEmulator.Style.DEFAULT_FG) plainFg else Color(style.fg)
                val rawBg = if (style.bg == TerminalEmulator.Style.DEFAULT_BG) terminalBackground else Color(style.bg)
                val fg = if (style.inverse) rawBg else rawFg
                val bg = if (style.inverse) rawFg else rawBg
                for (ch in run.text) {
                    val selectedHere = selection != null && index in selection
                    pushStyle(
                        SpanStyle(
                            color = if (selectedHere) readableForeground(selectionColor) else if (style.dim) fg.copy(alpha = 0.55f) else fg,
                            background = if (selectedHere) selectionColor else bg,
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
                    append(ch)
                    pop()
                    index++
                }
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
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalBackground)
            .pointerInput(line) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val longPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
                        var tapped = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                tapped = true
                                break
                            }
                            val dx = change.position.x - start.x
                            val dy = change.position.y - start.y
                            val slop = viewConfiguration.touchSlop
                            if (dx * dx + dy * dy > slop * slop) break
                        }
                        tapped
                    }
                    if (longPress == null) {
                        onLongPress(start.x)
                        drag(down.id) { change ->
                            onDragSelect(change.position.x)
                        }
                    } else if (longPress) {
                        onPress(start.x)
                    }
                }
            }
    )
}

@Composable
private fun SelectionOverlay(
    start: Offset?,
    end: Offset?,
    color: Color,
    onDragStart: (TextPoint) -> Unit,
    onDragEnd: (TextPoint) -> Unit,
    pointAt: (Offset) -> TextPoint?,
    onCopy: () -> Unit
) {
    if (start != null) {
        SelectionHandle(center = start, color = color, onMove = { pointAt(it)?.let(onDragStart) })
        Box(
            modifier = Modifier
                .offset {
                    val x = (start.x - 28.dp.toPx()).roundToInt().coerceAtLeast(4)
                    val y = (start.y - 52.dp.toPx()).roundToInt().coerceAtLeast(4)
                    IntOffset(x, y)
                }
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .background(Color(0xFF111827), RoundedCornerShape(10.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "复制", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (end != null) {
        SelectionHandle(center = end, color = color, onMove = { pointAt(it)?.let(onDragEnd) })
    }
}

@Composable
private fun SelectionHandle(
    center: Offset,
    color: Color,
    onMove: (Offset) -> Unit
) {
    val latest = androidx.compose.runtime.rememberUpdatedState(center)
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - 14.dp.toPx()).roundToInt(), (center.y - 6.dp.toPx()).roundToInt()) }
            .size(28.dp)
            .shadow(4.dp, CircleShape)
            .background(Color.White, CircleShape)
            .border(4.dp, color, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val origin = latest.value
                    drag(down.id) { change ->
                        onMove(origin + change.position - down.position)
                        change.consume()
                    }
                }
            }
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
    onBackspace: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
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
        TerminalKey("COPY", onClick = onCopy)
        TerminalKey("PASTE", onClick = onPaste)
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

private data class TextPoint(val line: Int, val col: Int)

private fun columnAt(x: Float, charWidth: Float, length: Int): Int {
    if (charWidth <= 0f || length <= 0) return 0
    return (x / charWidth).toInt().coerceIn(0, length - 1)
}

private fun wordBounds(text: String, col: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    val index = col.coerceIn(0, text.lastIndex)
    fun word(ch: Char) = ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '/' || ch == '-' || ch == ':'
    if (!word(text[index])) return index to index
    var start = index
    var end = index
    while (start > 0 && word(text[start - 1])) start--
    while (end < text.lastIndex && word(text[end + 1])) end++
    return start to end
}

private fun pointAfter(left: TextPoint, right: TextPoint): Boolean {
    return left.line > right.line || (left.line == right.line && left.col > right.col)
}

private fun selectionRange(line: Int, length: Int, start: TextPoint, end: TextPoint): IntRange? {
    if (length <= 0 || line < start.line || line > end.line) return null
    val from = if (line == start.line) start.col.coerceIn(0, length - 1) else 0
    val to = if (line == end.line) end.col.coerceIn(0, length - 1) else length - 1
    if (from > to) return null
    return from..to
}

private fun caretOffset(
    state: androidx.compose.foundation.lazy.LazyListState,
    line: Int,
    col: Int,
    charWidth: Float
): Offset? {
    val item = state.layoutInfo.visibleItemsInfo.find { it.index == line } ?: return null
    return Offset(col.coerceAtLeast(0) * charWidth, item.offset + item.size.toFloat())
}

private fun pointFromOffset(
    offset: Offset,
    state: androidx.compose.foundation.lazy.LazyListState,
    charWidth: Float,
    count: Int,
    lineText: (Int) -> String
): TextPoint? {
    if (count <= 0) return null
    val item = state.layoutInfo.visibleItemsInfo.minByOrNull {
        abs((it.offset + it.size / 2f) - offset.y)
    } ?: return null
    return TextPoint(item.index, columnAt(offset.x, charWidth, lineText(item.index).length))
}

private fun readableForeground(background: Color): Color {
    val luminance = (0.2126f * background.red) + (0.7152f * background.green) + (0.0722f * background.blue)
    return if (luminance > 0.62f) Color(0xFF1C1917) else Color(0xFFF8FAFC)
}

private fun committedText(value: TextFieldValue): String {
    val comp = value.composition
    return if (comp != null && comp.start < comp.end && comp.end <= value.text.length) {
        value.text.removeRange(comp.start, comp.end)
    } else {
        value.text
    }
}
