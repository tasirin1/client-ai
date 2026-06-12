package com.example.aiprediksi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF00C853),
    onPrimary = Color(0xFF0F0F0F),
    primaryContainer = Color(0xFF003D1A),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFF7C5CFC),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2D1F69),
    onSecondaryContainer = Color(0xFFC4B0FF),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color(0xFF1A1A1A),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFA8A8A8),
    outline = Color(0xFF333333),
    error = Color(0xFFE05555),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun PrediksiAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
