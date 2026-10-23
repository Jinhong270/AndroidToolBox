package com.jh270.toolbox.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TerminalInputField(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    ctrlActive: Boolean,
    onSendText: (String) -> Unit,
    onSendControl: (Char) -> Unit,
    onPaste: () -> Unit,
    onCopyShortcut: () -> Unit,
    onSubmit: () -> Unit
) {
    BasicTextField(
        value = input,
        onValueChange = { new ->
            val oldComm = committedText(input)
            val newComm = committedText(new)
            if (ctrlActive && newComm.length > oldComm.length && newComm.startsWith(oldComm)) {
                val added = newComm.removePrefix(oldComm)
                onSendControl(added[0])
                if (added.length > 1) {
                    onSendText(added.substring(1))
                }
                onInputChange(TextFieldValue(""))
            } else {
                when {
                    newComm.startsWith(oldComm) -> {
                        val added = newComm.removePrefix(oldComm)
                        if (added.isNotEmpty()) {
                            onSendText(added)
                        }
                    }
                    oldComm.startsWith(newComm) -> {
                        repeat(oldComm.length - newComm.length) {
                            onSendText("\u007F")
                        }
                    }
                    else -> {
                        repeat(oldComm.length) {
                            onSendText("\u007F")
                        }
                        if (newComm.isNotEmpty()) {
                            onSendText(newComm)
                        }
                    }
                }
                onInputChange(new)
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
                            onSendText("\t")
                            true
                        }
                        Key.Backspace -> {
                            if (input.text.isEmpty()) {
                                onSendText("\u007F")
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionLeft -> {
                            onSendText("\u001B[D")
                            true
                        }
                        Key.DirectionRight -> {
                            onSendText("\u001B[C")
                            true
                        }
                        Key.DirectionUp -> {
                            onSendText("\u001B[A")
                            true
                        }
                        Key.DirectionDown -> {
                            onSendText("\u001B[B")
                            true
                        }
                        Key.V -> {
                            if (event.isCtrlPressed) {
                                onPaste()
                                true
                            } else {
                                false
                            }
                        }
                        Key.C -> {
                            if (event.isCtrlPressed && event.isShiftPressed) {
                                onCopyShortcut()
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
        keyboardActions = KeyboardActions(onSend = { onSubmit() })
    )
}

internal fun committedText(value: TextFieldValue): String {
    val composition = value.composition
    return if (composition != null && composition.start < composition.end && composition.end <= value.text.length) {
        value.text.removeRange(composition.start, composition.end)
    } else {
        value.text
    }
}
