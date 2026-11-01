package com.jh270.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jh270.toolbox.R

internal fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, context.getString(R.string.err_open_link), Toast.LENGTH_SHORT).show() }
}

internal fun copyPlainText(context: Context, text: String, message: String? = null) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("toolbox", text))
    Toast.makeText(context, message ?: context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
}
