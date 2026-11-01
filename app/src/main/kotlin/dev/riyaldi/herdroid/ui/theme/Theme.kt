package dev.riyaldi.herdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
  primary = PrimaryDark,
  onPrimary = OnPrimaryDark,
  primaryContainer = PrimaryContainerDark,
  onPrimaryContainer = OnPrimaryContainerDark,
  secondary = SecondaryDark,
  onSecondary = OnSecondaryDark,
  secondaryContainer = Color(0xFF263A33),
  onSecondaryContainer = Color(0xFFC7E8DA),
  tertiary = TertiaryDark,
  onTertiary = OnTertiaryDark,
  tertiaryContainer = Color(0xFF244A55),
  onTertiaryContainer = Color(0xFFCDEBF5),
  background = BackgroundDark,
  onBackground = OnSurfaceDark,
  surface = SurfaceDark,
  onSurface = OnSurfaceDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = OnSurfaceVariantDark,
  surfaceContainerLowest = Color(0xFF070B0D),
  surfaceContainerLow = Color(0xFF0F171C),
  surfaceContainer = Color(0xFF141D22),
  surfaceContainerHigh = Color(0xFF1B262C),
  surfaceContainerHighest = Color(0xFF243138),
  surfaceDim = Color(0xFF070B0D),
  surfaceBright = Color(0xFF303D43),
  outline = OutlineDark,
  outlineVariant = Color(0xFF3F4945),
  error = ErrorDark,
  onError = Color(0xFF690005),
  errorContainer = ErrorContainerDark,
  onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
  primary = PrimaryLight,
  onPrimary = OnPrimaryLight,
  primaryContainer = PrimaryContainerLight,
  onPrimaryContainer = OnPrimaryContainerLight,
  secondary = SecondaryLight,
  secondaryContainer = Color(0xFFD8E8E1),
  onSecondaryContainer = Color(0xFF17231E),
  tertiary = TertiaryLight,
  tertiaryContainer = Color(0xFFD5E8EF),
  onTertiaryContainer = Color(0xFF0A2830),
  background = BackgroundLight,
  onBackground = OnSurfaceLight,
  surface = SurfaceLight,
  onSurface = OnSurfaceLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = OnSurfaceVariantLight,
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF1F6F3),
  surfaceContainer = Color(0xFFEAF0EC),
  surfaceContainerHigh = Color(0xFFE3EBE6),
  surfaceContainerHighest = Color(0xFFDCE5E0),
  surfaceDim = Color(0xFFD8E0DC),
  surfaceBright = Color(0xFFF9FCFA),
  outline = OutlineLight,
  outlineVariant = Color(0xFFC0CAC4),
  error = Color(0xFFB3261E),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

private val HerdrShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun HerdrOidTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = HerdrTypography,
    shapes = HerdrShapes,
    content = content,
  )
}
