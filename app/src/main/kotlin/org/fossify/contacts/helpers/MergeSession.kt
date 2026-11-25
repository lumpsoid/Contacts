package org.fossify.contacts.helpers

import android.net.Uri
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.contacts.models.MergeRunResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The merge run of this process. A run outlives the screen that started it, so a reopened screen shows the
 * run and its result instead of scanning and merging again. One run at a time.
 */
object MergeSession {
    /** What the result page needs besides the result: plan names, the destination's name, the backup. */
    data class Labels(val names: Map<String, String>, val destinationName: String, val backupUri: Uri?)

    sealed class State {
        abstract val labels: Labels

        data class Running(val done: Int, val total: Int, override val labels: Labels) : State()

        data class Done(val result: MergeRunResult, override val labels: Labels) : State()
    }

    private val cancelled = AtomicBoolean(false)
    private var owner: Any? = null
    private var listener: ((State) -> Unit)? = null

    /** The run in progress or its unseen result; null when there is none. */
    @Volatile
    var state: State? = null
        private set

    /**
     * Starts [run] on a background thread; false when a run is already going or its result is unseen.
     * [run] gets the cancel check and the progress callback.
     */
    @Synchronized
    fun start(
        total: Int,
        labels: Labels,
        run: (isCancelled: () -> Boolean, onProgress: (Int, Int) -> Unit) -> MergeRunResult
    ): Boolean {
        if (state != null) return false
        cancelled.set(false)
        update(State.Running(0, total, labels))
        ensureBackgroundThread {
            val result = run({ cancelled.get() }) { done, all -> update(State.Running(done, all, labels)) }
            update(State.Done(result, labels))
        }
        return true
    }

    fun cancel() = cancelled.set(true)

    /** Sends every state, starting with the current one, to [onState] until [stopObserving] by [by]. */
    @Synchronized
    fun observe(by: Any, onState: (State) -> Unit) {
        owner = by
        listener = onState
        state?.let(onState)
    }

    /** Stops the listener of [by]; a newer screen's listener stays. */
    @Synchronized
    fun stopObserving(by: Any) {
        if (owner === by) {
            owner = null
            listener = null
        }
    }

    /** Forgets a finished run once a screen has shown its result. */
    @Synchronized
    fun clearDone() {
        if (state is State.Done) state = null
    }

    @Synchronized
    private fun update(newState: State) {
        state = newState
        listener?.invoke(newState)
    }
}
