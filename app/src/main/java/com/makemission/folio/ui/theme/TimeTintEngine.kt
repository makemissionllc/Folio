package com.makemission.folio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * Time-aware ambient tinting — warm/redder tones in evening, neutral by day.
 *
 * Pure on-device, deterministic, system-clock based (no location/network).
 * Builds on top of existing palette + Colorimetric Contrast, not rewriting them.
 * Coordinates sensibly: palette → adaptive contrast (lux) → time tint (warmth),
 * so the three systems layer without fighting or producing muddy colors.
 *
 * Gradual: warmth is a smooth curve over the day, not a jarring toggle.
 * Editorial: subtle warm shift (8–14% lerp), not a harsh Night-Shift overlay.
 */
object TimeTintEngine {

    /**
     * Warmth 0..1 for given hour/minute.
     * 07:00–17:00 → 0 (day neutral)
     * 17:00–20:00 → 0→0.6 (evening ramp)
     * 20:00–23:00 → 0.6→1.0 (night warm)
     * 23:00–05:00 → 1.0 (late night peak)
     * 05:00–07:00 → 1.0→0 (dawn cool-down)
     */
    fun warmthFor(hour: Int, minute: Int): Float {
        val h = hour + minute / 60f
        return when {
            h >= 7f && h < 17f -> 0f
            h >= 17f && h < 20f -> ((h - 17f) / 3f) * 0.6f // 0→0.6
            h >= 20f && h < 23f -> 0.6f + ((h - 20f) / 3f) * 0.4f // 0.6→1
            h >= 23f || h < 5f -> 1f
            else -> { // 5–7
                1f - ((h - 5f) / 2f) // 1→0
            }
        }.coerceIn(0f, 1f)
    }

    /** Current warmth for system clock. */
    fun currentWarmth(): Float {
        val now = LocalTime.now()
        return warmthFor(now.hour, now.minute)
    }

    /**
     * Apply subtle warm tint to an already-resolved background/text pair
     * (which may already include palette + adaptive contrast). This keeps
     * the editorial palette coherent and ensures adaptive's WCAG correction
     * already happened before warming.
     *
     * Dark: background warms toward deep ember (#2E1A0A) / text toward warm paper;
     * Light: both warm toward sepia. Lerp fractions are editorially tuned:
     * bg up to 14% (dark) / 10% (light), text up to 8%, so never harsh or muddy.
     */
    fun tintedPair(
        background: Color,
        onBackground: Color,
        warmth: Float,
        isDark: Boolean,
    ): Pair<Color, Color> {
        if (warmth <= 0.01f) return background to onBackground
        val w = warmth.coerceIn(0f, 1f)
        return if (isDark) {
            // Dark palettes: warm toward ember/burnt umber, preserves deep-green/OLED/Slate identity
            val warmBgTarget = Color(0xFF2E1A0A) // deep warm ember
            val warmTextTarget = Color(0xFFFFE8C8) // warm paper text
            // Blend amount is subtle: bg up to 14%, text up to 7% at peak warmth
            val bg = lerp(background, warmBgTarget, w * 0.14f)
            val txt = lerp(onBackground, warmTextTarget, w * 0.07f)
            // Ensure we didn't break 7:1 — re-ensure toward extremes if needed but keep warmth
            // Only nudge text toward warm target already, then ensure contrast if needed via lerp to white
            val ensured = AdaptiveContrastEngine.ensureContrast(bg, txt, AdaptiveContrastEngine.TARGET_CONTRAST)
            bg to ensured
        } else {
            // Light (paper): warm toward cream/sepia
            val warmBgTarget = Color(0xFFF5E0B8) // warm sepia paper
            val warmTextTarget = Color(0xFF4A3420) // warm ink
            val bg = lerp(background, warmBgTarget, w * 0.10f)
            val txt = lerp(onBackground, warmTextTarget, w * 0.06f)
            val ensured = AdaptiveContrastEngine.ensureContrast(bg, txt, AdaptiveContrastEngine.TARGET_CONTRAST)
            bg to ensured
        }
    }
}

/**
 * Remember current warmth that updates gradually as time passes.
 * Polls every 60s (and on composition) — deterministic, no location.
 */
@Composable
fun rememberTimeWarmth(): State<Float> {
    val warmth = remember { mutableStateOf(TimeTintEngine.currentWarmth()) }
    LaunchedEffect(Unit) {
        while (true) {
            warmth.value = TimeTintEngine.currentWarmth()
            // Align to next minute boundary for efficiency
            val now = LocalTime.now()
            val sec = now.second
            val delayMs = (60 - sec) * 1000L - now.nano / 1_000_000L + 500L
            delay(delayMs.coerceIn(1000L, 61000L))
        }
    }
    return warmth
}
