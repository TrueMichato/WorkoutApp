package com.example.workoutapp.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = BrandTeal,
    onPrimary = BrandNavy,
    primaryContainer = BrandIndigo,
    onPrimaryContainer = BrandTextLight,
    secondary = BrandOrange,
    onSecondary = BrandNavy,
    tertiary = BrandLime,
    onTertiary = BrandNavy,
    background = BrandNavy,
    onBackground = BrandTextLight,
    surface = BrandSurfaceDark,
    onSurface = BrandTextLight,
    surfaceVariant = BrandIndigo,
    onSurfaceVariant = BrandLime,
    error = BrandRed,
    onError = BrandTextLight
)

private val LightColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = BrandTextLight,
    primaryContainer = BrandLime,
    onPrimaryContainer = BrandNavy,
    secondary = BrandOrange,
    onSecondary = BrandNavy,
    tertiary = BrandTeal,
    onTertiary = BrandNavy,
    background = BrandSand,
    onBackground = BrandTextDark,
    surface = BrandSurfaceLight,
    onSurface = BrandTextDark,
    surfaceVariant = Color(0xFFE7ECF5),
    onSurfaceVariant = Color(0xFF44506A),
    error = BrandRed,
    onError = BrandTextLight
)

@Composable
fun WorkoutAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}