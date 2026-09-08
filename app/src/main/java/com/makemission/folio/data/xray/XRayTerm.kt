package com.makemission.folio.data.xray

/**
 * X-Ray term — distinctive noun/phrase for a chapter, scored by TF-IDF.
 * Pure on-device, no network or external dictionary.
 */
data class XRayTerm(
    val term: String,              // display form, e.g. "Mara"
    val normalized: String,        // lowercased
    val score: Double,
    val chapterIndices: List<Int>, // chapters where it appears (for tap detail)
    val totalFrequency: Int,
)
