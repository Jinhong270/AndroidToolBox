package com.jh270.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalTopBar(
    title: String,
    background: Color,
    foreground: Color,
    dim: Color,
    onBack: () -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onClear: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = foreground
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = foreground
                    )
                    Text(
                        text = "SSH Shell Terminal",
                        fontSize = 10.sp,
                        color = dim
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSmaller) {
                Text(
                    text = "A-",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = foreground
                )
            }
            IconButton(onClick = onLarger) {
                Text(
                    text = "A+",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = foreground
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "清屏",
                    tint = dim
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = background,
            scrolledContainerColor = background,
            navigationIconContentColor = foreground,
            titleContentColor = foreground,
            actionIconContentColor = foreground
        )
    )
}
