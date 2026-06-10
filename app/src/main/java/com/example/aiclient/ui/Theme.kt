package com.example.aiclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF00FF88),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF003322),
    onPrimaryContainer = Color(0xFF88FFBB),
    secondary = Color(0xFF66DDAA),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A332A),
    onSecondaryContainer = Color(0xFFAAEECC),
    tertiary = Color(0xFF00CCCC),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0A0D0A),
    onBackground = Color(0xFFCCEECC),
    surface = Color(0xFF111811),
    onSurface = Color(0xFFCCEECC),
    surfaceVariant = Color(0xFF1A2A1A),
    onSurfaceVariant = Color(0xFF88BB88),
    outline = Color(0xFF2A442A),
    error = Color(0xFFFF4466),
    onError = Color(0xFF000000),
)

@Composable
fun AIClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
