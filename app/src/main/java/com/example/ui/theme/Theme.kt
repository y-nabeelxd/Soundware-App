package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SoundwaveColorScheme = darkColorScheme(
    primary = SoundwaveGreen,
    onPrimary = SoundwaveWhite,
    primaryContainer = SoundwaveSurfaceVariant,
    onPrimaryContainer = SoundwaveGreenBright,
    secondary = SoundwaveGreenBright,
    onSecondary = SoundwaveDark,
    background = SoundwaveDark,
    onBackground = SoundwaveWhite,
    surface = SoundwaveSurface,
    onSurface = SoundwaveWhite,
    surfaceVariant = SoundwaveSurfaceVariant,
    onSurfaceVariant = SoundwaveGray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SoundwaveColorScheme,
        typography = Typography,
        content = content
    )
}
