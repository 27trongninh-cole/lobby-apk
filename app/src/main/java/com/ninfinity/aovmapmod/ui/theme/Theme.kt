package com.ninfinity.aovmapmod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AOVDarkColorScheme = darkColorScheme(
    background = BackgroundBlack,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    primary = AccentPurple,
    onPrimary = Color.Black,
    secondary = AccentPink,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusError,
)

@Composable
fun AOVMapModTheme(
    // App chỉ dùng theme tối, đúng như định hướng "tinh tế hơn" của bạn
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AOVDarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
