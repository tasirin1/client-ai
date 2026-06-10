package com.example.aiclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF7C5CFC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2D1F69),
    onPrimaryContainer = Color(0xFFC4B0FF),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2C2C2C),
    onSecondaryContainer = Color(0xFFD4D4D4),
    tertiary = Color(0xFF4A90D9),
    onTertiary = Color(0xFF1A1A1A),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFFA8A8A8),
    outline = Color(0xFF333333),
    error = Color(0xFFE05555),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun AIClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
