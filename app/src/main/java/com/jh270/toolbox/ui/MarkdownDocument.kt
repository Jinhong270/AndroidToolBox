package com.jh270.toolbox.ui

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.net.URI
import java.util.Locale

internal fun isMarkdownFile(path: String): Boolean =
    path.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("md", "markdown", "mdown", "mkd")

internal fun markdownExternalLink(url: String): Boolean = runCatching {
    val uri = URI(url)
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> !uri.host.isNullOrBlank() && uri.userInfo == null
        "mailto" -> !uri.schemeSpecificPart.isNullOrBlank()
        else -> false
    }
}.getOrDefault(false)

internal object MarkdownDocument {
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
    )
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    fun render(text: String): String = renderer.render(parser.parse(text))

    fun page(body: String, foreground: String, background: String, accent: String, surface: String): String = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; form-action 'none'; base-uri 'none'">
        <style>
        :root{color-scheme:light dark}*{box-sizing:border-box}body{margin:0;padding:16px;color:$foreground;background:$background;font:16px/1.65 sans-serif;overflow-wrap:anywhere}
        h1,h2,h3,h4,h5,h6{line-height:1.3;margin:1.2em 0 .6em}h1,h2{border-bottom:1px solid $surface;padding-bottom:.3em}
        a{color:$accent}pre,code{font-family:monospace;background:$surface;border-radius:5px}code{padding:2px 4px}pre{padding:12px;overflow:auto;white-space:pre;overflow-wrap:normal}pre code{padding:0}
        blockquote{margin:1em 0;padding:0 1em;border-left:4px solid $accent}table{border-collapse:collapse;display:block;overflow:auto}th,td{padding:8px 12px;border:1px solid $accent}th{background:$surface}
        hr{border:0;border-top:1px solid $accent}li{margin:.25em 0}img{max-width:100%}input{pointer-events:none}p{margin:.7em 0}
        </style></head><body>$body</body></html>
    """.trimIndent()
}
