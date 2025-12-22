package one.t10o.cheering_rocket.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Orange40,
    primaryContainer = Orange40,
    onPrimaryContainer = Orange80,
    secondary = Teal80,
    onSecondary = Teal40,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal80,
    tertiary = Amber80,
    onTertiary = Amber40,
    tertiaryContainer = Amber40,
    onTertiaryContainer = Amber80,
    error = Red80,
    onError = Red40,
    background = DarkBackground,
    onBackground = LightBackground,
    surface = DarkSurface,
    onSurface = LightSurface
)

private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = LightBackground,
    primaryContainer = Orange80,
    onPrimaryContainer = Orange40,
    secondary = Teal40,
    onSecondary = LightBackground,
    secondaryContainer = Teal80,
    onSecondaryContainer = Teal40,
    tertiary = Amber40,
    onTertiary = LightBackground,
    tertiaryContainer = Amber80,
    onTertiaryContainer = Amber40,
    error = Red40,
    onError = LightBackground,
    background = LightBackground,
    onBackground = DarkBackground,
    surface = LightSurface,
    onSurface = DarkSurface
)

@Composable
fun CheeringRocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // 独自テーマを使うため false
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

