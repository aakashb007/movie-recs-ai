package com.reelwise.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Cinema at night": near-black with a warm projector-amber accent. Light mode keeps the
// same hues at contrast-safe depths (all text pairs meet WCAG AA 4.5:1).
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF5C3B00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFFF8A80),
    onSecondary = Color(0xFF3B0A08),
    secondaryContainer = Color(0xFF5A1F1B),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFF8FD0FF),
    onTertiary = Color(0xFF00344F),
    background = Color(0xFF0E0D12),
    onBackground = Color(0xFFECE6F0),
    surface = Color(0xFF0E0D12),
    onSurface = Color(0xFFECE6F0),
    surfaceVariant = Color(0xFF2A2533),
    onSurfaceVariant = Color(0xFFC4BBD0),
    surfaceContainerLowest = Color(0xFF09080C),
    surfaceContainerLow = Color(0xFF16131D),
    surfaceContainer = Color(0xFF1B1823),
    surfaceContainerHigh = Color(0xFF241F2E),
    surfaceContainerHighest = Color(0xFF2E2939),
    outline = Color(0xFF8D8499),
    outlineVariant = Color(0xFF433C4E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7F4D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF291800),
    secondary = Color(0xFFA23A33),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFF00658F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF1E1A22),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF1E1A22),
    surfaceVariant = Color(0xFFEFE0D0),
    onSurfaceVariant = Color(0xFF4F4539),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF1E7),
    surfaceContainer = Color(0xFFF6EBE1),
    surfaceContainerHigh = Color(0xFFF0E5DB),
    surfaceContainerHighest = Color(0xFFEADFD5),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B4),
)

private val base = Typography()
private val ReelwiseTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Small all-caps label used above sections ("BECAUSE YOU LIKED"). */
val Eyebrow = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)

@Composable
fun ReelwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
    MaterialTheme(colorScheme = colors, typography = ReelwiseTypography) {
        // Screens paint their own background; without this, text outside a Surface falls back to black.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
