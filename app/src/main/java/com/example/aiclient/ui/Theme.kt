package com.example.aiclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF7EE0D1),
    secondary = Color(0xFFF4B860),
    tertiary = Color(0xFF6CA0DC),
    background = Color(0xFF08121F),
    surface = Color(0xFF0F1F33),
    onPrimary = Color(0xFF08121F),
    onSecondary = Color(0xFF08121F),
    onTertiary = Color(0xFF08121F),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun AIClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}

