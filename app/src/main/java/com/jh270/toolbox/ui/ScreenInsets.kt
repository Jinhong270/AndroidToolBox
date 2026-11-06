package com.jh270.toolbox.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable
internal fun rememberKeyboardDismisser(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return remember(keyboard, focus) {
        {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
}

internal fun Modifier.screenPadding(padding: PaddingValues): Modifier {
    return this.padding(padding).consumeWindowInsets(padding).imePadding()
}
