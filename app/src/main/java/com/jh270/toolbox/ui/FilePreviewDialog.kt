package com.jh270.toolbox.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jh270.toolbox.R
import com.jh270.toolbox.data.FilePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class EditorAnalysis(val text: String, val spans: List<SourceSpan>, val words: List<String>)

@Composable
fun FileEditorScreen(
    filePreview: FilePreview?, isLoading: Boolean, errorMessage: String?, isSaving: Boolean,
    saveSuccessMessage: String?, saveErrorMessage: String?, allowSave: Boolean,
    onSaveContent: (String, String) -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bindKey = filePreview?.path
    val incoming = filePreview?.content
    var editor by remember(bindKey) { mutableStateOf(TextFieldValue(incoming.orEmpty())) }
    val history = remember(bindKey) { EditorHistory() }
    val writable = filePreview?.takeIf { allowSave && it.editable && !it.truncated && it.content != null }
    val markdown = filePreview?.let { isMarkdownFile(it.name) && it.content != null } == true
    var markdownMode by remember(bindKey) { mutableStateOf<String?>(null) }
    var showDiscard by remember(bindKey) { mutableStateOf(false) }
    var showSearch by remember(bindKey) { mutableStateOf(false) }
    var query by remember(bindKey) { mutableStateOf("") }
    var replacement by remember(bindKey) { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    var fontSize by remember { mutableIntStateOf(13) }
    val dirty = writable != null && editor.text != writable.content
    val focusRequester = remember { FocusRequester() }
    val sourceVisible = incoming != null && (!markdown || markdownMode == "edit")
    val requestClose: () -> Unit = { if (!isSaving) { if (dirty) showDiscard = true else onDismiss() } }
    val save: () -> Unit = { if (dirty && !isSaving) onSaveContent(writable!!.path, editor.text) }
    val language = remember(bindKey, incoming) { detectSourceLanguage(bindKey, incoming.orEmpty()) }
    val indent = remember(bindKey, incoming, language) { editorIndent(incoming.orEmpty(), language) }
    val analysis by produceState(EditorAnalysis("", emptyList(), emptyList()), editor.text, language) {
        val snapshot = editor.text
        if (snapshot.length > 10_000) delay(80)
        value = withContext(Dispatchers.Default) { EditorAnalysis(snapshot, sourceSpans(snapshot, language), editorDocumentWords(snapshot)) }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val highlight = remember(dark, language, analysis, wrap) {
        EditorVisualTransformation(
            comment = if (dark) Color(0xFF98A6B7) else Color(0xFF617365),
            string = if (dark) Color(0xFFA8D991) else Color(0xFF25713C),
            number = if (dark) Color(0xFFFFBC85) else Color(0xFF995000),
            keyword = if (dark) Color(0xFFC5A5FF) else Color(0xFF7543AD),
            language = language,
            type = if (dark) Color(0xFF66D9CC) else Color(0xFF006D77),
            function = if (dark) Color(0xFFFFD580) else Color(0xFF805A00),
            property = if (dark) Color(0xFF9CDCFE) else Color(0xFF175F9C),
            suppliedText = analysis.text, suppliedSpans = analysis.spans, wrap = wrap,
        )
    }
    fun change(next: TextFieldValue) { history.record(editor, next); editor = next }
    fun indentSelection(outdent: Boolean) {
        if (writable != null) { change(indentEditorSelection(editor, indent, outdent)); focusRequester.requestFocus() }
    }
    fun undo() { if (writable != null) history.undo(editor)?.let { editor = it } }
    fun redo() { if (writable != null) history.redo(editor)?.let { editor = it } }
    val matches = remember(editor.text, query, matchCase) { editorMatches(editor.text, query, matchCase) }
    val selectedMatch = matches.indexOfFirst { it == editor.selection }
    fun findMatch(forward: Boolean) {
        if (matches.isEmpty()) return
        val target = if (forward) matches.firstOrNull { it.start >= editor.selection.max && it != editor.selection } ?: matches.first()
            else matches.lastOrNull { it.end <= editor.selection.min && it != editor.selection } ?: matches.last()
        editor = editor.copy(selection = target, composition = null)
    }
    LaunchedEffect(query, matchCase) { if (query.isNotEmpty()) findMatch(true) }
    LaunchedEffect(saveSuccessMessage) {
        if (saveSuccessMessage != null) Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(bindKey, sourceVisible, writable != null) {
        if (sourceVisible && writable != null) focusRequester.requestFocus()
        else if (markdownMode == "preview") { focus.clearFocus(); keyboard?.hide() }
    }
    Dialog(onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ToolBoxScaffold(
                title = (filePreview?.name ?: stringResource(if (isLoading) R.string.opening_file else R.string.file_fallback)) + if (dirty) " *" else "",
                onBack = requestClose,
                autoSizeTitle = true,
                actions = {
                    if (markdown && markdownMode != null) {
                        TextButton(onClick = { markdownMode = if (markdownMode == "preview") "edit" else "preview" }) {
                            Text(stringResource(if (markdownMode == "preview") {
                                if (writable != null) R.string.markdown_edit else R.string.markdown_source
                            } else R.string.markdown_preview))
                        }
                    }
                    if (incoming != null) {
                        IconButton(onClick = { copyPlainText(context, editor.text) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.cd_copy)) }
                    }
                    if (writable != null) {
                        IconButton(onClick = save, enabled = !isSaving && dirty) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, stringResource(R.string.cd_save))
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    if (filePreview != null) {
                        Text(filePreview.path, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (filePreview.truncated) Text(stringResource(R.string.preview_truncated), Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        else if (filePreview.editable && !allowSave) Text(stringResource(R.string.archive_view_only), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                        if (saveErrorMessage != null) Text(saveErrorMessage, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (sourceVisible) {
                        if (!showSearch) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = ::undo, enabled = writable != null && history.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo)) }
                            IconButton(onClick = ::redo, enabled = writable != null && history.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo)) }
                            IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, stringResource(R.string.editor_find)) }
                            if (writable != null) {
                                TextButton(onClick = { indentSelection(false) }) { Text(stringResource(R.string.editor_indent)) }
                                TextButton(onClick = { indentSelection(true) }) { Text(stringResource(R.string.editor_outdent)) }
                            }
                            FilterChip(selected = wrap, onClick = { wrap = !wrap }, label = { Text(stringResource(R.string.editor_wrap)) })
                            TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(10) }, enabled = fontSize > 10) { Text("A−") }
                            TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(24) }, enabled = fontSize < 24) { Text("A+") }
                        }
                        if (showSearch) {
                            EditorSearchPanel(query, { query = it }, replacement, { replacement = it }, matchCase, { matchCase = it },
                                selectedMatch + 1, matches.size, writable != null, { findMatch(false) }, { findMatch(true) },
                                onReplace = { if (selectedMatch >= 0) change(replaceEditorSelection(editor, replacement)) else findMatch(true) },
                                onReplaceAll = { change(replaceAllEditorMatches(editor, query, replacement, matchCase)) },
                                onClose = { showSearch = false; focusRequester.requestFocus() })
                        }
                    }
                    when {
                        isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        markdown && markdownMode == "preview" -> {
                            Text(stringResource(R.string.markdown_offline), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MarkdownPreview(editor.text, Modifier.weight(1f).fillMaxWidth())
                        }
                        markdown && markdownMode == null -> Box(Modifier.weight(1f).fillMaxWidth())
                        sourceVisible -> {
                            CodeEditor(
                                value = editor,
                                onValueChange = { next ->
                                    if (writable == null) editor = editor.copy(selection = next.selection)
                                    else change(assistEditorChange(editor, next, language, indent, analysis.spans.takeIf { analysis.text == editor.text }))
                                },
                                readOnly = writable == null, highlight = highlight, wrap = wrap, fontSize = fontSize,
                                focusRequester = focusRequester, onIndent = ::indentSelection, onUndo = ::undo, onRedo = ::redo,
                                onFind = { showSearch = true }, onSave = save, modifier = Modifier.weight(1f).fillMaxWidth())
                            val completions = if (writable != null && !showSearch && analysis.text == editor.text) editorCompletions(editor, language, analysis.words, analysis.spans) else emptyList()
                            if (completions.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    completions.forEach { completion ->
                                        SuggestionChip(onClick = { change(applyEditorCompletion(editor, completion)); focusRequester.requestFocus() }, label = { Text(completion.label, fontFamily = FontFamily.Monospace) })
                                    }
                                }
                            }
                            val line = remember(editor.text, editor.selection.end) { editor.text.take(editor.selection.end).count { it == '\n' } + 1 }
                            val column = editor.selection.end - editor.text.lastIndexOf('\n', editor.selection.end - 1)
                            Text("${language.label} · ${filePreview?.charsetName.orEmpty()} · " + stringResource(R.string.editor_position, line, column),
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        errorMessage != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (markdown && markdownMode == null && !isLoading) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.markdown_open_title)) }, text = { Text(stringResource(R.string.markdown_open_message)) },
            confirmButton = { TextButton(onClick = { markdownMode = "preview" }) { Text(stringResource(R.string.markdown_preview)) } },
            dismissButton = { TextButton(onClick = { markdownMode = "edit" }) { Text(stringResource(if (writable != null) R.string.markdown_edit else R.string.markdown_source)) } })
    }
    if (showDiscard) {
        AlertDialog(onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) }, text = { Text(stringResource(R.string.editor_unsaved_message)) },
            confirmButton = { TextButton(onClick = { showDiscard = false; onDismiss() }) { Text(stringResource(R.string.editor_discard)) } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) } })
    }
}

