package xyz.block.gosling.shared.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Base colors
val primaryColor = Color(0xFF7A7EFB)  // Purple
val secondaryColor = Color(0xFFD8D8D8)
val tertiaryColor = Color(0xFF7A7EFB)
val errorColor = Color(0xFFB3261E)
val surfaceColor = Color(0xFFFAFBFF)
val outlineColor = Color(0xFF79747E)

// Dark mode colors
val darkPrimaryColor = Color(0xFF9A9DFC)  // Lighter purple
val darkSecondaryColor = Color(0xFF3A3A3A)
val darkTertiaryColor = Color(0xFF9A9DFC)
val darkErrorColor = Color(0xFFCF6679)
val darkSurfaceColor = Color(0xFF121212)
val darkOutlineColor = Color(0xFF919191)

private val DarkColorScheme = darkColorScheme(
    primary = darkPrimaryColor,
    onPrimary = Color.Black,
    primaryContainer = darkPrimaryColor,
    onPrimaryContainer = Color.Black,
    secondary = darkSecondaryColor,
    onSecondary = darkPrimaryColor,
    secondaryContainer = darkSecondaryColor,
    onSecondaryContainer = darkPrimaryColor,
    tertiary = darkTertiaryColor,
    onTertiary = Color.Black,
    tertiaryContainer = darkTertiaryColor,
    onTertiaryContainer = Color.Black,
    error = darkErrorColor,
    onError = Color.Black,
    errorContainer = errorColor,
    onErrorContainer = Color.Black,
    surface = darkSurfaceColor,
    onSurface = Color.White,
    surfaceContainerHighest = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outline = darkOutlineColor,
    inverseSurface = Color.White,
    inverseOnSurface = darkSurfaceColor,
    inversePrimary = primaryColor,
    surfaceTint = darkPrimaryColor,
    outlineVariant = darkOutlineColor,
    scrim = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = primaryColor,
    onPrimary = Color.White,
    primaryContainer = primaryColor,
    onPrimaryContainer = Color.White,
    secondary = secondaryColor,
    onSecondary = primaryColor,
    secondaryContainer = secondaryColor,
    onSecondaryContainer = primaryColor,
    tertiary = tertiaryColor,
    onTertiary = Color.White,
    tertiaryContainer = tertiaryColor,
    onTertiaryContainer = Color.White,
    error = errorColor,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surface = surfaceColor,
    onSurface = Color(0xFF1A1C1E),
    surfaceContainerHighest = Color(0xFFFFE8DE),
    onSurfaceVariant = Color(0xFF212121),
    outline = outlineColor,
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color.White,
    inversePrimary = Color.Black,
    surfaceTint = primaryColor
)

@Composable
fun GoslingTheme(
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

    CompositionLocalProvider {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
