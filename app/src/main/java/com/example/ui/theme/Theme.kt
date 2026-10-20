package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val SlantPastelDarkColorScheme = darkColorScheme(
    primary = SlantCyan,
    onPrimary = Color(0xFF102A22),
    primaryContainer = Color(0xFF244239),
    onPrimaryContainer = Color(0xFFDDFBEE),
    secondary = SlantViolet,
    onSecondary = Color(0xFF211832),
    secondaryContainer = Color(0xFF392E4E),
    onSecondaryContainer = Color(0xFFF2EBFF),
    tertiary = SlantMagenta,
    onTertiary = Color(0xFF331420),
    tertiaryContainer = Color(0xFF4F2636),
    onTertiaryContainer = Color(0xFFFFEBF1),
    background = SlantObsidian,
    onBackground = SlantTextPrimaryDark,
    surface = SlantSurfaceDark,
    onSurface = SlantTextPrimaryDark,
    surfaceVariant = SlantSurfaceVariantDark,
    onSurfaceVariant = SlantTextSecondaryDark,
    outline = Color(0xFF475569)
)

private val SlantPastelLightColorScheme = lightColorScheme(
    primary = SlantCyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3E8),
    onPrimaryContainer = Color(0xFF0E2E23),
    secondary = SlantVioletDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBE4F8),
    onSecondaryContainer = Color(0xFF261C3C),
    tertiary = SlantMagentaDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE4EC),
    onTertiaryContainer = Color(0xFF3B1222),
    background = SlantLightBg,
    onBackground = Color(0xFF1E293B),
    surface = SlantLightSurface,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = SlantLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1)
)

val SlantShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun SlantMuzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SlantPastelDarkColorScheme
        else -> SlantPastelLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SlantShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SlantMuzTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