@Composable
private fun EditorSearchPanel(
    query: String, onQuery: (String) -> Unit, replacement: String, onReplacement: (String) -> Unit,
    matchCase: Boolean, onMatchCase: (Boolean) -> Unit, index: Int, count: Int, writable: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onReplace: () -> Unit, onReplaceAll: () -> Unit, onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onQuery, Modifier.weight(1f), label = { Text(stringResource(R.string.editor_find)) },
                placeholder = { Text("$index/$count") }, singleLine = true,
                trailingIcon = { IconToggleButton(checked = matchCase, onCheckedChange = onMatchCase) { Text("Aa", color = if (matchCase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) } })
            IconButton(onClick = onPrevious, enabled = count > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.editor_previous)) }
            IconButton(onClick = onNext, enabled = count > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.editor_next)) }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, stringResource(R.string.editor_close_find)) }
        }
        if (writable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(replacement, onReplacement, Modifier.weight(1f), label = { Text(stringResource(R.string.editor_replace)) }, singleLine = true)
                TextButton(onClick = onReplace, enabled = count > 0) { Text(stringResource(R.string.editor_replace)) }
                TextButton(onClick = onReplaceAll, enabled = count > 0) { Text(stringResource(R.string.editor_replace_all)) }
            }
        }
        Text("$index/$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val index = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.2f %s", size / Math.pow(1024.0, index.toDouble()), units[index])
}

fun formatDate(timeMillis: Long): String = if (timeMillis <= 0) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
