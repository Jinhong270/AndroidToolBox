package com.jh270.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoOnContainerDark,
    secondary = SkySecondaryDark,
    onSecondary = SkyOnSecondaryDark,
    secondaryContainer = SkyContainerDark,
    onSecondaryContainer = SkyOnContainerDark,
    tertiary = EmeraldTertiaryDark,
    onTertiary = EmeraldOnTertiaryDark,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = EmeraldOnContainerDark,
    background = SlateBackgroundDark,
    onBackground = SlateOnBackgroundDark,
    surface = SlateSurfaceDark,
    onSurface = SlateOnSurfaceDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = SlateOnSurfaceVariantDark,
    surfaceContainerLowest = SlateBackgroundDark,
    surfaceContainerLow = SlateContainerLowDark,
    surfaceContainer = SlateSurfaceDark,
    surfaceContainerHigh = SlateContainerHighDark,
    surfaceContainerHighest = SlateSurfaceVariantDark,
    outline = SlateOutlineDark,
    outlineVariant = SlateOutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoOnContainerLight,
    secondary = SkySecondaryLight,
    onSecondary = SkyOnSecondaryLight,
    secondaryContainer = SkyContainerLight,
    onSecondaryContainer = SkyOnContainerLight,
    tertiary = EmeraldTertiaryLight,
    onTertiary = EmeraldOnTertiaryLight,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = EmeraldOnContainerLight,
    background = SlateBackgroundLight,
    onBackground = SlateOnBackgroundLight,
    surface = SlateSurfaceLight,
    onSurface = SlateOnSurfaceLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateOnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceLowestLight,
    surfaceContainerLow = SlateContainerLowLight,
    surfaceContainer = SlateSurfaceLight,
    surfaceContainerHigh = SlateContainerHighLight,
    surfaceContainerHighest = SlateSurfaceVariantLight,
    outline = SlateOutlineLight,
    outlineVariant = SlateOutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

@Composable
fun ToolBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = ToolBoxShapes,
        content = content
    )
}
