package app.dilosindecirlo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Identidad visual propia: crema, azul intenso, rojo y tinta negra con sombras "de cartón". */
object Dsd {
    val Cream = Color(0xFFFBF1DA)
    val CreamDark = Color(0xFFF1E2BC)
    val Paper = Color(0xFFFFFBF0)
    val Ink = Color(0xFF17120F)
    val Blue = Color(0xFF1F4FD8)
    val BlueDark = Color(0xFF173A9E)
    val BlueSoft = Color(0xFFDCE5FF)
    val Red = Color(0xFFE5382D)
    val RedSoft = Color(0xFFFFDAD6)
    val Yellow = Color(0xFFFFC93C)
    val Green = Color(0xFF1FAA59)
    val GreenSoft = Color(0xFFD5F5E0)
    val Grey = Color(0xFF6B635C)
}

private val scheme = lightColorScheme(
    primary = Dsd.Blue,
    onPrimary = Color.White,
    secondary = Dsd.Red,
    onSecondary = Color.White,
    tertiary = Dsd.Yellow,
    background = Dsd.Cream,
    onBackground = Dsd.Ink,
    surface = Dsd.Paper,
    onSurface = Dsd.Ink,
    error = Dsd.Red,
)

@Composable
fun DsdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
