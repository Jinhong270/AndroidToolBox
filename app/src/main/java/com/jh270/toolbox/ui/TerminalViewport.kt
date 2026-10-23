package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jh270.toolbox.ssh.TerminalEmulator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun TerminalViewport(
    lines: List<TerminalEmulator.TerminalLine>,
    fontSize: Float,
    background: Color,
    selectionColor: Color,
    accent: Color,
    chipColor: Color,
    listState: LazyListState,
    anchor: TextPoint?,
    focusPoint: TextPoint?,
    autoScroll: Boolean,
    onAnchorChange: (TextPoint?) -> Unit,
    onFocusChange: (TextPoint?) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onRequestKeyboard: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier) {
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

        fun linePlain(index: Int): String = terminalLinePlain(lines, index)

        fun clearSelection() {
            onAnchorChange(null)
            onFocusChange(null)
        }

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
                        onFocusChange(TextPoint(target.index, columnAt(lastSelectX, charWidthPx, linePlain(target.index).length)))
                    }
                    delay(60)
                }
            }
        }

        LaunchedEffect(cols) {
            onSizeChanged(cols, rows)
        }

        val pair = orderedSelection(anchor, focusPoint)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .pointerInput(Unit) {
                    detectTapGestures { clearSelection() }
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(lines.size) { index ->
                    val line = lines[index]
                    val text = linePlain(index)
                    val range = pair?.let { (start, end) -> selectionRange(index, text.length, start, end) }
                    TerminalLineText(
                        line = line,
                        fontSize = fontSize.sp,
                        selection = range,
                        selectionColor = selectionColor,
                        terminalBackground = background,
                        onPress = {
                            if (anchor != null) {
                                clearSelection()
                            } else {
                                onRequestKeyboard()
                            }
                        },
                        onLongPress = { x ->
                            lastSelectX = x
                            val col = columnAt(x, charWidthPx, text.length)
                            val word = wordBounds(text, col)
                            onAnchorChange(TextPoint(index, word.first))
                            onFocusChange(TextPoint(index, word.second))
                        },
                        onDragSelect = { x, yLocal ->
                            lastSelectX = x
                            val info = listState.layoutInfo
                            val itemInfo = info.visibleItemsInfo.find { it.index == index }
                            val pointerY = (itemInfo?.offset?.toFloat() ?: 0f) + yLocal
                            val target = info.visibleItemsInfo.firstOrNull { pointerY >= it.offset && pointerY < it.offset + it.size }
                            val targetIndex = target?.index ?: index
                            onFocusChange(TextPoint(targetIndex, columnAt(x, charWidthPx, linePlain(targetIndex).length)))
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
                        val other = orderedSelection(anchor, focusPoint)?.second ?: point
                        if (pointAfter(point, other)) {
                            onAnchorChange(other)
                            onFocusChange(point)
                        } else {
                            onAnchorChange(point)
                            onFocusChange(other)
                        }
                    },
                    onDragEnd = { point ->
                        val other = orderedSelection(anchor, focusPoint)?.first ?: point
                        if (pointAfter(other, point)) {
                            onAnchorChange(point)
                            onFocusChange(other)
                        } else {
                            onAnchorChange(other)
                            onFocusChange(point)
                        }
                    },
                    pointAt = { offset ->
                        pointFromOffset(offset, listState, charWidthPx, lines.size, ::linePlain)
                    },
                    onCopy = onCopy,
                    onPaste = onPaste
                )
            }

            if (!autoScroll && anchor == null) {
                Surface(
                    onClick = {
                        onAutoScrollChange(true)
                        if (lines.isNotEmpty()) {
                            scope.launch {
                                listState.scrollToItem(lines.size - 1)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = chipColor,
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
}
