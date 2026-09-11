package com.makemission.folio.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.cache.TruePageCache
import com.makemission.folio.data.epub.EpubParser

/**
 * True-Page Calculation Engine (§5).
 *
 * Off-screen virtual canvas / measurement pass that pre-computes the entire
 * book's text layout for the current screen dimensions and font size.
 *
 * Problem: reflowable EPUB text breaks differently per screen, so "location"
 * numbers are vague. Solution: measure every title/paragraph on a virtual
 * canvas constrained to the current column width, sum heights, then inject
 * synthetic page breaks every [availableHeightPx] (phone) or
 * [availableHeightPx * 2] (tablet spread — physical page turns, not columns).
 *
 * Cached: recalculates only when device rotates or typography changes
 * (screenWidthDp / screenHeightDp / orientation / fontScale / density /
 * bionicEnabled / chapters), not on every scroll or recomposition.
 *
 * Structure inspired by book-story-master's ReaderLayout / pagination ideas,
 * implementation is Folio-specific and uses [rememberTextMeasurer].
 */
data class TruePageInfo(
    val totalPages: Int,
    val pageForFlatIndex: (Int) -> Int,
) {
    fun pageFor(flatIndex: Int): Int = pageForFlatIndex(flatIndex)
}

/**
 * Phone: single-column. Tablet: two-column spread — [isTabletLandscape]
 * doubles the per-screen height (physical page turns).
 *
 * @param chapters whole book
 * @param isTabletLandscape true for landscape >=840dp spread
 * @param bionicEnabled affects measure (bold first syllable slightly wider)
 * @param bookId optional for disk caching (smart cache with hash invalidation)
 * @param fileHash optional file hash for cache invalidation (reuses dedup)
 */
