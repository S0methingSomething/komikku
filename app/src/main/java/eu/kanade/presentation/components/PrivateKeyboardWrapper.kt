package eu.kanade.presentation.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * CompositionLocal to indicate if private keyboard mode is enabled.
 */
val LocalPrivateKeyboardMode = compositionLocalOf { false }

/**
 * Provides private keyboard mode for text inputs within the content.
 */
@Composable
fun ProvidePrivateKeyboardMode(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPrivateKeyboardMode provides enabled,
        content = content,
    )
}

/**
 * Returns KeyboardOptions with private keyboard mode if enabled.
 * Uses "nm" which Gboard and compatible keyboards respect.
 */
@Composable
fun KeyboardOptions.withPrivateMode(): KeyboardOptions {
    val privateMode = LocalPrivateKeyboardMode.current
    return if (privateMode) {
        copy(platformImeOptions = PlatformImeOptions(privateImeOptions = "nm"))
    } else {
        this
    }
}



