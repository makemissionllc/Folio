package com.makemission.folio.ui.reader

/**
 * Bridge for hardware volume-key page turns (§3 Frictionless Navigation).
 *
 * [MainActivity] dispatches volume keys here; [ReadingScreen] registers its
 * handler while composed. Keeps Activity decoupled from Compose navigation.
 */
object ReaderPageTurnHandler {
    var onVolumeKey: ((isVolumeUp: Boolean) -> Unit)? = null
    /** Tablet keyboard bridge: true moves forward; false moves back. */
    var onKeyboardPageKey: ((isForward: Boolean) -> Unit)? = null
    var onKeyboardSearch: (() -> Unit)? = null
    /** Tablet keyboard bridge: opens the library search field. */
    var onKeyboardLibrarySearch: (() -> Unit)? = null
    /** Tablet keyboard bridge: clears the active library query or dismisses search. */
    var onKeyboardLibraryEscape: (() -> Unit)? = null
}
