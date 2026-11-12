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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jh270.toolbox.R

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
                    contentDescription = stringResource(R.string.cd_back),
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
                Column(modifier = Modifier.weight(1f)) {
                    AdaptiveTitleText(
                        text = title,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        minFontSize = 12.sp,
                        color = foreground
                    )
                    AdaptiveTitleText(
                        text = stringResource(R.string.terminal_subtitle),
                        style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp),
                        minFontSize = 9.sp,
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
                    contentDescription = stringResource(R.string.cd_clear_screen),
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
