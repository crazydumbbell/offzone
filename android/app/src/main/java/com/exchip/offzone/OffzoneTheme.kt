package com.exchip.offzone

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.annotation.FontRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import java.util.Locale

// D-47 palette. Ink is for text only; Pine carries every action, selection and progress state.
internal val Butter = Color(0xFFF7F0C7)
internal val Pine = Color(0xFF3D5B3F)
internal val Ink = Color(0xFF1F2A22)
internal val InkMuted = Color(0xFF5E6660)
internal val WarmIvory = Color(0xFFFAF7E8)
internal val Mint = Color(0xFFE8EEDD)
internal val SoftButter = Color(0xFFEFE5AB)
internal val Warning = Color(0xFF896839)
internal val PineLine = Pine.copy(alpha = 0.28f)
internal val PineHairline = Pine.copy(alpha = 0.14f)

private val FredokaLanguages = setOf("de", "es", "fr", "it", "pt")

private fun singleWeightFamily(@FontRes fontRes: Int): FontFamily = FontFamily(
    Font(fontRes, FontWeight.Thin),
    Font(fontRes, FontWeight.ExtraLight),
    Font(fontRes, FontWeight.Light),
    Font(fontRes, FontWeight.Normal),
    Font(fontRes, FontWeight.Medium),
    Font(fontRes, FontWeight.SemiBold),
    Font(fontRes, FontWeight.Bold),
    Font(fontRes, FontWeight.ExtraBold),
    Font(fontRes, FontWeight.Black),
)

private val JuaFontFamily = singleWeightFamily(R.font.jua_regular)
private val FredokaFontFamily = singleWeightFamily(R.font.fredoka_semibold)

internal fun usesFredokaForLanguage(languageTag: String?): Boolean {
    val primary = languageTag.orEmpty().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
    return primary in FredokaLanguages
}

internal fun appLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val appLocales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (appLocales != null && !appLocales.isEmpty) {
            appLocales[0]?.let { return it }
        }
    }
    val configLocales = context.resources.configuration.locales
    if (!configLocales.isEmpty) {
        configLocales[0]?.let { return it }
    }
    return Locale.getDefault()
}

@FontRes
internal fun appFontRes(locale: Locale): Int =
    if (usesFredokaForLanguage(locale.language)) R.font.fredoka_semibold else R.font.jua_regular

@FontRes
internal fun appFontRes(context: Context): Int = appFontRes(appLocale(context))

internal fun suitFontFamily(locale: Locale): FontFamily =
    if (usesFredokaForLanguage(locale.language)) FredokaFontFamily else JuaFontFamily

internal val Suit: FontFamily
    get() = suitFontFamily(Locale.getDefault())

internal fun offzoneTypography(family: FontFamily, opticalTrimSp: Int = if (family == FredokaFontFamily) 1 else 0): Typography = Typography().let { base ->
    val d = opticalTrimSp
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineBreak = LineBreak.Heading),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineBreak = LineBreak.Heading),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineBreak = LineBreak.Heading),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (30 - d).sp, lineHeight = (36 - d).sp, lineBreak = LineBreak.Heading),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (26 - d).sp, lineHeight = (32 - d).sp, lineBreak = LineBreak.Heading),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (22 - d).sp, lineHeight = (28 - d).sp, lineBreak = LineBreak.Heading),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (20 - d).sp, lineHeight = (26 - d).sp, lineBreak = LineBreak.Heading),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (17 - d).sp, lineHeight = (23 - d).sp, lineBreak = LineBreak.Heading),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineBreak = LineBreak.Heading),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (16 - d).sp, lineHeight = (22 - d).sp, lineBreak = LineBreak.Paragraph),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (15 - d).sp, lineHeight = (21 - d).sp, lineBreak = LineBreak.Paragraph),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (13 - d).sp, lineHeight = (18 - d).sp, lineBreak = LineBreak.Paragraph),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = (15 - d).sp, lineHeight = (20 - d).sp),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None),
    )
}

internal val OffzoneTypography: Typography
    get() = offzoneTypography(Suit)

// Every Material default that would otherwise show purple or grey is mapped to the palette here.
internal val OffzoneColors = lightColorScheme(
    primary = Pine, onPrimary = Butter, secondaryContainer = Mint, onSecondaryContainer = Ink,
    background = Butter, onBackground = Ink, surface = Butter, onSurface = Ink, onSurfaceVariant = InkMuted,
    surfaceContainerLowest = WarmIvory, surfaceContainerLow = WarmIvory, surfaceContainer = WarmIvory,
    surfaceContainerHigh = WarmIvory, surfaceContainerHighest = WarmIvory,
    outline = InkMuted, outlineVariant = PineHairline, error = Warning, onError = Butter, scrim = Ink.copy(alpha = 0.55f),
)
// small = chips, extraSmall = text fields (rows, radius 16); extraLarge = dialogs (radius 24).
internal val OffzoneShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp), small = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
internal fun OffzoneTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val locale = remember(configuration) {
        val perApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)
        } else null
        perApp ?: if (!configuration.locales.isEmpty) configuration.locales[0] else appLocale(context)
    }
    val typography = remember(locale.language) { offzoneTypography(suitFontFamily(locale)) }
    MaterialTheme(colorScheme = OffzoneColors, typography = typography, shapes = OffzoneShapes, content = content)
}

@Composable
internal fun StepProgress(progress: Float, modifier: Modifier = Modifier) =
    LinearProgressIndicator({ progress }, modifier.fillMaxWidth(), color = Pine, trackColor = PineHairline, gapSize = 0.dp, drawStopIndicator = {})

@Composable
internal fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier.heightIn(min = 56.dp), enabled, content = content)

@Composable
internal fun SecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    OutlinedButton(
        onClick, modifier.heightIn(min = 56.dp), enabled,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = WarmIvory, contentColor = Ink, disabledContainerColor = WarmIvory.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, if (enabled) PineLine else Ink.copy(alpha = 0.10f)), content = content,
    )

@Composable
internal fun OffzoneChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, label: @Composable () -> Unit) =
    FilterChip(
        selected, onClick, label, modifier.heightIn(min = 40.dp), enabled,
        colors = FilterChipDefaults.filterChipColors(containerColor = WarmIvory, labelColor = Ink, selectedContainerColor = Mint),
        border = FilterChipDefaults.filterChipBorder(enabled, selected, borderColor = PineLine, selectedBorderColor = Pine, selectedBorderWidth = 1.5.dp),
    )

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun Context.openExternalUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
