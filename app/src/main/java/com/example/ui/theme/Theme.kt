package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// =========================================================================
// MINIMAL THEME IMPLEMENTATION
// Single accent: #6C5CE7 (refined indigo-violet)
// Light & Dark themes with precise neutral surfaces
// =========================================================================

private val MinimalLightColorScheme = lightColorScheme(
    primary = AccentIndigo,
    onPrimary = Color.White,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = AccentIndigoLight,
    secondary = AccentIndigo,
    onSecondary = Color.White,
    secondaryContainer = LightAccentContainer,
    onSecondaryContainer = AccentIndigoLight,
    tertiary = AccentIndigo,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightSecondaryText,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = MinimalError,
    onError = Color.White,
    errorContainer = MinimalErrorContainerLight,
    onErrorContainer = MinimalError
)

private val MinimalDarkColorScheme = darkColorScheme(
    primary = AccentIndigoDark,
    onPrimary = Color.White,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkPrimaryText,
    secondary = AccentIndigoDark,
    onSecondary = Color.White,
    secondaryContainer = DarkAccentContainer,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = AccentIndigoDark,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = MinimalErrorDark,
    onError = Color.White,
    errorContainer = MinimalErrorContainerDark,
    onErrorContainer = MinimalErrorDark
)

private val MinimalFieldColorScheme = darkColorScheme(
    primary = FieldAccent,
    onPrimary = Color.Black,
    primaryContainer = FieldAccentContainer,
    onPrimaryContainer = FieldSecondaryText,
    secondary = FieldAccent,
    onSecondary = Color.Black,
    secondaryContainer = FieldAccentContainer,
    onSecondaryContainer = FieldSecondaryText,
    tertiary = FieldAccent,
    onTertiary = Color.Black,
    background = FieldBackground,
    onBackground = FieldPrimaryText,
    surface = FieldSurface,
    onSurface = FieldPrimaryText,
    surfaceVariant = FieldSurface,
    onSurfaceVariant = FieldSecondaryText,
    outline = FieldOutline,
    outlineVariant = FieldOutline,
    error = FieldError,
    onError = Color.Black,
    errorContainer = FieldErrorContainer,
    onErrorContainer = FieldError
)

val MinimalColorsInstance: MinimalColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMinimalColors.current

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isFieldMode: Boolean = false,
    fontScale: Float = 1.0f,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isFieldMode -> MinimalFieldColorScheme
        darkTheme -> MinimalDarkColorScheme
        else -> MinimalLightColorScheme
    }

    val minimalTokens = when {
        isFieldMode -> MinimalColors(
            background = FieldBackground,
            surface = FieldSurface,
            textPrimary = FieldPrimaryText,
            textSecondary = FieldSecondaryText,
            outline = FieldOutline,
            accent = FieldAccent,
            accentContainer = FieldAccentContainer,
            error = FieldError,
            errorContainer = FieldErrorContainer,
            isDark = true
        )
        darkTheme -> MinimalColors(
            background = DarkBackground,
            surface = DarkSurface,
            textPrimary = DarkPrimaryText,
            textSecondary = DarkSecondaryText,
            outline = DarkOutline,
            accent = AccentIndigoDark,
            accentContainer = DarkAccentContainer,
            error = MinimalErrorDark,
            errorContainer = MinimalErrorContainerDark,
            isDark = true
        )
        else -> MinimalColors(
            background = LightBackground,
            surface = LightSurface,
            textPrimary = LightPrimaryText,
            textSecondary = LightSecondaryText,
            outline = LightOutline,
            accent = AccentIndigo,
            accentContainer = LightAccentContainer,
            error = MinimalError,
            errorContainer = MinimalErrorContainerLight,
            isDark = false
        )
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val effectiveFontScale = if (fontScale > 0.1f) fontScale else 1.0f
    val scaledDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * effectiveFontScale
    )

    CompositionLocalProvider(
        LocalMinimalColors provides minimalTokens,
        androidx.compose.ui.platform.LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
