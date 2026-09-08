# Folio Project: Complete Architectural & Feature Specification

## 1. Core Identity & Vision
**Folio** is a premium, distraction-free Android and tablet ebook reader. It bridges the gap between digital convenience and the tactile craft of traditional bookmaking. The architecture prioritizes fluid stylus interactions, magazine-quality typography, and dual-pane tablet layouts.

* **License:** Apache License 2.0
* **Target Platforms:** Android Phones & Tablets (Optimized for both form factors using Jetpack Compose)
* **Design Philosophy:** Editorial elegance, absolute typographic control, and zero-friction interactions.

---

## 2. Visual Design System & Aesthetics
* **Primary Backgrounds:** Deep, grounding green (`#004F39`) for library views and dark mode, replacing standard dark gray.
* **Active States & Tags:** Rich burgundy (`#780116`) for category selections, UI active states, and interactive buttons.
* **Accent & Highlighting:** Warm xanthous (`#F7B538`) for the default stylus highlight, reading progress bars, and subtle focal points.
* **Typography Pairing:** 
  * *Headers:* Bold, heavy sans-serif fonts (`Druk Wide` or `Helvetica Neue Bold`) for commanding editorial category titles.
  * *Body:* High-legibility classic serif typography for immersive reading.

---

## 3. Form Factor & Layout Architecture
* **Smart Layout Adaptation:**
  * **Phones:** Edge-to-edge immersive reading with streamlined bottom-sheet navigation and hardware volume-key page turning.
  * **Tablets:** Automatic two-column text pagination in landscape mode, mirroring a physical book spread, backed by a persistent master-detail navigation drawer.
* **Editorial Library Interface:** A curated visual grid featuring cohesive, flat illustration styles for empty states and onboarding.

---

## 4. Stylus & Annotation Engine
* **Zero-Friction Annotation:** Stylus input bypasses UI menus entirely. Touching the screen instantly draws a highlight.
* **True-Ink Rendering:** Highlights render using a *Multiply* blend mode to ensure typography beneath remains crisp and saturated, simulating real ink on paper.
* **Organic Physics:** Reads Android `MotionEvent` pressure and tilt data to dynamically adjust stroke width.
* **Lasso Extraction:** Users can circle diagrams or text blocks with the stylus to extract them or run local OCR.

---

## 5. Intelligent Algorithmic Features (Non-AI)
* **Syllable-Based Bionic Reading:** A text-processing algorithm that calculates structural word roots to bold initial syllables, accelerating visual tracking and reading speed.
* **Rolling-Weight Velocity Estimator:** Uses an Exponential Moving Average (EMA) algorithm combined with outlier rejection (e.g., leaving the device idle) to deliver precise chapter time-remaining metrics.
* **TF-IDF Concept Extraction:** A local statistical algorithm that scans chapter noun frequency against the entire book to automatically generate an "X-Ray" context index of characters and terms.
* **Spaced Repetition Vocabulary (SM-2):** Integrates SuperMemo-2 scheduling algorithms to track vocabulary lookups and optimize cognitive retention intervals.
* **True-Page Calculation Engine:** Pre-computes reflowable text on a virtual off-screen canvas to display absolute, stable page numbers (e.g., "Page 45 of 312") across device rotations.
* **Knuth-Plass Line Breaking:** Programmatically adjusts micro-kerning and word spacing across paragraphs to eliminate orphans and widows.
* **LCS Annotation Anchors:** Uses the Longest Common Subsequence diff algorithm to bind user highlights to contextual strings, surviving EPUB file updates without data loss.
* **Colorimetric Contrast Optimization:** Ties ambient light sensors to WCAG relative luminance calculations to dynamically shift hex values and maintain optimal 7:1 contrast.
* **Bounding-Box Image Expansion:** Automatically strips artificial white margins from embedded EPUB graphics for full-width tablet display.

---

## 6. Technical Foundation & Storage
* **Framework:** Native Jetpack Compose for reactive, responsive scaling across varying screen sizes.
* **Local Database:** Room / SQLite for local book indexing, progress tracking, coordinate-path storage for marginalia, and vocabulary metrics.
* **Parsers:** Robust native engines for EPUB3 and PDF files with stylesheet override controls.

---
# More details
To make Folio feel truly indispensable, we need features that quietly anticipate the user's needs without cluttering the interface.

Intelligent Typography & Lighting

Time-Aware Ambient Tinting: Instead of a harsh toggle for night mode, the app could monitor the time of day to automatically shift the screen's color temperature from a harsh blue to a warmer red tone.

Custom Formatting Overrides: Give users the ability to disable a publisher's default CSS styles. Allowing them to easily customize fonts and color palettes provides a highly tailored, professional layout.

Advanced Annotation Workflows

Data Portability: Enable users to easily select, copy, and share text to other Android applications. Exporting these highlights and margin notes as raw .md or JSON files seamlessly supports users who want to push their reading data into a local workspace or PostgreSQL database.

The "Lasso" Extraction: Let users draw a circle around a specific diagram or paragraph with the stylus to instantly extract it as an image or run OCR to copy the text to their clipboard.

