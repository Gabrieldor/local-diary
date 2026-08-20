package com.localdiary.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.localdiary.app.R
import com.localdiary.app.home.CoverColor
import com.localdiary.app.home.PaperColor

// Fraunces is only shipped as a variable font, and only its italic instance is bundled
// (no upright counterpart downloaded) — each weight below selects the wght axis on the
// same file, matching the weights Material3's default Typography actually uses.
private fun frauncesWeight(weight: FontWeight, wght: Int) = Font(
    R.font.fraunces_italic_variable,
    weight = weight,
    style = FontStyle.Italic,
    variationSettings = FontVariation.Settings(FontVariation.weight(wght))
)

val AppFontFamily = FontFamily(
    frauncesWeight(FontWeight.Normal, 400),
    frauncesWeight(FontWeight.Medium, 500),
    frauncesWeight(FontWeight.SemiBold, 600),
    frauncesWeight(FontWeight.Bold, 700)
)

// Button/FilledIconButton container color, matching the locked-screen book cover.
fun appColorScheme(): ColorScheme = lightColorScheme(primary = CoverColor, onPrimary = PaperColor)

/** Default Material3 [Typography] with every text style repointed at [AppFontFamily]. */
fun appTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = AppFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = AppFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = AppFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = AppFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = AppFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = AppFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = AppFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = AppFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = AppFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = AppFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = AppFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = AppFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = AppFontFamily)
    )
}
