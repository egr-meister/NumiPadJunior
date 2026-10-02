package com.numipad.junior.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NumiColors {
    val Cream = Color(0xFFFFF8EC)
    val CreamDeep = Color(0xFFF7ECD6)
    val Navy = Color(0xFF1F2A44)
    val NavySoft = Color(0xFF3A4766)
    val Teal = Color(0xFF7FB8B1)
    val TealLight = Color(0xFFCFE6E2)
    val TealDark = Color(0xFF2F6A64)
    val Lavender = Color(0xFFE4DBF6)
    val LavenderDeep = Color(0xFFC9B9EC)
    val Yellow = Color(0xFFF6D365)
    val YellowSoft = Color(0xFFFBE9A9)
    val Key = Color(0xFFFFFDF7)
    val Correct = Color(0xFF2E7D4F)
    val CorrectBg = Color(0xFFDDF1E3)
    val Incorrect = Color(0xFFB3412E)
    val IncorrectBg = Color(0xFFF8E0DA)
    val ErrorText = Color(0xFF9A2F1F)
}

private val scheme = lightColorScheme(
    primary = NumiColors.TealDark,
    onPrimary = Color.White,
    primaryContainer = NumiColors.TealLight,
    onPrimaryContainer = NumiColors.Navy,
    secondary = NumiColors.Navy,
    onSecondary = NumiColors.Cream,
    secondaryContainer = NumiColors.Lavender,
    onSecondaryContainer = NumiColors.Navy,
    tertiary = NumiColors.Yellow,
    onTertiary = NumiColors.Navy,
    background = NumiColors.Cream,
    onBackground = NumiColors.Navy,
    surface = NumiColors.Cream,
    onSurface = NumiColors.Navy,
    surfaceVariant = NumiColors.CreamDeep,
    onSurfaceVariant = NumiColors.NavySoft,
    outline = NumiColors.NavySoft,
    error = NumiColors.ErrorText,
)

// System sans-serif for clarity; sizes are in sp so system font scaling applies.
// No colour in the style: text must follow LocalContentColor so it stays readable on dark
// surfaces (navy "=" key, navy buttons) and light ones alike.
private val base = TextStyle(fontFamily = FontFamily.SansSerif)
private val typography = Typography(
    displayLarge = base.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp),
    displayMedium = base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
    headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    headlineSmall = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = base.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    labelMedium = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

/** True when the "Reduce decorative animation" setting is on. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun NumiPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        // Default text colour outside any Surface (screen backgrounds are drawn manually).
        CompositionLocalProvider(LocalContentColor provides NumiColors.Navy, content = content)
    }
}