Contextual Reading Aids

Character & Concept Index: Implement a contextual tracking feature to help readers keep up with characters, notable items, and complex ideas throughout a book. Surfacing this data in a minimalist bottom-sheet prevents users from getting lost in massive storylines without needing to scrub backward.

Offline Dictionary Integration: Build in a localized dictionary where a simple double-tap on a word instantly displays its definition in a sleek popup.

Frictionless Navigation

Hardware Page Turns: Allow users to map the physical volume up and down keys on their device to advance pages. This is critical for comfortable, single-handed reading when using Folio on a phone rather than a tablet.

Dynamic Progress Indicators: Display a minimalist book progress bar that users can tap to instantly jump to different sections of the text, while ensuring the UI element can be completely hidden to preserve a distraction-free, immersive state.

Relying on deterministic, on-device algorithms elevates the reading experience by processing text structurally and mathematically, keeping the application fast and entirely private.

Syllable-Based Bionic Reading: A text-processing algorithm that calculates the structural root of words—analyzing the onset, nucleus, and coda—to strategically bold the first syllable. By mapping common vowel clusters and identifying the first nucleus without awkwardly splitting syllables, this creates visual anchor points that guide the eye faster across the line. This technique encourages faster eye movement and significantly boosts both reading speed and comprehension.

Rolling-Weight Velocity Estimator: Instead of calculating "Time left in chapter" using a static Words-Per-Minute (WPM) average, implement an Exponential Moving Average (EMA) algorithm. It calculates the time delta between page turns, automatically tosses out outlier data using a standard deviation threshold (e.g., if a page turn takes 15 minutes, the user set the tablet down), and adjusts the prediction dynamically based on the character density of the upcoming text arrays.

TF-IDF "X-Ray" Concept Extraction: Use Term Frequency-Inverse Document Frequency (TF-IDF), a classic statistical algorithm, to scan the book's local database. It compares the frequency of nouns in a single chapter against their frequency across the entire book. This automatically extracts the most unique entities—characters, locations, and specific jargon—building a dynamic, locally generated context index for every book without needing any external server or inference.

Spaced Repetition Vocabulary (SM-2): Whenever a user looks up a definition, the app stores the term locally. By applying a deterministic scheduling algorithm like SuperMemo-2 (SM-2), the app calculates the optimal forgetting curve for each word. It can subtly surface these terms in a discrete review state, helping readers naturally expand their vocabulary based on cognitive retention intervals.

White-Space Layout Balancing: A two-pointer or sliding-window layout algorithm that evaluates the line lengths and character counts of a parsed EPUB chapter. It automatically adjusts kerning and margin padding to prevent orphaned words at the end of paragraphs, ensuring the digital typesetting mirrors the meticulous margins of a printed codex.


True-Page Calculation Engine

The Problem: Standard EPUB readers use abstract "Location" metrics because reflowable text breaks differently on every screen.

The Logic: Implement an off-screen virtual canvas that pre-computes the entire book's DOM structure. By calculating the exact screen-height segments based on the device's current dimensions and font size, the engine dynamically injects synthetic page breaks. This provides absolute page numbers (e.g., "Page 45 of 312") that only recalculate when the user rotates the device or alters typography settings.

Knuth-Plass Line Breaking (Orphan & Widow Control)

The Problem: Basic text rendering often leaves a single line of a paragraph stranded at the top or bottom of a page, ruining the editorial aesthetic.

The Logic: Adapt a line-breaking algorithm to programmatically score and adjust micro-kerning and word spacing across an entire paragraph. This eliminates widows (where the last lines of a paragraph fall at the top of a new page) and orphans, ensuring the text block is squared off perfectly like a traditional printed codex.

Longest Common Subsequence (LCS) Annotation Anchors

The Problem: When publishers push an update to an EPUB file to fix typos, the character offsets shift, causing readers to permanently lose their saved stylus highlights.

The Logic: Instead of storing an annotation at a static byte offset, use a diff algorithm (like LCS or Myers). The system saves the surrounding text string as a contextual anchor. If the book file is updated, the algorithm scans the new file, locates the logical shift in the text, and dynamically reattaches the user's marginalia to the correct location.

Colorimetric Contrast Optimization

The Problem: Standard auto-brightness merely lowers the backlight, which can make text look muddy and harder to read in low light.

The Logic: Tie the device's ambient light sensor directly to a relative luminance calculation (using the WCAG contrast formula). Instead of just dimming the screen, the algorithm dynamically shifts the exact hex values of the app's background and text to continuously maintain an optimal 7:1 contrast ratio as environmental lighting changes.

Bounding-Box Image Expansion

The Problem: Diagrams and illustrations in technical EPUBs often contain massive, hardcoded white margins that force the image to render awkwardly small on tablets and phones.

The Logic: Implement a lightweight contour-detection pass on embedded images. When a user taps a diagram, the algorithm calculates the exact bounding box of the non-white pixels and algorithmically strips the publisher's artificial margins, allowing the graphic to scale up and fully utilize the screen width.