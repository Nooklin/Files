package org.nooklin.files.editor

/** One change of the text: at start, the text removed was replaced by the text inserted. */
class TextEdit(val start: Int, val removed: String, val inserted: String, val timeMs: Long)

/** The result of an undo or redo: the new text and where the cursor goes. */
class UndoResult(val text: String, val cursor: Int)

/**
 * Undo and redo for the editor. Only the changed part of the text is kept, not a copy of the whole file.
 * Typing or deleting one character after another within a second counts as one step.
 */
class UndoStack(private val maxEdits: Int = 500, private val maxChars: Int = 2_000_000) {
    private val undo = ArrayList<TextEdit>()
    private val redo = ArrayList<TextEdit>()
    private var chars = 0

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
        chars = 0
    }

    /** Call for every change of the text. A change of only the cursor is ignored. */
    fun record(old: String, new: String, nowMs: Long) {
        if (old == new) return
        var prefix = 0
        val limit = minOf(old.length, new.length)
        while (prefix < limit && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val removed = old.substring(prefix, old.length - suffix)
        val inserted = new.substring(prefix, new.length - suffix)
        redo.clear()
        val last = undo.lastOrNull()
        if (last != null && nowMs - last.timeMs < 1000 && removed.isEmpty() && last.removed.isEmpty() &&
            prefix == last.start + last.inserted.length && !inserted.contains('\n') && !last.inserted.contains('\n')
        ) {
            // More typing right after the previous typing
            undo[undo.lastIndex] = TextEdit(last.start, "", last.inserted + inserted, nowMs)
            chars += inserted.length
        } else if (last != null && nowMs - last.timeMs < 1000 && inserted.isEmpty() && last.inserted.isEmpty() &&
            prefix + removed.length == last.start
        ) {
            // Backspace again
            undo[undo.lastIndex] = TextEdit(prefix, removed + last.removed, "", nowMs)
            chars += removed.length
        } else {
            undo.add(TextEdit(prefix, removed, inserted, nowMs))
            chars += removed.length + inserted.length
        }
        while (undo.size > maxEdits || (chars > maxChars && undo.size > 1)) {
            val dropped = undo.removeAt(0)
            chars -= dropped.removed.length + dropped.inserted.length
        }
    }

    fun undo(text: String): UndoResult? {
        val edit = undo.removeLastOrNull() ?: return null
        redo.add(edit)
        val result = text.replaceRange(edit.start, edit.start + edit.inserted.length, edit.removed)
        return UndoResult(result, edit.start + edit.removed.length)
    }

    fun redo(text: String): UndoResult? {
        val edit = redo.removeLastOrNull() ?: return null
        undo.add(edit)
        val result = text.replaceRange(edit.start, edit.start + edit.removed.length, edit.inserted)
        return UndoResult(result, edit.start + edit.inserted.length)
    }
}
