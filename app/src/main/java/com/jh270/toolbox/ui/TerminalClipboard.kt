package com.jh270.toolbox.ui

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.ClipEntry
import com.jh270.toolbox.R
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun copyTerminalText(
    scope: CoroutineScope,
    clipboard: Clipboard,
    context: Context,
    text: String
) {
    if (text.isEmpty()) return
    scope.launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("terminal", text)))
        Toast.makeText(context, context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
    }
}

internal fun pasteTerminalClipboard(
    scope: CoroutineScope,
    clipboard: Clipboard,
    context: Context,
    onText: (String) -> Unit
) {
    scope.launch {
        val clip = clipboard.getClipEntry()?.clipData
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } else {
            null
        }
        if (text.isNullOrEmpty()) {
            Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
        } else {
            onText(text)
        }
    }
}
