package com.lumaos.pro.launcher.settings.studio

/**
 * Undo/redo history for the Studio draft.
 *
 * Pure Kotlin with no Compose/Android dependency so the transaction rules are
 * unit-tested on the JVM: edits push the previous value, undo/redo move a
 * cursor without destroying the redo branch until a new edit is made, and
 * [reset] (Apply/Revert/external saved-state change) starts a clean history.
 * Nothing here touches persistence — applying the draft remains the single
 * `SettingsViewModel.applyStudio` path.
 */
class StudioDraftSession<T>(initial: T) {
    private val history = StudioHistory(initial)
    private var lastSaved = initial

    val current: T get() = history.current
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /** Keep an unsaved draft when a tab is removed; accept external edits only if clean. */
    fun syncSaved(saved: T) {
        if (saved == lastSaved) return
        val wasClean = current == lastSaved
        lastSaved = saved
        if (wasClean || current == saved) history.reset(saved)
    }

    fun edit(next: T) = history.edit(next)
    fun undo(): T = history.undo()
    fun redo(): T = history.redo()
    fun reset(saved: T) {
        lastSaved = saved
        history.reset(saved)
    }
}

class StudioHistory<T>(initial: T) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records an edit. No-op when [next] equals the current value. */
    fun edit(next: T) {
        if (next == current) return
        undoStack.addLast(current)
        redoStack.clear()
        current = next
    }

    fun undo(): T {
        if (undoStack.isEmpty()) return current
        redoStack.addLast(current)
        current = undoStack.removeLast()
        return current
    }

    fun redo(): T {
        if (redoStack.isEmpty()) return current
        undoStack.addLast(current)
        current = redoStack.removeLast()
        return current
    }

    /** Starts over from [value] (applied, reverted, or externally reloaded). */
    fun reset(value: T) {
        undoStack.clear()
        redoStack.clear()
        current = value
    }
}
