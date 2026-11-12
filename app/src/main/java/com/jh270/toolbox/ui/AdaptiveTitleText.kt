package com.jh270.toolbox.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
internal fun AdaptiveTitleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 14.sp,
    maxLines: Int = 1,
) {
    require(minFontSize.isSp && minFontSize.value > 0f)
    require(maxLines > 0)
    val resolvedStyle = LocalTextStyle.current.merge(style)
    val preferredSize = resolvedStyle.fontSize.takeIf { it.isSp } ?: 16.sp
    val minimumSize = minFontSize.value.coerceAtMost(preferredSize.value)
    val measurer = rememberTextMeasurer(cacheSize = 16)

    fun styleAt(size: Float): TextStyle = resolvedStyle.copy(
        fontSize = size.sp,
        lineHeight = if (resolvedStyle.lineHeight.isSp) {
            resolvedStyle.lineHeight * (size / preferredSize.value)
        } else {
            resolvedStyle.lineHeight
        },
    )

    BoxWithConstraints(modifier = modifier) {
        fun fitsSingleLine(size: Float): Boolean = !measurer.measure(
            text = text,
            style = styleAt(size),
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            constraints = Constraints(maxWidth = constraints.maxWidth),
        ).hasVisualOverflow

        var size = preferredSize.value
        var lineLimit = 1
        if (constraints.hasBoundedWidth && !fitsSingleLine(size)) {
            size = minimumSize
            if (fitsSingleLine(size)) {
                var upper = preferredSize.value
                repeat(10) {
                    val candidate = (size + upper) / 2f
                    if (fitsSingleLine(candidate)) size = candidate else upper = candidate
                }
            } else {
                lineLimit = maxLines
            }
        }

        Text(
            text = text,
            style = styleAt(size),
            color = color,
            maxLines = lineLimit,
            softWrap = lineLimit > 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
