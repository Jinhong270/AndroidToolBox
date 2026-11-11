package com.jh270.toolbox.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Locale

@Composable
internal fun MarkdownPreview(text: String, modifier: Modifier = Modifier) {
    val body by produceState<String?>(null, text) {
        value = withContext(Dispatchers.Default) { MarkdownDocument.render(text) }
    }
    val colors = MaterialTheme.colorScheme
    val content = body
    if (content == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val page = remember(content, colors) {
        MarkdownDocument.page(content, colors.onSurface.css(), colors.background.css(), colors.primary.css(), colors.surfaceContainerHigh.css())
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    blockNetworkImage = true
                    loadsImagesAutomatically = false
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (request.isForMainFrame && request.hasGesture() && markdownExternalLink(url)) {
                            openExternalLink(context, url)
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
        },
        update = { view ->
            view.setBackgroundColor(colors.background.toArgb())
            if (view.tag != page) {
                view.tag = page
                view.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.tag = null
            view.destroy()
        },
    )
}

private fun Color.css(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)
