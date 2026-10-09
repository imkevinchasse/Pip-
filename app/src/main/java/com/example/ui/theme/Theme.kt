package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CosmicColorScheme = darkColorScheme(
    primary = OracleViolet,
    onPrimary = Color(0xFF1E0A3C),
    primaryContainer = CosmicSurfaceVariant,
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = OracleCyan,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = OraclePink,
    onTertiary = Color(0xFF4C0519),
    tertiaryContainer = Color(0xFF831843),
    onTertiaryContainer = Color(0xFFFCE7F3),
    background = CosmicDeepBg,
    onBackground = TextPrimary,
    surface = CosmicSurface,
    onSurface = TextPrimary,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CosmicBorder,
    outlineVariant = Color(0xFF2E224D)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to deep cosmic dark theme for pet oracle aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> CosmicColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
