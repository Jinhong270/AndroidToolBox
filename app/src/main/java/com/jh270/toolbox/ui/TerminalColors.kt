package com.jh270.toolbox.ui

import androidx.compose.ui.graphics.Color

internal fun luminance(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

internal fun readableForeground(background: Color): Color {
    return if (luminance(background) > 0.62f) Color(0xFF1C1917) else Color(0xFFF8FAFC)
}

internal fun derivedBar(background: Color): Color {
    val shift = if (luminance(background) > 0.5f) -0.07f else 0.07f
    return Color(
        (background.red + shift).coerceIn(0f, 1f),
        (background.green + shift).coerceIn(0f, 1f),
        (background.blue + shift).coerceIn(0f, 1f)
    )
}
