package com.jh270.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jh270.toolbox.data.FilePreview
import com.jh270.toolbox.data.FileType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(saveSuccessMessage) {
        if (saveSuccessMessage != null) {
            Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(bindKey, filePreview?.editable) {
        if (filePreview?.editable == true) {
            try {
                focusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }

    val commentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val stringColor = MaterialTheme.colorScheme.tertiary
    val numberColor = MaterialTheme.colorScheme.secondary
    val keywordColor = MaterialTheme.colorScheme.primary
    val highlight = remember(commentColor, stringColor, numberColor, keywordColor) {
        SourceVisualTransformation(commentColor, stringColor, numberColor, keywordColor)
    }

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
                title = filePreview?.name ?: if (isLoading) "正在打开" else "文件",
                onBack = onDismiss,
                actions = {
                    if (filePreview?.content != null) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("FileContent", editedText))
                                Toast.makeText(context, "已复制文本内容到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "复制")
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
                                Icon(imageVector = Icons.Default.Save, contentDescription = "保存")
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
                                    text = filePreview.charsetName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (filePreview.truncated) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "只载入了前 200 KB。继续保存会截断原文件，因此不能保存。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (filePreview.editable && !allowSave) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "压缩包里的文件只能查看。",
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
                                    Text(text = "正在获取远程文件内容...")
                                }
                            }
                        }

                        errorMessage != null && filePreview == null -> {
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

                        filePreview?.editable == true && filePreview.content != null -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                BasicTextField(
                                    value = editedText,
                                    onValueChange = { editedText = it },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp)
                                        .focusRequester(focusRequester),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    visualTransformation = highlight,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        filePreview != null && filePreview.fileType == FileType.IMAGE -> {
                            val imageBitmap = remember(filePreview.imageData) {
                                filePreview.imageData?.let { bytes ->
                                    try {
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "图片",
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "这个图片含有二进制数据，不能按文本编辑。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "这个文件含有二进制数据，不能按文本编辑。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class SourceVisualTransformation(
    private val comment: Color,
    private val string: Color,
    private val number: Color,
    private val keyword: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        for (span in sourceSpans(text.text)) {
            val color = when (span.kind) {
                SourceKind.COMMENT -> comment
                SourceKind.STRING -> string
                SourceKind.NUMBER -> number
                SourceKind.KEYWORD -> keyword
            }
            if (span.start < span.end) {
                builder.addStyle(SpanStyle(color = color), span.start, span.end)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
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
