package io.github.fran2402.kderemote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.fran2402.kderemote.R

// Fallback palette (KDE-blue seed) for Android 11 and older, which have no wallpaper colours.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBFB),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF124A73),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF53405C),
    onTertiaryContainer = Color(0xFFF4D9FF),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF33618D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D34),
    secondary = Color(0xFF526070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF6A5779),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
)

@OptIn(ExperimentalTextApi::class)
private fun roundedFont(weight: FontWeight) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", 100f),
    ),
)

/** Google Sans Flex with its roundness axis turned all the way up. */
val GoogleSansFlexRounded = FontFamily(
    roundedFont(FontWeight.Normal),
    roundedFont(FontWeight.Medium),
    roundedFont(FontWeight.SemiBold),
    roundedFont(FontWeight.Bold),
)

private fun TextStyle.rounded() = copy(fontFamily = GoogleSansFlexRounded)

private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.rounded(),
        displayMedium = displayMedium.rounded(),
        displaySmall = displaySmall.rounded(),
        headlineLarge = headlineLarge.rounded(),
        headlineMedium = headlineMedium.rounded(),
        headlineSmall = headlineSmall.rounded(),
        titleLarge = titleLarge.rounded(),
        titleMedium = titleMedium.rounded(),
        titleSmall = titleSmall.rounded(),
        bodyLarge = bodyLarge.rounded(),
        bodyMedium = bodyMedium.rounded(),
        bodySmall = bodySmall.rounded(),
        labelLarge = labelLarge.rounded(),
        labelMedium = labelMedium.rounded(),
        labelSmall = labelSmall.rounded(),
    )
}

@Composable
fun KdeRemoteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
