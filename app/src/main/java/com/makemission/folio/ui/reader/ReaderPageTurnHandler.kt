package com.makemission.folio.ui.reader

/**
 * Bridge for hardware volume-key page turns (§3 Frictionless Navigation).
 *
 * [MainActivity] dispatches volume keys here; [ReadingScreen] registers its
 * handler while composed. Keeps Activity decoupled from Compose navigation.
 */
object ReaderPageTurnHandler {
    var onVolumeKey: ((isVolumeUp: Boolean) -> Unit)? = null
}
