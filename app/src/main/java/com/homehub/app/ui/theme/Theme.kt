package com.homehub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1B1F3B)
private val Amber = Color(0xFFFFC857)
private val Teal = Color(0xFF2EC4B6)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Amber,
    tertiary = Teal
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    secondary = Teal,
    tertiary = Navy
)

@Composable
fun HomeHubTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
