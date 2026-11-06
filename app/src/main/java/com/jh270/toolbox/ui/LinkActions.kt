package com.jh270.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jh270.toolbox.R
import java.util.Locale

internal fun safeLink(raw: String): Uri? {
    val text = raw.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val full = if (text.contains("://") || text.startsWith("mailto:", ignoreCase = true)) text else "https://$text"
    val uri = Uri.parse(full)
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "http" && scheme != "https" && scheme != "mailto") return null
    return uri
}

internal fun openExternalLink(context: Context, url: String) {
    val uri = safeLink(url)
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.err_open_link), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { Toast.makeText(context, context.getString(R.string.err_open_link), Toast.LENGTH_SHORT).show() }
}

internal fun copyPlainText(context: Context, text: String, message: String? = null) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("toolbox", text))
    Toast.makeText(context, message ?: context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
}
