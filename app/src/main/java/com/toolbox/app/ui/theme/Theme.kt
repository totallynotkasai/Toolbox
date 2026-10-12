package com.toolbox.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Google's four brand accents, used for tool tiles and game pieces so they stay recognisable under dynamic colour. */
object GoogleColors {
    val Blue = Color(0xFF1A73E8)
    val Red = Color(0xFFEA4335)
    val Yellow = Color(0xFFFBBC04)
    val Green = Color(0xFF34A853)
    val all = listOf(Blue, Red, Yellow, Green)
}

/*
 * The colour schemes used without dynamic colour (Android 8 to 11). Every role is given, from Google's Material 3
 * blue, cyan, green, red and neutral tonal palettes; a missing one would fall back to Material's purple baseline.
 * The ColorScheme constructor has no defaults, so a role added by a later Material version fails the build here.
 */
private val LightColors = ColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    inversePrimary = Color(0xFFA8C7FA),
    secondary = Color(0xFF00639B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF146C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4EED0),
    onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFF444746),
    surfaceTint = Color(0xFF0B57D0),
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF2F2F2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFC4C7C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD3DBE5),
    primaryFixed = Color(0xFFD3E3FD),
    primaryFixedDim = Color(0xFFA8C7FA),
    onPrimaryFixed = Color(0xFF041E49),
    onPrimaryFixedVariant = Color(0xFF0842A0),
    secondaryFixed = Color(0xFFC2E7FF),
    secondaryFixedDim = Color(0xFF7FCFFF),
    onSecondaryFixed = Color(0xFF001D35),
    onSecondaryFixedVariant = Color(0xFF004A77),
    tertiaryFixed = Color(0xFFC4EED0),
    tertiaryFixedDim = Color(0xFF6DD58C),
    onTertiaryFixed = Color(0xFF072711),
    onTertiaryFixedVariant = Color(0xFF0F5223),
)

private val DarkColors = ColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceTint = Color(0xFFA8C7FA),
    inverseSurface = Color(0xFFE3E3E3),
    inverseOnSurface = Color(0xFF303030),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393B),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF282A2C),
    surfaceContainerHighest = Color(0xFF333537),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceDim = Color(0xFF131314),
    primaryFixed = Color(0xFFD3E3FD),
    primaryFixedDim = Color(0xFFA8C7FA),
    onPrimaryFixed = Color(0xFF041E49),
    onPrimaryFixedVariant = Color(0xFF0842A0),
    secondaryFixed = Color(0xFFC2E7FF),
    secondaryFixedDim = Color(0xFF7FCFFF),
    onSecondaryFixed = Color(0xFF001D35),
    onSecondaryFixedVariant = Color(0xFF004A77),
    tertiaryFixed = Color(0xFFC4EED0),
    tertiaryFixedDim = Color(0xFF6DD58C),
    onTertiaryFixed = Color(0xFF072711),
    onTertiaryFixedVariant = Color(0xFF0F5223),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Normal),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Normal),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
