package com.makemission.folio.ui.reader.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

private const val MENU_ITEM_COPY = 0
private const val MENU_ITEM_EXPLAIN = 1
private const val MENU_ITEM_HIGHLIGHT = 2

private class FolioTextActionModeCallback(
    private val context: Context,
    var rect: Rect = Rect.Zero,
    var onCopyRequested: (() -> Unit)? = null,
    var onExplainRequested: (() -> Unit)? = null,
    var onHighlightRequested: (() -> Unit)? = null,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        requireNotNull(menu)
        requireNotNull(mode)
        onCopyRequested?.let {
            menu.add(0, MENU_ITEM_COPY, 0, "Copy")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        onExplainRequested?.let {
            menu.add(0, MENU_ITEM_EXPLAIN, 1, "Explain")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        onHighlightRequested?.let {
            menu.add(0, MENU_ITEM_HIGHLIGHT, 2, "Highlight")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        // If Highlight was not present at onCreate (e.g. onHighlightRequested was null then but is now non-null
        // after highlight color/style prefs loaded), add it here. This handles the stale-toolbar case
        // where the ActionMode was created before the highlight callback was wired.
        if (menu != null && menu.findItem(MENU_ITEM_HIGHLIGHT) == null && onHighlightRequested != null) {
            menu.add(0, MENU_ITEM_HIGHLIGHT, 2, "Highlight")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            return true
        }
        return false
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item!!.itemId) {
            MENU_ITEM_COPY -> onCopyRequested?.invoke()
            MENU_ITEM_EXPLAIN -> onExplainRequested?.invoke()
            MENU_ITEM_HIGHLIGHT -> onHighlightRequested?.invoke()
            else -> return false
        }
        mode?.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {}
}

private class FloatingFolioCallback(
    val callback: FolioTextActionModeCallback
) : ActionMode.Callback2() {
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean =
        callback.onActionItemClicked(mode, item)

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean =
        callback.onCreateActionMode(mode, menu)

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean =
        callback.onPrepareActionMode(mode, menu)

    override fun onDestroyActionMode(mode: ActionMode?) = callback.onDestroyActionMode(mode)

    override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: android.graphics.Rect?) {
        val rect = callback.rect
        outRect?.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
    }
}

/**
 * Folio selection toolbar — extends existing double-tap DictionaryRepository flow
 * (don't rewrite it) with an "Explain" action for selected text ranges.
 *
 * Structural inspiration from book-story-master's SelectionContainer/TextActionModeCallback
 * (custom TextToolbar + ActionMode floating menu + clipboard-copy trick) only — no code copied.
 * On "Explain", the selected text is retrieved via the clipboard copy trick (copy → read
 * clipboard → restore) and forwarded to [onExplainRequested] which opens the same
 * [DictionaryPopup] (backed by the larger WordNet-derived gzipped dataset).
 */
private class FolioSelectionToolbar(
    private val view: View,
    context: Context,
    var onCopyRequest: (() -> Unit)?,
    var onExplainRequest: ((String) -> Unit)?,
    var onHighlightRequest: ((String) -> Unit)?,
) : TextToolbar {
    private var actionMode: ActionMode? = null
    private val callback = FolioTextActionModeCallback(context = context)
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        callback.rect = rect
        callback.onCopyRequested = {
            onCopyRequested?.invoke()
            onCopyRequest?.invoke()
        }
        callback.onExplainRequested = {
            val previousClip = clipboardManager.primaryClip
            onCopyRequested?.invoke()
            val selected = try {
                clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            } catch (_: Exception) { "" }
            // Restore previous clipboard to avoid clobbering user's copy
            try {
                if (previousClip != null) {
                    clipboardManager.setPrimaryClip(previousClip)
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, ""))
                }
            } catch (_: Exception) {}
            if (selected.isNotBlank()) onExplainRequest?.invoke(selected)
        }
        callback.onHighlightRequested = {
            val previousClip = clipboardManager.primaryClip
            onCopyRequested?.invoke()
            val selected = try {
                clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            } catch (_: Exception) { "" }
            try {
                if (previousClip != null) {
                    clipboardManager.setPrimaryClip(previousClip)
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, ""))
                }
            } catch (_: Exception) {}
            if (selected.isNotBlank()) onHighlightRequest?.invoke(selected)
        }

        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(
                FloatingFolioCallback(callback),
                ActionMode.TYPE_FLOATING
            )
        } else {
            actionMode?.invalidate()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }
}

/**
 * Wraps [SelectionContainer] with Folio's "Explain" and "Highlight" toolbar items.
 * Double-tap still goes through existing [DictionaryRepository.lookup] flow;
 * long-press selection now shows "Explain" and "Highlight" alongside "Copy" and opens the same popups/highlights.
 * Highlight via finger reuses the same Highlight entity/DAO + LCS anchor as stylus (bookId, chapterIndex, anchorText).
 */
@Composable
fun ExplainSelectionContainer(
    onExplainRequested: (String) -> Unit,
    onHighlightRequested: (String) -> Unit = {},
    onCopyRequested: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current

    // Use rememberUpdatedState so the toolbar always sees the latest lambdas
    // (highlight color/style may change via DataStore, and currentBookmarkPos/chapters change as user scrolls).
    // Previously toolbar was remembered with only view/context as keys, so its onHighlightRequest
    // stayed stale (captured old highlightColor/Style and old chapter), and after the
    // highlight customization session the Highlight menu could silently no-op or appear missing
    // because the stale lambda was considered null/mismatched.
    val currentExplain by androidx.compose.runtime.rememberUpdatedState(onExplainRequested)
    val currentHighlight by androidx.compose.runtime.rememberUpdatedState(onHighlightRequested)
    val currentCopy by androidx.compose.runtime.rememberUpdatedState(onCopyRequested)

    val toolbar = remember(view, context) {
        FolioSelectionToolbar(
            view = view,
            context = context,
            onCopyRequest = { currentCopy?.invoke() },
            onExplainRequest = { selected -> currentExplain(selected) },
            onHighlightRequest = { selected -> currentHighlight(selected) }
        )
    }
    // Keep toolbar's callbacks up-to-date when the upstream lambdas change (highlight color/style, chapter).
    androidx.compose.runtime.LaunchedEffect(currentExplain, currentHighlight, currentCopy) {
        toolbar.onExplainRequest = { selected -> currentExplain(selected) }
        toolbar.onHighlightRequest = { selected -> currentHighlight(selected) }
        toolbar.onCopyRequest = { currentCopy?.invoke() }
    }
    val isHidden by remember { derivedStateOf { toolbar.status == TextToolbarStatus.Hidden } }

    // Keep reference stable; isHidden triggers recomposition if needed
    @Suppress("UNUSED_VARIABLE")
    val hidden = isHidden

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer {
            content()
        }
    }
}
