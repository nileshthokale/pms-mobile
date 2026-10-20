package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PharmacyDarkTealPrimary,
    onPrimary = PharmacyDarkTealOnPrimary,
    primaryContainer = PharmacyDarkTealContainer,
    onPrimaryContainer = PharmacyDarkTealOnContainer,
    secondary = PharmacyDarkSlateSecondary,
    onSecondary = PharmacyDarkSlateOnSecondary,
    secondaryContainer = PharmacyDarkSlateContainer,
    onSecondaryContainer = PharmacyDarkSlateOnContainer,
    tertiary = PharmacyDarkAmberTertiary,
    onTertiary = PharmacyDarkAmberOnTertiary,
    tertiaryContainer = PharmacyDarkAmberContainer,
    onTertiaryContainer = PharmacyDarkAmberOnContainer,
    background = PharmacyDarkBackground,
    surface = PharmacyDarkSurface,
    surfaceVariant = PharmacyDarkSurfaceVariant,
    onBackground = PharmacyDarkOnSurface,
    onSurface = PharmacyDarkOnSurface,
    onSurfaceVariant = PharmacyDarkOnSurfaceVariant,
    error = PharmacyError,
    errorContainer = PharmacyErrorContainer,
    onErrorContainer = PharmacyOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = PharmacyTealPrimary,
    onPrimary = PharmacyTealOnPrimary,
    primaryContainer = PharmacyTealContainer,
    onPrimaryContainer = PharmacyTealOnContainer,
    secondary = PharmacySlateSecondary,
    onSecondary = PharmacySlateOnSecondary,
    secondaryContainer = PharmacySlateContainer,
    onSecondaryContainer = PharmacySlateOnContainer,
    tertiary = PharmacyAmberTertiary,
    onTertiary = PharmacyAmberOnTertiary,
    tertiaryContainer = PharmacyAmberContainer,
    onTertiaryContainer = PharmacyAmberOnContainer,
    background = PharmacyLightBackground,
    surface = PharmacyLightSurface,
    surfaceVariant = PharmacyLightSurfaceVariant,
    onBackground = PharmacyLightOnSurface,
    onSurface = PharmacyLightOnSurface,
    onSurfaceVariant = PharmacyLightOnSurfaceVariant,
    error = PharmacyError,
    errorContainer = PharmacyErrorContainer,
    onErrorContainer = PharmacyOnErrorContainer
)

@Composable
fun PharmacyBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
