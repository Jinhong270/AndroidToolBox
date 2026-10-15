package com.jh270.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}

internal fun copyPlainText(context: Context, text: String, message: String = "已复制") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("toolbox", text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
