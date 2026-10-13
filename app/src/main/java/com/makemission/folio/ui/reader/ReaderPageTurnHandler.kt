package com.makemission.folio.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    /** Tablet keyboard bridge: selects All, Reading, Want to Read, or Finished. */
    var onKeyboardLibraryShelf: ((shortcut: Int) -> Unit)? = null
    /** Tablet-only keyboard shortcut bridge for Ctrl+O. */
    var onKeyboardTabletAddBooks: (() -> Unit)? = null
    /** Tablet keyboard bridge: toggles the landscape Contents rail or opens the chapter list. */
    var onKeyboardTabletContents: (() -> Unit)? = null
    /** Tablet keyboard bridge: clears the active library query or dismisses search. */
    var onKeyboardLibraryEscape: (() -> Unit)? = null

    private val _libraryAddBooksRequests = MutableStateFlow(0L)
    val libraryAddBooksRequests: StateFlow<Long> = _libraryAddBooksRequests
    private var consumedAddBooksRequest = 0L

    /** Emits an Add Books request that the library observes when it is visible. */
    fun requestLibraryAddBooks() {
        _libraryAddBooksRequests.value += 1
    }

    fun consumeLibraryAddBooksRequest(token: Long): Boolean {
        if (token <= consumedAddBooksRequest) return false
        consumedAddBooksRequest = token
        return true
    }
}
