package dev.mulvey.justchess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Night = Color(0xFF0E1116)
val SurfaceDark = Color(0xFF171B22)
val SurfaceHigh = Color(0xFF222833)
val Gold = Color(0xFFD4A017)
val GoldDim = Color(0xFFB8860B)
val Cream = Color(0xFFE8E0D0)
val Danger = Color(0xFFC44536)

val BoardLight = Color(0xFF8B95A3)
val BoardDark = Color(0xFF3E4854)
val LastMove = Color(0x66E0B84A)
val Selected = Color(0x88E8D48B)
val CheckTint = Color(0x66C44536)
val LegalDot = Color(0x99F5F0E6)
val PieceWhite = Color(0xFFF4EFE4)
val PieceBlack = Color(0xFF1C1C1C)
val PieceBlackStroke = Color(0xFFD7D0C4)
val BoardHighlight = LastMove
val BoardSelected = Selected
val BoardLegal = LegalDot
val BoardCheck = CheckTint
val CoordColor = Cream
val WhitePieceFill = PieceWhite
val WhitePieceStroke = Color(0xFF2A2E36)
val BlackPieceFill = PieceBlack
val BlackPieceStroke = PieceBlackStroke
val ClockActive = Gold
val ClockIdle = Color(0xFF9AA3B2)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1404),
    secondary = Cream,
    onSecondary = Night,
    background = Night,
    onBackground = Cream,
    surface = SurfaceDark,
    onSurface = Cream,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFFB7B1A4),
    error = Danger,
    onError = Color.White,
    outline = Color(0xFF3A414D),
)

private val Typo = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun JustChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typo, content = content)
}
