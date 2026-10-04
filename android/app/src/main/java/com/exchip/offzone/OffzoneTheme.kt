package com.exchip.offzone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
internal val Suit = FontFamily(
    Font(R.font.suit_regular), Font(R.font.suit_medium, FontWeight.Medium),
    Font(R.font.suit_semibold, FontWeight.SemiBold), Font(R.font.suit_bold, FontWeight.Bold),
)
internal val OffzoneTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = Suit, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Suit, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Suit, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Suit, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Suit),
        bodyLarge = base.bodyLarge.copy(fontFamily = Suit, fontSize = 17.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Suit, fontSize = 16.sp),
        bodySmall = base.bodySmall.copy(fontFamily = Suit, fontSize = 14.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Suit, fontSize = 16.sp),
    )
}

// Every Material default that would otherwise show purple or grey is mapped to the palette here.
internal val OffzoneColors = lightColorScheme(
    primary = Pine, onPrimary = Butter, secondaryContainer = Mint, onSecondaryContainer = Ink,
    background = Butter, onBackground = Ink, surface = Butter, onSurface = Ink, onSurfaceVariant = InkMuted,
    surfaceContainerLowest = WarmIvory, surfaceContainerLow = WarmIvory, surfaceContainer = WarmIvory,
    surfaceContainerHigh = WarmIvory, surfaceContainerHighest = WarmIvory,
    outline = InkMuted, outlineVariant = PineHairline, scrim = Ink.copy(alpha = 0.55f),
)
// small = chips, extraSmall = text fields (rows, radius 16); extraLarge = dialogs (radius 24).
internal val OffzoneShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp), small = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
internal fun OffzoneTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = OffzoneColors, typography = OffzoneTypography, shapes = OffzoneShapes, content = content)

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
internal fun OffzoneChip(selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, label: @Composable () -> Unit) =
    FilterChip(
        selected, onClick, label, modifier.heightIn(min = 40.dp), enabled,
        colors = FilterChipDefaults.filterChipColors(containerColor = WarmIvory, labelColor = Ink, selectedContainerColor = Mint),
        border = FilterChipDefaults.filterChipBorder(enabled, selected, borderColor = PineLine, selectedBorderColor = Pine, selectedBorderWidth = 1.5.dp),
    )