@Composable
fun rememberTruePageState(
    chapters: List<EpubParser.EpubChapter>,
    isTabletLandscape: Boolean,
    bionicEnabled: Boolean,
    bookId: String? = null,
    fileHash: String? = null,
): TruePageInfo {
    val textMeasurer = rememberTextMeasurer()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val typography = MaterialTheme.typography

    // Cache keys — rotate or font/typography change triggers recompute.
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val orientation = configuration.orientation
    val fontScale = density.fontScale
    val densityValue = density.density

    // Use chapters content hash as part of key (size + first title hash)
    val chaptersKey = remember(chapters) {
        // Simple stable hash: size + total chars + first title
        var h = chapters.size * 31
        h = h * 31 + chapters.sumOf { it.paragraphs.sumOf { p -> p.length } }
        h = h * 31 + (chapters.firstOrNull()?.title?.hashCode() ?: 0)
        h
    }

    val context = LocalContext.current
    val configKey = remember(screenWidthDp, screenHeightDp, orientation, fontScale, densityValue, bionicEnabled, isTabletLandscape, chaptersKey) {
        TruePageCache.configKey(screenWidthDp, screenHeightDp, orientation, fontScale, densityValue, bionicEnabled, isTabletLandscape, chaptersKey)
    }

    return remember(
        screenWidthDp, screenHeightDp, orientation,
        fontScale, densityValue, bionicEnabled,
        isTabletLandscape, chaptersKey, configKey, bookId, fileHash,
    ) {
        if (chapters.isEmpty()) {
            return@remember TruePageInfo(totalPages = 1, pageForFlatIndex = { 1 })
        }

        // Smart disk cache: try to load previously computed pages for this book+config+hash
        if (bookId != null) {
            try {
                val cached = TruePageCache.load(context, bookId, configKey, fileHash)
                if (cached != null && cached.prefixSums.isNotEmpty()) {
                    val totalPages = cached.totalPages
                    val prefixSums = cached.prefixSums
                    val perScreen = cached.perScreenHeightPx
                    val pageFor: (Int) -> Int = { flatIndex ->
                        val clamped = flatIndex.coerceIn(0, prefixSums.size - 2)
                        val heightBefore = prefixSums[clamped]
                        val page = if (perScreen > 0) (heightBefore / perScreen) + 1 else 1
                        page.coerceIn(1, totalPages)
                    }
                    return@remember TruePageInfo(totalPages = totalPages, pageForFlatIndex = pageFor)
                }
            } catch (_: Exception) {}
        }

        // ---- Available dimensions (virtual canvas) ----
        // Horizontal: phone 20dp each side, tablet: 12dp outer + 14dp inner per column + 1dp gutter
        val availableWidthPx: Int = with(density) {
            val widthDp = if (isTabletLandscape) {
                // (screenWidth - 12*2 - 14*4 -1) /2
                val totalH = 12.dp * 2 + 14.dp * 4 + 1.dp
                ((screenWidthDp.dp - totalH) / 2).coerceAtLeast(120.dp)
            } else {
                (screenWidthDp.dp - 40.dp).coerceAtLeast(120.dp)
            }
            widthDp.toPx().toInt().coerceAtLeast(100)
        }
        // Vertical: viewport height ~ screenHeight minus chrome (TopAppBar ~64dp + statusBars ~24dp + bottom chrome ~56dp + paddings)
        // Using 140dp deduction matches ReadingScreen's Box padding + contentPadding (top 8 + bottom statusBars+56 + topBar)
        val availableHeightPx: Int = with(density) {
            val availDp = (screenHeightDp.dp - 140.dp).coerceAtLeast(200.dp)
            availDp.toPx().toInt().coerceAtLeast(200)
        }
        val perScreenHeightPx = if (isTabletLandscape) availableHeightPx * 2 else availableHeightPx

        // Styles matching ReadingScreen content
        val bodyStyle = if (isTabletLandscape) typography.bodyMedium else typography.bodyLarge
        val titleStyle = if (isTabletLandscape) typography.titleMedium else typography.headlineSmall

        // Fixed non-text heights
        val diagramHeightPx = with(density) { 172.dp.toPx().toInt() } // 140 + 16*2
        val gapHeightPx = with(density) { 17.dp.toPx().toInt() } // 8+1+8
        val titleGapHeightPx = with(density) { 0.dp.toPx().toInt() } // title padding not counted separately; measured via text

        // Measure each flat item's height on the virtual canvas.
        // Flat order matches VelocityEstimator / ReadingFlatMapper / ReadingScreen LazyColumn:
        // for each chapter: title (1), paragraphs (N), diagram after ch0 (1 if idx==0), gap (1)
        val flatHeights = mutableListOf<Int>() // height per flatIndex
        for ((chIdx, ch) in chapters.withIndex()) {
            // title
            val titleHeight = try {
                textMeasurer.measure(
                    text = AnnotatedString(ch.title),
                    style = titleStyle,
                    constraints = Constraints(maxWidth = availableWidthPx),
                ).size.height.coerceAtLeast(with(density) { 28.dp.toPx().toInt() })
            } catch (_: Exception) {
                with(density) { 32.dp.toPx().toInt() }
            }
            // Add title + its vertical padding (top 28 / bottom 12 for phone; 20/10 tablet)
            val titlePaddingPx = with(density) { if (isTabletLandscape) 30.dp.toPx().toInt() else 40.dp.toPx().toInt() }
            flatHeights.add(titleHeight + titlePaddingPx + titleGapHeightPx)

            // paragraphs
            for (para in ch.paragraphs) {
                val annotated: AnnotatedString = if (bionicEnabled) {
                    try {
                        BionicReading.toBionicAnnotated(para, BionicReading.boldSpan())
                    } catch (_: Exception) {
                        AnnotatedString(para)
                    }
                } else {
                    AnnotatedString(para)
                }
                val h = try {
                    textMeasurer.measure(
                        text = annotated,
                        style = bodyStyle,
                        constraints = Constraints(maxWidth = availableWidthPx),
                    ).size.height
                } catch (_: Exception) {
                    // Fallback: estimate via chars
                    val avgCharsPerLine = (availableWidthPx / with(density) { 8.dp.toPx() }).toInt().coerceAtLeast(20)
                    val lines = (para.length / avgCharsPerLine).coerceAtLeast(1) + 1
                    lines * with(density) { bodyStyle.lineHeight.toPx().toInt().coerceAtLeast(20) }
                }
                // paragraph bottom padding 14dp phone / 12dp tablet
                val paraPad = with(density) { if (isTabletLandscape) 12.dp.toPx().toInt() else 14.dp.toPx().toInt() }
                flatHeights.add((h + paraPad).coerceAtLeast(with(density) { 12.dp.toPx().toInt() }))
            }

            if (chIdx == 0) {
                flatHeights.add(diagramHeightPx)
            }
            flatHeights.add(gapHeightPx)
        }

        val totalHeightPx = flatHeights.sum()
        val totalPages = if (totalHeightPx <= 0 || perScreenHeightPx <= 0) 1
        else ((totalHeightPx + perScreenHeightPx - 1) / perScreenHeightPx).coerceAtLeast(1)

        // Prefix sums for page lookup
        val prefixSums = IntArray(flatHeights.size + 1)
        for (i in flatHeights.indices) {
            prefixSums[i + 1] = prefixSums[i] + flatHeights[i]
        }

        val pageFor: (Int) -> Int = { flatIndex ->
            val clamped = flatIndex.coerceIn(0, flatHeights.size.coerceAtLeast(1) - 1)
            val heightBefore = prefixSums[clamped]
            val page = (heightBefore / perScreenHeightPx) + 1
            page.coerceIn(1, totalPages)
        }

        // Save to disk cache for instant reopen (hash-validated)
        if (bookId != null) {
            try { TruePageCache.save(context, bookId, configKey, fileHash, totalPages, prefixSums, perScreenHeightPx) } catch (_: Exception) {}
        }

        TruePageInfo(totalPages = totalPages, pageForFlatIndex = pageFor)
    }
}

/**
 * Tablet helper: earliest visible page across both columns of the spread.
 * Left column holds chapters.take(mid), right holds drop(mid). The spread's
 * earliest content is min(leftPage, rightPage).
 */
@Composable
fun rememberTruePageStateForTablet(
    chapters: List<EpubParser.EpubChapter>,
    leftFlatIndex: Int,
    rightFlatIndex: Int,
    bionicEnabled: Boolean,
): Pair<TruePageInfo, Int> {
    // Single TruePageInfo for whole book measured at tablet column width
    val info = rememberTruePageState(
        chapters = chapters,
        isTabletLandscape = true,
        bionicEnabled = bionicEnabled,
    )
    // Need flat mapping from column-local flat to global linear flat.
    // For current page we approximate using left column's global height.
    // Compute left overall flat = leftFlatIndex (since left is first half)
    // For simplicity earliest page is left's page (left is earliest content).
    // If right column is not empty and left is at end, right may be later.
    val mid = (chapters.size + 1) / 2
    // Count flats before mid for height offset not needed because info already
    // measured whole book linearly; leftFlatIndex alone maps to prefix height
    // from 0, which is earliest. So just use left.
    val current = info.pageFor(leftFlatIndex.coerceIn(0, Int.MAX_VALUE))
    // If left is at the very end and right is scrolled, take min
    // (right's global flat = flatsInLeftHalf + rightFlatIndex)
    // flatsInLeftHalf approx: mid chapters' flat count
    // We could compute but earliest is still left, so keep left's page.
    return info to current
}
