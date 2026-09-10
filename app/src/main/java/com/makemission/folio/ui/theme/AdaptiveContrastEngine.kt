package com.makemission.folio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Colorimetric Contrast Optimization (§5).
 *
 * Instead of just dimming backlight, ties ambient light sensor to WCAG
 * relative luminance to dynamically shift exact hex values and maintain
 * optimal 7:1 contrast.
 *
 * Builds on top of [FolioTheme] — does not rewrite it. Pure on-device,
 * deterministic, smooth.
 *
 * Reference: WCAG 2.1 contrast formula.
 */
object AdaptiveContrastEngine {

    const val TARGET_CONTRAST = 7.0
    private const val LUX_MAX = 10000f

    /** WCAG relative luminance for [color] (0..1). */
    fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val srgb = channel.toDouble() // 0..1 already linear in Compose? No, need sRGB decode
            // Compose Color channels are sRGB 0..1
            return if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }

    /** Contrast ratio between [a] and [b] (>=1, e.g. 7:1). */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Lux (0..10000) -> factor 0..1 via log scale for perceptual smoothness. */
    fun luxToFactor(lux: Float): Float {
        val clamped = lux.coerceIn(0f, LUX_MAX)
        // log10(lux+1) / log10(LUX_MAX+1) => 0 at 0 lux, 1 at 10000 lux
        return (log10(clamped + 1f) / log10(LUX_MAX + 1f)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Lerp background for ambient light — now palette-aware and coordinated.
     * Dark theme: low lux -> darker (reduce glare), high lux -> lighter (visibility).
     * The adaptive background lerps **whichever palette is currently selected**
     * (via [base] which comes from `MaterialTheme.colorScheme.background`), not
     * hard-override to Folio green. For the classic Folio green we preserve the
     * original editorial low/high (#001A12 → #004F39 → #0B5C45) for exact parity;
     * for OLED/Sepia/Slate we derive low/high from the palette's own background
     * so the adaptive shift stays coherent (e.g., OLED stays near-black, Sepia
     * stays warm brown) rather than snapping back to green.
     * Light theme: unchanged warm paper.
     */
    fun adaptiveBackground(
        base: Color,
        luxFactor: Float,
        isDark: Boolean,
    ): Color {
        return if (isDark) {
            // Preserve exact Folio green editorial values when base is the default deep green
            val isFolioGreen = base == FolioDeepGreen
            val low: Color
            val high: Color
            val mid = base
            if (isFolioGreen) {
                // Original curated values: 0 -> #001A12, 0.5 -> #004F39, 1 -> #0B5C45
                low = Color(0xFF001A12)
                high = FolioDeepGreenContainer
            } else {
                // Generic palette-coherent derivation: darken/lighten the selected palette's base
                // Low: base darkened toward black (keep editorial, not pure black)
                low = lerp(Color.Black, base, 0.58f)
                // High: base lightened toward white / container (subtle, not washed)
                high = lerp(base, Color.White, 0.14f)
            }
            when {
                luxFactor < 0.5f -> lerp(low, mid, luxFactor * 2f)
                else -> lerp(mid, high, (luxFactor - 0.5f) * 2f)
            }
        } else {
            // Light: 0 -> #EDE6D1 (darker warm for low light), 0.5 -> #FBF6EC (base paper), 1 -> #FFFBF0 (bright)
            val low = FolioPaperContainer // #F1E8D2 slightly darker than #EDE6D1 but keep palette
            val mid = base // FolioPaper #FBF6EC
            val high = Color(0xFFFFFBF0)
            when {
                luxFactor < 0.5f -> lerp(low, mid, luxFactor * 2f)
                else -> lerp(mid, high, (luxFactor - 0.5f) * 2f)
            }
        }
    }

    /**
     * Ensure [text] vs [background] meets [target] contrast by moving text
     * towards white (dark bg) or black (light bg). Returns adjusted text color.
     * If already meets target, returns original.
     */
    fun ensureContrast(
        background: Color,
        text: Color,
        target: Double = TARGET_CONTRAST,
        maxSteps: Int = 20,
    ): Color {
        val current = contrastRatio(background, text)
        if (current >= target) return text

        val bgLum = relativeLuminance(background)
        val isDarkBg = bgLum < 0.5
        // Target direction: dark bg -> lighten to white, light bg -> darken to black
        val extreme = if (isDarkBg) Color.White else Color.Black

        // Binary search lerp fraction to just meet target
        var low = 0f
        var high = 1f
        var best = text
        repeat(maxSteps) {
            val mid = (low + high) / 2f
            val candidate = lerp(text, extreme, mid)
            val ratio = contrastRatio(background, candidate)
            if (ratio >= target) {
                best = candidate
                high = mid
            } else {
                low = mid
            }
        }
        // If even extreme doesn't reach target (rare), return best found
        return best
    }

    /**
     * Full adaptive pair for given lux and theme.
     * Returns (background, onBackground) that maintains TARGET_CONTRAST.
     */
    fun adaptivePair(
        baseBackground: Color,
        baseOnBackground: Color,
        lux: Float,
        isDark: Boolean,
        target: Double = TARGET_CONTRAST,
    ): Pair<Color, Color> {
        val factor = luxToFactor(lux)
        val bg = adaptiveBackground(baseBackground, factor, isDark)
        val text = ensureContrast(bg, baseOnBackground, target)
        return bg to text
    }
}
