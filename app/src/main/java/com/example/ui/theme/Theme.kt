package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SuaDarkColorScheme = darkColorScheme(
    primary = SuaCyan,
    onPrimary = SuaObsidian,
    primaryContainer = SuaSurfaceElevated,
    onPrimaryContainer = SuaCyan,
    secondary = SuaElectricBlue,
    onSecondary = SuaTextPrimary,
    secondaryContainer = SuaSurfaceCard,
    onSecondaryContainer = SuaTextPrimary,
    tertiary = SuaEmerald,
    onTertiary = SuaObsidian,
    background = SuaObsidian,
    onBackground = SuaTextPrimary,
    surface = SuaDeepNavy,
    onSurface = SuaTextPrimary,
    surfaceVariant = SuaSurfaceCard,
    onSurfaceVariant = SuaTextSecondary,
    error = SuaCrimson,
    onError = SuaTextPrimary,
    outline = SuaBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SuaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
