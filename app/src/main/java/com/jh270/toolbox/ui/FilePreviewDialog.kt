package com.jh270.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jh270.toolbox.R
import com.jh270.toolbox.data.FilePreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun FileEditorScreen(
    filePreview: FilePreview?,
    isLoading: Boolean,
    errorMessage: String?,
    isSaving: Boolean,
    saveSuccessMessage: String?,
    saveErrorMessage: String?,
    allowSave: Boolean,
    onSaveContent: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editedText by remember { mutableStateOf("") }
    var boundPath by remember { mutableStateOf<String?>(null) }
    val incoming = filePreview?.content
    val bindKey = filePreview?.path
    if (incoming != null && boundPath != bindKey) {
        editedText = incoming
        boundPath = bindKey
    }
    val preview = filePreview
    val writable = preview?.takeIf { allowSave && it.editable && !it.truncated && it.content != null }
    val markdown = preview?.let { isMarkdownFile(it.name) && it.content != null } == true
    var markdownMode by remember(bindKey) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(saveSuccessMessage) {
        if (saveSuccessMessage != null) {
            Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(bindKey, writable != null, markdownMode) {
        if (writable != null && (!markdown || markdownMode == "edit")) {
            try {
                focusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }

    val language = remember(bindKey, incoming) { detectSourceLanguage(bindKey, incoming.orEmpty()) }
    val tokens by produceState("" to emptyList<SourceSpan>(), editedText, language) {
        if (editedText.length > 10_000) delay(80)
        value = withContext(Dispatchers.Default) { editedText to sourceSpans(editedText, language) }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val highlight = remember(dark, language, tokens) {
        EditorVisualTransformation(
            comment = if (dark) Color(0xFF98A6B7) else Color(0xFF617365),
            string = if (dark) Color(0xFFA8D991) else Color(0xFF25713C),
            number = if (dark) Color(0xFFFFBC85) else Color(0xFF995000),
            keyword = if (dark) Color(0xFFC5A5FF) else Color(0xFF7543AD),
            language = language,
            type = if (dark) Color(0xFF66D9CC) else Color(0xFF006D77),
            function = if (dark) Color(0xFFFFD580) else Color(0xFF805A00),
            property = if (dark) Color(0xFF9CDCFE) else Color(0xFF175F9C),
            suppliedText = tokens.first,
            suppliedSpans = tokens.second,
        )
    }
    val editorStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Normal
    )
    val gutterColor = MaterialTheme.colorScheme.outline

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ToolBoxScaffold(
                title = filePreview?.name ?: if (isLoading) stringResource(R.string.opening_file) else stringResource(R.string.file_fallback),
                onBack = onDismiss,
                actions = {
                    if (markdown && markdownMode != null) {
                        TextButton(onClick = { markdownMode = if (markdownMode == "preview") "edit" else "preview" }) {
                            Text(stringResource(if (markdownMode == "preview") {
                                if (writable != null) R.string.markdown_edit else R.string.markdown_source
                            } else R.string.markdown_preview))
                        }
                    }
                    if (filePreview?.content != null) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("FileContent", editedText))
                                Toast.makeText(context, context.getString(R.string.copied_text), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = stringResource(R.string.cd_copy))
                        }
                    }
                    if (writable != null) {
                        IconButton(
                            onClick = { onSaveContent(writable.path, editedText) },
                            enabled = !isSaving && editedText != writable.content
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                ) {
                    if (filePreview != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = filePreview.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = formatFileSize(filePreview.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = filePreview.permissions,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = formatDate(filePreview.modifiedTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (filePreview.editable) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${language.label} · ${filePreview.charsetName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (filePreview.truncated) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.preview_truncated),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (filePreview.editable && !allowSave) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.archive_view_only),
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (saveErrorMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = saveErrorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = stringResource(R.string.loading_remote_content))
                                }
                            }
                        }

                        markdown && markdownMode == "preview" -> {
                            Text(
                                text = stringResource(R.string.markdown_offline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            MarkdownPreview(editedText, Modifier.weight(1f).fillMaxWidth())
                        }

                        markdown && markdownMode == null -> {
                            Box(Modifier.weight(1f).fillMaxWidth())
                        }

                        filePreview?.content != null -> {
                            LineNumberEditor(
                                text = editedText,
                                onTextChange = { editedText = it },
                                pathKey = bindKey,
                                textStyle = editorStyle,
                                gutterColor = gutterColor,
                                visualTransformation = highlight,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusRequester = focusRequester,
                                readOnly = writable == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .absolutePadding(right = 12.dp, bottom = 12.dp)
                            )
                        }

                        errorMessage != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (markdown && markdownMode == null && !isLoading) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.markdown_open_title)) },
            text = { Text(stringResource(R.string.markdown_open_message)) },
            confirmButton = {
                TextButton(onClick = { markdownMode = "preview" }) { Text(stringResource(R.string.markdown_preview)) }
            },
            dismissButton = {
                TextButton(onClick = { markdownMode = "edit" }) {
                    Text(stringResource(if (writable != null) R.string.markdown_edit else R.string.markdown_source))
                }
            },
        )
    }
}

@Composable
private fun LineNumberEditor(
    text: String,
    onTextChange: (String) -> Unit,
    pathKey: String?,
    textStyle: TextStyle,
    gutterColor: Color,
    visualTransformation: VisualTransformation,
    cursorColor: Color,
    focusRequester: FocusRequester,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(pathKey) {
        layoutResult = null
        scroll.scrollTo(0)
    }
    val layout = layoutResult
    val tops = remember(text, layout?.size, layout?.lineCount) {
        val current = layout ?: return@remember FloatArray(0)
        lineTopsPx(text, current)
    }
    val pad = 10.dp
    val gutter = 52.dp
    BoxWithConstraints(
        modifier = modifier
            .clip(AbsoluteRoundedCornerShape(topRight = 12.dp, bottomRight = 12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        val viewportPx = constraints.maxHeight
        val padPx = with(density) { pad.toPx() }
        val linePx = with(density) { textStyle.lineHeight.toPx() }
        val textWidth = (maxWidth - gutter - pad).coerceAtLeast(1.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
            contentAlignment = AbsoluteAlignment.TopLeft
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                readOnly = readOnly,
                modifier = Modifier
                    .absolutePadding(left = gutter, right = pad, top = pad, bottom = pad)
                    .width(textWidth)
                    .focusRequester(focusRequester),
                textStyle = textStyle,
                visualTransformation = visualTransformation,
                onTextLayout = { layoutResult = it },
                cursorBrush = SolidColor(cursorColor)
            )
            val scrollPx = scroll.value
            val numberStyle = textStyle.copy(color = gutterColor, textAlign = TextAlign.Right)
            for (index in tops.indices) {
                val y = padPx + tops[index]
                if (y + linePx < scrollPx - linePx) continue
                if (y > scrollPx + viewportPx + linePx) break
                key(index) {
                    Text(
                        text = (index + 1).toString(),
                        modifier = Modifier
                            .offset(y = with(density) { y.toDp() })
                            .width(gutter - 6.dp)
                            .absolutePadding(right = 4.dp),
                        textAlign = TextAlign.Right,
                        style = numberStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceIn(0, units.size - 1)
    val value = size / Math.pow(1024.0, index.toDouble())
    return String.format(Locale.getDefault(), "%.2f %s", value, units[index])
}

fun formatDate(timeMillis: Long): String {
    if (timeMillis <= 0) return "-"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}
