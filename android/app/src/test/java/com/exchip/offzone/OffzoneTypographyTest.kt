package com.exchip.offzone

import androidx.compose.ui.text.font.FontSynthesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OffzoneTypographyTest {
    @Test
    fun selectsFredokaOnlyForAccentedEuropeanLanguages() {
        for (tag in listOf("de", "de-DE", "es", "es-MX", "fr", "fr-CA", "it", "pt", "pt-BR", "PT_BR")) {
            assertTrue("Expected Fredoka for $tag", usesFredokaForLanguage(tag))
            assertEquals(R.font.fredoka_semibold, appFontRes(Locale.forLanguageTag(tag.replace('_', '-'))))
        }
        for (tag in listOf("en", "en-US", "ko", "ko-KR", "ja", "ja-JP", "zh-CN", "zh-TW", "")) {
            assertFalse("Expected Jua for $tag", usesFredokaForLanguage(tag))
            assertEquals(R.font.jua_regular, appFontRes(Locale.forLanguageTag(tag)))
        }
        assertFalse(usesFredokaForLanguage(null))
    }

    @Test
    fun mapsAllTypographyStylesToSelectedSingleWeightFamilyWithoutSynthesis() {
        val jua = suitFontFamily(Locale.US)
        val fredoka = suitFontFamily(Locale.GERMANY)
        assertEquals(jua, suitFontFamily(Locale.KOREA))
        assertEquals(jua, suitFontFamily(Locale.JAPAN))
        assertEquals(jua, suitFontFamily(Locale.SIMPLIFIED_CHINESE))
        assertEquals(fredoka, suitFontFamily(Locale.forLanguageTag("pt-BR")))
        assertEquals(fredoka, suitFontFamily(Locale.FRENCH))

        val typography = offzoneTypography(fredoka)
        val juaTypography = offzoneTypography(jua)
        val styles = listOf(
            typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        )
        assertTrue(styles.all { it.fontFamily == fredoka && it.fontSynthesis == FontSynthesis.None })
        assertTrue(typography.titleMedium.fontSize > typography.bodyLarge.fontSize)
        assertTrue(juaTypography.titleMedium.fontSize > juaTypography.bodyLarge.fontSize)
        assertTrue(typography.bodyMedium.fontSize < juaTypography.bodyMedium.fontSize)
    }
}
