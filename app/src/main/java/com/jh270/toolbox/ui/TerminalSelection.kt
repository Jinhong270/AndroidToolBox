package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jh270.toolbox.ssh.TerminalEmulator
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class TextPoint(val line: Int, val col: Int)

@Composable
internal fun TerminalLineText(
    line: TerminalEmulator.TerminalLine,
    fontSize: TextUnit,
    selection: IntRange?,
    selectionColor: Color,
    terminalBackground: Color,
    onPress: () -> Unit,
    onLongPress: (Float) -> Unit,
    onDragSelect: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
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
                            onDragSelect(change.position.x, change.position.y)
                            change.consume()
                        }
                        onDragEnd()
                    } else if (longPress) {
                        onPress()
                    }
                }
            }
    )
}

@Composable
internal fun SelectionOverlay(
    start: Offset?,
    end: Offset?,
    color: Color,
    onDragStart: (TextPoint) -> Unit,
    onDragEnd: (TextPoint) -> Unit,
    pointAt: (Offset) -> TextPoint?,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    if (start != null) {
        SelectionHandle(center = start, color = color, onMove = { pointAt(it)?.let(onDragStart) })
        Row(
            modifier = Modifier
                .offset {
                    val x = (start.x - 28.dp.toPx()).roundToInt().coerceAtLeast(4)
                    val y = (start.y - 52.dp.toPx()).roundToInt().coerceAtLeast(4)
                    IntOffset(x, y)
                }
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .background(Color(0xFF111827), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            PopupAction("复制", onCopy)
            PopupAction("粘贴", onPaste)
        }
    }
    if (end != null) {
        SelectionHandle(center = end, color = color, onMove = { pointAt(it)?.let(onDragEnd) })
    }
}

@Composable
private fun PopupAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SelectionHandle(
    center: Offset,
    color: Color,
    onMove: (Offset) -> Unit
) {
    val latest = rememberUpdatedState(center)
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
                    drag(down.id) { change ->
                        onMove(latest.value + change.position - down.position)
                        change.consume()
                    }
                }
            }
    )
}

internal fun columnAt(x: Float, charWidth: Float, length: Int): Int {
    if (charWidth <= 0f || length <= 0) return 0
    return (x / charWidth).toInt().coerceIn(0, length - 1)
}

internal fun wordBounds(text: String, col: Int): Pair<Int, Int> {
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

internal fun pointAfter(left: TextPoint, right: TextPoint): Boolean {
    return left.line > right.line || (left.line == right.line && left.col > right.col)
}

internal fun selectionRange(line: Int, length: Int, start: TextPoint, end: TextPoint): IntRange? {
    if (length <= 0 || line < start.line || line > end.line) return null
    val from = if (line == start.line) start.col.coerceIn(0, length - 1) else 0
    val to = if (line == end.line) end.col.coerceIn(0, length - 1) else length - 1
    if (from > to) return null
    return from..to
}

internal fun caretOffset(
    state: LazyListState,
    line: Int,
    col: Int,
    charWidth: Float
): Offset? {
    val item = state.layoutInfo.visibleItemsInfo.find { it.index == line } ?: return null
    return Offset(col.coerceAtLeast(0) * charWidth, item.offset + item.size.toFloat())
}

internal fun pointFromOffset(
    offset: Offset,
    state: LazyListState,
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

internal fun terminalLinePlain(lines: List<TerminalEmulator.TerminalLine>, index: Int): String {
    val line = lines.getOrNull(index) ?: return ""
    return line.runs.joinToString("") { it.text }.trimEnd()
}

internal fun orderedSelection(anchor: TextPoint?, focus: TextPoint?): Pair<TextPoint, TextPoint>? {
    val start = anchor ?: return null
    val end = focus ?: return null
    return if (start.line < end.line || (start.line == end.line && start.col <= end.col)) start to end else end to start
}

internal fun selectedTerminalText(
    lines: List<TerminalEmulator.TerminalLine>,
    anchor: TextPoint?,
    focus: TextPoint?
): String {
    val pair = orderedSelection(anchor, focus) ?: return ""
    val (start, end) = pair
    return (start.line..end.line).joinToString("\n") { index ->
        val text = terminalLinePlain(lines, index)
        val from = if (index == start.line) start.col.coerceIn(0, text.length) else 0
        val toExclusive = if (index == end.line) (end.col + 1).coerceIn(0, text.length) else text.length
        if (from < toExclusive) text.substring(from, toExclusive) else ""
    }
}
