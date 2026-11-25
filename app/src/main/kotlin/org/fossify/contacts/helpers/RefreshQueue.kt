package org.fossify.contacts.helpers

/**
 * Coalesces contact-list refreshes: a request made while a load runs is kept
 * (its tab mask OR-ed in) and handed back when that load finishes.
 * Main thread only: callers and the load callback both run on the main looper,
 * so no synchronization is needed.
 */
class RefreshQueue {
    private var running = false
    private var pendingMask = 0

    /** True when the caller should load now; false when a load runs and [mask] waits. */
    fun request(mask: Int): Boolean {
        if (running) {
            pendingMask = pendingMask or mask
            return false
        }
        running = true
        return true
    }

    /** Ends the running load; returns the mask to load next, 0 when nothing waits. */
    fun finish(): Int {
        val next = pendingMask
        pendingMask = 0
        running = false
        return next
    }
}
