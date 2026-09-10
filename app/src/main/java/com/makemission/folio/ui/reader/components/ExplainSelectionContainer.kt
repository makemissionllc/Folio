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

private class FolioTextActionModeCallback(
    private val context: Context,
    var rect: Rect = Rect.Zero,
    var onCopyRequested: (() -> Unit)? = null,
    var onExplainRequested: (() -> Unit)? = null,
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
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item!!.itemId) {
            MENU_ITEM_COPY -> onCopyRequested?.invoke()
            MENU_ITEM_EXPLAIN -> onExplainRequested?.invoke()
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
    private val onCopyRequest: (() -> Unit)?,
    private val onExplainRequest: ((String) -> Unit)?,
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
 * Wraps [SelectionContainer] with Folio's "Explain" toolbar item.
 * Double-tap still goes through existing [DictionaryRepository.lookup] flow;
 * long-press selection now shows "Explain" alongside "Copy" and opens the same popup.
 */
@Composable
fun ExplainSelectionContainer(
    onExplainRequested: (String) -> Unit,
    onCopyRequested: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current

    val toolbar = remember(view, context) {
        FolioSelectionToolbar(
            view = view,
            context = context,
            onCopyRequest = { onCopyRequested?.invoke() },
            onExplainRequest = { selected -> onExplainRequested(selected) }
        )
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
