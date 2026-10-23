package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalScreen(
    viewModel: SshViewModel,
    uiState: SshUiState
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
    val chromeFg = readableForeground(termBg)
    val chromeDim = chromeFg.copy(alpha = 0.6f)
    val keyBg = derivedBar(termBg)
    val keyFg = readableForeground(keyBg)
    val accent = if (luminance(termBg) > 0.5f) Color(0xFF0369A1) else Color(0xFF38BDF8)
    val lines = uiState.terminalLines

    fun clearSelection() {
        anchor = null
        focus = null
    }

    fun copyText(text: String) {
        copyTerminalText(scope, clipboard, context, text)
    }

    fun pasteClipboard() {
        pasteTerminalClipboard(scope, clipboard, context) { viewModel.pasteTerminalText(it) }
    }

    fun sendBackspace() {
        viewModel.sendTerminalText("\u007F")
        val text = input.text
        if (text.isNotEmpty()) {
            input = TextFieldValue(text.dropLast(1))
        }
    }

    LaunchedEffect(uiState.terminalRevision) {
        if (autoScroll && anchor == null && lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
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
            TerminalTopBar(
                title = "${uiState.config.username}@${uiState.config.host}",
                background = termBg,
                foreground = chromeFg,
                dim = chromeDim,
                onBack = { viewModel.hideTerminal() },
                onSmaller = { fontSize = (fontSize - 2f).coerceAtLeast(9f) },
                onLarger = { fontSize = (fontSize + 2f).coerceAtMost(24f) },
                onClear = { viewModel.clearTerminal() }
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
            TerminalViewport(
                lines = lines,
                fontSize = fontSize,
                background = termBg,
                selectionColor = selectionColor,
                accent = accent,
                chipColor = keyBg,
                listState = listState,
                anchor = anchor,
                focusPoint = focus,
                autoScroll = autoScroll,
                onAnchorChange = { anchor = it },
                onFocusChange = { focus = it },
                onAutoScrollChange = { autoScroll = it },
                onRequestKeyboard = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                onCopy = {
                    copyText(selectedTerminalText(lines, anchor, focus))
                    clearSelection()
                },
                onPaste = ::pasteClipboard,
                onSizeChanged = viewModel::onTerminalSizeChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            if (uiState.terminalClosed) {
                TerminalClosedBar(
                    onExit = { viewModel.closeTerminal() },
                    barColor = termBg,
                    accentColor = accent
                )
            } else {
                TerminalKeyBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBackspace = ::sendBackspace,
                    barColor = termBg,
                    keyColor = keyBg,
                    keyFgColor = keyFg,
                    accentColor = accent
                )
            }

            TerminalInputField(
                input = input,
                onInputChange = { input = it },
                focusRequester = focusRequester,
                ctrlActive = uiState.isCtrlActive,
                onSendText = viewModel::sendTerminalText,
                onSendControl = viewModel::sendTerminalControlChar,
                onPaste = ::pasteClipboard,
                onCopyShortcut = {
                    if (anchor != null) {
                        copyText(selectedTerminalText(lines, anchor, focus))
                        clearSelection()
                    } else {
                        copyText(viewModel.terminalPlainText())
                    }
                },
                onSubmit = {
                    if (uiState.terminalClosed) {
                        viewModel.closeTerminal()
                    } else {
                        viewModel.sendTerminalText("\r")
                        input = TextFieldValue("")
                    }
                }
            )
        }
    }
}
