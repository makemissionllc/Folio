package com.makemission.folio.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makemission.folio.ui.theme.FolioAmber

/**
 * Reading typography & highlight appearance — Kindle / Apple Books–comparable
 * customization, adapted for Folio's editorial serif. Ideas adapted from the
 * reference APK's DisplayOptions / AnnotationColors (multiple highlight colors,
 * type toggles), not copied.
 *
 * Persisted via SettingsRepository/DataStore and applied to existing rendering
 * (body TextStyle + TruePage virtual canvas + Knuth) the same way bionic/fontScale
 * already trigger recompute — no rewrite of engines, just new keys.
 */

// ---- Font family (built-in system families) ----
enum class FolioReadingFont(val displayName: String, val family: FontFamily) {
    DEFAULT("Serif (Default)", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    SERIF_GEORGIA("Literary Serif", FontFamily.Serif),
    MONO("Monospace", FontFamily.Monospace);

    companion object {
        fun fromKey(key: String?): FolioReadingFont =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

// ---- Font size — scale applied to baseline 17sp/15sp ----
enum class FolioFontSize(val scale: Float, val label: String) {
    SMALL(0.88f, "Small"),
    NORMAL(1.0f, "Normal"),
    LARGE(1.15f, "Large"),
    XLARGE(1.32f, "Extra Large");

    companion object {
        fun fromKey(key: String?): FolioFontSize =
            entries.firstOrNull { it.name == key } ?: NORMAL
        fun fromScale(scale: Float): FolioFontSize =
            entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: NORMAL
    }
}

// ---- Line spacing — multiplier on top of base lineHeight (1.0 = default 27sp for 17sp) ----
enum class FolioLineSpacing(val factor: Float, val label: String) {
    COMPACT(0.92f, "Compact"),
    NORMAL(1.0f, "Normal"),
    RELAXED(1.22f, "Relaxed"),
    LOOSE(1.48f, "Loose");

    companion object {
        fun fromKey(key: String?): FolioLineSpacing =
            entries.firstOrNull { it.name == key } ?: NORMAL
    }
}

// ---- Margin width — horizontal padding for reading columns ----
enum class FolioMargin(val label: String, val horizontalDp: Dp) {
    NARROW("Narrow", 12.dp),
    NORMAL("Normal", 20.dp),
    WIDE("Wide", 32.dp),
    EXTRA_WIDE("Extra Wide", 48.dp);

    companion object {
        fun fromKey(key: String?): FolioMargin =
            entries.firstOrNull { it.name == key } ?: NORMAL
    }

    fun tabletHorizontal(): Dp = when (this) {
        NARROW -> 10.dp
        NORMAL -> 14.dp
        WIDE -> 20.dp
        EXTRA_WIDE -> 28.dp
    }
}

// ---- Highlight appearance — multiple colors + fill/underline ----
enum class FolioHighlightColor(val label: String, val color: Color, val argb: Int) {
    AMBER("Amber", FolioAmber, 0xFFF7B538.toInt()),
    YELLOW("Yellow", Color(0xFFFFE55C), 0xFFFFE55C.toInt()),
    GREEN("Green", Color(0xFFA8E6A0), 0xFFA8E6A0.toInt()),
    PINK("Pink", Color(0xFFFF9EB8), 0xFFFF9EB8.toInt()),
    BLUE("Blue", Color(0xFF8EC8FF), 0xFF8EC8FF.toInt());

    companion object {
        fun fromKey(key: String?): FolioHighlightColor =
            entries.firstOrNull { it.name == key } ?: AMBER
        fun fromArgb(argb: Int): FolioHighlightColor =
            entries.firstOrNull { it.argb == argb } ?: when (argb) {
                FolioAmber.value.toInt() -> AMBER
                else -> entries.find { it.color.value.toLong() == argb.toLong() } ?: AMBER
            }
        // Legacy heuristic: map any stored argb to closest preset (for migration)
        fun fromStoredArgb(argb: Int): FolioHighlightColor {
            val exact = entries.firstOrNull { it.argb == argb }
            if (exact != null) return exact
            // Try Color equality with alpha stripped
            return AMBER
        }
    }
}

enum class FolioHighlightStyle(val label: String) {
    FILL("Fill"),
    UNDERLINE("Underline");

    companion object {
        fun fromKey(key: String?): FolioHighlightStyle =
            entries.firstOrNull { it.name == key } ?: FILL
    }
}
