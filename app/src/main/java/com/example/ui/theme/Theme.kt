package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MinimalPrimaryContainer,
    primaryContainer = MinimalPrimary,
    onPrimaryContainer = Color.White,
    secondary = MinimalPrimaryContainer,
    background = Color(0xFF121318),
    surface = Color(0xFF1E2025),
    onPrimary = MinimalOnPrimaryContainer,
    onSecondary = Color.White,
    onBackground = Color(0xFFE1E2EC),
    onSurface = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    error = MinimalNegative
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalPrimary,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    secondary = MinimalPrimaryContainer,
    background = MinimalBackground,
    surface = MinimalSurface,
    onPrimary = Color.White,
    onSecondary = MinimalOnPrimaryContainer,
    onBackground = MinimalOnSurface,
    onSurface = MinimalOnSurface,
    onSurfaceVariant = MinimalOnSurfaceVariant,
    outline = MinimalOutline,
    error = MinimalNegative
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
