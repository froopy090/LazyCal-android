package com.francescocanossi.lazycal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class LazyCalColors(
    val protein: Color = Color.Unspecified,
    val carbs: Color = Color.Unspecified,
    val fats: Color = Color.Unspecified,
    val success: Color = Color.Unspecified,
    val error: Color = Color.Unspecified,
    val successShading: Color = Color.Unspecified,
    val errorShading: Color = Color.Unspecified
)

val LocalLazyCalColors = staticCompositionLocalOf { LazyCalColors() }

object LazyCalTheme {
    val colors: LazyCalColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLazyCalColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    error = ErrorColor
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    error = ErrorColor
)

@Composable
fun LazyCalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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

    val customColors = LazyCalColors(
        protein = ProteinColor,
        carbs = CarbsColor,
        fats = FatsColor,
        success = SuccessColor,
        error = ErrorColor,
        successShading = SuccessShading,
        errorShading = ErrorShading
    )

    CompositionLocalProvider(LocalLazyCalColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}