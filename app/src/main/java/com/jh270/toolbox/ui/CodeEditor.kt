package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    highlight: VisualTransformation,
    wrap: Boolean,
    fontSize: Int,
    focusRequester: FocusRequester,
    onIndent: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val starts = remember(value.text) { logicalLineStartOffsets(value.text) }
    val tops = remember(value.text, layout, wrap) { layout?.let { lineTopsPx(value.text, it, wrap) } ?: FloatArray(0) }
    val activeLine = starts.binarySearch(value.selection.end).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp, color = MaterialTheme.colorScheme.onSurface)
    val pad = 10.dp
    val padPx = with(density) { pad.toPx() }
    val lineHeight = with(density) { style.lineHeight.toPx() }
    val longestLine = remember(value.text) { value.text.lineSequence().maxOfOrNull { it.length } ?: 0 }
    val mapping = remember(value.text, highlight) { highlight.filter(AnnotatedString(value.text)).offsetMapping }
    val currentLayout = layout
    LaunchedEffect(value.selection, currentLayout, wrap) {
        if (currentLayout != null && currentLayout.layoutInput.text.length > 0) {
            val offset = mapping.originalToTransformed(value.selection.end).coerceIn(0, currentLayout.layoutInput.text.length)
            val cursor = currentLayout.getCursorRect(offset)
            val bottom = cursor.bottom + padPx
            val top = cursor.top + padPx
            if (bottom > vertical.value + vertical.viewportSize) vertical.scrollTo((bottom - vertical.viewportSize + padPx).toInt().coerceAtLeast(0))
            else if (top < vertical.value) vertical.scrollTo((top - padPx).toInt().coerceAtLeast(0))
            if (!wrap) {
                if (cursor.right + padPx > horizontal.value + horizontal.viewportSize) horizontal.scrollTo((cursor.right + padPx * 2 - horizontal.viewportSize).toInt().coerceAtLeast(0))
                else if (cursor.left < horizontal.value) horizontal.scrollTo(cursor.left.toInt().coerceAtLeast(0))
            }
        }
    }
    Row(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).clipToBounds()) {
        val gutter = (starts.size.toString().length.coerceAtLeast(3) * fontSize * 0.65f + 12).dp
        Box(Modifier.width(gutter).fillMaxHeight().clipToBounds().background(MaterialTheme.colorScheme.surfaceContainer)) {
            val first = tops.binarySearch((vertical.value - padPx - lineHeight).coerceAtLeast(0f)).let { if (it >= 0) it else (-it - 1).coerceAtLeast(0) }
            for (index in first until tops.size) {
                val y = padPx + tops[index] - vertical.value
                if (y > vertical.viewportSize + lineHeight) break
                Text(
                    text = (index + 1).toString(),
                    style = style.copy(color = if (index == activeLine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(y = with(density) { y.toDp() }).width(gutter).padding(end = 8.dp),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewport = maxWidth
            val viewportHeight = maxHeight
            val textWidth = if (wrap) viewport else maxOf(viewport, with(density) { (longestLine.coerceAtMost(4096) * fontSize * 0.66f).sp.toDp() } + pad * 2).coerceAtMost(12000.dp)
            Box(Modifier.fillMaxSize().horizontalScroll(horizontal, enabled = !wrap)) {
                Box(Modifier.width(textWidth).verticalScroll(vertical).heightIn(min = viewportHeight)) {
                    if (activeLine in tops.indices) {
                        Box(Modifier.offset(y = with(density) { (padPx + tops[activeLine]).toDp() }).fillMaxWidth().height((fontSize + 7).sp.let { with(density) { it.toDp() } }).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        readOnly = readOnly,
                        textStyle = style,
                        visualTransformation = highlight,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        onTextLayout = { layout = it },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(pad).focusRequester(focusRequester).onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false
                            else if ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.F) { onFind(); true }
                            else if (readOnly) false
                            else when {
                                event.key == Key.Tab -> { onIndent(event.isShiftPressed); true }
                                (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.Z -> { if (event.isShiftPressed) onRedo() else onUndo(); true }
                                (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.Y -> { onRedo(); true }
                                (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.S -> { onSave(); true }
                                else -> false
                            }
                        },
                    )
                }
            }
        }
    }
}
