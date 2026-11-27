package com.mibolsillointeligente.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mibolsillointeligente.app.domain.model.ThemeMode

val DarkGreenPrimary = Color(0xFF1B5E20)
val MediumGreenProgress = Color(0xFF4CAF50)
val SoftGreenContainer = Color(0xFFDCEFE0)
val AlertRed = Color(0xFFC62828)
val AlertRedContainer = Color(0xFFFFDAD6)

private val LightColors = lightColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = SoftGreenContainer,
    onPrimaryContainer = Color(0xFF062109),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F4E7),
    onSecondaryContainer = Color(0xFF0D2D12),
    tertiary = MediumGreenProgress,
    onTertiary = Color.White,
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF171D18),
    surface = Color.White,
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFEAEFEA),
    onSurfaceVariant = Color(0xFF424D43),
    outline = Color(0xFF727D73),
    outlineVariant = Color(0xFFD5DDD6),
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedContainer,
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF042909),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFDCEFE0),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF072B0B),
    secondaryContainer = Color(0xFF1D3B21),
    onSecondaryContainer = Color(0xFFD3EED6),
    tertiary = MediumGreenProgress,
    onTertiary = Color(0xFF032308),
    background = Color(0xFF0E1510),
    onBackground = Color(0xFFE3EAE3),
    surface = Color(0xFF151E17),
    onSurface = Color(0xFFE3EAE3),
    surfaceVariant = Color(0xFF212D23),
    onSurfaceVariant = Color(0xFFB8C4B9),
    outline = Color(0xFF869387),
    outlineVariant = Color(0xFF2D3A2F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun MiBolsilloTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
