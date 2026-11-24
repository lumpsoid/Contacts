package org.fossify.contacts.helpers

/** How many items [runEach] handled, and how many of them failed. */
data class RunCounts(val done: Int, val failed: Int)

/**
 * Runs [action] on every item. An item whose action throws or returns false counts as failed,
 * and the remaining items still run. [onFailure] gets each exception.
 */
@Suppress("TooGenericExceptionCaught") // one bad item must not abort the rest, whatever it throws
fun <T> runEach(items: Iterable<T>, onFailure: (Exception) -> Unit = {}, action: (T) -> Boolean): RunCounts {
    var done = 0
    var failed = 0
    for (item in items) {
        try {
            if (action(item)) done++ else failed++
        } catch (e: Exception) {
            failed++
            onFailure(e)
        }
    }
    return RunCounts(done, failed)
}
