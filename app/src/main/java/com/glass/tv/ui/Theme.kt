package com.glass.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glass.tv.R
import com.glass.tv.data.ThemeMode

// Glass sigue las guías de interfaz de tvOS (Apple TV): fondos oscuros profundos, materiales translúcidos
// ("Liquid Glass") que dejan ver lo que hay detrás, el elemento enfocado en blanco y elevado, y la tipografía
// del sistema. SF Pro solo se puede usar en plataformas de Apple, así que aquí va Inter (licencia OFL),
// que es su equivalente libre más cercano: Inter para el texto e Inter Display para los títulos,
// igual que SF Pro Text y SF Pro Display.

data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val focusBg: Color,
    val focusText: Color,
    val placeholder: Color,
    val rec: Color = Color(0xFFFF453A),
    /** Relleno del material de cristal (translúcido). */
    val glass: Color,
    /** Relleno del cristal más denso (menús, diálogos). */
    val glassThick: Color,
    /** Brillo especular del borde superior del cristal. */
    val glassEdge: Color,
    /** Color de acento del sistema (azul de tvOS). */
    val accent: Color = Color(0xFF0A84FF),
    /** Dos tonos del fondo ambiental que se ve a través del cristal. */
    val ambientA: Color,
    val ambientB: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    bg = Color(0xFFE9E9EE),
    surface = Color(0xCCFFFFFF),
    surface2 = Color(0x99FFFFFF),
    text = Color(0xFF1C1C1E),
    muted = Color(0xFF6C6C70),
    line = Color(0x1F000000),
    focusBg = Color(0xFFFFFFFF),
    focusText = Color(0xFF000000),
    placeholder = Color(0xFFD1D1D6),
    glass = Color(0x8CFFFFFF),
    glassThick = Color(0xD9F7F7FA),
    glassEdge = Color(0xFFFFFFFF),
    accent = Color(0xFF007AFF),
    ambientA = Color(0xFFDCE3F0),
    ambientB = Color(0xFFF1E6EC),
    dark = false,
)

val OledPalette = Palette(
    bg = Color(0xFF000000),
    surface = Color(0x1FFFFFFF),
    surface2 = Color(0x14FFFFFF),
    text = Color(0xFFFFFFFF),
    muted = Color(0x99EBEBF5),
    line = Color(0x24FFFFFF),
    focusBg = Color(0xFFFFFFFF),
    focusText = Color(0xFF000000),
    placeholder = Color(0xFF1C1C1E),
    glass = Color(0x24FFFFFF),
    glassThick = Color(0xB31C1C1E),
    glassEdge = Color(0x66FFFFFF),
    ambientA = Color(0xFF0B1426),
    ambientB = Color(0xFF1A0E1F),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { OledPalette }

/** Inter (texto), equivalente libre de SF Pro Text. */
val TextFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Inter Display (títulos), equivalente libre de SF Pro Display. */
val DisplayFamily = FontFamily(
    Font(R.font.interdisplay_medium, FontWeight.Normal),
    Font(R.font.interdisplay_medium, FontWeight.Medium),
    Font(R.font.interdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.interdisplay_bold, FontWeight.Bold),
    Font(R.font.interdisplay_extrabold, FontWeight.ExtraBold),
    Font(R.font.interdisplay_extrabold, FontWeight.Black),
)

/** Como en SF Pro: a partir de 20 pt se usa el corte Display, más ajustado. */
fun withSystemFont(style: TextStyle): TextStyle {
    if (style.fontFamily != null) return style
    val big = style.fontSize.isSp && style.fontSize.value >= 20f
    return style.copy(fontFamily = if (big) DisplayFamily else TextFamily)
}

object Type {
    val title = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val tab = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val h2 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val heading = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun GlassTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val p = if (mode == ThemeMode.OLED) OledPalette else LightPalette
    val base = Typography()
    val typo = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = TextFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = TextFamily),
        bodySmall = base.bodySmall.copy(fontFamily = TextFamily),
        labelLarge = base.labelLarge.copy(fontFamily = TextFamily),
        labelMedium = base.labelMedium.copy(fontFamily = TextFamily),
        labelSmall = base.labelSmall.copy(fontFamily = TextFamily),
        titleMedium = base.titleMedium.copy(fontFamily = TextFamily),
    )
    val scheme = if (p.dark) darkColorScheme(primary = Color.White, onPrimary = Color.Black, surface = Color(0xFF1C1C1E))
    else lightColorScheme(primary = p.accent, surface = Color.White)
    MaterialTheme(colorScheme = scheme, typography = typo) {
        CompositionLocalProvider(LocalPalette provides p, LocalTextStyle provides TextStyle(fontFamily = TextFamily), content = content)
    }
}
