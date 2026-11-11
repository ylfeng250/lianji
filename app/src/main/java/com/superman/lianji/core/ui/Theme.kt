package com.superman.lianji.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF1F6A64)
private val TealContainer = Color(0xFFD7EBE7)
private val Ink = Color(0xFF1A1C1B)
private val Ivory = Color(0xFFF7F4EE)
private val Card = Color(0xFFFFFCF8)
private val Sand = Color(0xFFE7E1D6)
private val Night = Color(0xFF101211)
private val NightCard = Color(0xFF1C1F1D)
private val Mist = Color(0xFF8ED0C8)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF04211E),
    secondary = Color(0xFF6E6256),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EAE6),
    onSecondaryContainer = Color(0xFF0B2F2B),
    tertiary = Color(0xFF8A6A3B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E0C6),
    onTertiaryContainer = Color(0xFF3A2A10),
    background = Ivory,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEAE2),
    onSurfaceVariant = Color(0xFF5C574F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Card,
    surfaceContainer = Color(0xFFF1ECE4),
    surfaceContainerHigh = Color(0xFFECE6DC),
    surfaceContainerHighest = Sand,
    surfaceDim = Color(0xFFDDD6CA),
    surfaceBright = Color(0xFFFFFCF8),
    outline = Color(0xFFD5CFC4),
    outlineVariant = Color(0xFFE3DCD1),
    inverseSurface = Color(0xFF2F322F),
    inverseOnSurface = Color(0xFFF3F1EA),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    onPrimary = Color(0xFF07312E),
    primaryContainer = Color(0xFF1B4A46),
    onPrimaryContainer = Color(0xFFD7EBE7),
    secondary = Color(0xFFB9CBC5),
    onSecondary = Color(0xFF1E332F),
    secondaryContainer = Color(0xFF23423D),
    onSecondaryContainer = Color(0xFFD7EBE7),
    tertiary = Color(0xFFE5C08A),
    onTertiary = Color(0xFF3A2A10),
    tertiaryContainer = Color(0xFF4A3A1E),
    onTertiaryContainer = Color(0xFFF6E2C0),
    background = Night,
    onBackground = Color(0xFFF3F1EA),
    surface = NightCard,
    onSurface = Color(0xFFF3F1EA),
    surfaceVariant = Color(0xFF2A2E2C),
    onSurfaceVariant = Color(0xFFC3C7C2),
    surfaceContainerLowest = Color(0xFF0B0D0C),
    surfaceContainerLow = NightCard,
    surfaceContainer = Color(0xFF202422),
    surfaceContainerHigh = Color(0xFF2A2E2C),
    surfaceContainerHighest = Color(0xFF353936),
    surfaceDim = Night,
    surfaceBright = Color(0xFF3A3F3C),
    outline = Color(0xFF444844),
    outlineVariant = Color(0xFF3D423E),
    inverseSurface = Color(0xFFF3F1EA),
    inverseOnSurface = Color(0xFF1C1F1D),
    scrim = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppType = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp))

@Composable
fun LianjiTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppType, shapes = AppShapes, content = content)
}
