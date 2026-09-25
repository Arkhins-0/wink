package com.arkhins.wink.ui.components

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * The phone's own text-selection menu (the floating Cut · Copy · Paste bar) for the message box, with the
 * formatting in it, the way WhatsApp does: Bold beside Cut, Copy and Paste, and Format, which turns the same menu
 * into Italic, Underline and Strikethrough. [onFormat] gets the marker to wrap the selection in (see Formatting.kt).
 */
class FormattingToolbar(private val view: View, private val onFormat: (String) -> Unit) : TextToolbar {
    private var mode: ActionMode? = null
    private var rect = Rect.Zero
    private var copy: (() -> Unit)? = null
    private var paste: (() -> Unit)? = null
    private var cut: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null
    // Format tapped: the menu shows the other styles instead.
    private var formats = false

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.rect = rect
        copy = onCopyRequested
        paste = onPasteRequested
        cut = onCutRequested
        selectAll = onSelectAllRequested
        val open = mode
        if (open == null) {
            formats = false
            status = TextToolbarStatus.Shown
            mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            open.invalidate()
            open.invalidateContentRect()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        formats = false
        mode?.finish()
        mode = null
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = fill(menu).let { true }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = fill(menu).let { true }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                CUT -> cut?.invoke()
                COPY -> copy?.invoke()
                PASTE -> paste?.invoke()
                SELECT_ALL -> selectAll?.invoke()
                BOLD -> onFormat("*")
                ITALIC -> onFormat("_")
                UNDERLINE -> onFormat("__")
                STRIKE -> onFormat("~")
                MONO -> onFormat("```")
                FORMAT -> { formats = true; mode.invalidate(); return true }
                BACK -> { formats = false; mode.invalidate(); return true }
                else -> return false
            }
            if (item.itemId in listOf(CUT, COPY, PASTE)) mode.finish() else { formats = false; mode.invalidate() }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@FormattingToolbar.mode = null
            status = TextToolbarStatus.Hidden
            formats = false
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) {
            outRect.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        }
    }

    private fun fill(menu: Menu) {
        menu.clear()
        var order = 0
        fun add(id: Int, title: String) = menu.add(0, id, order++, title).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        if (formats) {
            add(BACK, "←")
            add(ITALIC, "Italic")
            add(UNDERLINE, "Underline")
            add(STRIKE, "Strikethrough")
            add(MONO, "Monospace")
            return
        }
        if (cut != null) add(CUT, "Cut")
        if (copy != null) add(COPY, "Copy")
        if (paste != null) add(PASTE, "Paste")
        // Only with words selected: formatting wraps a selection.
        if (copy != null) {
            add(BOLD, "Bold")
            add(FORMAT, "Format")
        }
        if (selectAll != null) add(SELECT_ALL, "Select all")
    }

    private companion object {
        const val CUT = 1
        const val COPY = 2
        const val PASTE = 3
        const val SELECT_ALL = 4
        const val BOLD = 10
        const val FORMAT = 11
        const val ITALIC = 12
        const val UNDERLINE = 13
        const val STRIKE = 14
        const val BACK = 15
        const val MONO = 16
    }
}
