package com.reelwise.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.reelwise.app.data.local.ThemeMode

/** Brand red for marks that are large or non-text: the wordmark, dots, rings, selected states. */
val BrandRed = Color(0xFFE50914)

/** Red for filled buttons and badges carrying white text (5.6:1). */
val ButtonRed = Color(0xFFC40812)

// Netflix-like: true black or clean white, one red. Red text uses `tertiary`, tuned per theme
// so it passes 4.5:1 on the background; filled red uses `primary` with white text.
private val DarkColors = darkColorScheme(
    primary = ButtonRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0A0D),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFE5E5E5),
    onSecondary = Color(0xFF141414),
    secondaryContainer = ButtonRed,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFF5A5F),
    onTertiary = Color(0xFF1A0002),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF5F5F1),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF5F5F1),
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    outline = Color(0xFF77777C),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = ButtonRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E1),
    onPrimaryContainer = Color(0xFF410003),
    secondary = Color(0xFF141414),
    onSecondary = Color.White,
    secondaryContainer = ButtonRed,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFB20710),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF141414),
    surface = Color.White,
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF5C5C63),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFF2F2F3),
    surfaceContainerHigh = Color(0xFFEBEBED),
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFE0E0E3),
)

private val base = Typography()
private val ReelwiseTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.75).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

/** Small all-caps label used above sections ("BECAUSE YOU LOVED"). */
val Eyebrow = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)

/** Condensed, heavy poster-style lettering for titles and the wordmark. */
val PosterType = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.5).sp,
)

@Composable
fun ReelwiseTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
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
