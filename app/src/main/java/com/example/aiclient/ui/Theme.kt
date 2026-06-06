package com.example.aiclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF10A37F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A3D32),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = Color(0xFFA8A8A8),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFD4D4D4),
    tertiary = Color(0xFF6CA0DC),
    onTertiary = Color(0xFF1A1A1A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF444444),
    error = Color(0xFFCF6679),
    onError = Color(0xFF1A1A1A),
)

@Composable
fun AIClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
